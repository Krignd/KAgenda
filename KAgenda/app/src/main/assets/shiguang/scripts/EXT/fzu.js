/*
 * Derived from west2-online/jwch/course.go, Apache-2.0.
 * Copyright 2024 The west2-online Authors.
 * Snapshot 81779d5a6307fa46f2017cba89d246691b5be73e.
 * KAgenda changes: read the visible, selected semester from the signed-in DOM;
 * preserve explicit weeks/parity and apply table adjustment rules; reject
 * unrecognized/full-week schedules instead of inventing periods; preview first.
 * License and exact provenance: academic/school-provenance.json.
 */
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const text = n => String(n && (n.innerText || n.textContent) || '').trim();
  function parseSchedule(value, name, teacher) {
    const courses = [];
    String(value).split(/\r?\n/).map(s => s.trim()).filter(Boolean).forEach(line => {
      const m = line.match(/^(\d+(?:-\d+)?)\s+星期([1-7]):(\d+)-(\d+)节(?:[（(]([单双])[）)])?\s*(.*)$/);
      k.requireValue(m, '不能识别排课或整周安排：' + line);
      courses.push(k.course(name, teacher, m[6], m[2], m[3], m[4], k.weeks(m[1] + (m[5] ? '(' + m[5] + ')' : ''))));
    });
    k.requireValue(courses.length > 0, '课程缺少明确排课');
    return courses;
  }
  function adjustments(courses, value) {
    String(value || '').split(/\r?\n/).map(s => s.trim()).filter(Boolean).forEach(line => {
      const m = line.match(/^(\d+)\s*周\s*星期([1-7]):(\d+)-(\d+)节\s*调至\s*(\d+)\s*周\s*星期([1-7]):(\d+)-(\d+)节\s*(.*)$/);
      k.requireValue(m, '不能识别调课信息：' + line);
      const week = k.integer(m[1], 1, 40, '原周次');
      const original = courses.filter(c => c.day === Number(m[2]) && c.startSection === Number(m[3]) && c.endSection === Number(m[4]) && c.weeks.includes(week));
      k.requireValue(original.length === 1, '调课信息与原课表不一致，未导入部分结果');
      const course = original[0];
      course.weeks = course.weeks.filter(w => w !== week);
      courses.push(k.course(course.name, course.teacher, m[9], m[6], m[7], m[8], [k.integer(m[5], 1, 40, '调课周次')]));
    });
    return courses.filter(c => c.weeks.length > 0);
  }
  window.kagendaFzuParsers = {parseSchedule, adjustments};
  k.run(async () => {
    k.host(['jwcjwxt2.fzu.edu.cn']);
    const term = document.querySelector('#ContentPlaceHolder1_DDL_xnxq');
    const table = document.querySelector('#ContentPlaceHolder1_DataList_xxk');
    k.requireValue(term && String(term.value || '').trim() && table, '请在教务系统打开我的选课，并选择学期后查询');
    // The source returns the whole semester in this table. Paging controls mean
    // that assumption no longer holds, so do not silently import the first page.
    k.requireValue(!table.querySelector('a[href*="Page$"]'), '课表出现分页，请使用其他导入方案');
    const result = [];
    Array.from(table.querySelectorAll('tr')).forEach(row => {
      const cells = row.querySelectorAll(':scope > td');
      if (!cells.length || cells.length === 1 || text(cells[1]) === '课程名称') return;
      k.requireValue(cells.length === 12, '课表列数发生变化，未导入部分结果');
      const name = text(cells[1]), teacher = text(cells[7]), schedule = text(cells[8]);
      k.requireValue(schedule && !/^(?:未安排|暂无|待定)$/.test(schedule),
        '课程“' + name + '”缺少完整排课，未导入部分结果');
      result.push(...adjustments(parseSchedule(schedule, name, teacher), text(cells[11])));
    });
    await k.finish(result, 0);
  });
})();
