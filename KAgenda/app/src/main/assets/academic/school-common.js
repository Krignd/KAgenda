// KAgenda host helpers, MIT. Source-specific implementations and attribution
// live in shiguang/scripts/EXT/ and academic/school-provenance.json.
(function () {
  'use strict';
  const api = window.AndroidBridgePromise;
  function requireValue(ok, message) { if (!ok) throw new Error(message); }
  function integer(value, min, max, label) {
    const text = String(value == null ? '' : value).trim();
    requireValue(/^\d+$/.test(text), '缺少有效的' + label);
    const number = Number(text);
    requireValue(Number.isSafeInteger(number) && number >= min && number <= max, label + '超出范围');
    return number;
  }
  function weeks(value) {
    if (Array.isArray(value)) {
      requireValue(value.length > 0, '缺少明确周次');
      return Array.from(new Set(value.map(w => integer(w, 1, 40, '周次')))).sort((a, b) => a - b);
    }
    let text = String(value == null ? '' : value).trim()
      .replace(/[（]/g, '(').replace(/[）]/g, ')').replace(/[，、]/g, ',')
      .replace(/[－—–~～至]/g, '-').replace(/\s+/g, '').replace(/第/g, '');
    requireValue(text.length > 0 && text.length < 500, '缺少明确周次');
    let defaultParity = '';
    const suffix = text.match(/\(([单双])(?:周)?\)$/);
    const before = suffix ? text.slice(0, -suffix[0].length) : text;
    if (suffix && before.includes(',') && before.split(',').slice(0, -1).every(p => !p.includes('周') && !/[单双]/.test(p))) {
      defaultParity = suffix[1]; text = before;
    }
    const result = [];
    text.split(',').forEach(part => {
      const m = part.match(/^(\d+)(?:-(\d+))?周?(?:\(([单双])(?:周)?\)|([单双])周?)?$/);
      requireValue(m, '无法识别周次：' + part);
      const first = integer(m[1], 1, 40, '周次');
      const last = m[2] ? integer(m[2], first, 40, '周次') : first;
      const parity = m[3] || m[4] || defaultParity;
      for (let w = first; w <= last; w++) {
        if (!parity || (parity === '单' ? w % 2 === 1 : w % 2 === 0)) result.push(w);
      }
    });
    requireValue(result.length > 0, '周次范围与单双周标记不一致');
    return Array.from(new Set(result)).sort((a, b) => a - b);
  }
  function bitmap(value, zeroBased) {
    const text = String(value == null ? '' : value).trim();
    requireValue(/^[01]{1,41}$/.test(text), '缺少有效周位图');
    const active = [];
    for (let i = 0; i < text.length; i++) if (text[i] === '1') active.push(zeroBased ? i : i + 1);
    return weeks(active);
  }
  function course(name, teacher, room, day, first, last, active) {
    const title = String(name == null ? '' : name).trim();
    requireValue(title.length > 0 && title.length <= 200, '课程名称无效');
    const start = integer(first, 1, 24, '起始节次');
    return {name: title, teacher: String(teacher || '').trim(), position: String(room || '').trim(),
      day: integer(day, 1, 7, '星期'), startSection: start,
      endSection: integer(last, start, 24, '结束节次'), weeks: weeks(active)};
  }
  function host(expected) {
    requireValue(location.protocol === 'https:' && expected.includes(location.hostname), '请先进入该学校的 HTTPS 课表页面并完成登录');
  }
  async function json(path, fields, multipart) {
    const url = new URL(path, location.href);
    requireValue(url.origin === location.origin, '请进入教务系统课表页面后重试，不能跨站采集');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    let body, headers = {'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest'};
    if (fields !== undefined) {
      if (multipart) { body = new FormData(); Object.keys(fields).forEach(k => body.append(k, String(fields[k]))); }
      else { body = new URLSearchParams(); Object.keys(fields).forEach(k => body.append(k, String(fields[k]))); }
    }
    try {
      const response = await fetch(url.href, {method: fields === undefined ? 'GET' : 'POST', body, headers,
        credentials: 'same-origin', redirect: 'error', signal: controller.signal});
      requireValue(response.ok, '课表请求失败，请检查登录状态');
      const text = await response.text();
      requireValue(text.length > 0 && text.length <= 1500000 && !/^\s*</.test(text), '课表接口未返回有效数据，可能需要重新登录');
      return JSON.parse(text);
    } finally { clearTimeout(timer); }
  }
  async function choose(title, rows, code, label) {
    requireValue(Array.isArray(rows) && rows.length > 0 && rows.length <= 200, '没有取得学期列表');
    requireValue(rows.every(r => r && String(r[code] || '').trim() && String(r[label] || '').trim()), '学期列表格式无效');
    const index = await api.showSingleSelection(title, JSON.stringify(rows.map(r => String(r[label]))), -1);
    requireValue(index !== null && index !== undefined, '已取消导入');
    return rows[integer(index, 0, rows.length - 1, '学期选择')];
  }
  async function page(path) {
    const url = new URL(path, location.href);
    requireValue(url.origin === location.origin, '不能跨站读取课表');
    const controller = new AbortController(), timer = setTimeout(()=>controller.abort(),20000);
    try {
      const response = await fetch(url.href, {credentials:'same-origin',redirect:'error',signal:controller.signal});
      requireValue(response.ok, '课表页面请求失败，请重新登录');
      const buffer = await response.arrayBuffer();
      requireValue(buffer.byteLength > 0 && buffer.byteLength <= 2000000, '课表页面大小无效');
      const contentType = response.headers.get('Content-Type') || '';
      const charset = contentType.match(/charset\s*=\s*["']?([\w-]+)/i);
      if(charset) {
        requireValue(/^(?:utf-8|utf8|gbk|gb2312|gb18030)$/i.test(charset[1]), '课表字符编码不受支持');
        return new TextDecoder(charset[1], {fatal:true}).decode(buffer);
      }
      try { return new TextDecoder('utf-8', {fatal:true}).decode(buffer); }
      catch (_) { return new TextDecoder('gb18030', {fatal:true}).decode(buffer); }
    } finally { clearTimeout(timer); }
  }
  async function semesterWeeks(lastWeek, supplied) {
    if (supplied != null) return integer(supplied, lastWeek, 40, '学期周数');
    // Course weeks are a lower bound, never the length of the semester.
    const validator = value => {
      try { integer(value, lastWeek, 40, '学期周数'); return false; }
      catch (error) { return String(error.message || error); }
    };
    window.kagendaSemesterWeekCountValidator = validator;
    const answer = await api.showPrompt('学期教学周数',
      '请按学校校历填写本学期的总教学周数（' + lastWeek + '–40 周）。', '', 'kagendaSemesterWeekCountValidator');
    requireValue(answer !== null && answer !== undefined, '已取消导入');
    return integer(answer, lastWeek, 40, '学期周数');
  }
  async function finish(courses, omitted, totalWeeks, semesterStartDate) {
    requireValue(Array.isArray(courses) && courses.length > 0 && courses.length <= 5000, '没有可导入的已排课课程');
    // Revalidate at the shared boundary; no silent filtering of malformed rows.
    const checked = courses.map(c => {
      const value=course(c.name, c.teacher, c.position, c.day, c.startSection, c.endSection, c.weeks);
      if(c.extraInfo != null) {
        requireValue(typeof c.extraInfo==='string' && c.extraInfo.length<=4000,'课程附加信息格式无效');
        if(c.extraInfo.trim()) value.extraInfo=c.extraInfo.trim();
      }
      return value;
    });
    const unique = Array.from(new Map(checked.map(c => [JSON.stringify(c), c])).values());
    const missing = integer(omitted, 0, 5000, '未排课课程数');
    requireValue(missing === 0, '另有 ' + missing + ' 门课程缺少完整排课，未导入部分结果');
    const lastWeek = unique.reduce((max, c) => c.weeks.reduce((n, week) => Math.max(n, week), max), 0);
    const count = await semesterWeeks(lastWeek, totalWeeks);
    const config = {semesterTotalWeeks: count};
    if (semesterStartDate != null) {
      const value = String(semesterStartDate);
      const date = new Date(value + 'T00:00:00Z');
      requireValue(/^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(date.getTime()) &&
        date.toISOString().slice(0,10) === value, '学期起始日期无效');
      config.semesterStartDate = value;
    }
    await api.saveCourseConfig(JSON.stringify(config));
    await api.saveImportedCourses(JSON.stringify(unique));
    api.notifyTaskCompletion();
  }
  async function run(task) {
    try { await task(); }
    catch (error) {
      await api.showAlert('课表导入未完成', String(error && error.message || error).slice(0, 500), '关闭');
      // No courses are staged on failure; completion clears the busy state.
      api.notifyTaskCompletion();
    }
  }
  function clock(value) {
    const match = String(value).trim().match(/^(\d{1,2}):(\d{2})$/);
    requireValue(match, '上课时间格式无效');
    const h = integer(match[1], 0, 23, '小时'), m = integer(match[2], 0, 59, '分钟');
    return String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
  }
  async function finishTimed(rows, totalWeeks) {
    requireValue(Array.isArray(rows) && rows.length > 0 && rows.length <= 5000, '没有可导入的定时课程');
    const checked = rows.map(r => {
      const start = clock(r.start), end = clock(r.end);
      requireValue(start < end, '课程结束时间早于开始时间');
      return Object.assign({}, r, {start, end, weeks: weeks(r.weeks)});
    });
    const boundaries = Array.from(new Set(checked.flatMap(r => [r.start, r.end]))).sort();
    const slots = [];
    for (let i = 0; i + 1 < boundaries.length; i++) {
      const start = boundaries[i], end = boundaries[i + 1];
      if (checked.some(r => r.start <= start && r.end >= end)) slots.push({number: slots.length + 1, startTime: start, endTime: end});
    }
    requireValue(slots.length > 0 && slots.length <= 24, '课表时间分段超过 24 个，请使用其他导入方案');
    const courses = checked.map(r => {
      const first = slots.findIndex(s => s.startTime === r.start) + 1;
      const last = slots.findIndex(s => s.endTime === r.end) + 1;
      return course(r.name, r.teacher, r.position, r.day, first, last, r.weeks);
    });
    const lastWeek = checked.reduce((max, row) => row.weeks.reduce((n, week) => Math.max(n, week), max), 0);
    const count = await semesterWeeks(lastWeek, totalWeeks);
    await api.savePresetTimeSlots(JSON.stringify(slots));
    await finish(courses, 0, count);
  }
  window.kagendaSchool = {requireValue, integer, weeks, bitmap, course, host, json, page, choose, finish, clock, finishTimed, run, api, adapterId: __ADAPTER_ID__};
})();
