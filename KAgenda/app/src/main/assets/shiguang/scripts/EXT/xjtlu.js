// Derived from sceneeeee/xjtlu-timetable-bookmarklet, MIT,
// snapshot 64951fce1204a3bce92a44885b85040c8996a5c7.
// Structured event selectors and week syntax from src/lib/parseTimetable.ts
// and parseWeeks.ts. KAgenda: strict completeness, explicit open-range end,
// preserve observed clock times, no fixed Monday/13-week default or guessed day.
// Original copyright/license: licenses/single-school-XJTLU-MIT.txt.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const days = {MON:1,TUE:2,WED:3,THU:4,FRI:5,SAT:6,SUN:7};
  const text = n => String(n && n.textContent || '').replace(/\u00a0/g, ' ').trim();
  function parseEvent(element) {
    const cell = element.closest('td.day-cell');
    const day = cell && days[String(cell.getAttribute('data-day')).toUpperCase()];
    const name = text(element.querySelector('.event-name'));
    k.requireValue(day && name, '课程缺少明确的星期或名称，请打开完整学期课表');
    const info = Array.from(element.querySelectorAll('.event-info')).map(text).filter(Boolean);
    const week = info.filter(s => /^Week\s*:/i.test(s));
    const time = info.filter(s => /^\d{1,2}:\d{2}\s*[-–]\s*\d{1,2}:\d{2}$/.test(s));
    k.requireValue(week.length === 1 && time.length === 1, '课程缺少唯一的教学周或起止时间');
    const range = time[0].split(/\s*[-–]\s*/);
    const rest = info.filter(s => s !== week[0] && s !== time[0] && s !== name);
    const rooms = rest.filter(s => /^(?:(?:SIP|TC)-)?(?:EB|IR|CB|SD|BS|PB|SA|MA|HS|SC)[A-Z0-9-]*\d[A-Z0-9-]*$/i.test(s) || /^No Location Required$/i.test(s));
    const teachers = rest.filter(s => !rooms.includes(s));
    k.requireValue(teachers.length <= 1, '课程教师或教室格式发生变化，请使用其他导入方案');
    return {name, day, teacher:teachers[0] || '', position:rooms.join(' '),
      start:range[0], end:range[1], weekText:week[0].replace(/^Week\s*:\s*/i, '')};
  }
  function activeWeeks(value, last) {
    const parts = String(value).split(',').map(s => s.trim());
    k.requireValue(parts.length > 0 && parts.every(s => /^\d+(?:\s*-\s*\d*)?$/.test(s)), '无法识别教学周');
    return k.weeks(parts.map(s => {
      if (/\-\s*$/.test(s)) return s.replace(/-\s*$/, '-' + k.integer(last, 1, 40, '最后教学周'));
      return s;
    }).join(','));
  }
  window.kagendaXjtluParsers = {parseEvent, activeWeeks};
  k.run(async () => {
    k.host(['timetableplus.xjtlu.edu.cn', 'ebridge.xjtlu.edu.cn']);
    // Protected timetable frames must be opened directly. Never combine a
    // visible subset with an inaccessible frame or guess weekdays from columns.
    k.requireValue(document.querySelectorAll('iframe,frame').length === 0, '请直接打开 timetableplus.xjtlu.edu.cn 的完整学期课表后再采集');
    const events = Array.from(document.querySelectorAll('table.timetable .event'));
    k.requireValue(events.length > 0 && events.length <= 5000, '未找到完整结构的学期课表，请打开完整学期课表后重试');
    const rows = events.map(parseEvent);
    let last;
    if (rows.some(r => /-\s*(?:,|$)/.test(r.weekText))) {
      const answer = await k.api.showPrompt('最后教学周', '课表含开放周次范围，请按学校本学期安排填写最后教学周（1–40）。', '', '');
      k.requireValue(answer !== null && answer !== undefined, '已取消导入');
      last = k.integer(answer, 1, 40, '最后教学周');
    }
    rows.forEach(r => { r.weeks = activeWeeks(r.weekText, last); });
    if (last != null) k.requireValue(rows.every(r => r.weeks.every(w => w <= last)), '最后教学周早于课表中明确的周次');
    await k.finishTimed(rows, last);
  });
})();
