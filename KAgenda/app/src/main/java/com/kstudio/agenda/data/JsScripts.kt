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
    for (var i = 0; i < docs.length; i++) {
      try {
        var pwds = docs[i].querySelectorAll('input[type="password"]');
        for (var j = 0; j < pwds.length; j++) {
          if (isVisible(pwds[j])) { hasPwd = true; }
        }
        var cap = docs[i].getElementById ? docs[i].getElementById('captchaPasswor') : null;
        if (cap && isVisible(cap)) { captchaVisible = true; }
      } catch(e){}
    }
    var text = document.body ? String(document.body.innerText || '') : '';
    var err = '';
    try {
      var errEls = document.querySelectorAll('#errPassword, .item-validate');
      for (var k = 0; k < errEls.length; k++) {
        var t = String(errEls[k].innerText || '').trim();
        if (t) { err = t; }
      }
      if (!err && /密码错误|用户名或密码错误|账号或密码错误|invalid|incorrect/i.test(text)) { err = '页面提示凭据错误'; }
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
}
