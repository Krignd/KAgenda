// KAgenda SUDA personal semester importer. Protocol reference: hlfzsi/Aero_su,
// Apache-2.0, bd6aa7f2e9596c50c58b0f9af027caec0a89acba.
// DeepSeek assisted the independent draft; primary-agent corrections add
// scheduled practice, explicit grid-based periods, identity and final snapshots.
// Pinned files/hashes/dates: academic/school-provenance.json.
// Full original license: licenses/jiangsu-school-bundle.txt.
/*
 * Independent browser-only parser for the personal timetable DOM served by
 * 苏州大学 xk.suda.edu.cn. This file is not copied from Aero_su; the public
 * Apache-2.0 source at hlfzsi/Aero_su@bd6aa7f2e9596c50c58b0f9af027caec0a89acba
 * was used only to identify the page/field conventions.
 *
 * Public contract:
 *   globalThis.extractSudaTimetable(doc, expectedYear, expectedSemester)
 *     -> { year, semester, meetings, maxWeek }
 *
 * This parser:
 *   - reads only the Document supplied by the caller;
 *   - never fetches, logs in, writes storage, evaluates JS, dispatches events,
 *     parses innerHTML as HTML again, or decodes HTML entities a second time;
 *   - throws on every ambiguity or invalid value and never returns partial data.
 *
 * Time grammar implemented (braces and weeks are mandatory):
 *   周X第<SectionList>节{<WeekList>}
 *   SectionList := N | N-M | item(,item)*, ASCII digits, 1..24, no overlap
 *   WeekList    := Part(,Part)*; Part := 第?N(-M)?周(单周|双周)?
 *   Week values are 1..40 and ranges may not be inverted.
 *
 * All errors are thrown as Error instances carrying a stable `code` property.
 * No UI, bridge, network, storage, or DOM mutation is included.
 */
(function (globalObject) {
  'use strict';

  var LIMITS = {
    maxMeetings: 5000,
    maxNameCodePoints: 200,
    minDay: 1,
    minPeriod: 1,
    maxPeriod: 24,
    minWeek: 1,
    maxWeek: 40,
    gridColumns: 9,
    periodColumn: 1,
    firstDayColumn: 2
  };

  var DAY_BY_CHAR = {
    '一': 1,
    '二': 2,
    '三': 3,
    '四': 4,
    '五': 5,
    '六': 6,
    '日': 7,
    '天': 7
  };

  var MAIN_FORBIDDEN_TAGS = [
    'script', 'style', 'template', 'iframe', 'object', 'embed', 'form',
    'input', 'select', 'textarea', 'button', 'link', 'meta', 'base',
    'svg', 'canvas', 'video', 'audio', 'img', 'picture', 'source', 'track'
  ];

  var ANCHOR_FORBIDDEN_TAGS = [
    'a', 'table', 'tr', 'td', 'th', 'script', 'style', 'template', 'iframe',
    'object', 'embed', 'form', 'input', 'select', 'textarea', 'button', 'svg',
    'canvas', 'video', 'audio', 'img', 'picture'
  ];

  var EXTRA_TABLE_IDS = ['DataGrid1', 'DBGridYxkc', 'Datagrid2'];

  var SCHEDULE_ENTRY_RE = new RegExp(
    '^周\\s*([一二三四五六日天])\\s*第\\s*' +
    '([0-9]+(?:\\s*[-－—–~～]\\s*[0-9]+)?' +
    '(?:\\s*[,，、]\\s*[0-9]+(?:\\s*[-－—–~～]\\s*[0-9]+)?)*)' +
    '\\s*节\\s*\\{([^{}]*)\\}'
  );
  var SECTION_EXPR_RE = /^[0-9]+(?:-[0-9]+)?(?:,[0-9]+(?:-[0-9]+)?)*$/;
  var WEEK_PART_RE = /^(?:第\s*)?([0-9]+)\s*(?:[-－—–~～]\s*([0-9]+))?\s*周(?:\s*(单周|双周))?$/;
  var PERIOD_LABEL_RE = /^第\s*(.+?)\s*节$/;
  var WEEKDAY_HEADER_RE = /^(?:星期|周)([一二三四五六日天])$/;
  var CLASS_ID_TERM_RE = /\(\d{4}-\d{4}-[1-3]\)/g;
  var CLASS_ID_TERM_ANY_RE = /\(\d{4}-\d{4}-[0-9]+\)/g;
  var CLASS_ID_SHAPE_RE = /^\(\d{4}-\d{4}-[1-3]\)-[^\s]+$/;
  var CONTROL_CHAR_RE = /[\u0000-\u001f\u007f]/;
  var SCHEDULE_HINT_RE = /周\s*[一二三四五六日天]\s*第\s*[0-9]/;

  function SudaTimetableParseError(code, message, details) {
    this.name = 'SudaTimetableParseError';
    this.code = code;
    this.message = message;
    if (details !== undefined) this.details = details;
    if (typeof Error.captureStackTrace === 'function') {
      Error.captureStackTrace(this, SudaTimetableParseError);
    } else {
      this.stack = new Error(message).stack;
    }
  }
  SudaTimetableParseError.prototype = Object.create(Error.prototype, {
    constructor: {
      value: SudaTimetableParseError,
      enumerable: false,
      writable: true,
      configurable: true
    }
  });

  function fail(code, message, details) {
    throw new SudaTimetableParseError(code, message, details);
  }

  function localName(element) {
    var name = element.localName || element.tagName || '';
    return String(name).toLowerCase();
  }

  function directChildElements(element) {
    var result = [];
    var children = element.childNodes || [];
    for (var i = 0; i < children.length; i += 1) {
      if (children[i].nodeType === 1) result.push(children[i]);
    }
    return result;
  }

  function queryAll(root, selector) {
    if (!root || typeof root.querySelectorAll !== 'function') {
      fail('DOM_QUERY', 'The supplied DOM node cannot be queried.');
    }
    var list;
    try {
      list = root.querySelectorAll(selector);
    } catch (cause) {
      fail('DOM_QUERY', 'DOM query failed for selector "' + selector + '".', {
        selector: selector,
        cause: cause && cause.message ? cause.message : String(cause)
      });
    }
    var result = [];
    for (var i = 0; i < list.length; i += 1) result.push(list[i]);
    return result;
  }

  function hasAttribute(element, name) {
    if (typeof element.hasAttribute === 'function') return element.hasAttribute(name);
    return getAttribute(element, name) !== null;
  }

  function getAttribute(element, name) {
    if (!element || typeof element.getAttribute !== 'function') return null;
    var value = element.getAttribute(name);
    return value === null || value === undefined ? null : String(value);
  }

  function normalizeText(value) {
    return String(value == null ? '' : value)
      .replace(/\u00a0/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function elementText(element) {
    var parts = [];
    function walk(node) {
      if (!node) return;
      if (node.nodeType === 3) {
        parts.push(node.nodeValue == null ? '' : String(node.nodeValue));
        return;
      }
      if (node.nodeType !== 1) return;
      var tag = localName(node);
      if (tag === 'script' || tag === 'style' || tag === 'template' || tag === 'noscript') {
        return;
      }
      if (tag === 'br') {
        parts.push(' ');
        return;
      }
      var children = node.childNodes || [];
      for (var i = 0; i < children.length; i += 1) walk(children[i]);
    }
    walk(element);
    return normalizeText(parts.join(''));
  }

  function codePointLength(value) {
    if (typeof Array.from === 'function') return Array.from(value).length;
    return value.replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g, '_').length;
  }

  function oneById(doc, id, missingCode, ambiguousCode) {
    var found = queryAll(doc, '#' + id);
    if (found.length === 0) {
      fail(missingCode, 'Required element #' + id + ' is missing.');
    }
    if (found.length !== 1) {
      fail(ambiguousCode, 'Element #' + id + ' must be unique.', {
        id: id,
        count: found.length
      });
    }
    return found[0];
  }

  function assertDocument(doc) {
    if (!doc || doc.nodeType !== 9 || typeof doc.querySelectorAll !== 'function') {
      fail('INPUT_DOCUMENT', 'doc must be a browser Document node.');
    }
  }

  function canonicalExpectedYear(year) {
    if (typeof year !== 'string' || !/^\d{4}-\d{4}$/.test(year)) {
      fail('INPUT_EXPECTED_YEAR', 'expectedYear must be a complete "YYYY-YYYY" string.');
    }
    var first = Number(year.slice(0, 4));
    var second = Number(year.slice(5, 9));
    if (!Number.isSafeInteger(first) || second !== first + 1) {
      fail('INPUT_EXPECTED_YEAR', 'expectedYear must contain consecutive academic years.', {
        expectedYear: year
      });
    }
    return year;
  }

  function rejectNonTimetableDocument(doc) {
    var body = doc.body || doc.documentElement;
    var visibleText = body ? elementText(body) : '';
    if (visibleText.indexOf('三秒防刷') !== -1) {
      fail('RATE_LIMITED', 'The server returned the three-second anti-refresh page; refusing to parse.');
    }
    var textBox1 = queryAll(doc, 'input[name="TextBox1"]');
    var textBox2 = queryAll(doc, 'input[name="TextBox2"]');
    var textBox3 = queryAll(doc, 'input[name="TextBox3"]');
    if (textBox1.length > 0 && textBox2.length > 0 && textBox3.length > 0) {
      var button = queryAll(doc, 'input[name="Button1"]');
      if (button.length > 0 || visibleText.indexOf('登录(LOGIN)') !== -1) {
        fail('LOGIN_PAGE', 'The supplied Document is a login page; this parser never logs in.');
      }
    }
  }

  function canonicalExpectedSemester(semester) {
    if (semester === 1 || semester === 2 || semester === 3) return String(semester);
    if (typeof semester === 'string' && /^[1-3]$/.test(semester)) return semester;
    fail('INPUT_EXPECTED_SEMESTER', 'expectedSemester must be one of "1", "2", or "3".');
  }

  function readSelectedOption(select, id) {
    if (hasAttribute(select, 'multiple')) {
      fail('TERM_SELECT_MULTIPLE', 'Semester selector #' + id + ' must not be multiple.');
    }
    var options = queryAll(select, 'option');
    if (options.length === 0) {
      fail('TERM_OPTION_NONE', 'Semester selector #' + id + ' has no options.');
    }
    var selected = [];
    for (var i = 0; i < options.length; i += 1) {
      if (hasAttribute(options[i], 'selected')) selected.push(options[i]);
    }
    if (selected.length === 0) {
      fail('TERM_OPTION_NONE', 'Semester selector #' + id + ' has no explicitly selected option.');
    }
    if (selected.length > 1) {
      fail('TERM_OPTION_AMBIGUOUS', 'Semester selector #' + id + ' has multiple selected options.', {
        id: id,
        count: selected.length
      });
    }
    var option = selected[0];
    if (hasAttribute(option, 'disabled')) {
      fail('TERM_OPTION_DISABLED', 'The selected option in #' + id + ' is disabled.');
    }
    var ancestor = option.parentNode;
    while (ancestor && ancestor !== select) {
      if (ancestor.nodeType === 1 && hasAttribute(ancestor, 'disabled')) {
        fail('TERM_OPTION_DISABLED', 'The selected option in #' + id + ' is inside a disabled group.');
      }
      ancestor = ancestor.parentNode;
    }
    if (!hasAttribute(option, 'value')) {
      fail('TERM_OPTION_VALUE', 'The selected option in #' + id + ' has no explicit value attribute.');
    }
    var value = getAttribute(option, 'value');
    if (value === null || value === '' || value !== value.trim()) {
      fail('TERM_OPTION_VALUE', 'The selected option in #' + id + ' has an empty or padded value.');
    }
    if (elementText(option) === '') {
      fail('TERM_OPTION_LABEL', 'The selected option in #' + id + ' has no visible label.');
    }
    return value;
  }

  function readTerm(doc, expectedYear, expectedSemester) {
    var yearSelect = oneById(
      doc, 'xnd', 'TERM_SELECT_MISSING', 'TERM_SELECT_AMBIGUOUS'
    );
    var semesterSelect = oneById(
      doc, 'xqd', 'TERM_SELECT_MISSING', 'TERM_SELECT_AMBIGUOUS'
    );
    if (localName(yearSelect) !== 'select' || localName(semesterSelect) !== 'select') {
      fail('TERM_SELECT_TYPE', '#xnd and #xqd must both be SELECT elements.');
    }
    var yearValue = readSelectedOption(yearSelect, 'xnd');
    var semesterValue = readSelectedOption(semesterSelect, 'xqd');
    if (yearValue !== expectedYear) {
      fail('TERM_YEAR_MISMATCH', 'Selected academic year does not match expectedYear.', {
        selected: yearValue,
        expected: expectedYear
      });
    }
    if (semesterValue !== expectedSemester) {
      fail('TERM_SEMESTER_MISMATCH', 'Selected semester does not match expectedSemester.', {
        selected: semesterValue,
        expected: expectedSemester
      });
    }
    return { year: yearValue, semester: semesterValue };
  }

  function getMainTable(doc) {
    var tables = queryAll(doc, '#Table1');
    if (tables.length === 0) {
      fail('MAIN_TABLE_MISSING', 'The unique timetable table #Table1 is missing.');
    }
    if (tables.length !== 1) {
      fail('MAIN_TABLE_AMBIGUOUS', 'The timetable table #Table1 must be unique.', {
        count: tables.length
      });
    }
    var table = tables[0];
    if (localName(table) !== 'table') {
      fail('MAIN_TABLE_TYPE', '#Table1 must be a TABLE element.');
    }
    if (queryAll(table, 'table').length > 0) {
      fail('MAIN_TABLE_NESTED', 'Nested tables inside #Table1 are not supported.');
    }
    return table;
  }

  function assertNoForbiddenElements(table) {
    for (var i = 0; i < MAIN_FORBIDDEN_TAGS.length; i += 1) {
      var tag = MAIN_FORBIDDEN_TAGS[i];
      if (queryAll(table, tag).length > 0) {
        fail('MAIN_TABLE_FORBIDDEN', 'Unsupported element <' + tag + '> exists inside #Table1.', {
          tag: tag
        });
      }
    }
  }

  function tableLooksEmpty(table) {
    if (queryAll(table, '*').length > 0) return false;
    if (normalizeText(elementText(table)) !== '') return false;
    return true;
  }


  function isInside(node, root) {
    while (node) {
      if (node === root) return true;
      node = node.parentNode;
    }
    return false;
  }

  function assertNoPagination(doc, table) {
    var marked = queryAll(doc, '[class], [id]');
    for (var i = 0; i < marked.length; i += 1) {
      if (isInside(marked[i], table)) continue;
      var marker = String(marked[i].className || '') + ' ' + String(marked[i].id || '');
      if (/(?:^|[\s_-])(?:pagination|pager|page-?nav|pagenav)(?:$|[\s_-])/i.test(marker)) {
        fail('PAGINATION_UNSUPPORTED', 'The document exposes pagination controls; a complete timetable is required.');
      }
    }
    var clickables = queryAll(doc, 'a, button, input');
    for (var j = 0; j < clickables.length; j += 1) {
      if (isInside(clickables[j], table)) continue;
      var tag = localName(clickables[j]);
      var label = tag === 'input'
        ? getAttribute(clickables[j], 'value')
        : elementText(clickables[j]);
      label = normalizeText(label);
      if (label === '下一页' || label === '尾页' || label === '末页') {
        fail('PAGINATION_UNSUPPORTED', 'The document exposes a pagination control; a complete timetable is required.', {
          label: label
        });
      }
    }
  }

  function assertNoStrayCourseAnchors(doc, table) {
    var anchors = queryAll(doc, 'a[onclick]');
    for (var i = 0; i < anchors.length; i += 1) {
      var onclick = getAttribute(anchors[i], 'onclick');
      if (!onclick || onclick.indexOf('xkkh') === -1) continue;
      if (!isInside(anchors[i], table)) {
        fail('STRAY_COURSE_ANCHOR', 'A course-like a[onclick] with xkkh exists outside #Table1.');
      }
    }
  }

  function parseSpanValue(cell, name, maximum) {
    var raw = getAttribute(cell, name);
    if (raw === null) return 1;
    if (!/^[1-9][0-9]*$/.test(raw)) {
      fail('BAD_SPAN', 'Invalid ' + name + ' value on a timetable cell.', {
        attribute: name,
        value: raw
      });
    }
    var value = Number(raw);
    if (!Number.isSafeInteger(value) || value < 1 || value > maximum) {
      fail('BAD_SPAN', name + ' is outside the supported layout range.', {
        attribute: name,
        value: raw
      });
    }
    return value;
  }

  function buildGrid(table) {
    var rows = queryAll(table, 'tr');
    if (rows.length === 0) {
      fail('GRID_WIDTH', '#Table1 has no rows.');
    }
    var carry = new Map();
    var records = [];

    for (var r = 0; r < rows.length; r += 1) {
      var directCells = directChildElements(rows[r]);
      var cells = [];
      for (var c = 0; c < directCells.length; c += 1) {
        var tag = localName(directCells[c]);
        if (tag === 'td' || tag === 'th') cells.push(directCells[c]);
      }
      if (cells.length === 0) {
        fail('GRID_WIDTH', 'Timetable row ' + (r + 1) + ' has no table cells.');
      }

      var slots = new Array(LIMITS.gridColumns).fill(null);
      carry.forEach(function (span, column) {
        slots[column] = span.placement;
      });

      var column = 0;
      var newCarry = [];
      for (var i = 0; i < cells.length; i += 1) {
        while (column < LIMITS.gridColumns && slots[column] !== null) column += 1;
        if (column >= LIMITS.gridColumns) {
          fail('BAD_SPAN', 'Timetable row ' + (r + 1) + ' exceeds nine logical columns.');
        }
        var rowspan = parseSpanValue(cells[i], 'rowspan', LIMITS.maxPeriod);
        var colspan = parseSpanValue(cells[i], 'colspan', LIMITS.gridColumns);
        if (column + colspan > LIMITS.gridColumns) {
          fail('BAD_SPAN', 'A colspan crosses the nine-column timetable boundary.', {
            row: r + 1,
            column: column + 1,
            colspan: colspan
          });
        }
        var placement = {
          row: r,
          column: column,
          rowspan: rowspan,
          colspan: colspan,
          cell: cells[i]
        };
        for (var k = 0; k < colspan; k += 1) {
          if (slots[column + k] !== null) {
            fail('BAD_SPAN', 'Timetable cells overlap after applying spans.', {
              row: r + 1,
              column: column + k + 1
            });
          }
        }
        for (var k2 = 0; k2 < colspan; k2 += 1) {
          slots[column + k2] = placement;
          if (rowspan > 1) {
            newCarry.push({
              column: column + k2,
              remaining: rowspan - 1,
              placement: placement
            });
          }
        }
        column += colspan;
      }

      for (var widthCheck = 0; widthCheck < LIMITS.gridColumns; widthCheck += 1) {
        if (slots[widthCheck] === null) {
          fail('GRID_WIDTH', 'Timetable row ' + (r + 1) + ' does not fill nine logical columns.', {
            row: r + 1,
            logicalWidth: LIMITS.gridColumns
          });
        }
      }

      carry.forEach(function (span, key) {
        span.remaining -= 1;
        if (span.remaining <= 0) carry.delete(key);
      });
      for (var n = 0; n < newCarry.length; n += 1) {
        if (carry.has(newCarry[n].column)) {
          fail('BAD_SPAN', 'A rowspan collides with another span below the current row.');
        }
        carry.set(newCarry[n].column, {
          remaining: newCarry[n].remaining,
          placement: newCarry[n].placement
        });
      }

      records.push({
        index: r,
        rowElement: rows[r],
        slots: slots,
        placements: cells.map(function (cell) {
          for (var p = 0; p < slots.length; p += 1) {
            if (slots[p] && slots[p].cell === cell) return slots[p];
          }
          return null;
        }).filter(Boolean)
      });
    }

    if (carry.size > 0) {
      fail('BAD_SPAN', 'A rowspan extends beyond the last timetable row.');
    }
    return records;
  }

  function weekdayLabelToNumber(text) {
    var compact = String(text).replace(/\s+/g, '');
    var match = WEEKDAY_HEADER_RE.exec(compact);
    if (!match) return null;
    return DAY_BY_CHAR[match[1]];
  }

  function validateHeader(headerRecord) {
    if (queryAll(headerRecord.rowElement, 'a').length > 0) {
      fail('HEADER_LAYOUT', 'The weekday header row must not contain links.');
    }
    var dayCells = [];
    for (var i = 0; i < headerRecord.placements.length; i += 1) {
      var placement = headerRecord.placements[i];
      var label = elementText(placement.cell);
      var day = weekdayLabelToNumber(label);
      if (day !== null) dayCells.push({ day: day, placement: placement });
    }
    if (dayCells.length !== 7) {
      fail('HEADER_DAY', 'The first row must contain exactly seven explicit weekday headers.', {
        count: dayCells.length
      });
    }
    var seen = {};
    var dayByColumn = {};
    for (var j = 0; j < dayCells.length; j += 1) {
      var item = dayCells[j];
      var placement2 = item.placement;
      if (seen[item.day]) {
        fail('HEADER_DAY', 'Duplicate weekday header.', { day: item.day });
      }
      seen[item.day] = true;
      dayByColumn[placement2.column] = item.day;
      if (placement2.row !== 0 || placement2.rowspan !== 1 || placement2.colspan !== 1) {
        fail('HEADER_LAYOUT', 'Weekday header cells must be single, unspanned cells.');
      }
      var expectedColumn = LIMITS.firstDayColumn + item.day - LIMITS.minDay;
      if (placement2.column !== expectedColumn) {
        fail('HEADER_DAY', 'Weekday header is in the wrong column.', {
          day: item.day,
          column: placement2.column + 1
        });
      }
    }
    for (var dayNumber = 1; dayNumber <= 7; dayNumber += 1) {
      if (!seen[dayNumber]) {
        fail('HEADER_DAY', 'A weekday header is missing.', { day: dayNumber });
      }
    }
    return { dayByColumn: dayByColumn };
  }

  function parseSectionList(raw, code, context) {
    var original = String(raw);
    if (/[0-9]\s+[0-9]/.test(original)) {
      fail(code, 'Adjacent section numbers must be separated by an explicit comma or range.', {
        context: context,
        value: original
      });
    }
    var normalized = original
      .replace(/\s+/g, '')
      .replace(/，/g, ',')
      .replace(/、/g, ',')
      .replace(/[－—–~～]/g, '-');
    if (normalized === '' || !SECTION_EXPR_RE.test(normalized)) {
      fail(code, 'Invalid section expression in ' + context + '.', {
        context: context,
        value: String(raw)
      });
    }
    var pieces = normalized.split(',');
    var seen = {};
    var result = [];
    for (var i = 0; i < pieces.length; i += 1) {
      var match = /^([0-9]+)(?:-([0-9]+))?$/.exec(pieces[i]);
      if (!match) {
        fail(code, 'Invalid section token in ' + context + '.', {
          context: context,
          token: pieces[i]
        });
      }
      var start = Number(match[1]);
      var end = match[2] === undefined ? start : Number(match[2]);
      if (
        !Number.isSafeInteger(start) ||
        !Number.isSafeInteger(end) ||
        start < LIMITS.minPeriod ||
        end > LIMITS.maxPeriod ||
        start > end
      ) {
        fail(code, 'Section value is outside 1..24 or the range is inverted.', {
          context: context,
          token: pieces[i]
        });
      }
      for (var value = start; value <= end; value += 1) {
        if (seen[value]) {
          fail(code, 'Duplicate section value in ' + context + '.', {
            context: context,
            section: value
          });
        }
        seen[value] = true;
        result.push(value);
      }
    }
    result.sort(function (a, b) { return a - b; });
    return result;
  }

  function parsePeriodLabel(text, recordIndex) {
    var match = PERIOD_LABEL_RE.exec(text);
    if (!match) {
      fail('PERIOD_LAYOUT', 'The period column must contain an explicit "第n节" or "第n-m节" label.', {
        row: recordIndex + 1,
        value: text
      });
    }
    return parseSectionList(match[1], 'PERIOD_LAYOUT', 'period row ' + (recordIndex + 1));
  }

  function parsePeriodRows(records) {
    var previousMaximum = 0;
    for (var r = 1; r < records.length; r += 1) {
      var record = records[r];
      var placement = record.slots[LIMITS.periodColumn];
      if (
        !placement ||
        placement.row !== r ||
        placement.column !== LIMITS.periodColumn ||
        placement.rowspan !== 1 ||
        placement.colspan !== 1
      ) {
        fail('PERIOD_LAYOUT', 'Each data row must have a direct, unspanned period label in column 2.', {
          row: r + 1
        });
      }
      var sections = parsePeriodLabel(elementText(placement.cell), r);
      if (sections[0] <= previousMaximum) {
        fail('PERIOD_ORDER', 'Period labels must be strictly increasing and non-overlapping.', {
          row: r + 1,
          sections: sections
        });
      }
      previousMaximum = sections[sections.length - 1];
      record.periodSections = sections;
    }
    return records;
  }

  function parseWeekList(raw, context) {
    var text = String(raw).trim();
    if (text === '') {
      fail('TIME_WEEKS', 'Week expression is empty in ' + context + '.');
    }
    var pieces = text.split(/[,，、;；]/);
    if (pieces.length === 0) {
      fail('TIME_WEEKS', 'Week expression is empty in ' + context + '.');
    }
    var seen = {};
    var result = [];
    for (var i = 0; i < pieces.length; i += 1) {
      var piece = pieces[i].trim();
      if (piece === '') {
        fail('TIME_WEEKS', 'Week expression contains an empty segment in ' + context + '.');
      }
      var match = WEEK_PART_RE.exec(piece.replace(/\|\s*(?=(?:单周|双周)$)/, ''));
      if (!match) {
        fail('TIME_WEEKS', 'Unsupported week segment "' + piece + '" in ' + context + '.');
      }
      var start = Number(match[1]);
      var end = match[2] === undefined ? start : Number(match[2]);
      var parity = match[3] || null;
      if (
        !Number.isSafeInteger(start) ||
        !Number.isSafeInteger(end) ||
        start < LIMITS.minWeek ||
        end > LIMITS.maxWeek ||
        start > end
      ) {
        fail('TIME_WEEKS', 'Week range is outside 1..40 or inverted in ' + context + '.', {
          context: context,
          segment: piece
        });
      }
      var added = 0;
      for (var week = start; week <= end; week += 1) {
        if (parity === '单周' && week % 2 !== 1) continue;
        if (parity === '双周' && week % 2 !== 0) continue;
        if (seen[week]) {
          fail('TIME_WEEKS', 'Duplicate week value in ' + context + '.', {
            context: context,
            week: week
          });
        }
        seen[week] = true;
        result.push(week);
        added += 1;
      }
      if (added === 0) {
        fail('TIME_WEEKS', 'Week parity removes every week from segment "' + piece + '".');
      }
    }
    result.sort(function (a, b) { return a - b; });
    return result;
  }

  function parseScheduleLine(line, context, basis) {
    var weekOnly = /^\{([^{}]+)\}$/.exec(line);
    if (weekOnly) {
      if (!basis || !basis.sections.length) fail('TIME_SYNTAX', 'Missing explicit weekday/period basis.');
      var weekText = weekOnly[1];
      var load = /\|\s*(\d+)\s*节\/周$/.exec(weekText);
      if (load) {
        if (Number(load[1]) !== basis.sections.length) fail('SECTION_MISMATCH', 'Weekly load contradicts explicit grid periods.');
        weekText = weekText.slice(0,load.index);
      }
      return [{day:basis.day,sections:basis.sections.slice(),weeks:parseWeekList(weekText,context)}];
    }
    var remaining = line;
    var entries = [];
    while (remaining.length > 0) {
      var match = SCHEDULE_ENTRY_RE.exec(remaining);
      if (!match) {
        fail('TIME_SYNTAX', 'Unsupported or incomplete timetable time in ' + context + '.', {
          context: context,
          value: line
        });
      }
      var day = DAY_BY_CHAR[match[1]];
      var sections = parseSectionList(match[2], 'TIME_SECTIONS', context);
      var weeks = parseWeekList(match[3], context);
      entries.push({ day: day, sections: sections, weeks: weeks });
      remaining = remaining.slice(match[0].length);
      if (remaining.length === 0) break;
      var separator = /^[\s,，;；、]+/.exec(remaining);
      if (!separator) {
        fail('TIME_SYNTAX', 'Multiple time entries must be separated explicitly in ' + context + '.', {
          context: context,
          value: line
        });
      }
      remaining = remaining.slice(separator[0].length);
      if (remaining.length === 0) {
        fail('TIME_SYNTAX', 'Time expression has a trailing separator in ' + context + '.');
      }
    }
    if (entries.length === 0) {
      fail('TIME_SYNTAX', 'No explicit timetable time was found in ' + context + '.');
    }
    return entries;
  }

  function decodeQueryComponent(raw, context) {
    try {
      return decodeURIComponent(raw.replace(/\+/g, ' '));
    } catch (cause) {
      fail('ANCHOR_PATH', 'Malformed percent-encoding in a course link path.', {
        context: context
      });
    }
  }

  function extractPathXkkh(onclick, context) {
    if (typeof onclick !== 'string' || onclick.trim() === '') {
      fail('ANCHOR_PATH', 'Course anchor has an empty onclick in ' + context + '.');
    }
    var literals = [];
    var literalRe = /'([^'\\]*)'|"([^"\\]*)"/g;
    var match;
    while ((match = literalRe.exec(onclick)) !== null) {
      literals.push(match[1] !== undefined ? match[1] : match[2]);
    }
    var mentions = [];
    for (var i = 0; i < literals.length; i += 1) {
      if (literals[i].indexOf('xkkh') !== -1) mentions.push(literals[i]);
    }
    if (mentions.length === 0) {
      fail('ANCHOR_PATH', 'No quoted literal path containing xkkh was found in ' + context + '.');
    }
    if (mentions.length !== 1) {
      fail('ANCHOR_PATH', 'Multiple quoted literals mention xkkh; refusing an ambiguous course path.', {
        context: context,
        count: mentions.length
      });
    }
    var path = mentions[0];
    if (!/^(?:\/|\.\/|\.\.\/)/.test(path)) {
      fail('ANCHOR_PATH', 'The xkkh literal is not a relative literal path in ' + context + '.');
    }
    if (
      path.indexOf('#') !== -1 ||
      path.indexOf('\\') !== -1 ||
      CONTROL_CHAR_RE.test(path)
    ) {
      fail('ANCHOR_PATH', 'The xkkh literal path contains unsupported characters in ' + context + '.');
    }
    if (/&(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-f]+);/i.test(path)) {
      fail('ANCHOR_PATH', 'The course path still contains an HTML entity after DOM decode; ' +
        'refusing a second decode in ' + context + '.');
    }
    var question = path.indexOf('?');
    if (question <= 0 || question === path.length - 1) {
      fail('ANCHOR_PATH', 'The xkkh literal path has no nonempty query string in ' + context + '.');
    }
    var query = path.slice(question + 1);
    var pairs = query.split('&');
    var xkkh = null;
    for (var p = 0; p < pairs.length; p += 1) {
      if (pairs[p] === '') {
        fail('ANCHOR_PATH', 'The course query string has an empty parameter in ' + context + '.');
      }
      var equal = pairs[p].indexOf('=');
      if (equal <= 0) {
        fail('ANCHOR_PATH', 'The course query string has a malformed parameter in ' + context + '.');
      }
      var key = decodeQueryComponent(pairs[p].slice(0, equal), context);
      var value = decodeQueryComponent(pairs[p].slice(equal + 1), context);
      if (key === 'xkkh') {
        if (xkkh !== null) {
          fail('ANCHOR_PATH', 'Duplicate xkkh parameters in a course link ' + context + '.');
        }
        xkkh = value;
      }
    }
    if (xkkh === null || xkkh === '') {
      fail('ANCHOR_PATH', 'The course link has no nonempty xkkh parameter in ' + context + '.');
    }
    return xkkh;
  }

  function validateClassId(xkkh, term, context) {
    if (typeof xkkh !== 'string' || xkkh === '' || xkkh !== xkkh.trim() || /\s/.test(xkkh)) {
      fail('CLASS_ID', 'classId is empty, padded, or contains whitespace in ' + context + '.');
    }
    if (CONTROL_CHAR_RE.test(xkkh) || !CLASS_ID_SHAPE_RE.test(xkkh)) {
      fail('CLASS_ID', 'classId does not have the expected structured shape in ' + context + '.', {
        context: context
      });
    }
    var matches = xkkh.match(CLASS_ID_TERM_ANY_RE) || [];
    var validTermMatches = xkkh.match(CLASS_ID_TERM_RE) || [];
    var prefix = '(' + term.year + '-' + term.semester + ')-';
    if (matches.length !== 1 || validTermMatches.length !== 1 || xkkh.indexOf(prefix) !== 0) {
      fail('CLASS_ID_TERM', 'classId does not contain exactly the selected academic term in ' + context + '.', {
        context: context,
        expectedPrefix: prefix
      });
    }
    if (xkkh.length === prefix.length) {
      fail('CLASS_ID', 'classId has no course/class suffix in ' + context + '.');
    }
    return xkkh;
  }

  function readAnchorLines(anchor, context) {
    var lines = [''];
    function walk(node) {
      if (!node) return;
      if (node.nodeType === 3) {
        lines[lines.length - 1] += String(node.nodeValue == null ? '' : node.nodeValue);
        return;
      }
      if (node.nodeType !== 1) return;
      var tag = localName(node);
      if (tag === 'br') {
        lines.push('');
        return;
      }
      if (ANCHOR_FORBIDDEN_TAGS.indexOf(tag) !== -1) {
        fail('ANCHOR_LAYOUT', 'Unsupported <' + tag + '> inside a course anchor in ' + context + '.');
      }
      var children = node.childNodes || [];
      for (var i = 0; i < children.length; i += 1) walk(children[i]);
    }
    Array.from(anchor.childNodes || []).forEach(walk);
    var normalized = [];
    for (var i = 0; i < lines.length; i += 1) normalized.push(normalizeText(lines[i]));
    while (normalized.length > 0 && normalized[normalized.length - 1] === '') {
      normalized.pop();
    }
    if (normalized.length < 4) {
      fail('ANCHOR_LINES', 'A course anchor must have name, time, teacher, and room lines in ' + context + '.', {
        context: context,
        lineCount: normalized.length
      });
    }
    for (var index = 0; index < 3; index += 1) {
      if (normalized[index] === '') {
        fail('ANCHOR_LINES', 'A required course anchor line is empty in ' + context + '.', {
          context: context,
          lineIndex: index
        });
      }
    }
    var roomParts = normalized.slice(3).filter(function (line) {
      return line !== '';
    });
    if (roomParts.length === 0) {
      fail('ANCHOR_LINES', 'A course anchor has no explicit room line in ' + context + '.');
    }
    return {
      name: normalized[0],
      time: normalized[1],
      teacher: normalized[2],
      room: roomParts.join(' ')
    };
  }

  function validateField(value, fieldName, context) {
    if (value === '') {
      fail('ANCHOR_LINES', 'Course ' + fieldName + ' is empty in ' + context + '.');
    }
    if (CONTROL_CHAR_RE.test(value)) {
      fail('FIELD_VALUE', 'Course ' + fieldName + ' contains a control character in ' + context + '.');
    }
    if (SCHEDULE_HINT_RE.test(value)) {
      fail('ANCHOR_LINES', 'Course ' + fieldName + ' looks like timetable text in ' + context + '.');
    }
  }

  function parseCourseBlock(anchor, term, context, day, sections) {
    var onclick = getAttribute(anchor, 'onclick');
    if (onclick === null) {
      fail('ANCHOR_LAYOUT', 'A course block has no onclick attribute in ' + context + '.');
    }
    var xkkh = extractPathXkkh(onclick, context);
    var classId = validateClassId(xkkh, term, context);
    var lines = readAnchorLines(anchor, context);
    validateField(lines.name, 'name', context);
    validateField(lines.teacher, 'teacher', context);
    validateField(lines.room, 'room', context);
    if (codePointLength(lines.name) > LIMITS.maxNameCodePoints) {
      fail('NAME_LIMIT', 'Course name exceeds 200 Unicode code points in ' + context + '.', {
        context: context,
        codePoints: codePointLength(lines.name)
      });
    }
    var entries = parseScheduleLine(lines.time, context, {day:day,sections:sections});
    return {
      classId: classId,
      name: lines.name,
      teacher: lines.teacher,
      room: lines.room,
      entries: entries
    };
  }

  function assertNoTextOutsideAnchors(cell, context) {
    function walk(node, insideAnchor) {
      if (!node) return;
      if (node.nodeType === 3) {
        if (!insideAnchor && normalizeText(node.nodeValue) !== '') {
          fail('ANCHOR_LAYOUT', 'Visible text outside a course anchor exists in ' + context + '.');
        }
        return;
      }
      if (node.nodeType !== 1) return;
      var tag = localName(node);
      if (tag === 'script' || tag === 'style' || tag === 'template') {
        fail('ANCHOR_LAYOUT', 'Executable/unknown content exists inside a timetable cell.');
      }
      var nextInside = insideAnchor || tag === 'a';
      if (tag === 'a' && insideAnchor) {
        fail('ANCHOR_LAYOUT', 'Nested anchors exist inside a course block.');
      }
      var children = node.childNodes || [];
      for (var i = 0; i < children.length; i += 1) walk(children[i], nextInside);
    }
    walk(cell, false);
  }

  function coveredSections(records, placement, context) {
    var endRow = placement.row + placement.rowspan;
    if (endRow > records.length) {
      fail('BAD_SPAN', 'A course cell rowspan extends past the timetable body in ' + context + '.');
    }
    var seen = {};
    var result = [];
    for (var r = placement.row; r < endRow; r += 1) {
      var sections = records[r].periodSections;
      if (!sections || sections.length === 0) {
        fail('PERIOD_LAYOUT', 'A course cell covers a row without a period label in ' + context + '.');
      }
      for (var i = 0; i < sections.length; i += 1) {
        if (seen[sections[i]]) {
          fail('PERIOD_LAYOUT', 'A course cell covers overlapping period labels in ' + context + '.');
        }
        seen[sections[i]] = true;
        result.push(sections[i]);
      }
    }
    result.sort(function (a, b) { return a - b; });
    return result;
  }

  function sameNumberArray(left, right) {
    if (left.length !== right.length) return false;
    for (var i = 0; i < left.length; i += 1) {
      if (left[i] !== right[i]) return false;
    }
    return true;
  }

  function makeState() {
    return {
      meetings: [],
      slots: new Map(),
      names: new Map(),
      rawMeetingCount: 0,
      maxWeek: 0,
      processedAnchors: []
    };
  }

  function addMeeting(state, block, entry, day, context) {
    state.rawMeetingCount += 1;
    if (state.rawMeetingCount > LIMITS.maxMeetings) {
      fail('MEETING_LIMIT', 'Timetable expands to more than 5000 meetings.', {
        limit: LIMITS.maxMeetings
      });
    }
    var knownName = state.names.get(block.classId);
    if (knownName !== undefined && knownName !== block.name) {
      fail('MEETING_CONFLICT', 'One classId has contradictory course names in ' + context + '.', {
        classId: block.classId,
        first: knownName,
        second: block.name
      });
    }
    state.names.set(block.classId, block.name);
    for (var previous of state.meetings) {
      if (previous.classId === block.classId && previous.day === day &&
          previous.sections.some(n => entry.sections.includes(n)) &&
          previous.weeks.some(n => entry.weeks.includes(n)) &&
          (previous.teacher !== block.teacher || previous.room !== block.room)) {
        fail('MEETING_CONFLICT', 'Overlapping weeks/sections of one teaching class contradict teacher or room.');
      }
    }


    var slotKey = JSON.stringify([block.classId, day, entry.sections, entry.weeks]);
    var valueKey = JSON.stringify([block.teacher, block.room]);
    var existing = state.slots.get(slotKey);
    if (existing !== undefined) {
      if (existing !== valueKey) {
        fail('MEETING_CONFLICT', 'The same classId/day/sections/weeks has contradictory teacher/room facts.', {
          classId: block.classId,
          day: day,
          sections: entry.sections,
          weeks: entry.weeks
        });
      }
      return;
    }
    state.slots.set(slotKey, valueKey);
    var meeting = {
      classId: block.classId,
      name: block.name,
      teacher: block.teacher,
      room: block.room,
      day: day,
      sections: entry.sections.slice(),
      weeks: entry.weeks.slice()
    };
    state.meetings.push(meeting);
    for (var i = 0; i < entry.weeks.length; i += 1) {
      if (entry.weeks[i] > state.maxWeek) state.maxWeek = entry.weeks[i];
    }
  }

  function parseDayCell(cell, day, expectedSections, term, state, context) {
    var anchors = queryAll(cell, 'a');
    for (var check = 0; check < anchors.length; check += 1) {
      if (queryAll(anchors[check], 'a').length > 0) {
        fail('ANCHOR_LAYOUT', 'Nested anchors are not supported in ' + context + '.');
      }
    }
    if (anchors.length === 0) {
      if (normalizeText(elementText(cell)) !== '') {
        fail('UNPARSED_CELL', 'A timetable cell has visible content but no course anchor in ' + context + '.');
      }
      return;
    }
    assertNoTextOutsideAnchors(cell, context);
    for (var i = 0; i < anchors.length; i += 1) {
      var anchor = anchors[i];
      if (!hasAttribute(anchor, 'onclick')) {
        fail('ANCHOR_LAYOUT', 'A link in a course cell has no onclick attribute in ' + context + '.');
      }
      var block = parseCourseBlock(anchor, term, context, day, expectedSections);
      state.processedAnchors.push(anchor);
      for (var e = 0; e < block.entries.length; e += 1) {
        var entry = block.entries[e];
        if (entry.day !== day) {
          fail('TIME_DAY_MISMATCH', 'Explicit weekday text contradicts the weekday table header in ' + context + '.', {
            context: context,
            headerDay: day,
            explicitDay: entry.day
          });
        }
        if (!sameNumberArray(entry.sections, expectedSections)) {
          fail('SECTION_MISMATCH', 'Explicit sections contradict the covered period-row labels in ' + context + '.', {
            context: context,
            expected: expectedSections,
            explicit: entry.sections
          });
        }
        addMeeting(state, block, entry, day, context);
      }
    }
  }

  function parseMainTable(table, term, header, records) {
    var state = makeState();
    var allAnchors = queryAll(table, 'a');

    for (var r = 1; r < records.length; r += 1) {
      var record = records[r];
      for (var p = 0; p < record.placements.length; p += 1) {
        var placement = record.placements[p];
        if (
          placement.column < LIMITS.firstDayColumn ||
          placement.column > LIMITS.firstDayColumn + 6
        ) {
          continue;
        }
        if (placement.colspan !== 1) {
          var spanningAnchors = queryAll(placement.cell, 'a');
          var spanningText = normalizeText(elementText(placement.cell));
          if (spanningAnchors.length === 0 && spanningText === '') {
            // A purely empty weekday placeholder may legally span columns.
            // It carries no course fact, so skipping it cannot invent a day.
            continue;
          }
          fail('BAD_SPAN', 'A nonempty weekday cell spanning multiple weekday columns is not supported.', {
            row: r + 1,
            column: placement.column + 1
          });
        }
        var day = header.dayByColumn[placement.column];
        if (!day) {
          fail('HEADER_DAY', 'A course cell is outside the verified weekday columns.');
        }
        var expectedSections = coveredSections(
          records, placement, 'row ' + (r + 1) + ', weekday column ' + (placement.column + 1)
        );
        parseDayCell(
          placement.cell,
          day,
          expectedSections,
          term,
          state,
          'row ' + (r + 1) + ', weekday column ' + (placement.column + 1)
        );
      }
    }

    if (state.processedAnchors.length !== allAnchors.length) {
      fail('ANCHOR_LAYOUT', 'One or more anchors in #Table1 were not in a parseable course cell.', {
        allAnchors: allAnchors.length,
        processed: state.processedAnchors.length
      });
    }

    state.meetings.sort(function (left, right) {
      if (left.day !== right.day) return left.day - right.day;
      if (left.sections[0] !== right.sections[0]) return left.sections[0] - right.sections[0];
      if (left.classId !== right.classId) return left.classId < right.classId ? -1 : 1;
      for (var s = 0; s < Math.min(left.sections.length, right.sections.length); s += 1) {
        if (left.sections[s] !== right.sections[s]) return left.sections[s] - right.sections[s];
      }
      if (left.sections.length !== right.sections.length) {
        return left.sections.length - right.sections.length;
      }
      for (var w = 0; w < Math.min(left.weeks.length, right.weeks.length); w += 1) {
        if (left.weeks[w] !== right.weeks[w]) return left.weeks[w] - right.weeks[w];
      }
      if (left.weeks.length !== right.weeks.length) return left.weeks.length - right.weeks.length;
      if (left.teacher !== right.teacher) return left.teacher < right.teacher ? -1 : 1;
      if (left.room !== right.room) return left.room < right.room ? -1 : 1;
      return 0;
    });

    return {
      meetings: state.meetings,
      maxWeek: state.maxWeek
    };
  }

  // Header and field contract: Aero_su timetable.dart:317-339 and its static
  // timetable_test.dart:220-225. No upstream fixture is used by our tests.
  function parseAdditionalTables(doc, parsed) {
    var extra = [], seen = new Set();
    for (var id of EXTRA_TABLE_IDS) {
      var found = queryAll(doc, '#' + id);
      if (!found.length) continue;
      if (found.length !== 1) fail('EXTRA_TABLE_AMBIGUOUS', 'Additional table must be unique.');
      if (localName(found[0]) !== 'table') fail('EXTRA_TABLE_TYPE', 'Additional element must be a table.');
      var table = found[0];
      if (queryAll(table, 'table').length) fail('EXTRA_TABLE_UNSUPPORTED', 'Nested additional table.');
      assertNoForbiddenElements(table);
      var rows = Array.from(table.rows);
      if (!rows.length && !normalizeText(elementText(table))) continue;
      if (!rows.length) fail('EXTRA_TABLE_UNSUPPORTED', 'Unstructured additional course content.');
      var header = Array.from(rows[0].cells);
      if (header.some(c => parseSpanValue(c,'rowspan',1) !== 1 || parseSpanValue(c,'colspan',1) !== 1)) fail('EXTRA_TABLE_UNSUPPORTED', 'Merged additional header.');
      var labels = header.map(c => normalizeText(elementText(c)).replace(/\s/g,''));
      var isDeclaredHeader = rows[0].classList.contains('datelisthead') || header.every(c => localName(c) === 'th');
      if (id !== 'DataGrid1') {
        // These two source tables have no scheduled-time contract. Header-only
        // empty tables are harmless; any data row must abort the entire import.
        if (rows.length === 1 && isDeclaredHeader && labels[0] === '学年') continue;
        fail('EXTRA_TABLE_UNSUPPORTED', 'Additional selected/unscheduled courses have no complete time contract.');
      }
      var aliases = {name:['课程名称'],teacher:['教师','任课教师','授课教师'],
        weeks:['起止周','起止周次'],time:['上课时间'],room:['上课地点']};
      var columns = {};
      for (var field of Object.keys(aliases)) {
        var matches = labels.map((v,i) => aliases[field].includes(v) ? i : -1).filter(i => i >= 0);
        if (matches.length !== 1) fail('EXTRA_TABLE_UNSUPPORTED', 'Missing or repeated additional course header: ' + field);
        columns[field] = matches[0];
      }
      for (var row of rows.slice(1)) {
        var cells = Array.from(row.cells);
        if (cells.length !== labels.length || cells.some(c => parseSpanValue(c,'rowspan',1) !== 1 || parseSpanValue(c,'colspan',1) !== 1)) fail('EXTRA_TABLE_UNSUPPORTED', 'Incomplete additional course row.');
        var value = field => normalizeText(elementText(cells[columns[field]]));
        var name = value('name'), teacher = value('teacher'), room = value('room');
        validateField(name,'course name','additional course');
        validateField(teacher,'teacher','additional course');
        validateField(room,'room','additional course');
        if ([name,teacher,room].some(v => v.length > 200)) fail('FIELD_VALUE', 'Additional field exceeds 200 characters.');
        var time = value('time'), declared = value('weeks');
        if (!time) fail('EXTRA_TABLE_UNSUPPORTED', 'Additional course has no explicit scheduled time.');
        var declaredWeeks = declared ? parseWeekList(declared,'additional course week column') : null;
        // Week numbers may be in the separate, explicitly labelled source
        // column. Never substitute a default semester range.
        if (!time.includes('{')) {
          if (!declaredWeeks) fail('EXTRA_TABLE_UNSUPPORTED', 'Additional course is missing weeks.');
          time = time.split(/[;；\n]+|\s+(?=周[一二三四五六日天]第)/).map(part => part.trim() + '{' + declared + '}').join(';');
        }
        var entries = parseScheduleLine(time, 'additional course');
        for (var entry of entries) {
          if (declaredWeeks && entry.weeks.some(w => !declaredWeeks.includes(w))) fail('EXTRA_TABLE_UNSUPPORTED', 'Additional course time contradicts the explicit week column.');
          var fact = {classId:'',name:name,teacher:teacher,room:room,day:entry.day,sections:entry.sections,weeks:entry.weeks};
          // No server classId is provided by this table. Preserve that absence;
          // do not fabricate a class code or silently deduplicate by its name.
          var key = JSON.stringify([name,teacher,room,fact.day,fact.sections,fact.weeks]);
          if (seen.has(key)) fail('MEETING_CONFLICT', 'Repeated additional course facts without a stable teaching-class identity.');
          for (var old of parsed.meetings.concat(extra)) {
            if (old.name === name && old.day === fact.day && old.sections.some(n => fact.sections.includes(n)) && old.weeks.some(w => fact.weeks.includes(w)))
              fail('MEETING_CONFLICT', 'Overlapping same-name additional course cannot be uniquely identified.');
          }
          seen.add(key); extra.push(fact);
          parsed.maxWeek = Math.max(parsed.maxWeek, ...fact.weeks);
          if (parsed.meetings.length + extra.length > LIMITS.maxMeetings) fail('MEETING_LIMIT', 'Too many meetings.');
        }
      }
    }
    parsed.meetings.push(...extra);
  }

  function extractSudaTimetable(doc, expectedYear, expectedSemester) {
    assertDocument(doc);
    var year = canonicalExpectedYear(expectedYear);
    var semester = canonicalExpectedSemester(expectedSemester);
    rejectNonTimetableDocument(doc);
    var term = readTerm(doc, year, semester);
    var table = getMainTable(doc);
    assertNoForbiddenElements(table);
    assertNoPagination(doc, table);
    assertNoStrayCourseAnchors(doc, table);
    var records = buildGrid(table);
    var header = validateHeader(records[0]);
    parsePeriodRows(records);
    var parsed = parseMainTable(table, term, header, records);
    parseAdditionalTables(doc, parsed);
    return {
      year: term.year,
      semester: term.semester,
      meetings: parsed.meetings,
      maxWeek: parsed.maxWeek
    };
  }

  globalObject.extractSudaTimetable = extractSudaTimetable;
})(typeof globalThis !== 'undefined' ? globalThis : this);

// KAgenda host composition. Kept separate from the public-only delegated inputs.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const need = k.requireValue;
  const startUrl = new URL(location.href);
  let nextRequestAt = 0;
  const text = e => String(e && e.textContent || '').trim();
  function entry() {
    const u = new URL(location.href);
    need(k.adapterId === 'ext_suda_personal' &&
      ['http:', 'https:'].includes(u.protocol) && u.hostname === 'xk.suda.edu.cn' &&
      !u.port && !u.username && !u.password && u.pathname === '/xskbcx.aspx' &&
      u.searchParams.getAll('xh').length === 1 && u.searchParams.get('xh') &&
      u.searchParams.getAll('xm').length === 1 && u.searchParams.get('xm') &&
      u.searchParams.getAll('gnmkdm').length === 1 && u.searchParams.get('gnmkdm') === 'N121603' &&
      u.href === startUrl.href,
      '请先进入苏州大学个人课表页面并完成登录，导入期间请勿切换页面');
  }
  function unique(doc, selector, label) {
    const all = doc.querySelectorAll(selector);
    need(all.length === 1, '缺少或重复的' + label);
    return all[0];
  }
  function options(doc, id) {
    const select = unique(doc, 'select#' + id, '学期选择框');
    need(!select.multiple && !select.disabled, '学期选择框状态无效');
    const result = Array.from(select.options).map(o => ({value: o.value, label: text(o)}));
    need(result.length > 0 && result.length <= 200 && result.every(o => o.value && o.label) &&
      new Set(result.map(o => o.value)).size === result.length, '服务器学期列表不完整或含重复项');
    if (id === 'xnd') need(result.every(o => /^\d{4}-\d{4}$/.test(o.value) && +o.value.slice(5) === +o.value.slice(0,4) + 1), '学年格式无效');
    else need(result.every(o => /^[1-3]$/.test(o.value)), '学期格式无效');
    return result;
  }
  function selected(doc) {
    options(doc, 'xnd'); options(doc, 'xqd');
    return {year: doc.querySelector('#xnd').value, semester: doc.querySelector('#xqd').value};
  }
  function noFilters(doc) {
    need(!doc.querySelector('input[type="password"]'), '登录失效，请重新登录');
    for (const s of doc.querySelectorAll('select')) {
      if (s.id !== 'xnd' && s.id !== 'xqd') need(!String(s.value || '').trim(), '存在未核实的课表筛选项，请清空后重试');
    }
    for (const e of doc.querySelectorAll('input,textarea')) {
      const id = e.name || e.id || '';
      if (/^(?:zc|xqj|week|weekday|course|kcmc|kclbdm|kclxdm)$/i.test(id))
        need(!String(e.value || '').trim(), '存在未清空的课表筛选项');
    }
  }
  function fields(doc, target, year, semester) {
    const form = unique(doc, 'select#xnd', '学年').form;
    need(form && doc.querySelector('#xqd').form === form, '学期表单不完整');
    const action = new URL(form.getAttribute('action') || startUrl.href, startUrl);
    need(action.origin === startUrl.origin && action.pathname === startUrl.pathname, '学校表单地址发生变化');
    const data = new URLSearchParams();
    for (const e of form.elements) {
      const name = e.name;
      if (!name || e.disabled || ['button','submit','image','reset'].includes(e.type)) continue;
      need(!['password','file'].includes(e.type), '个人课表表单含不支持的敏感输入项');
      if (['checkbox','radio'].includes(e.type) && !e.checked) continue;
      need(!data.has(name), '学校表单含重复字段，无法可靠提交');
      data.set(name, e.value);
    }
    need(data.has('xnd') && data.has('xqd'), '学校表单缺少学年学期字段');
    need(data.has('__VIEWSTATE') && data.get('__VIEWSTATE'), '缺少学校页面状态，请重新打开个人课表');
    data.set('__EVENTTARGET', target); data.set('__EVENTARGUMENT', ''); data.set('__LASTFOCUS', '');
    data.set('xnd', year); data.set('xqd', semester);
    return data;
  }
  async function request(body) {
    entry();
    const delay = nextRequestAt - Date.now();
    if (delay > 0) await new Promise(resolve => setTimeout(resolve, delay));
    entry();
    nextRequestAt = Date.now() + 3100;
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(startUrl.href, {method: body ? 'POST' : 'GET', body,
        credentials: 'same-origin', redirect: 'error', cache:'no-store', signal: controller.signal,
        headers: {Accept: 'text/html,application/xhtml+xml'}});
      need(response.ok && response.status === 200 && !response.redirected, '个人课表请求失败或登录失效');
      const u = new URL(response.url || startUrl.href);
      need(u.origin === startUrl.origin && u.pathname === startUrl.pathname && !u.username && !u.password &&
        ['xh','xm','gnmkdm'].every(n => u.searchParams.getAll(n).length === 1 && u.searchParams.get(n) === startUrl.searchParams.get(n)),
        '个人课表响应身份或地址不一致');
      const type = response.headers.get('Content-Type') || '';
      need(!type || /text\/html|application\/xhtml\+xml/i.test(type), '学校未返回课表 HTML');
      const bytes = await response.arrayBuffer();
      need(bytes.byteLength > 0 && bytes.byteLength <= 2000000, '课表响应大小无效');
      const charset = /charset\s*=\s*["']?([\w-]+)/i.exec(type);
      let html;
      if (charset) {
        need(/^(?:utf-8|utf8|gbk|gb2312|gb18030)$/i.test(charset[1]), '课表字符编码不受支持');
        html = new TextDecoder(charset[1], {fatal:true}).decode(bytes);
      } else {
        try { html = new TextDecoder('utf-8', {fatal:true}).decode(bytes); }
        catch (_) { html = new TextDecoder('gb18030', {fatal:true}).decode(bytes); }
      }
      need(html.trim() !== '三秒防刷', '学校正在限流，请稍后重新导入');
      const doc = new DOMParser().parseFromString(html, 'text/html');
      noFilters(doc); selected(doc);
      need(doc.querySelectorAll('#Table1').length === 1, '未取得唯一的个人学期课表');
      return doc;
    } finally { clearTimeout(timer); }
  }
  function offered(doc, id, value) {
    need(options(doc,id).some(o => o.value === value), '所选学期已不在学校列表中');
  }
  async function loadTerm(year, semester) {
    let doc = await request(), current = selected(doc);
    offered(doc, 'xnd', year);
    if (current.year !== year) {
      doc = await request(fields(doc,'xnd',year,current.semester)); current = selected(doc);
      need(current.year === year, '学校没有切换到所选学年');
    }
    offered(doc,'xqd',semester);
    if (current.semester !== semester) {
      doc = await request(fields(doc,'xqd',year,semester)); current = selected(doc);
    }
    need(current.year === year && current.semester === semester, '返回课表学期与选择不一致');
    return doc;
  }
  function snapshot(parsed) {
    return JSON.stringify({year:parsed.year, semester:parsed.semester,
      meetings: parsed.meetings.map(m => [m.classId,m.name,m.teacher,m.room,m.day,m.sections,m.weeks]).sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)))});
  }
  k.run(async () => {
    entry(); noFilters(document);
    const visibleTerm = JSON.stringify(selected(document));
    let doc = await request();
    const year = await k.choose('选择教务学年', options(doc,'xnd'), 'value','label');
    let current = selected(doc);
    if (current.year !== year.value) {
      doc = await request(fields(doc,'xnd',year.value,current.semester));
      need(selected(doc).year === year.value, '学校未切换到所选学年');
    }
    const semester = await k.choose('选择教务学期', options(doc,'xqd'),'value','label');
    current = selected(doc);
    if (current.semester !== semester.value) doc = await request(fields(doc,'xqd',year.value,semester.value));
    const first = globalThis.extractSudaTimetable(doc, year.value, semester.value);
    need(first.meetings.length > 0 && first.maxWeek >= 1, '当前学期没有可导入的完整排课');
    const value = await k.api.showPrompt('学期总周数','请按校历填写总周数（1-40），且不少于课程最大周次 '+first.maxWeek,'');
    need(value !== null && value !== undefined, '已取消导入');
    const weeks = k.integer(value, first.maxWeek, 40, '学期总周数');
    const finalDoc = await loadTerm(year.value,semester.value);
    need(options(finalDoc,'xnd').some(o=>o.value===year.value && o.label===year.label) &&
      options(finalDoc,'xqd').some(o=>o.value===semester.value && o.label===semester.label), '学校学期名称已变化');
    const final = globalThis.extractSudaTimetable(finalDoc,year.value,semester.value);
    need(snapshot(first) === snapshot(final), '查询期间课程安排发生变化，未导入部分课程');
    entry(); noFilters(document);
    need(JSON.stringify(selected(document)) === visibleTerm, '导入期间网页学期已切换，请重新导入');
    const courses = [];
    for (const m of final.meetings) {
      let run = null;
      for (const n of m.sections) {
        if (run && n === run[1]+1) run[1] = n;
        else { if (run) courses.push(k.course(m.name,m.teacher,m.room,m.day,run[0],run[1],m.weeks)); run=[n,n]; }
      }
      if (run) courses.push(k.course(m.name,m.teacher,m.room,m.day,run[0],run[1],m.weeks));
    }
    await k.finish(courses,0,weeks);
  });
})();
