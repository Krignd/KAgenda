/* ECNU personal course-table DOM adapter (ext_ecnu_dom).
 * Independent KAgenda implementation, DSH draft corrected by primary agent.
 * Protocol: Taulee/ECNUCourseTableTool-2.0@d3d60512b3c084df8f65154e6fc5b45fedccb1ea, MIT.
 * Fixed files/dates/hashes and original notice: academic/school-provenance.json. */
(function () {
  'use strict';
  var ID = 'ext_ecnu_dom';
  var k = window.kagendaSchool;
  function bad(message) { throw new Error(message); }
  function need(value, message) { if (!value) bad(message); }
  function text(node) { return node && node.textContent != null ? String(node.textContent).trim() : ''; }
  function detailText(node) {
    var walker=node.ownerDocument.createTreeWalker(node,4), parts=[], current;
    while((current=walker.nextNode())) {var value=String(current.nodeValue || '').trim();if(value)parts.push(value);}
    return parts.join(' ');
  }
  function exactly(doc, selector, message) {
    var nodes = doc.querySelectorAll(selector);
    need(nodes.length === 1, message);
    return nodes[0];
  }
  function classes(node) { return String(node && node.getAttribute('class') || '').split(/\s+/); }
  function numericClasses(node) { return classes(node).filter(function (c) { return /^\d+$/.test(c); }); }
  function clock(hour, minute) {
    var h = Number(hour), m = Number(minute);
    need(Number.isInteger(h) && h >= 0 && h <= 23 && Number.isInteger(m) && m >= 0 && m <= 59, '上课时间格式无效');
    return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
  }
  function auditUrl(loc) {
    var url;
    try { url = new URL(String(loc && loc.href || '')); }
    catch (_) { bad('无法识别当前页面地址'); }
    need(url.protocol === 'https:', '请使用 HTTPS 打开 ECNU 课表页面');
    need(url.hostname === 'byyt.ecnu.edu.cn', '请进入 byyt.ecnu.edu.cn 的 ECNU 课表页面');
    need(!url.port, '课表页面不能使用非默认端口');
    need(!url.username && !url.password, '课表页面地址不能包含用户名或密码');
    need(url.pathname === '/student/for-std/course-table' || url.pathname === '/student/for-std/course-table/',
      '请打开“我的课表”完整页面，而不是数据接口');
    return url.href;
  }
  function auditControls(doc) {
    var nodes = doc.querySelectorAll('input,select,textarea');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i], tag = el.tagName;
      if (tag === 'SELECT') {
        if (el.id === 'allSemesters') continue;
        var opts = el.querySelectorAll('option');
        for (var j = 0; j < opts.length; j++) {
          if (text(opts[j]) || String(opts[j].getAttribute('value') || '').trim()) bad('存在未识别的筛选下拉框');
        }
        continue;
      }
      if (tag === 'TEXTAREA') { if (String(el.value || '').trim()) bad('存在未识别的筛选条件'); continue; }
      var type = String(el.getAttribute('type') || 'text').toLowerCase();
      if (/^(?:button|submit|reset|image)$/.test(type)) continue;
      if (type === 'checkbox' || type === 'radio') bad('存在未识别的筛选条件');
      if (type === 'hidden') {
        var name = ((el.getAttribute('name') || '') + ' ' + (el.getAttribute('id') || '')).toLowerCase();
        if (!/^(?:csrf|csrftoken|_csrf|__requestverificationtoken)$/.test((el.name || el.id || '').toLowerCase())) bad('存在未识别的隐藏筛选项');
        continue;
      }
      if (type === 'password' || type === 'file' || String(el.value || '').trim()) bad('存在未识别的筛选条件');
    }
  }
  function auditExtras(doc, table) {
    need(table.querySelectorAll('table').length === 0, '课表中存在未知的嵌套表格');
    need(doc.querySelectorAll('td.td-content').length === table.querySelectorAll('td.td-content').length,
      '课表外存在未知的星期单元格');
    need(doc.querySelectorAll('td.dayPartUnit').length === table.querySelectorAll('td.dayPartUnit').length,
      '课表外存在未知的节次时间单元格');
    var bodyNode=doc.body && doc.body.cloneNode(true);
    if(bodyNode)bodyNode.querySelectorAll('script,style').forEach(function(n){n.remove();});
    var body = bodyNode ? text(bodyNode) : '';
    need(!/未排课|未安排课程|待排课/.test(body), '存在未排课课程，请显示完整课表后再导入');
    need(!/共\s*\d+\s*页/.test(body), '课表存在分页，无法导入完整学期');
    need(!doc.querySelector('.pagination,.pager,[class*="pagination"],[class*="pager"]'),
      '课表存在分页，无法导入完整学期');
    var links = doc.querySelectorAll('a[href]');
    for (var i = 0; i < links.length; i++) {
      var label = text(links[i]), href = String(links[i].getAttribute('href') || '');
      need(!/下一页|上一页|末页|首页/.test(label) && !/[?&](?:page|p|pageNo|pageNum)=\d+/i.test(href),
        '课表存在分页，无法导入完整学期');
    }
    var lists = doc.querySelectorAll('ul,ol,dl');
    for (var j = 0; j < lists.length; j++) {
      var sig = ((lists[j].getAttribute('id') || '') + ' ' + (lists[j].getAttribute('class') || '')).toLowerCase();
      if (/course|lesson|schedule|unscheduled|semester/.test(sig) && text(lists[j])) bad('发现额外的未知课程列表');
    }
  }
  function parseSemester(doc) {
    var sel = exactly(doc, '#allSemesters', '页面中缺少唯一的学期下拉框 #allSemesters');
    need(sel.tagName === 'SELECT' && !sel.disabled && !sel.multiple, '请选择唯一可用的学期控件');
    var selected = sel.selectedOptions;
    need(selected && selected.length === 1, '学期下拉框必须实际选中且仅选中一个学期');
    var opt = selected[0];
    var value = String(opt.getAttribute('value') || '').trim(), label = text(opt);
    need(value, '所选学期缺少真实 value');
    need(label && /\d{4}/.test(label) && /学期|[春夏秋冬]季/.test(label) && !/全部|所有|请选择/.test(label), '所选学期缺少明确学年学期');
    need(!opt.disabled, '所选学期不可用');
    return {value: value, label: label};
  }
  function parseStartDate(doc) {
    var node = exactly(doc, '#startDate', '页面中缺少唯一的学期起始日期 #startDate');
    var value = text(node), match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    need(match && value.length === 10, '学期起始日期必须为 YYYY-MM-DD');
    var y = Number(match[1]), m = Number(match[2]), d = Number(match[3]);
    var date = new Date(Date.UTC(y, m - 1, d));
    need(date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d,
      '学期起始日期不是有效日期');
    need(date.getUTCDay() === 1, '学期起始日期必须是星期一');
    return value;
  }
  function overlapsWeeks(a, b) {
    var i = 0, j = 0;
    while (i < a.length && j < b.length) {
      if (a[i] === b[j]) return true;
      if (a[i] < b[j]) i++; else j++;
    }
    return false;
  }
  function checkConflicts(courses) {
    for (var i = 0; i < courses.length; i++) {
      for (var j = i + 1; j < courses.length; j++) {
        var a = courses[i], b = courses[j];
        if (a.code !== b.code || a.day !== b.day) continue;
        if (a.startSection > b.endSection || b.startSection > a.endSection) continue;
        if (!overlapsWeeks(a.weeks, b.weeks)) continue;
        if (a.teacher !== b.teacher || a.position !== b.position) {
          bad('同一教学班在相交周次与节次上教师或教室不一致');
        }
      }
    }
  }
  function collectCell(doc, table, cell, day, period, rowCount, courses, byLesson, byCode, seen) {
    var cards = cell.querySelectorAll('.tdHtml');
    Array.prototype.forEach.call(cards, function (card) {
      need(card.closest('td.td-content') === cell, '课程卡片不在星期单元格内');
      var lesson = String(card.getAttribute('lessonid') || '').trim();
      need(lesson, '发现没有 lessonid 的课程卡片');
      need(card.querySelectorAll('.tdHtml').length === 0, '课程卡片存在未知嵌套');
      var nodes = Array.prototype.slice.call(card.childNodes), divs = [];
      nodes.forEach(function (node) {
        if (node.nodeType === 1) {
          need(node.tagName === 'DIV', '课程卡片包含未知元素');
          divs.push(node);
        } else if (node.nodeType === 3) {
          need(!String(node.nodeValue || '').trim(), '课程卡片包含未知文本');
        }
      });
      need(divs.length >= 3 && divs.length <= 15000 && divs.length % 3 === 0, '课程卡片分组不完整或过多');
      for (var i = 0; i < divs.length; i += 3) {
        seen.__processed=(seen.__processed || 0)+1;
        need(seen.__processed<=5000,'原始课程组超过支持范围');
        var nameNode = divs[i], codeNode = divs[i + 1], detailNode = divs[i + 2];
        need(nameNode.classList && nameNode.classList.contains('course-name'), '课程卡片缺少 course-name');
        var name = text(nameNode);
        need(name && name.length <= 200, '课程名称无效');
        var codeMatch = text(codeNode).match(/^教学班代码[：:]\s*(\S+)$/);
        need(codeMatch, '课程卡片缺少有效的教学班代码');
        var code = codeMatch[1];
        var detail = detailText(detailNode);
        var dm = detail.match(/^\((.+)周\)\s*\((\d+)-(\d+)节\)\s*(\S+)\s+(\S+)\s+(.+)$/);
        need(dm, '无法解析课程详情');
        var weekText = dm[1].trim(), startSection = Number(dm[2]), endSection = Number(dm[3]);
        var campus = dm[4].trim(), room = dm[5].trim(), teacher = dm[6].trim();
        need(weekText && startSection === period, '课程详情起节与卡片所在节次不一致');
        need(endSection >= startSection && endSection <= rowCount, '课程详情结束节次无效');
        need(campus && room && teacher, '课程校区、教室或教师不能为空');
        var weeks;
        try { weeks = k.weeks(weekText); }
        catch (_) { bad('无法解析周次：' + weekText); }
        need(Array.isArray(weeks) && weeks.length > 0, '课程周次不能为空');
        var pairKey = JSON.stringify([lesson, code]), prevPair = byLesson[pairKey];
        if (prevPair) need(prevPair === name, '同一 lessonid 与代码的课程名不一致');
        else byLesson[pairKey] = name;
        var prevCode = byCode[code];
        if (prevCode) need(prevCode === name, '同一教学班代码对应了不同课程名');
        else byCode[code] = name;
        var identity = JSON.stringify([name, code, day, startSection, endSection, weeks, campus, room, teacher]);
        if (seen[identity]) continue;
        seen[identity] = true;
        courses.push({name: name, teacher: teacher, position: campus + ' ' + room, day: day,
          startSection: startSection, endSection: endSection, weeks: weeks, code: code, lessonId: lesson});
        need(courses.length <= 5000, '课程记录超过支持范围');
      }
    });
    var clone = cell.cloneNode(true), clonedCards = clone.querySelectorAll('.tdHtml');
    Array.prototype.forEach.call(clonedCards, function (c) { if (c.parentNode) c.parentNode.removeChild(c); });
    need(!text(clone), '星期单元格中存在无法识别的非课程文本');
  }
  function parse(doc) {
    doc = doc || document;
    need(doc && doc.querySelectorAll, '无法读取当前课表页面');
    need(doc.querySelectorAll('iframe,frame').length === 0,
      '课表在未展开的 iframe 中，请打开完整课表页面');
    auditControls(doc);
    var table = exactly(doc, 'table.courseTable', '页面中缺少唯一的官方课表 table.courseTable');
    auditExtras(doc, table);
    need(doc.querySelectorAll('.tdHtml').length === table.querySelectorAll('td.td-content .tdHtml').length,
      '存在位置未知的课程卡片');
    need(doc.querySelectorAll('[lessonid]').length === table.querySelectorAll('.tdHtml[lessonid]').length,
      '存在未识别的课程标记');
    var knownNames=Array.prototype.slice.call(table.querySelectorAll('.tdHtml')).reduce(function(count,card) {
      return count+Array.prototype.slice.call(card.children).filter(function(n){return n.classList.contains('course-name');}).length;
    },0);
    need(doc.querySelectorAll('.course-name').length===knownNames,'存在未审计的课程名称标记');
    var semester = parseSemester(doc);
    var startDate = parseStartDate(doc);
    var allRows = Array.prototype.slice.call(table.querySelectorAll('tbody > tr'));
    var rows = allRows.filter(function(row) {
      if (numericClasses(row).length) return true;
      need(!row.querySelector('.tdHtml,[lessonid],td.td-content,td.dayPartUnit'), '非节次行包含未知课程');
      var cells=Array.prototype.slice.call(row.children);
      need(cells.length>0 && cells.every(function(cell) {
        return /^(TH|TD)$/.test(cell.tagName) && /^(?:节次|时间|时段|星期[一二三四五六日天]|周[一二三四五六日天]|上午|下午|晚上)?$/.test(text(cell));
      }), '存在未审计的非节次行');
      return false;
    });
    need(rows.length > 0 && rows.length <= 24, '课表节次行数量无效');
    need(doc.querySelectorAll('td.dayPartUnit').length === rows.length, '节次时间单元格数量与节次行不一致');
    var slots = [], courses = [], byLesson = Object.create(null), byCode = Object.create(null), seen = Object.create(null), prevEnd = -1;
    var occupied=rows.map(function(){return new Set();}), auditedCells=0;
    rows.forEach(function (row, index) {
      var period = index + 1, rowNums = numericClasses(row);
      need(rowNums.length === 1 && Number(rowNums[0]) === period, '课表节次必须从 1 开始连续编号');
      var timeCells = row.querySelectorAll('td.dayPartUnit');
      need(timeCells.length === 1, '每个节次行必须只有一个 td.dayPartUnit');
      var match = text(timeCells[0]).match(/(\d{1,2}):(\d{2})\s*~\s*(\d{1,2}):(\d{2})/);
      need(match, '第 ' + period + ' 节缺少真实起止时间');
      var start = Number(match[1]) * 60 + Number(match[2]);
      var end = Number(match[3]) * 60 + Number(match[4]);
      need(start < end, '第 ' + period + ' 节起止时间无效');
      need(start >= prevEnd, '课表节次时间重叠或未递增');
      prevEnd = end;
      slots.push({number: period, startTime: clock(match[1], match[2]), endTime: clock(match[3], match[4])});
      var cells = row.querySelectorAll('td.td-content'), days = {};
      need(cells.length <= 7, '节次行星期单元格数量无效');
      Array.prototype.forEach.call(cells, function (cell) {
        var dayNums = numericClasses(cell);
        need(dayNums.length === 1, '星期单元格必须且只能有一个数字 class');
        var day = Number(dayNums[0]);
        need(day >= 1 && day <= 7, '星期单元格的星期必须在 1..7');
        need(!days[day] && !occupied[index].has(day), '同一节次行的星期单元格重复或交叠');
        days[day] = true;
        var span=cell.getAttribute('rowspan') || '1';
        need(/^\d+$/.test(span) && Number(span)>=1 && index+Number(span)<=rows.length, '课表跨行范围无效');
        for(var n=index;n<index+Number(span);n++) {need(!occupied[n].has(day),'跨行星期单元格交叠');occupied[n].add(day);}
        auditedCells++;
        collectCell(doc, table, cell, day, period, rows.length, courses, byLesson, byCode, seen);
      });
      need(occupied[index].size === 7, '星期单元格必须完整覆盖 1..7');
    });
    need(doc.querySelectorAll('td.td-content').length === auditedCells, '存在未审计的星期单元格');
    need(courses.length > 0, '官方课表中没有解析到课程');
    checkConflicts(courses);
    courses.sort(function (a, b) {
      return a.day - b.day || a.startSection - b.startSection ||
        a.name.localeCompare(b.name) || a.code.localeCompare(b.code);
    });
    var maxWeek = 0;
    courses.forEach(function (course) { maxWeek = Math.max(maxWeek, course.weeks[course.weeks.length - 1]); });
    courses.forEach(function(course){course.extraInfo='教学班代码：'+course.code+'\n学期：'+semester.label;});
    return {semester: semester, startDate: startDate, slots: slots, courses: courses, maxWeek: maxWeek};
  }
  async function askWeeks(minimum) {
    need(k.api && typeof k.api.showPrompt === 'function', '原生桥接不支持学期总周数输入');
    var answer = await k.api.showPrompt('学期总周数', '请输入本学期总周数（不少于 ' + minimum + ' 周）', '');
    need(answer !== null && answer !== undefined, '已取消导入');
    var value = String(answer).trim();
    need(/^\d+$/.test(value), '学期总周数必须是正整数');
    var number = Number(value);
    need(Number.isSafeInteger(number) && number >= minimum && number <= 40,
      '学期总周数无效或小于课程最大周 ' + minimum);
    return number;
  }
  window.kagendaEcnuDom = {parse: parse, adapterId: ID};
  if (!k) return;
  k.run(async function () {
    need(k.adapterId === ID, '适配器标识不匹配，请使用 ECNU DOM 入口');
    var href = auditUrl(location);
    var before = parse(document);
    var totalWeeks = await askWeeks(before.maxWeek);
    var afterHref = auditUrl(location);
    need(afterHref === href, '导入过程中页面地址发生变化，请重新打开完整课表');
    var after = parse(document);
    need(JSON.stringify(after) === JSON.stringify(before), '导入过程中课表或筛选发生变化，请重新打开完整课表');
    need(totalWeeks >= after.maxWeek, '学期总周数小于课程最大周');
    var saved=await k.api.savePresetTimeSlots(JSON.stringify(after.slots));
    need(saved!==false,'预设时间段暂存失败');
    await k.finish(after.courses, 0, totalWeeks, after.startDate);
  });
})();
