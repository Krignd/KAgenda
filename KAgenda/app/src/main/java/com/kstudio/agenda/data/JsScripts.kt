package com.kstudio.agenda.data

import org.json.JSONObject

/**
 * 注入到教务系统网页中的 JS 脚本。
 *
 * 说明：网页为 React SPA（金智 jwapp 框架），课表以 DOM 渲染在
 * “[class*='kbappTimetableDayColumnRoot']” 的 7 个日期列中，
 * 每个课程块包含 课程名/课程号/教师[周次]/教室/节次 文本。
 * 这里不依赖带 hash 的完整类名，只使用类名前缀匹配，降低改版影响。
 */
object JsScripts {

    /** 生成安全的 JSON 字符串字面量（用于拼接注入的学号/密码） */
    fun quote(value: String): String = JSONObject.quote(value)

    /**
     * 在页面最早时机挂钩 XMLHttpRequest / fetch。
     * 针对教务系统关键接口（getMyScheduleDetail / getSections / schoolCalendars / currentUser 等）
     * 同时捕获“请求体 + 响应体”，以 { url, request, response } 信封回传给 App。
     */
    val HOOKS = """
(function(){
  try {
    if (window.__kbHooked) { return; }
    window.__kbHooked = true;
    var KEYS = /getMyScheduleDetail|getSections|schoolCalendars|currentUser|getTermWeeks|global\.do|kb\.do/i;
    function brief(s, n){
      s = String(s === undefined || s === null ? '' : s);
      return s.length > n ? (s.slice(0, n) + '...[cut:' + s.length + ']') : s;
    }
    function post(url, reqBody, respText){
      try {
        url = String(url || '');
        if (!KEYS.test(url)) { return; }
        var envelope = { url: url, request: brief(reqBody, 2000), response: brief(respText, 300000), at: Date.now() };
        if (window.KebiaoBridge && window.KebiaoBridge.onApiData) {
          window.KebiaoBridge.onApiData(url, JSON.stringify(envelope));
        }
      } catch(e){}
    }
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function(method, url){
      try { this.__kbUrl = url; this.__kbMethod = method; } catch(e){}
      return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function(body){
      var xhr = this;
      try {
        xhr.addEventListener('load', function(){
          try { post(xhr.__kbUrl, body, xhr.responseText); } catch(e){}
        });
      } catch(e){}
      return origSend.apply(this, arguments);
    };
    if (window.fetch) {
      var origFetch = window.fetch;
      window.fetch = function(){
        var args = arguments;
        var url = '';
        var reqBody = '';
        try { url = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url ? args[0].url : ''); } catch(e){}
        try { reqBody = (args[1] && args[1].body) ? String(args[1].body) : ''; } catch(e){}
        return origFetch.apply(this, args).then(function(resp){
          try {
            if (KEYS.test(String(url || ''))) {
              resp.clone().text().then(function(t){ post(url, reqBody, t); }).catch(function(){});
            }
          } catch(e){}
          return resp;
        });
      };
    }
  } catch(e){}
})();
""".trimIndent()

    /**
     * 页面状态探针：登录表单（含同源 iframe 内）、验证码、课表网格、
     * “网络异常”页、登录错误提示等，返回 JSON。
     */
    val DETECT = """
(function(){
  try {
    function isVisible(el){
      try {
        if (!el) { return false; }
        var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        if (rect && rect.width === 0 && rect.height === 0) { return false; }
        var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (style && (style.display === 'none' || style.visibility === 'hidden')) { return false; }
        return true;
      } catch(e) { return !!el; }
    }
    function allDocs(){
      var list = [document];
      try {
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
          try { var d = iframes[i].contentDocument; if (d) { list.push(d); } } catch(e){}
        }
      } catch(e){}
      return list;
    }
    var docs = allDocs();
    var hasPwd = false;
    var captchaVisible = false;
    // 统一认证的登录表单在 iframe（login-normal.html）里，错误提示也在 iframe 内，
    // 因此这里必须聚合所有文档的文本与错误元素，否则“密码错误”会探测不到（表现为登录静默失败）。
    var allText = '';
    for (var i = 0; i < docs.length; i++) {
      try {
        var pwds = docs[i].querySelectorAll('input[type="password"]');
        for (var j = 0; j < pwds.length; j++) {
          if (isVisible(pwds[j])) { hasPwd = true; }
        }
        var cap = docs[i].getElementById ? docs[i].getElementById('captchaPasswor') : null;
        if (cap && isVisible(cap)) { captchaVisible = true; }
        try {
          if (docs[i].body) { allText += ' ' + String(docs[i].body.innerText || docs[i].body.textContent || ''); }
        } catch(e) {}
      } catch(e){}
    }
    var text = allText;
    var err = '';
    try {
      for (var d = 0; d < docs.length; d++) {
        var errEls = null;
        try { errEls = docs[d].querySelectorAll('#errPassword, .item-validate, .alert-danger, [class*="error"]'); } catch(e){}
        if (errEls) {
          for (var k = 0; k < errEls.length; k++) {
            var t = String(errEls[k].innerText || errEls[k].textContent || '').trim();
            if (t && isVisible(errEls[k])) { err = t; }
          }
        }
      }
      if (!err && /密码错误|用户名或密码错误|账号或密码错误|密码不正确|密码有误|invalid|incorrect/i.test(text)) {
        err = '页面提示凭据错误';
      }
    } catch(e){}
    return JSON.stringify({
      url: String(location.href || ''),
      title: String(document.title || ''),
      login: hasPwd,
      captcha: captchaVisible,
      grid: document.querySelectorAll('[class*="kbappTimetableDayColumnRoot"]').length > 0,
      app: text.indexOf('我的课表') >= 0,
      netError: text.indexOf('网络异常') >= 0,
      ssoError: text.indexOf('未认证授权') >= 0 || /unauthorized\s+service|service\s+not\s+found/i.test(text),
      error: err,
      snippet: text.replace(/\s+/g, ' ').slice(0, 240)
    });
  } catch(e) {
    return JSON.stringify({ url: '', title: '', login: false, captcha: false, grid: false, app: false, netError: false, ssoError: false, error: String(e), snippet: '' });
  }
})();
""".trimIndent()

    /**
     * 自动填写统一身份认证页面并提交（支持同源 iframe 内的表单，如 login-normal.html）。
     * 优先按统一身份认证的已知字段（#unPassword / input[name=username]、#pwPassword）
     * 定位，其次按 id/name/placeholder 关键词打分兜底。
     * 若出现验证码等无法自动处理的要素，会自然失败并回退为“网页手动登录”。
     */
    fun autoFill(studentId: String, password: String): String = """
(function(id, pwd){
  try {
    function setValue(el, v){
      try {
        var proto = (String(el.tagName || '').toUpperCase() === 'TEXTAREA')
          ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
        setter.call(el, v);
      } catch(e) { el.value = v; }
      try { el.dispatchEvent(new Event('input', { bubbles: true })); } catch(e){}
      try { el.dispatchEvent(new Event('change', { bubbles: true })); } catch(e){}
    }
    function isVisible(el){
      try {
        if (!el) { return false; }
        var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
        if (rect && rect.width === 0 && rect.height === 0) { return false; }
        var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
        if (style && (style.display === 'none' || style.visibility === 'hidden')) { return false; }
        return true;
      } catch(e) { return !!el; }
    }
    function collectDocs(){
      // 优先 iframe（统一认证的实际登录界面常在 login-normal.html 里），
      // 外层文档只是兼容性视图，直接提交可能携带过期的 execution token。
      var list = [];
      try {
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length; i++) {
          try {
            var d = iframes[i].contentDocument;
            if (d) { list.push({ doc: d, where: 'iframe' }); }
          } catch(e){}
        }
      } catch(e){}
      list.push({ doc: document, where: 'top' });
      return list;
    }
    var docs = collectDocs();
    var found = null;
    for (var i = 0; i < docs.length; i++) {
      var d = docs[i].doc;
      var pwds = null;
      try { pwds = d.querySelectorAll('input[type="password"]'); } catch(e){}
      if (!pwds) { continue; }
      for (var j = 0; j < pwds.length; j++) {
        if (isVisible(pwds[j])) { found = { doc: d, pwd: pwds[j], where: docs[i].where }; break; }
      }
      if (found) { break; }
    }
    if (!found) { return JSON.stringify({ done: false, reason: 'no-password-field', where: '' }); }
    var doc = found.doc;
    var pwdEl = found.pwd;

    var idEl = null;
    try { idEl = doc.querySelector('#unPassword'); } catch(e){}
    if (!idEl) { try { idEl = doc.querySelector('input[name="username"]'); } catch(e){} }
    if (!idEl) {
      var form = pwdEl.form || (pwdEl.closest ? pwdEl.closest('form') : null) || doc;
      var inputs = null;
      try { inputs = form.querySelectorAll('input'); } catch(e){}
      var best = -1;
      if (inputs) {
        for (var k = 0; k < inputs.length; k++) {
          var el = inputs[k];
          var tp = String(el.type || 'text').toLowerCase();
          if (tp === 'password' || tp === 'hidden' || tp === 'checkbox' || tp === 'submit' || tp === 'button') { continue; }
          var sig = String((el.id || '') + ' ' + (el.name || '') + ' ' + (el.placeholder || '')).toLowerCase();
          var score = 0;
          if (/user|account|login|name|xh|stu|学号|账号|用户名|学工号/.test(sig)) { score += 2; }
          if (tp === 'text' || tp === '') { score += 1; }
          if (score > best) { best = score; idEl = el; }
        }
      }
    }
    if (!idEl) { return JSON.stringify({ done: false, reason: 'no-username-field', where: found.where }); }

    setValue(idEl, id);
    setValue(pwdEl, pwd);

    var captchaVisible = false;
    try {
      var cap = doc.getElementById ? doc.getElementById('captchaPasswor') : null;
      if (cap && isVisible(cap)) { captchaVisible = true; }
    } catch(e){}

    var clicked = false;
    // 1) 已知的登录按钮（统一认证页面）
    try {
      var known = doc.querySelector('input.submit-btn')
        || doc.querySelector('input[name="submit"]')
        || doc.querySelector('button[type="submit"]')
        || doc.querySelector('input[type="submit"]');
      if (known) { known.click(); clicked = true; }
    } catch(e){}
    // 2) 文本为“登录”的按钮
    if (!clicked) {
      try {
        var btns = doc.querySelectorAll('button, input[type="button"], a, div[role="button"], input[type="submit"]');
        for (var b = 0; b < btns.length; b++) {
          var t = String(btns[b].innerText || btns[b].value || '').replace(/\s+/g, '');
          if (t === '登录' || t === '登陆' || t.indexOf('登录') === 0) { btns[b].click(); clicked = true; break; }
        }
      } catch(e){}
    }
    // 3) 表单提交兜底
    if (!clicked) {
      try { if (pwdEl.form) { pwdEl.form.submit(); clicked = true; } } catch(e){}
    }
    return JSON.stringify({ done: true, clicked: clicked, captcha: captchaVisible, where: found.where });
  } catch(e) {
    return JSON.stringify({ done: false, reason: String(e), where: '' });
  }
})(${quote(studentId)}, ${quote(password)});
""".trimIndent()

    /**
     * 自定义学校适配器的登录脚本包装：
     * 先把学号/密码写入页面全局变量 window.__kagendaUser / window.__kagendaPass，
     * 再执行适配器提供的 loginJs（可用这两个变量填充表单并提交；提交后 return 'ok'）。
     */
    fun customFill(loginJs: String, studentId: String, password: String): String = """
(function(){
  try {
    window.__kagendaUser = ${quote(studentId)};
    window.__kagendaPass = ${quote(password)};
    var result = (function(){
$loginJs
    })();
    return JSON.stringify(String(result === undefined ? 'ok' : result));
  } catch(e) {
    return JSON.stringify('错误：' + String(e));
  }
})();
""".trimIndent()

    /**
     * 从“我的课表”周网格中提取数据，返回 JSON：
     * { ok, meta: {semester, weekNo, weekRange, today, url, fetchedAt}, courses: [...] }
     */
    val EXTRACT = """
(function(){
  try {
    function textOf(el){
      try { return el ? String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim() : ''; } catch(e) { return ''; }
    }
    function firstByPrefix(root, prefix){
      try {
        var nodes = root.getElementsByTagName('*');
        for (var i = 0; i < nodes.length; i++) {
          var c = nodes[i].className;
          if (typeof c === 'string' && c.indexOf(prefix) >= 0) { return nodes[i]; }
        }
      } catch(e){}
      return null;
    }
    function allByPrefix(root, prefix){
      var out = [];
      try {
        var nodes = root.getElementsByTagName('*');
        for (var i = 0; i < nodes.length; i++) {
          var c = nodes[i].className;
          if (typeof c === 'string' && c.indexOf(prefix) >= 0) { out.push(nodes[i]); }
        }
      } catch(e){}
      return out;
    }
    function flexGrow(el){
      var raw = '';
      try { raw = String(el.style.getPropertyValue('flex') || ''); } catch(e){}
      var m = raw.match(/([0-9]+(\.[0-9]+)?)/);
      if (m) { return parseFloat(m[1]); }
      return 1;
    }
    var bodyText = document.body ? String(document.body.innerText || '') : '';
    var meta = { url: String(location.href || ''), fetchedAt: Date.now(), semester: '', weekNo: 0, weekRange: '', today: '' };
    var m = bodyText.match(/([0-9]{4})\s*(春|秋)季/);
    if (m) { meta.semester = m[1] + m[2] + '季'; }
    m = bodyText.match(/今天是\s*([0-9]+)月([0-9]+)日\s*星期([一二三四五六日])/);
    if (m) { meta.today = m[1] + '-' + m[2]; }
    m = bodyText.match(/第\s*([0-9]+)\s*周/);
    if (m) { meta.weekNo = parseInt(m[1], 10); }
    m = bodyText.match(/([0-9]{1,2}\/[0-9]{1,2})\s*~\s*([0-9]{1,2}\/[0-9]{1,2})/);
    if (m) { meta.weekRange = m[1] + '~' + m[2]; }
    var courses = [];
    var columns = document.querySelectorAll('[class*="kbappTimetableDayColumnRoot"]');
    for (var d = 0; d < columns.length; d++) {
      var col = columns[d];
      var day = d + 1;
      var period = 0;
      var kids = col.children;
      for (var i = 0; i < kids.length; i++) {
        var k = kids[i];
        var grow = flexGrow(k);
        if (!(grow > 0)) { grow = 1; }
        var item = firstByPrefix(k, 'kbappTimetableCourseRenderCourseItem___');
        if (!item) { period += grow; continue; }
        var title = textOf(firstByPrefix(item, 'title___'));
        var code = textOf(firstByPrefix(item, 'courseCode'));
        var tag = textOf(firstByPrefix(item, 'benType'));
        var infos = [];
        var infoEls = allByPrefix(item, 'InfoText');
        for (var j = 0; j < infoEls.length; j++) { infos.push(textOf(infoEls[j])); }
        var allText = textOf(item);
        var weeks = '';
        var wm = allText.match(/\[?([0-9]{1,2}(?:-[0-9]{1,2})?(?:,[0-9]{1,2}(?:-[0-9]{1,2})?)*)周\]?/);
        if (wm) { weeks = wm[1]; }
        var periodStart = 0;
        var periodEnd = 0;
        var pm = allText.match(/([0-9]{1,2})\s*-\s*([0-9]{1,2})\s*节/);
        if (pm) { periodStart = parseInt(pm[1], 10); periodEnd = parseInt(pm[2], 10); }
        else {
          pm = allText.match(/([0-9]{1,2})\s*节/);
          if (pm) { periodStart = parseInt(pm[1], 10); periodEnd = periodStart; }
        }
        var teacher = '';
        var room = '';
        for (var t = 0; t < infos.length; t++) {
          var s = infos[t];
          if (!s || s.indexOf('节') >= 0) { continue; }
          if (/^\[?[0-9, \-]+周\]?$/.test(s)) { continue; }
          if (/楼|室|场|馆|机房|中心|R[0-9]|区/.test(s)) { if (!room) { room = s; } continue; }
          if (!teacher) { teacher = s; }
        }
        if (!room) {
          for (var t2 = 0; t2 < infos.length; t2++) {
            var s2 = infos[t2];
            if (s2 && s2 !== teacher && s2.indexOf('节') < 0 && !/^\[?[0-9, \-]+周\]?$/.test(s2)) { room = s2; break; }
          }
        }
        if (teacher) { teacher = teacher.replace(/\[?[0-9, \-]+周\]?/g, '').trim(); }
        if (periodStart <= 0) {
          periodStart = period + 1;
          periodEnd = period + Math.max(1, Math.round(grow));
        }
        var color = '';
        try { color = String(item.style.backgroundColor || ''); } catch(e){}
        courses.push({
          day: day, start: periodStart, end: periodEnd,
          title: title, code: code, teacher: teacher,
          weeks: weeks, room: room, tag: tag, color: color
        });
        period += grow;
      }
    }
    return JSON.stringify({ ok: courses.length > 0, meta: meta, courses: courses });
  } catch(e) {
    return JSON.stringify({ ok: false, error: String(e) });
  }
})();
""".trimIndent()

    /**
     * 在页面上下文内按周重放课表接口，抓取整个学期。
     * 异步执行（结果写入 window.__kbWeeks），需配合 FETCH_WEEKS_POLL 轮询。
     * 不依赖外部捕获：未提供 termCode 时脚本自行从 localStorage 或 kb/xnxq.do 学期列表获取。
     * 接口：POST ../../../sys/homeapp/api/home/student/getMyScheduleDetail.do
     *       body: termCode=..&campusCode=..&type=week&week=N（表单编码，带 Fetch-Api 头）
     */
    fun fetchWeeksScript(termCode: String, campusCode: String, maxWeek: Int = 20): String = """
(function(termCode, campusCode, maxWeek){
  window.__kbWeeks = null;
  window.__kbWeeksBusy = true;
  (async function(){
    var debug = { source: '', termCodeUsed: '', campusCodeUsed: '', weeklyCounts: [], errors: [] };
    try {
      function enc(s){ return encodeURIComponent(String(s === undefined || s === null ? '' : s)); }
      var API = '../../../sys/homeapp/api/home/student/getMyScheduleDetail.do';
      var tc = String(termCode || '');
      var cc = String(campusCode || '');

      // 1) 未提供 termCode：先扫 localStorage 里形如 2026-2027-1 的值
      if (!tc) {
        try {
          for (var i = 0; i < localStorage.length; i++) {
            var k = localStorage.key(i);
            var v = String(localStorage.getItem(k) || '');
            var m = v.match(/[0-9]{4}-[0-9]{4}-[12]/);
            if (m) { tc = m[0]; debug.source = 'localStorage:' + k; break; }
          }
        } catch(e) {}
      }

      // 2) 仍未拿到：调学期列表接口 kb/xnxq.do，取 selected 的 itemCode
      if (!tc) {
        try {
          var resp0 = await fetch('../../../sys/homeapp/api/home/kb/xnxq.do', {
            method: 'GET', credentials: 'include', headers: { 'Fetch-Api': 'true' }
          });
          var t0 = await resp0.text();
          var d0 = null;
          try { d0 = JSON.parse(t0); } catch(e) { d0 = null; }
          if (d0 && d0.datas && d0.datas.length) {
            var sel = null;
            for (var j = 0; j < d0.datas.length; j++) {
              if (d0.datas[j].selected) { sel = d0.datas[j]; break; }
            }
            if (!sel) { sel = d0.datas[0]; }
            tc = String(sel.itemCode || sel.dm || sel.itemCodeValue || '');
            debug.source = 'xnxq';
          } else {
            debug.errors.push('xnxq-get:' + resp0.status);
          }
        } catch(e) { debug.errors.push('xnxq-get-ex:' + String(e)); }
      }

      // 2b) GET 无果时用 POST 再试一次（不同版本部署参数位置不同）
      if (!tc) {
        try {
          var resp1 = await fetch('../../../sys/homeapp/api/home/kb/xnxq.do', {
            method: 'POST', credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'Fetch-Api': 'true' },
            body: ''
          });
          var t1 = await resp1.text();
          var d1 = null;
          try { d1 = JSON.parse(t1); } catch(e) { d1 = null; }
          if (d1 && d1.datas && d1.datas.length) {
            var sel1 = null;
            for (var j1 = 0; j1 < d1.datas.length; j1++) {
              if (d1.datas[j1].selected) { sel1 = d1.datas[j1]; break; }
            }
            if (!sel1) { sel1 = d1.datas[0]; }
            tc = String(sel1.itemCode || sel1.dm || sel1.itemCodeValue || '');
            debug.source = 'xnxq-post';
          } else {
            debug.errors.push('xnxq-post:' + resp1.status);
          }
        } catch(e) { debug.errors.push('xnxq-post-ex:' + String(e)); }
      }
      if (termCode) { debug.source = debug.source || 'captured'; }
      debug.termCodeUsed = tc;
      debug.campusCodeUsed = cc;

      // 并行分批抓取（每批 4 周，批内 Promise.all）：总耗时从“周数×单次延迟”降为“批数×单次延迟”
      var result = {};
      var emptyStreak = 0;
      var BATCH = 5;
      for (var batchStart = 1; batchStart <= maxWeek; batchStart += BATCH) {
        var batchWeeks = [];
        for (var wi = batchStart; wi < batchStart + BATCH && wi <= maxWeek; wi++) { batchWeeks.push(wi); }
        var settled = await Promise.all(batchWeeks.map(function(wn){
          var body = 'termCode=' + enc(tc) + '&campusCode=' + enc(cc) + '&type=week&week=' + wn;
          return fetch(API, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'Fetch-Api': 'true' },
            body: body
          }).then(function(resp){ return resp.text(); }).then(function(text){
            var data = null;
            try { data = JSON.parse(text); } catch(e) { data = null; }
            return { week: wn, data: data };
          }).catch(function(e){
            debug.errors.push('w' + wn + ':' + String(e));
            return { week: wn, data: null };
          });
        }));
        settled.sort(function(a, b){ return a.week - b.week; });
        for (var si = 0; si < settled.length; si++) {
          var item = settled[si];
          var list = (item.data && item.data.datas && item.data.datas.arrangedList) ? item.data.datas.arrangedList : null;
          debug.weeklyCounts.push(item.week + ':' + (list ? list.length : 'x'));
          if (list) {
            result[item.week] = list;
            emptyStreak = list.length ? 0 : (emptyStreak + 1);
          } else {
            emptyStreak++;
          }
        }
        if (emptyStreak >= 4) { break; }
      }
      window.__kbWeeks = JSON.stringify({ ok: true, termCode: tc, campusCode: cc, weeks: result, debug: debug });
    } catch(e) {
      window.__kbWeeks = JSON.stringify({ ok: false, error: String(e), weeks: {}, debug: debug });
    }
    window.__kbWeeksBusy = false;
  })();
})(${quote(termCode)}, ${quote(campusCode)}, $maxWeek);
""".trimIndent()

    /** 轮询多周抓取结果：{ busy, data } */
    val FETCH_WEEKS_POLL = """
(function(){
  try {
    return JSON.stringify({ busy: !!window.__kbWeeksBusy, data: window.__kbWeeks || '' });
  } catch(e) {
    return JSON.stringify({ busy: false, data: '' });
  }
})();
""".trimIndent()

/* ===========================================================================
 * 【学校脚本区】以下为各学校专用的注入脚本（按学校分块，追加在文件末尾）。
 *
 * 约定（与「学校差异隔离」配套）：
 *  - 上面的脚本是通用/北航（金智 jwapp）用的，**不要因为适配新学校而改动它们**；
 *  - 新学校的脚本一律追加到这里，并只由该校的学校流程插件（data/SchoolFlow*.kt）引用；
 *  - 这样改一所学校的脚本不会碰到其他学校的代码。
 * =========================================================================== */

/* ===========================================================================
 * 江苏大学适配脚本（正方教务 V-9.0 + WebVPN 门户）
 *
 * 与上面金智 jwapp 那一套的根本差异：
 *   1. 正方课表是「表格型」DOM（#table1 表格视图 / #table2 列表视图），
 *      不是 7 列 flex 网格 —— 选择器完全不同；
 *   2. 周次直接写在课程块文本里（「周数：4-11周」），
 *      一次提取即可覆盖整学期，无需按周重放（fetchWeeksScript 用不上）；
 *   3. 页面裹在 WebVPN 里，注入脚本不依赖 jQuery，只用原生 DOM API。
 *
 * 登录方式：WebVPN 门户有滑块验证码 + 短信二次认证，无法脚本化，
 *          故走「手动登录 + 自动抓取」——见 WebScheduleEngine 的手动登录分支。
 * =========================================================================== */

/**
 * 江苏大学 · 课表页探针。
 * 判断「已进入课表页且课程已渲染」，供可见登录窗口与引擎共同使用。
 * 返回 {ready, login, captcha, grid, app, error, netError}，字段名与 [JsScripts.DETECT] 保持一致。
 */
    /**
     * 江苏大学 · 触发课表「查询」。
     *
     * 正方课表页（xskbcx_cxXskbcxIndex.html）首次打开时表格是空的：
     * 页面提供了学年（#xnm）/ 学期（#xqm）下拉框 + 「查询」按钮（#search_go），
     * 必须点一次才会异步拉取并渲染课程。
     *
     * 这里直接点按钮（不自己拼请求）：避免复刻 csrftoken 等参数，
     * 交给页面自己的点击处理器完成，改版时也更稳。
     * 若页面已渲染课程则不做任何事。
     */
    val UJS_TRIGGER_QUERY = """
    (function(){
      try {
        if (document.querySelectorAll('.timetable_con').length > 0) {
          return 'already-rendered';
        }
        var btn = document.querySelector('#search_go');
        if (!btn) {
          // 兜底：按文本找「查询」按钮
          var all = document.querySelectorAll('button, a, input[type=button]');
          for (var i = 0; i < all.length; i++) {
            var t = (all[i].textContent || all[i].value || '').replace(/\s+/g, '');
            if (t.indexOf('\u67e5\u8be2') >= 0) { btn = all[i]; break; }
          }
        }
        if (!btn) return 'no-query-button';
        btn.click();
        return 'clicked';
      } catch (e) {
        return 'error:' + String(e && e.message ? e.message : e);
      }
    })();
    """.trimIndent()

    val UJS_DETECT = """
    (function(){
      try {
        // 【关键】必须聚合顶层文档与同源 iframe。
        // 统一身份认证的登录表单在 iframe（login-normal.html）里，只看顶层文档会
        // 把「用户正在输密码」误判成「页面没有登录表单」，进而被当成「已登录」
        // （实测：刚打开首屏就提示登录成功）。
        function allDocs(){
          var list = [document];
          try {
            var iframes = document.querySelectorAll('iframe');
            for (var i = 0; i < iframes.length; i++) {
              try { var d = iframes[i].contentDocument; if (d) { list.push(d); } } catch(e){}
            }
          } catch(e){}
          return list;
        }
        var docs = allDocs();
        var txt = '';
        for (var i = 0; i < docs.length; i++) {
          try {
            if (docs[i].body) {
              txt += ' ' + String(docs[i].body.innerText || docs[i].body.textContent || '');
            }
          } catch(e){}
        }
        if (!txt && document.body) { txt = String(document.body.innerText || ''); }

        var hasPwd = false, hasGridTable = false, hasListTable = false, hasBlock = false;
        for (var j = 0; j < docs.length; j++) {
          try {
            if (!hasPwd && docs[j].querySelectorAll('input[type=password]').length > 0) { hasPwd = true; }
            if (!hasGridTable && docs[j].querySelectorAll('#kbgrid_table_0, table.timetable1').length > 0) { hasGridTable = true; }
            if (!hasListTable && docs[j].querySelectorAll('#kblist_table').length > 0) { hasListTable = true; }
            if (!hasBlock && docs[j].querySelectorAll('.timetable_con').length > 0) { hasBlock = true; }
          } catch(e){}
        }
        var ready = (hasGridTable || hasListTable) && hasBlock;
        // 是否已通过 WebVPN 门户：代理成功时地址形如 /http/<hex>/...
        // （未登录会被门户 302 到 /login，地址里不含该片段）
        var proxied = location.href.indexOf('/http/') >= 0 || location.href.indexOf('/https/') >= 0;
        // 404 页特征（xuanke 的 Apache 直接返回「Object not found!」+「Error 404」）
        var notFound = txt.indexOf('Object not found') >= 0 || txt.indexOf('Error 404') >= 0
            || txt.indexOf('404 Not Found') >= 0;
        return JSON.stringify({
          url: location.href,
          title: document.title || '',
          login: hasPwd,
          captcha: false,
          grid: hasGridTable,
          app: hasListTable || hasBlock,
          ready: ready,
          proxied: proxied,
          notFound: notFound,
          netError: txt.indexOf('网络异常') >= 0 || txt.indexOf('无法访问') >= 0,
          error: '',
          snippet: txt.replace(/\s+/g, ' ').slice(0, 200)
        });
      } catch (e) {
        return JSON.stringify({ url: location.href, login: false, captcha: false, grid: false, app: false, ready: false, proxied: false, notFound: false, netError: false, error: String(e && e.message ? e.message : e) });
      }
    })();
    """.trimIndent()

    /**
     * 江苏大学 · 课表提取脚本（正方教务）。
     *
     * 双视图解析：优先「列表视图」#table2（结构最规整），回退「表格视图」#table1。
     * 输出符合 [ScheduleParser.fromCustomAdapter] 约定的 JSON：
     *   { ok, meta:{semester,studentNo,studentName,url,fetchedAt},
     *     courses:[{day,start,end,title,code,teacher,weeks,room,tag}], count, view }
     *
     * 已在真实的江大课表页存档上验证：16 条课程、周次/地点/教师零缺失。
     */
    val UJS_EXTRACT = """
    (function(){
      'use strict';
      function norm(s){
        return String(s == null ? '' : s).replace(/[\u00a0\u3000]/g, ' ').replace(/\s+/g, ' ').trim();
      }
      function textOf(el){ return el ? norm(el.textContent) : ''; }
      function qsa(sel, root){
        try { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
        catch (e) { return []; }
      }
      function qs(sel, root){
        try { return (root || document).querySelector(sel); } catch (e) { return null; }
      }
      var DAYMAP = {'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7,'天':7};
      function parseWeeks(text){
        var t = String(text || '');
        var m = t.match(/周\s*数\s*[:：]?\s*([0-9][0-9,\-~\u2014\uff0d\s]*)/);
        var body = m ? m[1] : null;
        if (!body) {
          m = t.match(/([0-9]{1,2}(?:\s*[-\u2014\uff0d~]\s*[0-9]{1,2})?(?:\s*[,\uff0c\u3001]\s*[0-9]{1,2}(?:\s*[-\u2014\uff0d~]\s*[0-9]{1,2})?)*)\s*周/);
          body = m ? m[1] : null;
        }
        if (!body) return '';
        return norm(body)
          .replace(/[~\u2014\uff0d]/g, '-')
          .replace(/[\uff0c\u3001]/g, ',')
          .replace(/\s*-\s*/g, '-')
          .replace(/\s*,\s*/g, ',')
          .replace(/^-|-${'$'}/g, '');
      }
      function parseSection(text){
        var m = String(text).match(/([0-9]{1,2})\s*[-\u2014\uff0d~]\s*([0-9]{1,2})\s*节?/);
        if (m) {
          var a = parseInt(m[1], 10), b = parseInt(m[2], 10);
          if (a >= 1 && a <= 20 && b >= a && b <= 20) return { start: a, end: b };
        }
        m = String(text).match(/^第?\s*([0-9]{1,2})\s*节?${'$'}/);
        if (m) { var v = parseInt(m[1], 10); if (v >= 1 && v <= 20) return { start: v, end: v }; }
        return null;
      }
      function parseStudentName(text){
        if (!text) return '';
        var m = String(text).match(/\u7684?\u8bfe\u8868/);
        var head = m ? String(text).slice(0, m.index) : String(text);
        head = head.replace(/[0-9]{4}\s*-\s*[0-9]{4}\s*\u5b66\u5e74\s*\u7b2c\s*[12]\s*\u5b66\u671f/g, '');
        head = head.replace(/\u7b2c?\s*[12]\s*\u5b66\u671f/g, '');
        head = head.replace(/[0-9]{4}\s*-\s*[0-9]{4}\s*\u5b66\u5e74/g, '');
        var nm = head.match(/([\u4e00-\u9fa5]{2,6})\s*${'$'}/);
        return nm ? nm[1] : '';
      }
      // ---------- 【2026-10-04 优化】按「标签 / tooltip」取字段 ----------
      // 表格视图(#kbgrid_table_0)：<p><span title="教师">…</span><font color="blue"> 刘宏</font></p>
      //   → 值在 <p> 的文本里，标签只在 tooltip 的 title 属性上（纯文本启发式很容易取错）；
      // 列表视图(#kblist_table)：<font>教师 ：刘宏</font> → 显式「标签：值」。
      // 两个视图都先收集成 fields，再由调用方按优先级覆盖启发式结果。
      var FIELD_LABELS = ['教学班组成','教学班名称','教学班类型','教学班','校区','上课地点','地点',
                          '教师','老师','课程性质','选课备注','周数','节/周','学分','班型',
                          '开课学院','学院','课程类别'];
      // 按长度降序：生成正则时「长的标签先匹配」（教学班组成 先于 教学班、选课备注 先于 备注）
      var LABELS_BY_LEN = FIELD_LABELS.slice().sort(function(a, b){ return b.length - a.length; });
      function fieldKey(label){
        var l = norm(label).replace(/\s/g, '').replace(/[:：]+${'$'}/, '');
        if (!l) return '';
        if (l.indexOf('节') >= 0 && l.indexOf('周') >= 0) return 'weeks';
        if (l.indexOf('周数') >= 0 || l.indexOf('周次') >= 0) return 'weeks';
        if (l.indexOf('教学班组成') >= 0 || l.indexOf('班级组成') >= 0) return 'classNames';
        if (l.indexOf('教学班') >= 0 && l.indexOf('类型') >= 0) return 'classType';
        if (l.indexOf('班型') >= 0) return 'classType';
        if (l.indexOf('教学班') >= 0) return 'clazz';
        if (l.indexOf('上课地点') >= 0 || l.indexOf('教室') >= 0 || l === '地点') return 'room';
        if (l.indexOf('校区') >= 0) return 'campus';
        if (l.indexOf('教师') >= 0 || l.indexOf('老师') >= 0) return 'teacher';
        if (l.indexOf('课程性质') >= 0 || l === '性质') return 'nature';
        if (l.indexOf('选课备注') >= 0 || l.indexOf('备注') >= 0) return 'note';
        if (l.indexOf('学分') >= 0) return 'credits';
        if (l.indexOf('课程类别') >= 0 || l.indexOf('类别') >= 0) return 'nature';
        if (l.indexOf('学院') >= 0) return 'college';
        return '';
      }
      /** tooltip 所在的 <p>（值就写在这个 <p> 里） */
      function nearestP(el){
        var node = el;
        for (var i = 0; i < 4 && node; i++) {
          if (node.tagName && String(node.tagName).toLowerCase() === 'p') return node;
          node = node.parentElement;
        }
        return el.parentElement || el;
      }
      function collectFields(block){
        var fields = {};
        function put(label, value){
          var k = fieldKey(label);
          var v = norm(value);
          if (k && v && !fields[k]) fields[k] = v;
        }
        /**
         * 列表视图里「值」会一直取到下一个冒号，尾部可能粘着下一个字段的标签
         * （如 校区:本部 上课地点：三江楼0802 → 校区值会带出「上课地点」），这里剥掉。
         */
        function stripTrailingLabel(v){
          var s = norm(v);
          for (var round = 0; round < 3; round++) {
            var changed = false;
            for (var i = 0; i < LABELS_BY_LEN.length; i++) {
              var lb = LABELS_BY_LEN[i];
              if (s.length >= lb.length && s.slice(-lb.length) === lb) {
                s = norm(s.slice(0, -lb.length));
                changed = true;
                break;
              }
            }
            if (!changed) break;
          }
          return s;
        }
        // 1) tooltip（表格视图）
        qsa('[title]', block).forEach(function(el){
          var title = el.getAttribute('title') || '';
          if (!fieldKey(title)) return;
          var box = nearestP(el);
          var val = norm(box.textContent);
          var own = norm(el.textContent);
          if (own && val.indexOf(own) === 0) val = norm(val.substring(own.length));
          put(title, val);
        });
        // 2) 文本标签（列表视图）："教师 ：刘宏"
        //    长的标签先匹配（教学班组成 先于 教学班、选课备注 先于 备注），避免被短标签截胡
        var text = textOf(block);
        LABELS_BY_LEN.forEach(function(lb){
          var re = new RegExp(lb.replace(/[.*+?^${'$'}()|[\]\\\/]/g, '\\${'$'}&') + '\\s*[:：]\\s*([^:：]*)');
          var m = text.match(re);
          if (m) put(lb, stripTrailingLabel(m[1]));
        });
        return fields;
      }
      /** 教学班名称 → {term, code, classNo}，如 "(2026-2027-1)-03620003-04" */
      function parseClazz(clazz){
        var out = { term: '', code: '', classNo: '' };
        var s = norm(clazz).replace(/\s/g, '');
        if (!s) return out;
        var m = s.match(/^[（(]([0-9]{4}-[0-9]{4}-[0-9])[)）]-?([0-9A-Za-z]+)(?:-([0-9A-Za-z]+))?/);
        if (m) { out.term = m[1]; out.code = m[2]; out.classNo = m[3] || ''; return out; }
        m = s.match(/^([0-9]{4}-[0-9]{4}-[0-9])-([0-9A-Za-z]+)(?:-([0-9A-Za-z]+))?/);
        if (m) { out.term = m[1]; out.code = m[2]; out.classNo = m[3] || ''; return out; }
        m = s.match(/([0-9A-Za-z]{4,})/);
        if (m) out.code = m[1];
        return out;
      }
      function parseCourseBlock(block){
        var full = textOf(block);
        if (!full) return null;
        var titleEl = qs('.title', block) || qs('font', block);
        var title = textOf(titleEl);
        if (!title) return null;
        title = title.replace(/^[（(](本|研|专|硕|博)[)）]/, '').trim();
        if (!title || title.length > 60) return null;
        var weeks = parseWeeks(full);
        var room = '';
        var r = full.match(/\u4e0a\u8bfe\u5730\u70b9\s*[:：]\s*([^\s]+(?:\s*[^\s]+)*?)(?=\s*(?:\u6559\u5e08|\u6559\u5b66\u73ed|\u9009\u8bfe\u5907\u6ce8|\u5b66\u5206|\u8bfe\u7a0b\u6027\u8d28|${'$'}))/);
        if (r) { room = norm(r[1]); }
        else { r = full.match(/([^\s]*\u697c\s*[0-9A-Za-z]{2,6})/); if (r) room = norm(r[1]); }
        room = room.replace(/^(\u672c\u90e8|\u4e1c\u6821\u533a?|\u897f\u6821\u533a?|\u5357\u6821\u533a?|\u5317\u6821\u533a?|\u65b0\u6821\u533a?)\s*/, '').trim();
        var teacher = '';
        var t = full.match(/\u6559\u5e08\s*[:：]?\s*([^\s]+(?:[,\uff0c\u3001][^\s]+)*)/);
        if (t) teacher = norm(t[1]).replace(/[,\uff0c\u3001]+${'$'}/, '');
        if (!teacher) {
          var lines = qsa('p', block).map(textOf).filter(Boolean);
          for (var i = 0; i < lines.length; i++) {
            var ln = lines[i];
            if (/\u8282|\u5468|\u697c|\u6821\u533a|\u6559\u5ba4/.test(ln)) continue;
            if (/^[0-9,\uff0c\u3001;；\-\s]+${'$'}/.test(ln)) continue;
            if (/\u5b66\u5206|\u8bfe\u7a0b\u6027\u8d28|\u6559\u5b66\u73ed|\u9009\u8bfe\u5907\u6ce8/.test(ln)) continue;
            teacher = ln.replace(/^\u6559\u5e08\s*[:：]?\s*/, '').trim();
            if (teacher) break;
          }
        }
        teacher = teacher.replace(/\s*\u6559\u5e08\s*${'$'}/, '').trim();
        var code = '';
        var cd = full.match(/\u6559\u5b66\u73ed\s*[:：]?\s*([（(][^）)]*[)）]\s*-\s*[0-9A-Za-z\-]+)/);
        if (cd) code = norm(cd[1]);
        var tag = '';
        var tg = full.match(/\u8bfe\u7a0b\u6027\u8d28\s*[:：]?\s*([^\s]+)/);
        if (tg) tag = norm(tg[1]).slice(0, 4);
        // ---------- 【2026-10-04 优化】用「标签 / tooltip」的精确值覆盖上面的启发式结果 ----------
        var f = collectFields(block);
        if (f.weeks) { var wk = parseWeeks(f.weeks); if (wk) weeks = wk; }
        if (f.room) room = f.room;
        var campus = f.campus || '';
        if (!campus) {
          // 表格视图里「本部 三江楼0802」是连在一起的，把校区拆出来单独记
          var cm = norm(room).match(/^(本部|东校区?|西校区?|南校区?|北校区?|新校区?|京江校区?|梦溪校区?|中校区?)\s+/);
          if (cm) { campus = cm[1]; room = norm(room.substring(cm[0].length)); }
        }
        if (f.teacher) teacher = f.teacher.replace(/^教师\s*[:：]?\s*/, '').trim();
        if (f.nature) tag = norm(f.nature).slice(0, 6);
        var clz = parseClazz(f.clazz || '');
        if (clz.code) code = clz.code;   // 课程代码（不带教学班号，同一门课的多个班才能归为同一系列）
        var extra = {
          term: clz.term || '',        // 开设学年学期，如 2026-2027-1
          classNo: clz.classNo || '',  // 教学班号
          clazz: f.clazz || '',        // 教学班名称原文
          classNames: f.classNames || '',  // 教学班组成（开设班级）
          credits: f.credits || '',    // 学分
          nature: f.nature || '',      // 课程性质
          classType: f.classType || '',// 班型 / 教学班类型（页面上有就取）
          college: f.college || '',    // 开课学院
          campus: campus || '',        // 校区
          note: f.note || ''           // 选课备注
        };
        return { title: title, weeks: weeks, room: room, teacher: teacher, code: code, tag: tag, extra: extra };
      }
      function extractTable2(){
        var courses = [], semester = '', studentNo = '', studentName = '';
        var headBox = qs('#kblist_table .timetable_title');
        if (headBox) {
          var tt = textOf(headBox);
          var ms = tt.match(/([0-9]{4}\s*-\s*[0-9]{4}\s*\u5b66\u5e74\s*\u7b2c\s*[12]\s*\u5b66\u671f)/);
          if (ms) semester = norm(ms[1]);
          var mn = tt.match(/\u5b66\u53f7\s*[:：]\s*([0-9A-Za-z]+)/);
          if (mn) studentNo = mn[1];
          studentName = parseStudentName(tt);
        }
        for (var day = 1; day <= 7; day++) {
          var tbody = qs('#xq_' + day);
          if (!tbody) continue;
          qsa('tr', tbody).forEach(function(tr){
            var sec = null;
            qsa('[id^=jc_]', tr).forEach(function(td){
              if (sec) return;
              var m = (td.getAttribute('id') || '').match(/^jc_\d-(\d{1,2})-(\d{1,2})${'$'}/);
              if (m) sec = { start: parseInt(m[1], 10), end: parseInt(m[2], 10) };
            });
            if (!sec) { var fest = qs('.festival', tr); if (fest) sec = parseSection(textOf(fest)); }
            if (!sec) return;
            qsa('.timetable_con', tr).forEach(function(blk){
              var c = parseCourseBlock(blk);
              if (!c) return;
              courses.push({ day: day, start: sec.start, end: sec.end, title: c.title, code: c.code,
                             teacher: c.teacher, weeks: c.weeks, room: c.room, tag: c.tag,
                             extra: c.extra });
            });
          });
        }
        if (!courses.length) return null;
        return { view: 'table2', semester: semester, studentNo: studentNo, studentName: studentName, courses: courses };
      }
      function extractTable1(){
        var tb = null;
        var cands = qsa('[id^=kbgrid_table_], table.timetable1');
        for (var i = 0; i < cands.length; i++) { if (qsa('.timetable_con', cands[i]).length) { tb = cands[i]; break; } }
        if (!tb) return null;
        var semester = '', studentNo = '', studentName = '';
        var titleBox = qs('.timetable_title', tb);
        if (titleBox) {
          var tt = textOf(titleBox);
          var ms = tt.match(/([0-9]{4}\s*-\s*[0-9]{4}\s*\u5b66\u5e74\s*\u7b2c\s*[12]\s*\u5b66\u671f)/);
          if (ms) semester = norm(ms[1]);
          var mn = tt.match(/\u5b66\u53f7\s*[:：]\s*([0-9A-Za-z]+)/);
          if (mn) studentNo = mn[1];
          studentName = parseStudentName(tt);
        }
        var courses = [];
        qsa('td[id]', tb).forEach(function(td){
          var m = (td.getAttribute('id') || '').match(/^(\d)-(\d{1,2})${'$'}/);
          if (!m) return;
          var day = parseInt(m[1], 10);
          var cellStart = parseInt(m[2], 10);
          if (day < 1 || day > 7) return;
          var rowspan = parseInt(td.getAttribute('rowspan') || '1', 10);
          var blocks = qsa('.timetable_con', td);
          if (!blocks.length) blocks = [td];
          blocks.forEach(function(blk){
            var c = parseCourseBlock(blk);
            if (!c) return;
            var sec = parseSection(textOf(blk));
            var start, end;
            if (sec) { start = sec.start; end = sec.end; }
            else { start = cellStart; end = (rowspan > 1) ? cellStart + rowspan - 1 : cellStart; }
            if (start < 1 || start > 20) return;
            if (end < start) end = start;
            if (end > 20) end = 20;
            courses.push({ day: day, start: start, end: end, title: c.title, code: c.code,
                           teacher: c.teacher, weeks: c.weeks, room: c.room, tag: c.tag,
                           extra: c.extra });
          });
        });
        if (!courses.length) return null;
        return { view: 'table1', semester: semester, studentNo: studentNo, studentName: studentName, courses: courses };
      }
      try {
        var result = extractTable2() || extractTable1();
        var meta = { semester: '', weekNo: 0, weekRange: '', today: '', url: location.href, fetchedAt: Date.now() };
        if (result) {
          meta.semester = result.semester || '';
          meta.studentNo = result.studentNo || '';
          meta.studentName = result.studentName || '';
        }
        if (!meta.semester) {
          var y = qs('#xnm'), q = qs('#xqm');
          var yv = y ? y.value : '', qv = q ? q.value : '';
          if (yv) {
            var label = yv + '-' + (parseInt(yv, 10) + 1);
            var term = (qv === '3') ? '1' : ((qv === '12') ? '2' : '');
            meta.semester = term ? (label + '\u5b66\u5e74\u7b2c' + term + '\u5b66\u671f') : (label + '\u5b66\u5e74');
          }
        }
        // 第 1 教学周周一（江大规则：秋季学期「含 9 月 1 日的一周」为第 1 周，
        // 2026-2027学年第1学期 → 第1周周一 = 2026-08-31）。
        // 解析器（ScheduleParser.fromCustomAdapter）优先用该字段作锚点；
        // 春季学期的起始规则未知，留空走解析器兜底。
        var ms = (meta.semester || '').match(/(\d{4})-(\d{4})\u5b66\u5e74\s*\u7b2c(\d)\u5b66\u671f/);
        if (ms && ms[3] === '1') {
          var d1 = new Date(Date.UTC(parseInt(ms[1], 10), 8, 1)); // 9 月 1 日
          var wd = d1.getUTCDay(); if (wd === 0) wd = 7;          // 周日按 7
          d1.setUTCDate(d1.getUTCDate() - (wd - 1));              // 回到所在周周一
          meta.firstWeekMonday = d1.toISOString().slice(0, 10);
        }
        if (!result || !result.courses.length) {
          var bodyTxt = textOf(document.body);
          var hasTable = !!qs('#kblist_table, #kbgrid_table_0, table.timetable1');
          return JSON.stringify({
            ok: false,
            reason: hasTable ? 'no-courses-parsed' : 'page-not-ready',
            hint: hasTable ? '\u5df2\u6253\u5f00\u8bfe\u8868\u9875\u4f46\u672a\u89e3\u6790\u5230\u8bfe\u7a0b\uff0c\u8bf7\u786e\u8ba4\u5df2\u9009\u5b66\u5e74\u5b66\u671f\u5e76\u70b9\u51fb\u67e5\u8be2'
                           : '\u8bfe\u8868\u9875\u9762\u5c1a\u672a\u51fa\u73b0\uff0c\u8bf7\u5148\u767b\u5f55\u5e76\u8fdb\u5165\u4e2a\u4eba\u8bfe\u8868',
            meta: meta, courses: []
          });
        }
        var seen = {}, unique = [];
        result.courses.forEach(function(c){
          var k = [c.title, c.code, c.day, c.start, c.end, c.weeks, c.room].join('|');
          if (seen[k]) return;
          seen[k] = 1; unique.push(c);
        });
        return JSON.stringify({ ok: true, meta: meta, courses: unique, count: unique.length, view: result.view });
      } catch (e) {
        return JSON.stringify({ ok: false, reason: 'exception', message: String(e && e.message ? e.message : e), courses: [] });
      }
    })();
    """.trimIndent()

    /**
     * 滑块验证码触摸修复（幂等，可重复注入）。
     *
     * 【背景】WebVPN / 正方登录页的滑块在 Android WebView 里「能按但立刻弹回」。
     * 根因：拖动带纵向分量时 WebView 把手势判给滚动容器，向页面派发 `touchcancel`，
     * 拖动 handler 被中断 → 滑块回弹。
     *
     * 【做法】在捕获阶段挂一层事件闸门：
     *  - 屏蔽页面/框架误加的 `touchcancel`（拖动过程中最常见的“弹回”触发器）；
     *  - 拖动期间对 `touchmove` 强制 `preventDefault()`，阻止手势升级为滚动；
     *  - 单指按压时对可滚动祖先置 `touch-action: none`；
     *  - 只作用于疑似滑块元素（class/id 含 slider/verify/captcha/drag 等），
     *    避免影响页面正常滚动与手写签名之类的其它交互。
     *
     * 【为什么不是“合成事件”】这里不伪造任何 touch 事件，只做转发与拦截，
     * 因此不会触碰验证服务端的行为风控——用户依然是自己在拖。
     */
    val SLIDER_TOUCH_FIX = """
    (function(){
      try {
        if (window.__kagendaSliderFix) { return 'already'; }
        window.__kagendaSliderFix = 1;

        // 是否为疑似滑块/验证码元素（含其祖先，兼容“手柄在容器里”的常见结构）
        function isSliderEl(el) {
          var n = 0, p = el;
          while (p && p.nodeType === 1 && n < 6) {
            var cls = (p.className && p.className.toString ? p.className.toString() : '') || '';
            var id = p.id || '';
            var tag = (p.tagName || '').toLowerCase();
            var s = (cls + ' ' + id).toLowerCase();
            if (tag === 'canvas') return true;
            if (s.indexOf('slider') >= 0 || s.indexOf('verify') >= 0 ||
                s.indexOf('captcha') >= 0 || s.indexOf('drag') >= 0 ||
                s.indexOf('nc_') === 0 || s.indexOf('yidun') >= 0 ||
                s.indexOf('geetest') >= 0 || s.indexOf('jigsaw') >= 0 ||
                s.indexOf('puzzle') >= 0 || s.indexOf('滑块') >= 0) {
              return true;
            }
            p = p.parentElement; n++;
          }
          return false;
        }

        var dragging = false;

        // 捕获阶段先手：屏蔽拖动期间的 touchcancel
        document.addEventListener('touchcancel', function(e){
          if (dragging || isSliderEl(e.target)) {
            e.stopImmediatePropagation();
            e.preventDefault();
          }
        }, true);

        document.addEventListener('touchstart', function(e){
          if (!isSliderEl(e.target)) return;
          dragging = true;
          // 让祖先滚动容器在这次手势期间不参与
          var p = e.target;
          while (p && p.nodeType === 1) {
            try {
              var st = window.getComputedStyle(p);
              if (st && (st.overflowY === 'auto' || st.overflowY === 'scroll' ||
                         st.overflow === 'auto' || st.overflow === 'scroll')) {
                p.style.touchAction = 'none';
              }
            } catch (ignored) {}
            p = p.parentElement;
          }
        }, true);

        document.addEventListener('touchmove', function(e){
          if (!dragging && !isSliderEl(e.target)) return;
          // 关键：阻止浏览器把手势升级成页面滚动（否则会连带触发 touchcancel）
          if (e.cancelable) e.preventDefault();
        }, { capture: true, passive: false });

        function endDrag(){ dragging = false; }
        document.addEventListener('touchend', endDrag, true);
        document.addEventListener('touchcancel', endDrag, true);

        return 'ok';
      } catch (e) {
        return 'err:' + String(e && e.message ? e.message : e);
      }
    })();
    """.trimIndent()
}
