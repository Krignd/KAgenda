// KAgenda original implementation, MIT. Protocol and field reference:
// GUY-ATM/KEBIAO@1a5ba8507c6dd324cc9e91ff98d7584c1b8c654f/kebiao.html
// Copyright (c) 2026 GUY, MIT; retain its full license when integrating.
// Uses the visible signed-in school session. No upstream login, defaults or merge logic copied.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const ORIGIN = 'https://jwxt.fjjxu.edu.cn';
  function need(ok, message) { k.requireValue(ok, message); }
  async function fresh(path, asJson) {
    const url = new URL(path, ORIGIN);
    need(url.origin === ORIGIN, '不能跨站读取课表');
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(),20000);
    try {
      const response = await fetch(url.href,{method:'GET',credentials:'same-origin',redirect:'error',
        cache:'no-store',signal:controller.signal,headers:{Accept:asJson?'application/json':'text/html'}});
      need(response.ok && !response.redirected, '教务请求失败，请重新登录');
      if (response.url) {
        const actual = new URL(response.url);
        need(actual.origin === ORIGIN && actual.pathname === url.pathname, '教务响应来源或页面发生变化');
      }
      const charset = (response.headers?.get('Content-Type') || '').match(/charset\s*=\s*["']?([^;"'\s]+)/i);
      need(!charset || /^(utf-8|utf8)$/i.test(charset[1]), '教务数据编码发生变化');
      const text = await response.text();
      need(text.length > 0 && text.length <= 1500000 && !text.includes('\ufffd'), '教务数据为空、过大或编码无效');
      if (!asJson) return text;
      need(!/^\s*</.test(text), '教务会话已失效，请重新登录');
      return JSON.parse(text);
    } finally { clearTimeout(timer); }
  }
  function guard() {
    need(k.adapterId === 'ext_fjjxu_personal' && location.origin === ORIGIN &&
      !location.username && !location.password, '请在本校 HTTPS 教务系统完成登录后重试');
    need(!document.querySelector('input[type="password"]'), '请先在教务系统完成登录');
  }
  function date(value) {
    const d = new Date(value + 'T00:00:00Z');
    need(/^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(d.getTime()) &&
      d.toISOString().slice(0, 10) === value, '教务学期日期格式无效');
    return d;
  }
  function semester(raw) {
    const doc = new DOMParser().parseFromString(raw, 'text/html');
    need(!doc.querySelector('input[type="password"]'), '教务会话已失效');
    const text = doc.body.textContent.replace(/[\s\u00a0]/g, '');
    const matches = Array.from(text.matchAll(/(\d{4})-(\d{4})学年([123])学期[（(](\d{4}-\d{2}-\d{2})至(\d{4}-\d{2}-\d{2})[）)]/g));
    const choices = Array.from(new Set(matches.map(m => JSON.stringify(m.slice(1))))).map(x => JSON.parse(x));
    need(choices.length === 1, '没有取得唯一的当前教务学期与日期范围');
    const [first, last, term, start, end] = choices[0];
    need(Number(last) === Number(first) + 1 && date(start) <= date(end), '教务学期或日期范围冲突');
    return {xnm: first, xqm: {'1':'3','2':'12','3':'16'}[term], start, end,
      label: first + '-' + last + '学年第' + term + '学期'};
  }
  function sections(value) {
    need(typeof value === 'string' || typeof value === 'number', '缺少明确节次');
    const raw = String(value).replace(/[第节\s]/g, '').replace(/[，、]/g, ',');
    need(raw.length > 0 && raw.length < 150, '缺少明确节次');
    const result = [];
    for (const part of raw.split(',')) {
      const m = part.match(/^(\d+)(?:[-~～至](\d+))?$/);
      need(m, '课程节次格式发生变化');
      const first = k.integer(m[1], 1, 24, '节次'), last = m[2] ? k.integer(m[2], first, 24, '节次') : first;
      for (let n = first; n <= last; n++) result.push(n);
    }
    return Array.from(new Set(result)).sort((a,b) => a-b);
  }
  function required(value, label) {
    need(typeof value === 'string' && value.trim() && value.length <= 200, '课程缺少有效的' + label);
    need(!/^(?:待定|未安排|暂无|未排|未分配|null|undefined|[-—–]+)$/i.test(value.trim()), '课程尚未提供明确的' + label);
    return value.trim();
  }
  function parsed(data, term) {
    need(data && typeof data === 'object' && !Array.isArray(data) &&
      (data.success == null || ['true','1'].includes(String(data.success))) &&
      (data.code == null || ['0','200'].includes(String(data.code))), '教务接口返回失败');
    if (data.error != null) need(['','false','0'].includes(String(data.error)), '教务接口返回错误');
    for (const key of ['message','msg']) if (typeof data[key] === 'string')
      need(!/(?:未登录|请.{0,12}登录|登录.{0,12}(?:失效|过期)|会话.{0,12}(?:失效|过期)|session.{0,12}(?:expired|invalid))/i.test(data[key]), '教务会话已失效，请重新登录');
    need(Array.isArray(data.kbList) && data.kbList.length > 0 && data.kbList.length <= 5000, '未取得完整个人课表');
    for (const key of ['hasMore','hasNext','nextPage']) if (data[key] != null)
      need(['false','0',''].includes(String(data[key])), '课表存在未读取的后续页');
    for (const key of ['pages','totalPages','totalPage','currentPage','pageNumber']) if (data[key] != null)
      need(k.integer(data[key],1,5000,'课表页码') === 1, '课表存在未读取的后续页');
    for (const name of ['total','totalSize','totalCount']) if (data[name] != null)
      need(k.integer(data[name], 0, 5000, '课程总数') === data.kbList.length, '课表页数或总数不完整');
    for (const name of Object.keys(data)) {
      if (name !== 'kbList' && Array.isArray(data[name]))
        need(data[name].length === 0, '存在尚未完整核验的其他课程安排，未导入部分结果');
      if (name !== 'xsxx' && data[name] && typeof data[name] === 'object' && !Array.isArray(data[name]))
        need(Object.keys(data[name]).length === 0, '出现尚未核验的课表数据分支，未导入部分结果');
    }
    const identity = [];
    if (data.xsxx != null) {
      need(typeof data.xsxx === 'object' && !Array.isArray(data.xsxx), '个人课表身份格式发生变化');
      for (const key of ['XH','xh','xh_id','XM','xm','XNMC','XNM','xnm','XQMMC','XQM','xqm']) if (data.xsxx[key] != null) {
        need((typeof data.xsxx[key] === 'string' || typeof data.xsxx[key] === 'number') &&
          String(data.xsxx[key]).length <= 200, '个人课表身份或学期格式无效');
        identity.push([key, String(data.xsxx[key])]);
      }
      if (data.xsxx.XNMC != null) need(String(data.xsxx.XNMC).replace(/\s|学年/g,'') === term.xnm+'-'+(Number(term.xnm)+1), '个人课表学年与教务校历冲突');
      if (data.xsxx.XQMMC != null) {
        const ordinal={'3':'1','12':'2','16':'3'}[term.xqm], chinese={'1':'一','2':'二','3':'三'}[ordinal];
        need([ordinal,'第'+ordinal+'学期',ordinal+'学期','第'+chinese+'学期'].includes(String(data.xsxx.XQMMC).replace(/\s/g,'')), '个人课表学期与教务校历冲突或名称未核验');
      }
    }
    const courses = [];
    for (const row of data.kbList) {
      need(row && typeof row === 'object' && !Array.isArray(row), '存在不完整课程记录');
      for (const value of Object.values(row)) if (value && typeof value === 'object')
        need(Object.keys(value).length === 0, '出现尚未核验的课程内数据分支，未导入部分结果');
      for (const key of ['xnm','xqm']) if (row[key] != null) need(String(row[key]) === term[key], '课表混入其他学期');
      need(row.dsz == null || String(row.dsz).trim() === '', '出现尚未核验的独立单双周标志，未导入部分结果');
      const days = ['星期一','星期二','星期三','星期四','星期五','星期六','星期日'];
      const label = row.xqjmc == null || row.xqjmc === '' ? '' : required(row.xqjmc, '星期');
      const day = label ? days.indexOf(label) + 1 : k.integer(row.xqj,1,7,'星期');
      need(day > 0 && (row.xqj == null || k.integer(row.xqj,1,7,'星期') === day), '星期字段缺失或相互冲突');
      const slots = sections(row.jcs == null || row.jcs === '' ? row.jc : row.jcs);
      if (row.jcs != null && row.jcs !== '' && row.jc != null && String(row.jc).trim()) need(JSON.stringify(sections(row.jc)) === JSON.stringify(slots), '课程节次字段冲突');
      const active = k.weeks(row.zcd);
      const name = required(row.kcmc,'名称'), teacher = required(row.xm,'教师'), room = required(row.cdmc,'地点');
      let campus = '';
      if (row.xqmc != null) { need(typeof row.xqmc === 'string', '校区字段格式无效'); campus = row.xqmc.trim(); }
      const position = campus && !room.includes(campus) ? campus + ' ' + room : room;
      const info = {semester:term.label};
      for (const key of ['kch_id','kch','jxb_id','kclb','jxbmc','jxbzc','xqh_id','xkbz']) if (row[key] != null) {
        need((typeof row[key] === 'string' || typeof row[key] === 'number') &&
          String(row[key]).length <= 1000, '课程身份、班级或备注格式无效');
        info[key] = String(row[key]);
      }
      const extraInfo = JSON.stringify(info);
      need(extraInfo.length <= 4000, '课程附加信息过大，未导入部分结果');
      const groups = [];
      for (const n of slots) {
        const previous = groups[groups.length-1];
        if (previous && previous[1] + 1 === n) previous[1] = n; else groups.push([n,n]);
      }
      for (const [first,last] of groups) courses.push(Object.assign(k.course(name,teacher,position,day,first,last,active), {extraInfo}));
    }
    // Compare complete row facts, including fields not displayed in the preview.
    // JSON object key order and server row order carry no course meaning.
    const stable = value => Array.isArray(value) ? value.map(stable) : value && typeof value === 'object' ?
      Object.fromEntries(Object.keys(value).sort().map(key => [key,stable(value[key])])) : value;
    const signature = JSON.stringify({term, identity, rows:data.kbList.map(row=>JSON.stringify(stable(row))).sort()});
    return {courses,signature,lastWeek:Math.max(...courses.flatMap(c=>c.weeks))};
  }
  const calendarPath = '/jwglxt/xtgl/index_cxAreaFive.html?localeKey=zh_CN&gnmkdm=index';
  async function fetchCourses(term) {
    const query = new URLSearchParams({gnmkdm:'N2151', xnm:term.xnm, xqm:term.xqm});
    return parsed(await fresh('/jwglxt/kbcx/xskbcx_cxXsKb.html?' + query, true), term);
  }
  window.kagendaFjjxuParsers = {semester,sections,parsed};
  k.run(async () => {
    guard();
    const url = location.href, firstTerm = semester(await fresh(calendarPath, false));
    const before = await fetchCourses(firstTerm);
    window.kagendaFjjxuWeeksValidator = value => {
      try { k.integer(value,before.lastWeek,40,'学期教学周数'); return false; }
      catch (error) { return String(error.message || error); }
    };
    const answer = await k.api.showPrompt('按校历填写学期教学周数',
      firstTerm.label + '。请按学校校历填写本学期的总教学周数。', '', 'kagendaFjjxuWeeksValidator');
    need(answer !== null && answer !== undefined, '已取消导入');
    const count = k.integer(answer,before.lastWeek,40,'学期教学周数');
    let anchor = firstTerm.start;
    if (date(anchor).getUTCDay() !== 1) {
      window.kagendaFjjxuMondayValidator = value => {
        try { need(date(String(value).trim()).getUTCDay() === 1, '请填写周一日期'); return false; }
        catch (error) { return String(error.message || error); }
      };
      const value = await k.api.showPrompt('开学第一周的周一',
        '教务学期起始日期为 ' + firstTerm.start + '。请按学校校历填写第一周周一日期（YYYY-MM-DD）。', '', 'kagendaFjjxuMondayValidator');
      need(value !== null && value !== undefined, '已取消导入');
      anchor = String(value).trim();
      need(date(anchor).getUTCDay() === 1, '请填写周一日期');
    }
    guard(); need(location.href === url, '确认期间网页发生跳转');
    const afterTerm = semester(await fresh(calendarPath, false));
    need(JSON.stringify(afterTerm) === JSON.stringify(firstTerm), '确认期间教务学期发生变化');
    const after = await fetchCourses(afterTerm);
    need(after.signature === before.signature, '确认期间课表或身份发生变化，未导入部分结果');
    guard(); need(location.href === url, '查询期间网页发生跳转');
    // No inferred clock table. The existing native preview requires a Monday
    // and displays all time slots for explicit confirmation/editing.
    await k.finish(after.courses,0,count,anchor);
  });
})();
