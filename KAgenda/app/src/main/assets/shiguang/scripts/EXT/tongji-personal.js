/* KAgenda entry ext_tongji_personal: Tongji personal timetable, logged-in visible WebView only.
   Independent KAgenda implementation; DSH draft reviewed and corrected locally.
   Protocol: gzy31007/TJDesktopTimetable@4d79f8c (MIT) and the separate
   community TONGJI/tongji_01.js. Full notices and pinned files: school-provenance.json. */
(() => {
  'use strict';
  const k = window.kagendaSchool;
  const ID = 'ext_tongji_personal';
  const ORIGIN = 'https://1.tongji.edu.cn';
  const API = ORIGIN + '/api';
  const LIST = API + '/baseresservice/schoolCalendar/list';
  const detailUrl = id => API + '/baseresservice/schoolCalendar/detail?id=' + encodeURIComponent(id);
  const courseUrl = id => API + '/electionservice/reportManagement/findStudentTimetab?calendarId=' + encodeURIComponent(id);
  const entryUrl = window.location.href;
  let requestStamp = 0;
  function fresh(path) {
    const url = new URL(path);
    requestStamp = Math.max(Date.now(), requestStamp + 1);
    url.searchParams.set('_t', String(requestStamp));
    return k.json(url.href);
  }

  function checkLocation() {
    const l = window.location;
    const url = new URL(l.href);
    k.requireValue(l && l.protocol === 'https:' && l.hostname === '1.tongji.edu.cn' && l.port === '' &&
      l.origin === ORIGIN && url.username === '' && url.password === '' && l.href === entryUrl,
      '请先进入 https://1.tongji.edu.cn 并完成登录');
  }
  function text(value, label, max) {
    k.requireValue(typeof value === 'string', '缺少' + label);
    const s = value.trim();
    k.requireValue(s.length > 0 && s.length <= (max || 200), label + '无效');
    return s;
  }
  function idOf(value, label) {
    if (typeof value === 'number') k.requireValue(Number.isSafeInteger(value) && value > 0, label + '无效');
    else k.requireValue(typeof value === 'string' && /^\S{1,100}$/.test(value), label + '无效');
    return String(value);
  }
  function envelope(root, label) {
    k.requireValue(root && typeof root === 'object' && !Array.isArray(root), label + '返回格式无效');
    k.requireValue(root.code === undefined || root.code === null || root.code === 0 || root.code === 200, label + '返回码异常');
    k.requireValue(root.success === undefined || root.success === null || root.success === true, label + '返回失败');
    k.requireValue('data' in root, label + '缺少 data');
    Object.keys(root).forEach(key => k.requireValue(key === 'data' || !Array.isArray(root[key]), label + '包含未识别的附加列表'));
    return root;
  }
  function complete(root, count, label) {
    ['hasNext', 'hasMore'].forEach(key => {
      if (root[key] !== undefined && root[key] !== null)
        k.requireValue(root[key] === false, label + '分页未取全或标志无效');
    });
    const totals = ['total', 'totalSize', 'totalCount', 'totalElements', 'recordCount'];
    totals.forEach(key => {
      if (root[key] !== undefined && root[key] !== null)
        k.requireValue(Number.isSafeInteger(root[key]) && root[key] === count, label + '声明的总数不完整');
    });
    ['pages', 'totalPages', 'page', 'pageNum', 'pageNumber', 'currentPage'].forEach(key => {
      if (root[key] !== undefined && root[key] !== null)
        k.requireValue(Number.isSafeInteger(root[key]) && root[key] >= 0 && root[key] <= 1, label + '不是完整单页数据');
    });
    if (root.pageSize != null) k.requireValue(Number.isSafeInteger(root.pageSize) && root.pageSize >= count &&
      totals.some(key => root[key] === count), label + '缺少完整分页总数');
  }
  function clock(value, label) {
    const m = typeof value === 'string' && value.trim().match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
    k.requireValue(m, label + '格式无效');
    if (m[3] !== undefined) k.requireValue(m[3] === '00', label + '秒必须为 00');
    const h = Number(m[1]), min = Number(m[2]);
    k.requireValue(h <= 23 && min <= 59, label + '无效');
    return String(h).padStart(2, '0') + ':' + String(min).padStart(2, '0');
  }
  function calendarOf(root, termKey) {
    const data = envelope(root, '校历').data;
    k.requireValue(data && typeof data === 'object' && !Array.isArray(data), '校历格式无效');
    if (data.id !== undefined && data.id !== null) k.requireValue(idOf(data.id, '校历 id') === termKey, '校历 id 与所选学期不一致');
    k.requireValue(Number.isSafeInteger(data.beginDay), '校历缺少有效 beginDay');
    const date = new Date(data.beginDay + 8 * 3600000);
    k.requireValue(Number.isFinite(date.getTime()) && date.getUTCDay() === 1, '校历开始日期不是周一');
    const raw = data.noWeekendWorkTimes;
    k.requireValue(Array.isArray(raw) && raw.length > 0 && raw.length <= 24, '校历缺少有效节次');
    const slots = [];
    let previousEnd = '';
    raw.forEach((slot, index) => {
      k.requireValue(slot && typeof slot === 'object' && !Array.isArray(slot), '校历节次格式无效');
      if (slot.classNode !== undefined && slot.classNode !== null)
        k.requireValue(slot.classNode === index + 1, '校历 classNode 与节次不一致');
      const start = clock(slot.beginTime, '节次开始时间'), end = clock(slot.endTime, '节次结束时间');
      k.requireValue(start < end, '校历节次时间无效');
      k.requireValue(previousEnd === '' || previousEnd <= start, '校历节次时间交叠');
      slots.push({number: index + 1, startTime: start, endTime: end});
      previousEnd = end;
    });
    if (data.teachingWeekStart !== undefined && data.teachingWeekStart !== null)
      k.requireValue(data.teachingWeekStart === 1, '校历教学周起始不是第 1 周');
    let teachingWeekEnd = null;
    if (data.teachingWeekEnd !== undefined && data.teachingWeekEnd !== null) {
      k.requireValue(Number.isSafeInteger(data.teachingWeekEnd) && data.teachingWeekEnd >= 1 && data.teachingWeekEnd <= 40,
        '校历总周数无效');
      teachingWeekEnd = data.teachingWeekEnd;
    }
    const startDate = date.toISOString().slice(0, 10);
    k.requireValue(/^\d{4}-\d{2}-\d{2}$/.test(startDate), '校历日期超出支持范围');
    return {key: termKey, beginDay: data.beginDay, startDate, slots,
      teachingWeekStart: data.teachingWeekStart === undefined ? null : data.teachingWeekStart, teachingWeekEnd};
  }
  function weeksOf(value) {
    k.requireValue(Array.isArray(value) && value.length > 0 && value.length <= 500, '缺少明确周次');
    const weeks = value.map(w => {
      k.requireValue(typeof w === 'number' && Number.isSafeInteger(w) && w >= 1 && w <= 40, '周次无效');
      return w;
    });
    return Array.from(new Set(weeks)).sort((a, b) => a - b);
  }
  function sectionOf(value, min, max, label) {
    k.requireValue(typeof value === 'number' && Number.isSafeInteger(value) && value >= min && value <= max, label + '无效');
    return value;
  }
  function codeOf(value, label) {
    if (value === undefined || value === null) return '';
    if (typeof value === 'string') {
      const s = value.trim();
      k.requireValue(s.length <= 100, label + '无效');
      return s;
    }
    k.requireValue(typeof value === 'number' && Number.isSafeInteger(value) && value > 0, label + '无效');
    return String(value);
  }
  function courseIds(course) {
    const ids = [];
    [['teachingClassId', course.teachingClassId], ['classCode', course.classCode], ['courseCode', course.courseCode]]
      .forEach(pair => { const s = codeOf(pair[1], pair[0]); if (s) ids.push(pair[0] + ':' + s); });
    k.requireValue(ids.length > 0, '课程缺少 teachingClassId/classCode/courseCode');
    return ids;
  }
  function coursesOf(root, slotCount) {
    const data = envelope(root, '课程').data;
    k.requireValue(Array.isArray(data) && data.length > 0 && data.length <= 5000, '没有取得课程数据');
    complete(root, data.length, '课程');
    let omitted = 0;
    const facts = [], snapshots = [];
    data.forEach(course => {
      k.requireValue(course && typeof course === 'object' && !Array.isArray(course), '课程格式无效');
      const name = text(course.courseName, '课程名称');
      Object.keys(course).forEach(key => k.requireValue(key === 'timeTableList' || !Array.isArray(course[key]) ||
        course[key].length === 0, '课程包含未识别的附加列表'));
      const ids = courseIds(course);
      const row = [name, ids.slice().sort(), []];
      k.requireValue(Array.isArray(course.timeTableList), '课程缺少 timeTableList');
      if (course.timeTableList.length === 0) { omitted++; snapshots.push(row); return; }
      k.requireValue(course.timeTableList.length <= 500, '课程排课数据过多');
      course.timeTableList.forEach(time => {
        k.requireValue(time && typeof time === 'object' && !Array.isArray(time), '排课格式无效');
        const teacher = text(time.teacherName, '教师');
        [time.roomIdI18n, time.roomLable, time.classRoomName].forEach(v =>
          k.requireValue(v == null || typeof v === 'string', '教室字段格式无效'));
        const room = [time.roomIdI18n, time.roomLable, time.classRoomName]
          .map(v => typeof v === 'string' ? v.trim() : '').find(Boolean);
        const position = text(room, '教室');
        const day = sectionOf(time.dayOfWeek, 1, 7, '星期');
        const startSection = sectionOf(time.timeStart, 1, slotCount, '起始节次');
        const endSection = sectionOf(time.timeEnd, startSection, slotCount, '结束节次');
        const weeks = weeksOf(time.weeks);
        facts.push({name, teacher, position, day, startSection, endSection, weeks, ids});
        k.requireValue(facts.length <= 5000, '排课记录超过支持范围');
        row[2].push([teacher, position, day, startSection, endSection, weeks]);
      });
      snapshots.push(row);
    });
    k.requireValue(facts.length > 0, '没有可导入的已排课课程');
    for (let i = 0; i < facts.length; i++) for (let j = i + 1; j < facts.length; j++) {
      const a = facts[i], b = facts[j];
      // A shared courseCode does not imply a shared teaching class.
      if (a.ids[0] !== b.ids[0] || a.day !== b.day) continue;
      if (a.startSection > b.endSection || b.startSection > a.endSection) continue;
      if (!a.weeks.some(week => b.weeks.includes(week))) continue;
      if (a.teacher !== b.teacher || a.position !== b.position) throw new Error('同一教学班同一时段教师或教室冲突');
    }
    const seen = new Set(), courses = [];
    facts.forEach(f => {
      const key = JSON.stringify([f.name, f.teacher, f.position, f.day, f.startSection, f.endSection, f.weeks, f.ids]);
      if (seen.has(key)) return;
      seen.add(key);
      courses.push({name: f.name, teacher: f.teacher, position: f.position, day: f.day,
        startSection: f.startSection, endSection: f.endSection, weeks: f.weeks, extraInfo:f.ids.join('\n')});
    });
    const signature = snapshots.map(row => JSON.stringify(row)).sort().join('\n');
    return {courses, omitted, signature};
  }
  async function termList() {
    const root = envelope(await fresh(LIST), '学期列表');
    k.requireValue(Array.isArray(root.data) && root.data.length > 0 && root.data.length <= 200, '没有取得学期列表');
    complete(root, root.data.length, '学期列表');
    const ids = new Set();
    root.data.forEach(row => {
      k.requireValue(row && typeof row === 'object' && !Array.isArray(row), '学期列表格式无效');
      const id = idOf(row.id, '学期 id');
      k.requireValue(!ids.has(id), '学期 id 重复');
      ids.add(id);
      text(row.fullName, '学期名称', 100);
    });
    return root.data;
  }
  async function totalWeeksOf(calendar) {
    if (calendar.teachingWeekEnd !== null) return calendar.teachingWeekEnd;
    k.requireValue(k.api && typeof k.api.showPrompt === 'function', '当前环境无法确认学期总周数');
    const answer = await k.api.showPrompt('按校历填写学期总周数', '请输入 1-40 的整数，不能留空', '');
    k.requireValue(answer !== null && answer !== undefined, '已取消导入');
    const text = String(answer).trim();
    k.requireValue(/^\d{1,2}$/.test(text), '学期总周数无效');
    const weeks = Number(text);
    k.requireValue(Number.isSafeInteger(weeks) && weeks >= 1 && weeks <= 40, '学期总周数无效');
    return weeks;
  }

  async function main() {
    k.requireValue(k.adapterId === ID, '适配器标识不匹配');
    checkLocation();
    const firstList = await termList();
    const picked = await k.choose('请选择学期', firstList, 'id', 'fullName');
    const termKey = idOf(picked.id, '学期 id');
    const termName = text(picked.fullName, '学期名称', 100);
    const firstCalendar = calendarOf(await fresh(detailUrl(termKey)), termKey);
    const firstCourses = coursesOf(await fresh(courseUrl(termKey)), firstCalendar.slots.length);
    k.requireValue(firstCourses.omitted === 0, '存在未排课课程，未导入部分结果');

    const totalWeeks = await totalWeeksOf(firstCalendar);
    const lastWeek = Math.max(...firstCourses.courses.flatMap(course => course.weeks));
    k.requireValue(totalWeeks >= lastWeek, '学期总周数小于已排周次');
    // All interaction precedes final snapshots: a modal may outlive the session.
    checkLocation();

    const secondCalendar = calendarOf(await fresh(detailUrl(termKey)), termKey);
    k.requireValue(JSON.stringify(firstCalendar) === JSON.stringify(secondCalendar), '校历读取期间发生变化');
    const secondCourses = coursesOf(await fresh(courseUrl(termKey)), secondCalendar.slots.length);
    k.requireValue(firstCourses.omitted === secondCourses.omitted && firstCourses.signature === secondCourses.signature,
      '课程读取期间发生变化');
    const secondList = await termList();
    const matches = secondList.filter(row => idOf(row.id, '学期 id') === termKey);
    k.requireValue(matches.length === 1 && text(matches[0].fullName, '学期名称', 100) === termName,
      '学期列表读取期间发生变化');
    checkLocation();

    const saved = await k.api.savePresetTimeSlots(JSON.stringify(secondCalendar.slots));
    k.requireValue(saved !== false, '预设时间段保存失败');
    await k.finish(secondCourses.courses, 0, totalWeeks, secondCalendar.startDate);
  }
  k.run(main);
})();
