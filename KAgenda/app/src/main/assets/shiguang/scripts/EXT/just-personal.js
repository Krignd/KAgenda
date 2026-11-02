// Independent KAgenda implementation; protocol reference: lawrencehaohan-coder/suike, MIT.
// Pinned 99b7dc1f6db54fd9c36afb69ac4d30a724c9ef1b; no upstream fixtures or code executed.
/*
 * Independent, DOM-only JUST personal-timetable reader.
 *
 * Contract:
 *   globalThis.extractJustTimetable(doc) -> { year, semester, meetings, maxWeek }
 *
 * The caller supplies an already-open Document. This function never consults
 * location/document/fetch/storage, mutates no node, and triggers no event.
 * It is intentionally all-or-nothing: every error is thrown, never returned as
 * a partial timetable.
 *
 * Static design was informed by the public MIT-licensed files
 * teaching-collector.js / teaching-parser.js / schools.json at the pinned
 * commit. No upstream fixture, account data or app code is bundled here.
 */
(function () {
  'use strict';

  var MAX_MEETINGS = 5000;
  var MAX_WEEKS = 40;
  var MAX_SECTIONS = 11;
  var MAX_NAME = 200;
  var MAX_FIELD = 200;
  var MAX_CLASS_ID = 120;

  var CONTROL_CHARS = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;
  var EXPECTED_HEADERS = ['时间段', '节次', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
  var GROUP_LABELS = ['上午', '下午', '晚上'];
  var BAD_BLOCK_TAGS = ['table', 'form', 'input', 'select', 'textarea', 'button', 'script', 'style', 'iframe', 'object', 'embed', 'img', 'svg', 'canvas', 'video', 'audio'];
  var BAD_BOTTOM_TAGS = ['table', 'form', 'input', 'select', 'textarea', 'button', 'script', 'style', 'iframe', 'object', 'embed', 'a', 'img', 'ul', 'ol', 'li', 'dl', 'dt', 'dd', 'p', 'video', 'audio', 'canvas'];
  var BOTTOM_TITLE_TAGS = ['td', 'th', 'div', 'span', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'strong', 'b', 'em', 'i', 'font', 'br'];
  var BOTTOM_TITLE_ATTRS = ['class', 'colspan', 'rowspan'];

  function fail(code) {
    var error = new Error(code);
    error.name = 'JustTimetableError';
    error.code = code;
    throw error;
  }

  function tagOf(node) {
    if (!node) return '';
    if (typeof node.localName === 'string' && node.localName) return node.localName.toLowerCase();
    if (typeof node.nodeName === 'string') return node.nodeName.toLowerCase();
    return '';
  }

  function hasClass(node, name) {
    if (!node) return false;
    if (node.classList && typeof node.classList.contains === 'function') {
      return node.classList.contains(name);
    }
    var raw = node.getAttribute && node.getAttribute('class');
    return typeof raw === 'string' && (' ' + raw.replace(/\s+/g, ' ') + ' ').indexOf(' ' + name + ' ') >= 0;
  }

  function isText(node) {
    return !!node && node.nodeType === 3;
  }

  function isElement(node) {
    return !!node && node.nodeType === 1;
  }

  function isInside(node, ancestor) {
    var cursor = node;
    while (cursor) {
      if (cursor === ancestor) return true;
      cursor = cursor.parentNode;
    }
    return false;
  }

  function nodeArray(root, selector, code) {
    if (!root || typeof root.querySelectorAll !== 'function') fail(code || 'DOCUMENT_INVALID');
    var found = root.querySelectorAll(selector);
    var result = [];
    for (var i = 0; i < found.length; i++) result.push(found[i]);
    return result;
  }

  function exactlyOne(root, selector, code) {
    var found = nodeArray(root, selector, code);
    if (found.length !== 1) fail(code);
    return found[0];
  }

  function readText(element, max, required, code) {
    if (!isElement(element)) fail(code || 'TEXT_INVALID');
    var raw = element.textContent;
    if (typeof raw !== 'string' || raw.length > max * 8 + 64 || CONTROL_CHARS.test(raw)) fail(code || 'TEXT_INVALID');
    var value = raw.replace(/\s+/g, ' ').trim();
    if (value.length > max || (required && !value)) fail(code || 'TEXT_INVALID');
    return value;
  }

  function ambiguousSpan(cell) {
    if (!cell) return true;
    return Number(cell.colSpan) !== 1 || Number(cell.rowSpan) !== 1;
  }

  function periodLabel(text, section) {
    var match = /^(?:第)?(\d{1,2})(?:节)?(?:\s*[（(]?\s*((?:[01]?\d|2[0-3])[:：][0-5]\d)\s*(?:[-–—~～至]|\s+)\s*((?:[01]?\d|2[0-3])[:：][0-5]\d)\s*[）)]?)?$/.exec(text);
    return !!match && Number(match[1]) === section && (!match[2] || match[2].replace('：',':').padStart(5,'0') < match[3].replace('：',':').padStart(5,'0'));
  }

  function groupLabel(text) {
    return GROUP_LABELS.indexOf(text) >= 0;
  }

  function intervalIntersects(left, right) {
    var i = 0;
    var j = 0;
    while (i < left.length && j < right.length) {
      if (left[i] === right[j]) return true;
      if (left[i] < right[j]) i++;
      else j++;
    }
    return false;
  }

  function compareNumbers(left, right) {
    for (var i = 0; i < left.length && i < right.length; i++) {
      if (left[i] !== right[i]) return left[i] - right[i];
    }
    return left.length - right.length;
  }

  function textCompare(left, right) {
    if (left < right) return -1;
    if (left > right) return 1;
    return 0;
  }

  function findMainTable(doc) {
    var tables = nodeArray(doc, '#kbgrid_table_0', 'TABLE_INVALID');
    if (tables.length !== 1) fail('TABLE_INVALID');
    var table = tables[0];
    if (tagOf(table) !== 'table') fail('TABLE_INVALID');
    if (table.querySelector('table')) fail('TABLE_NESTED');
    var parent = table.parentElement;
    while (parent) {
      if (tagOf(parent) === 'table') fail('TABLE_NESTED');
      parent = parent.parentElement;
    }
    if (!table.rows || table.rows.length !== 14) fail('TABLE_INVALID');
    return table;
  }

  function readTermTitle(table) {
    var firstRow = table.rows[0];
    if (!firstRow) fail('TERM_TITLE_INVALID');
    var titleNode = firstRow.querySelector('h6');
    if (!titleNode) fail('TERM_TITLE_INVALID');
    var title = readText(titleNode, 80, true, 'TERM_TITLE_INVALID').replace(/\s/g, '');
    var match = /^(\d{4})-(\d{4})学年第([12])学期$/.exec(title);
    if (!match) fail('TERM_TITLE_INVALID');
    return match;
  }

  function readSelectedOptions(doc, form) {
    var yearSelect = exactlyOne(doc, '#xnm', 'TERM_SELECT_INVALID');
    var semesterSelect = exactlyOne(doc, '#xqm', 'TERM_SELECT_INVALID');
    if (tagOf(yearSelect) !== 'select' || tagOf(semesterSelect) !== 'select') fail('TERM_SELECT_INVALID');
    if (yearSelect === semesterSelect) fail('TERM_SELECT_INVALID');
    if (!isInside(yearSelect, form) || !isInside(semesterSelect, form)) fail('TERM_SELECT_INVALID');
    return {
      year: selectedLabel(yearSelect, 20, 'TERM_SELECTION_INVALID'),
      semester: selectedLabel(semesterSelect, 1, 'TERM_SELECTION_INVALID'),
      yearSelect: yearSelect,
      semesterSelect: semesterSelect
    };
  }

  function selectedLabel(select, max, code) {
    if (select.disabled || select.multiple) fail(code);
    var options = select.options;
    if (!options || !options.length) fail(code);
    var selected = [];
    for (var i = 0; i < options.length; i++) {
      if (options[i].selected) {
        if (options[i].disabled) fail(code);
        selected.push(options[i]);
      }
    }
    if (selected.length !== 1) fail(code);
    var label = readText(selected[0], max, true, code);
    if (CONTROL_CHARS.test(label)) fail(code);
    return label;
  }

  function validateTerm(titleMatch, year, semester) {
    var years = /^(\d{4})-(\d{4})$/.exec(year);
    if (!years) fail('TERM_INVALID');
    var first = Number(years[1]);
    var second = Number(years[2]);
    if (first < 1900 || second > 2200 || second !== first + 1) fail('TERM_INVALID');
    if (!/^[12]$/.test(semester)) fail('TERM_INVALID');
    if (titleMatch[1] !== years[1] || titleMatch[2] !== years[2] || titleMatch[3] !== semester) fail('TERM_MISMATCH');
    return { year: years[1] + '-' + years[2], semester: semester };
  }

  function validateHeader(row) {
    var cells = row.cells;
    if (!cells || cells.length !== EXPECTED_HEADERS.length) fail('HEADER_INVALID');
    for (var i = 0; i < EXPECTED_HEADERS.length; i++) {
      var cell = cells[i];
      if (ambiguousSpan(cell)) fail('HEADER_INVALID');
      if (tagOf(cell) !== 'td' && tagOf(cell) !== 'th') fail('HEADER_INVALID');
      if (readText(cell, 12, true, 'HEADER_INVALID') !== EXPECTED_HEADERS[i]) fail('HEADER_INVALID');
    }
  }

  function buildDayCells(doc, table) {
    var map = Object.create(null);
    var groupUntil = 0;
    for (var section = 1; section <= MAX_SECTIONS; section++) {
      var row = table.rows[section + 1];
      if (!row) fail('PERIOD_ROW_INVALID');
      var cells = row.cells;
      if (!cells || (cells.length !== 8 && cells.length !== 9)) fail('PERIOD_ROW_INVALID');
      var leadCount = cells.length - 7;
      for (var c = 0; c < cells.length; c++) {
        if (c === 0 && leadCount === 2 && groupLabel(readText(cells[c],12,true,'PERIOD_LABEL_INVALID'))) {
          var span = Number(cells[c].rowSpan);
          if (Number(cells[c].colSpan)!==1 || !Number.isInteger(span) || span<1 || section+span-1>11 || groupUntil>=section) fail('SPAN_AMBIGUOUS');
          groupUntil=section+span-1;
        } else if (ambiguousSpan(cells[c])) fail('SPAN_AMBIGUOUS');
      }
      for (var lead = 0; lead < leadCount; lead++) {
        if (cells[lead].getAttribute('id') !== null) fail('CELL_ID_INVALID');
      }
      for (var day = 1; day <= 7; day++) {
        var cell = cells[leadCount + day - 1];
        if (tagOf(cell) !== 'td') fail('CELL_ID_INVALID');
        var expectedId = day + '-' + section;
        if (cell.getAttribute('id') !== expectedId) fail('CELL_ID_INVALID');
        if (map[expectedId]) fail('CELL_ID_INVALID');
        map[expectedId] = cell;
      }
      var firstLabel = readText(cells[0], 80, true, 'PERIOD_LABEL_INVALID');
      if (leadCount === 2) {
        var secondLabel = readText(cells[1], 80, true, 'PERIOD_LABEL_INVALID');
        if (!groupLabel(firstLabel) || !periodLabel(secondLabel, section)) fail('PERIOD_LABEL_INVALID');
      } else if (!periodLabel(firstLabel, section)) {
        fail('PERIOD_LABEL_INVALID');
      }
    }

    var allIds = doc.querySelectorAll('[id]');
    var dayIdCount = 0;
    for (var i = 0; i < allIds.length; i++) {
      var id = allIds[i].getAttribute('id');
      if (/^[1-7]-(?:1[01]|[1-9])$/.test(id)) {
        if (map[id] !== allIds[i]) fail('CELL_ID_INVALID');
        dayIdCount++;
      }
    }
    if (dayIdCount !== 77) fail('CELL_ID_INVALID');

    var tableTds = table.querySelectorAll('td[id]');
    if (tableTds.length !== 77) fail('CELL_ID_INVALID');
    for (var t = 0; t < tableTds.length; t++) {
      var tableId = tableTds[t].getAttribute('id');
      if (map[tableId] !== tableTds[t]) fail('CELL_ID_INVALID');
    }
    return map;
  }

  function filterHint(control) {
    var values = [];
    var names = ['name', 'id', 'aria-label', 'placeholder', 'title'];
    for (var i = 0; i < names.length; i++) {
      var value = control.getAttribute(names[i]);
      if (typeof value === 'string') values.push(value);
    }
    var joined = values.join(' ');
    return /week|weeks|zhou|course|kcmc|campus|xq|周|课程|校区|教学班/i.test(joined);
  }

  function nonEmpty(value) {
    return typeof value === 'string' && value.replace(/\s+/g, '').length > 0;
  }

  function controlList(form) {
    var result = [];
    var push = function (node) {
      if (node && result.indexOf(node) < 0) result.push(node);
    };
    var descendants = form.querySelectorAll('input,select,textarea,button');
    for (var i = 0; i < descendants.length; i++) push(descendants[i]);
    if (form.elements) {
      for (var j = 0; j < form.elements.length; j++) push(form.elements[j]);
    }
    return result;
  }

  function auditOneControl(control, xnm, xqm) {
    if (control === xnm || control === xqm) return;
    var tag = tagOf(control);
    if (tag === 'button') return;
    if (tag === 'select') {
      var options = control.options || [];
      for (var s = 0; s < options.length; s++) {
        if (!options[s].selected) continue;
        var optionText = readText(options[s], 200, false, 'FILTER_INVALID');
        var optionValue = typeof options[s].value === 'string' ? options[s].value : '';
        if ((nonEmpty(optionText) || nonEmpty(optionValue)) && !/^(全部|所有|不限|全部周次|全学期)$/.test(optionText)) fail('FILTER_ACTIVE');
      }
      return;
    }
    if (tag === 'textarea') {
      var areaValue = typeof control.value === 'string' ? control.value : control.textContent;
      if (nonEmpty(areaValue)) fail('FILTER_ACTIVE');
      return;
    }
    if (tag === 'input') {
      var type = String(control.getAttribute('type') || 'text').toLowerCase();
      if (type === 'button' || type === 'submit' || type === 'reset' || type === 'image') return;
      var inputValue = typeof control.value === 'string' ? control.value : '';
      if (type === 'hidden') {
        if (nonEmpty(inputValue) && !/^(?:csrf|csrfToken|_csrf|__RequestVerificationToken)$/i.test(control.name||control.id||'')) fail('FILTER_ACTIVE');
        return;
      }
      if (type === 'checkbox' || type === 'radio') {
        fail('FILTER_ACTIVE');
        return;
      }
      if (nonEmpty(inputValue)) fail('FILTER_ACTIVE');
    }
  }

  function auditFilters(form, xnm, xqm) {
    var controls = controlList(form);
    for (var i = 0; i < controls.length; i++) {
      auditOneControl(controls[i], xnm, xqm);
    }
  }

  function auditExternalControls(doc, form, xnm, xqm) {
    var controls = doc.querySelectorAll('input,select,textarea');
    for (var i = 0; i < controls.length; i++) {
      if (isInside(controls[i], form)) continue;
      auditOneControl(controls[i], xnm, xqm);
    }
  }

  function parseWeeks(expression) {
    var text = normalizePattern(expression);
    if (!text) fail('WEEKS_INVALID');
    var parts = text.split(',');
    if (parts.length > MAX_WEEKS) fail('WEEKS_INVALID');
    var result = [];
    var seen = Object.create(null);
    for (var p = 0; p < parts.length; p++) {
      var match = /^(\d{1,2})(?:-(\d{1,2}))?周(?:\((单|双)\))?$/.exec(parts[p]);
      if (!match) fail('WEEKS_INVALID');
      var first = Number(match[1]);
      var last = Number(match[2] || match[1]);
      if (first < 1 || last < first || last > MAX_WEEKS) fail('WEEKS_INVALID');
      var added = 0;
      for (var week = first; week <= last; week++) {
        if (match[3] === '单' && week % 2 !== 1) continue;
        if (match[3] === '双' && week % 2 !== 0) continue;
        if (!seen[week]) {
          seen[week] = true;
          result.push(week);
          added++;
        }
      }
      if (!added) fail('WEEKS_EMPTY_SEGMENT');
    }
    result.sort(function (a, b) { return a - b; });
    if (!result.length) fail('WEEKS_INVALID');
    return result;
  }

  function normalizePattern(value) {
    return value
      .replace(/\s+/g, '')
      .replace(/[，、]/g, ',')
      .replace(/（/g, '(')
      .replace(/）/g, ')')
      .replace(/[–—－~～]/g, '-');
  }

  function parsePattern(value, cellPeriod) {
    var text = normalizePattern(value);
    if (text.charAt(0) !== '(') fail('PATTERN_INVALID');
    var close = text.indexOf(')');
    if (close < 3 || close === text.length - 1) fail('PATTERN_INVALID');
    var sectionText = text.slice(1, close);
    var weekText = text.slice(close + 1);
    if (sectionText.charAt(sectionText.length - 1) !== '节') fail('PATTERN_INVALID');
    var body = sectionText.slice(0, -1);
    if (!body || body.indexOf('节') >= 0) fail('PATTERN_INVALID');
    var tokens = body.split(',');
    if (tokens.length > MAX_SECTIONS) fail('SECTIONS_INVALID');
    var sections = [];
    var previousEnd = 0;
    for (var i = 0; i < tokens.length; i++) {
      var match = /^(\d{1,2})(?:-(\d{1,2}))?$/.exec(tokens[i]);
      if (!match) fail('SECTIONS_INVALID');
      var first = Number(match[1]);
      var last = Number(match[2] || match[1]);
      if (first < 1 || last < first || last > MAX_SECTIONS || first <= previousEnd) fail('SECTIONS_INVALID');
      for (var section = first; section <= last; section++) sections.push(section);
      previousEnd = last;
    }
    if (!sections.length || sections.length > MAX_SECTIONS) fail('SECTIONS_INVALID');
    if (sections[0] !== cellPeriod) fail('CELL_PERIOD_MISMATCH');
    return { sections: sections, weeks: parseWeeks(weekText) };
  }

  function insideFields(node, fields, root) {
    var cursor = node.parentNode;
    while (cursor && cursor !== root) {
      if (fields.indexOf(cursor) >= 0) return true;
      cursor = cursor.parentNode;
    }
    return false;
  }

  function auditBlockText(block, title, paragraphs) {
    var fields = [title].concat(Array.from(paragraphs));
    var stack = [block];
    while (stack.length) {
      var current = stack.pop();
      var child = current.firstChild;
      while (child) {
        if (isText(child)) {
          if (/\S/.test(child.nodeValue) && !insideFields(child, fields, block)) fail('COURSE_UNKNOWN_CONTENT');
        } else if (isElement(child)) {
          if (BAD_BLOCK_TAGS.indexOf(tagOf(child)) >= 0) fail('COURSE_UNKNOWN_CONTENT');
          if (hasClass(child, 'timetable_con')) fail('COURSE_NESTED_BLOCK');
          stack.push(child);
        }
        child = child.nextSibling;
      }
    }
  }

  function auditFonts(block) {
    var fonts = block.querySelectorAll('font');
    if (!fonts.length) fail('COURSE_COLOR_INVALID');
    var allBlue = true;
    var allRed = true;
    for (var i = 0; i < fonts.length; i++) {
      var font = fonts[i];
      var color = font.getAttribute('color');
      var style = font.getAttribute('style');
      if (style && /(?:^|;)\s*color\s*:/i.test(style)) fail('COURSE_COLOR_INVALID');
      if (color !== 'blue') allBlue = false;
      if (color !== 'red') allRed = false;
    }
    if (!allBlue && !allRed) fail('COURSE_COLOR_INVALID');
    return allBlue;
  }

  function readBlock(block, cell, cellPeriod, day, term) {
    if (block.querySelectorAll('.timetable_con').length) fail('COURSE_NESTED_BLOCK');
    var titles = block.querySelectorAll('.title');
    if (titles.length !== 1) fail('COURSE_TITLE_INVALID');
    var title = titles[0];
    var paragraphs = block.querySelectorAll('p');
    if (paragraphs.length !== 4) fail('COURSE_PARAGRAPH_INVALID');
    for (var i = 0; i < paragraphs.length; i++) {
      if (title.contains(paragraphs[i]) || paragraphs[i].contains(title)) fail('COURSE_STRUCTURE_INVALID');
      for (var j = i + 1; j < paragraphs.length; j++) {
        if (paragraphs[i].contains(paragraphs[j]) || paragraphs[j].contains(paragraphs[i])) fail('COURSE_STRUCTURE_INVALID');
      }
    }
    auditBlockText(block, title, paragraphs);
    var selected = auditFonts(block);

    var name = readText(title, MAX_NAME, true, 'COURSE_NAME_INVALID');
    var patternText = readText(paragraphs[0], 500, true, 'COURSE_PATTERN_INVALID');
    var room = readText(paragraphs[1], MAX_FIELD, true, 'COURSE_ROOM_INVALID');
    var teacher = readText(paragraphs[2], MAX_FIELD, true, 'COURSE_TEACHER_INVALID');
    var classId = readText(paragraphs[3], MAX_CLASS_ID, true, 'TEACHING_CLASS_INVALID');
    if (room === '未排地点' || room === '地点待定') fail('COURSE_ROOM_INVALID');

    var classMatch = /^\((\d{4})-(\d{4})-([12])\)-([A-Za-z0-9]{2,40})-([A-Za-z0-9]{1,20})$/.exec(classId);
    if (!classMatch) fail('TEACHING_CLASS_INVALID');
    if (classMatch[1] + '-' + classMatch[2] !== term.year || classMatch[3] !== term.semester) fail('TEACHING_CLASS_INVALID');
    var pattern = parsePattern(patternText, cellPeriod);

    return {
      classId: classId,
      name: name,
      teacher: teacher,
      room: room,
      day: day,
      sections: pattern.sections,
      weeks: pattern.weeks,
      selected: selected
    };
  }

  function collectBlocks(doc, cells) {
    var nodes = doc.querySelectorAll('.timetable_con');
    if (!nodes.length) fail('NO_COURSES');
    if (nodes.length > MAX_MEETINGS) fail('TOO_MANY_COURSES');
    var blocks = [];
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i];
      if (tagOf(node) !== 'div') fail('COURSE_BLOCK_INVALID');
      var cell = node.parentElement;
      if (!cell || tagOf(cell) !== 'td') fail('COURSE_OUTSIDE_GRID');
      var id = cell.getAttribute('id');
      if (!cells[id] || cells[id] !== cell) fail('COURSE_OUTSIDE_GRID');
      blocks.push({ node: node, cell: cell, id: id });
    }

    var cellKeys = Object.keys(cells);
    for (var c = 0; c < cellKeys.length; c++) {
      var dayCell = cells[cellKeys[c]];
      var child = dayCell.firstChild;
      while (child) {
        if (isElement(child)) {
          if (tagOf(child) === 'br') {
            child = child.nextSibling;
            continue;
          }
          if (tagOf(child) !== 'div' || !hasClass(child, 'timetable_con')) fail('CELL_UNKNOWN_CONTENT');
        } else if (isText(child) && /\S/.test(child.nodeValue)) {
          fail('CELL_UNKNOWN_CONTENT');
        }
        child = child.nextSibling;
      }
    }
    return blocks;
  }

  function bottomTitleOnly(text) {
    return text === '实践课程' || text === '其它课程' ||
      text === '实践课程其它课程' || text === '其它课程实践课程';
  }

  function auditBottom(row) {
    var raw = row.textContent;
    if (typeof raw !== 'string') fail('BOTTOM_AREA_INVALID');
    var compact = raw.replace(/\s+/g, '');
    var descendants = row.querySelectorAll('*');
    for (var i = 0; i < descendants.length; i++) {
      var element = descendants[i];
      if (BAD_BOTTOM_TAGS.indexOf(tagOf(element)) >= 0) fail('BOTTOM_AREA_INVALID');
      if (hasClass(element, 'timetable_con')) fail('BOTTOM_AREA_INVALID');
      for (var attr of Array.from(element.attributes || [])) {
        if (/^(?:data-|on)/i.test(attr.name) || /^(href|src|value|contenteditable)$/i.test(attr.name)) fail('BOTTOM_AREA_INVALID');
      }
    }
    if (!compact) return;
    if (!bottomTitleOnly(compact)) fail('BOTTOM_AREA_INVALID');
    for (var t = 0; t < descendants.length; t++) {
      var node = descendants[t];
      if (BOTTOM_TITLE_TAGS.indexOf(tagOf(node)) < 0) fail('BOTTOM_AREA_INVALID');
      var attrs = node.attributes || [];
      for (var a = 0; a < attrs.length; a++) {
        if (BOTTOM_TITLE_ATTRS.indexOf(String(attrs[a].name).toLowerCase()) < 0) fail('BOTTOM_AREA_INVALID');
      }
    }
  }

  function dedupeAndCheck(facts) {
    var dataSeen = Object.create(null);
    var classFacts = Object.create(null);
    var classNames = Object.create(null);
    var unique = [];
    for (var i = 0; i < facts.length; i++) {
      var fact = facts[i];
      if (classNames[fact.classId] !== undefined && classNames[fact.classId] !== fact.name) {
        fail('CLASS_NAME_CONFLICT');
      }
      classNames[fact.classId] = fact.name;
      var key = JSON.stringify([fact.classId, fact.name, fact.teacher, fact.room, fact.day, fact.sections, fact.weeks]);
      var previous = dataSeen[key];
      if (previous) {
        if (previous.selected !== fact.selected) fail('SELECTION_CONFLICT');
        continue;
      }
      var peers = classFacts[fact.classId] || [];
      if (peers.some(p => p.name !== fact.name || p.selected !== fact.selected)) fail('CLASS_FACT_CONFLICT');
      for (var p = 0; p < peers.length; p++) {
        if (peers[p].day !== fact.day) continue;
        if (!intervalIntersects(peers[p].weeks, fact.weeks)) continue;
        if (!intervalIntersects(peers[p].sections, fact.sections)) continue;
        if (peers[p].room !== fact.room || peers[p].teacher !== fact.teacher) fail('CLASS_SLOT_CONFLICT');
      }
      dataSeen[key] = fact;
      peers.push(fact);
      classFacts[fact.classId] = peers;
      unique.push(fact);
    }
    return unique;
  }

  function extractJustTimetable(doc) {
    if (!doc || doc.nodeType !== 9 || typeof doc.querySelectorAll !== 'function') fail('DOCUMENT_INVALID');
    var table = findMainTable(doc);
    var titleMatch = readTermTitle(table);
    validateHeader(table.rows[1]);

    var form = exactlyOne(doc, '#ajaxForm', 'FORM_INVALID');
    var selected = readSelectedOptions(doc, form);
    var term = validateTerm(titleMatch, selected.year, selected.semester);
    auditFilters(form, selected.yearSelect, selected.semesterSelect);
    auditExternalControls(doc, form, selected.yearSelect, selected.semesterSelect);

    var cells = buildDayCells(doc, table);
    var blocks = collectBlocks(doc, cells);
    var facts = [];
    for (var i = 0; i < blocks.length; i++) {
      var block = blocks[i];
      var cellMatch = /^([1-7])-(1[01]|[1-9])$/.exec(block.id);
      if (!cellMatch) fail('CELL_ID_INVALID');
      facts.push(readBlock(block.node, block.cell, Number(cellMatch[2]), Number(cellMatch[1]), term));
    }
    auditBottom(table.rows[13]);

    var unique = dedupeAndCheck(facts);
    var meetings = [];
    var maxWeek = 0;
    for (var u = 0; u < unique.length; u++) {
      var fact = unique[u];
      if (!fact.selected) continue;
      if (fact.weeks[fact.weeks.length - 1] > maxWeek) maxWeek = fact.weeks[fact.weeks.length - 1];
      meetings.push({
        classId: fact.classId,
        name: fact.name,
        teacher: fact.teacher,
        room: fact.room,
        day: fact.day,
        sections: fact.sections.slice(),
        weeks: fact.weeks.slice()
      });
    }
    if (!meetings.length) fail('NO_SELECTED_COURSES');
    if (meetings.length > MAX_MEETINGS) fail('TOO_MANY_COURSES');
    meetings.sort(function (left, right) {
      if (left.day !== right.day) return left.day - right.day;
      var bySection = compareNumbers(left.sections, right.sections);
      if (bySection) return bySection;
      var byWeeks = compareNumbers(left.weeks, right.weeks);
      if (byWeeks) return byWeeks;
      var byClass = textCompare(left.classId, right.classId);
      if (byClass) return byClass;
      var byRoom = textCompare(left.room, right.room);
      if (byRoom) return byRoom;
      var byTeacher = textCompare(left.teacher, right.teacher);
      if (byTeacher) return byTeacher;
      return textCompare(left.name, right.name);
    });
    return {
      year: term.year,
      semester: term.semester,
      meetings: meetings,
      maxWeek: maxWeek
    };
  }

  globalThis.extractJustTimetable = extractJustTimetable;
})();

// KAgenda host composition. Independent visible-login host, MIT.
(function () {
  'use strict';
  const k=window.kagendaSchool;
  k.run(async () => {
    const entry=new URL(location.href);
    k.requireValue(k.adapterId==='ext_just_personal' && entry.protocol==='https:' &&
      ['client.v.just.edu.cn','jwxt.just.edu.cn'].includes(entry.hostname) &&
      !entry.username && !entry.password && !entry.port &&
      /\/jwglxt\/kbcx\/xskbcx_cxXskbcxIndex\.html$/.test(entry.pathname),
      '请自行登录江苏科技大学教务，打开完整个人课表');
    const canonical=value=>JSON.stringify(value.meetings.map(m=>[m.classId,m.name,m.teacher,m.room,m.day,m.sections,m.weeks]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b))));
    const selectedCodes=()=>['xnm','xqm'].map(id=>{
      const controls=document.querySelectorAll('#ajaxForm #'+id);
      k.requireValue(controls.length===1 && controls[0].selectedOptions.length===1,'学期控件不唯一');
      const option=controls[0].selectedOptions[0];
      k.requireValue(option.value.trim(),'学期选项缺少服务器标识');
      return [id,option.value,option.textContent.trim()];
    });
    const guard=()=>{
      k.requireValue(location.href===entry.href,'查询期间页面地址变化');
      k.requireValue(!document.querySelector('input[type="password"]'),'请重新完成学校登录');
      return selectedCodes();
    };
    const codes=JSON.stringify(guard()),first=window.extractJustTimetable(document);
    k.requireValue(first.meetings.length,'没有完整已排课课程');
    await new Promise(resolve=>setTimeout(resolve,300));
    const stable=window.extractJustTimetable(document);
    k.requireValue(JSON.stringify(guard())===codes && first.year===stable.year && first.semester===stable.semester && canonical(first)===canonical(stable),'课表尚未稳定，请等待加载完成');
    const answer=await k.api.showPrompt('学期总周数','请按学校校历填写总教学周数；日期和逐节作息请在预览中核对。','',1);
    k.requireValue(answer!==null && answer!==undefined,'已取消导入');
    const count=k.integer(answer,first.maxWeek,40,'学期总周数');
    const final=window.extractJustTimetable(document);
    k.requireValue(JSON.stringify(guard())===codes && final.year===first.year && final.semester===first.semester && canonical(final)===canonical(first),'交互期间学期、课程或筛选变化，未导入部分数据');
    const courses=final.meetings.flatMap(m=>{
      const sections=[...m.sections].sort((a,b)=>a-b),groups=[];
      for(const n of sections){const last=groups.at(-1);if(last && n===last[1]+1)last[1]=n;else groups.push([n,n]);}
      return groups.map(([a,b])=>k.course(m.name,m.teacher,m.room,m.day,a,b,m.weeks));
    });
    await k.finish(courses,0,count);
  });
})();
