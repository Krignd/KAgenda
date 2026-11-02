// KAgenda NJUST undergraduate personal timetable adapter.
// Protocol references: flyShaoyu/NJUSTLocalClassSche (Apache-2.0), pinned
// 50299ccab1f36f82c83a4c0c259e1513df702b83, src/html-parser.ts, config.ts,
// endpoints.ts, timetable-page.ts; JirakiRance/NJUST-NannoSchedule (MIT),
// 32342f7d66e9b5f3ec46a3d736966dd9ede665e2, back/parsers/nj_parser.py and
// request-only excerpts back/core/config.py, back/api/user_api.py.
// Exact source paths, hashes, dates and original notices: school-provenance.json.
// DeepSeek assisted the independent draft; KAgenda primary agent reviewed and
// corrected room constraints, entry guards, completeness and final snapshots.
// Target adapter id: ext_njust_personal (installed by the host bridge).
// Independent implementation. No upstream code is copied or executed.
// The parser is intentionally strict: every imported meeting needs an explicit
// weekday, period, week and room basis in both #kbtable and #dataList.
(function () {
  'use strict';

  const K = window.kagendaSchool;
  const SCHEDULE_ORIGIN = 'https://bkjw.njust.edu.cn';
  const SCHEDULE_PATH = '/njlgdx/xskb/xskb_list.do';
  const SCHEDULE_QUERY = 'Ves632DSdyV=NEW_XSD_PYGL';
  const MAX_HTML = 2000000;
  const MAX_COURSES = 5000;

  const WEEKDAY = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7};
  const BLOCK_TAGS = new Set([
    'ADDRESS', 'ARTICLE', 'ASIDE', 'BLOCKQUOTE', 'DIV', 'DL', 'DT', 'DD', 'FIELDSET',
    'FIGCAPTION', 'FIGURE', 'FOOTER', 'FORM', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
    'HEADER', 'HR', 'LI', 'MAIN', 'NAV', 'OL', 'P', 'PRE', 'SECTION', 'TABLE',
    'TBODY', 'THEAD', 'TFOOT', 'TR', 'TD', 'TH', 'UL', 'FONT'
  ]);

  function fail(message) {
    throw new Error(message);
  }

  function need(ok, message) {
    if (!ok) fail(message);
  }

  function normalizeText(value) {
    return String(value == null ? '' : value)
      .replace(/\u00a0/g, ' ')
      .replace(/\u3000/g, ' ')
      .replace(/\r\n?/g, '\n')
      .replace(/[ \t\f\v]+/g, ' ')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }

  function normalizeInline(value) {
    return normalizeText(value).replace(/\s*\n\s*/g, ' ');
  }

  function textOf(element) {
    return normalizeInline(element && element.textContent != null ? element.textContent : '');
  }

  function attr(element, name) {
    try {
      return element && typeof element.getAttribute === 'function' ? element.getAttribute(name) : null;
    } catch (_) {
      return null;
    }
  }

  function hasAttr(element, name) {
    try {
      return !!element && typeof element.hasAttribute === 'function' && element.hasAttribute(name);
    } catch (_) {
      return false;
    }
  }

  function tagOf(element) {
    return String(element && element.tagName || '').toUpperCase();
  }

  function hasClass(element, name) {
    const raw = attr(element, 'class');
    return !!raw && String(raw).split(/\s+/).indexOf(name) >= 0;
  }

  function nearestTable(element) {
    let current = element && element.parentNode;
    while (current) {
      if (tagOf(current) === 'TABLE') return current;
      current = current.parentNode;
    }
    return null;
  }

  function nearestKbContent(element) {
    let current = element && element.parentNode;
    while (current) {
      if (tagOf(current) === 'DIV' && hasClass(current, 'kbcontent')) return current;
      current = current.parentNode;
    }
    return null;
  }

  function directCells(row) {
    const children = row && row.children ? Array.from(row.children) : [];
    return children.filter(function (element) {
      const tag = tagOf(element);
      return tag === 'TD' || tag === 'TH';
    });
  }

  function directRows(table) {
    return Array.from(table.querySelectorAll('tr')).filter(function (row) {
      return nearestTable(row) === table;
    });
  }

  function uniqueElement(doc, selector, label) {
    const matches = Array.from(doc.querySelectorAll(selector));
    need(matches.length === 1, '页面存在重复或缺失的' + label);
    return matches[0];
  }

  function linesOf(element) {
    const result = [];
    let buffer = '';
    function flush() {
      const line = normalizeInline(buffer);
      buffer = '';
      if (line) result.push(line);
    }
    function walk(node) {
      if (!node) return;
      if (node.nodeType === 3) {
        const pieces = String(node.data == null ? '' : node.data).split(/\r?\n/);
        for (let i = 0; i < pieces.length; i++) {
          buffer += pieces[i];
          if (i < pieces.length - 1) flush();
        }
        return;
      }
      if (node.nodeType !== 1) return;
      const name = tagOf(node);
      if (name === 'SCRIPT' || name === 'STYLE') return;
      if (name === 'BR') {
        flush();
        return;
      }
      const block = BLOCK_TAGS.has(name);
      if (block) flush();
      const children = Array.from(node.childNodes || []);
      for (const child of children) walk(child);
      if (block) flush();
    }
    walk(element);
    flush();
    return result;
  }

  function parseDay(value) {
    const text = normalizeInline(value).replace(/\s+/g, '');
    const match = /^(?:星期|周)?([一二三四五六日天])$/.exec(text);
    return match ? WEEKDAY[match[1]] : null;
  }

  function isHeaderText(value) {
    const text = normalizeInline(value);
    if (!text) return true;
    return parseDay(text) != null || /^(节次|时间|上午|下午|晚上|中午|课程|星期|周次|日期)$/.test(text);
  }

  function labelKind(title) {
    const text = normalizeInline(title).replace(/[（]/g, '(').replace(/[）]/g, ')').replace(/\s+/g, '');
    if (/^(老师|教师|任课教师|授课教师)$/.test(text)) return 'teacher';
    if (/^(周次\(节次\)|周次|起止周|上课周次)$/.test(text)) return 'weeks';
    if (/^(教室|上课地点|地点|上课教室)$/.test(text)) return 'room';
    return null;
  }

  function readBlockSegments(block) {
    const segments = [];
    let current = {lines: [], labels: Object.create(null)};
    let buffer = '';

    function flushLine() {
      const line = normalizeInline(buffer);
      buffer = '';
      if (line) current.lines.push(line);
    }

    function pushSegment() {
      flushLine();
      if (current.lines.length || Object.keys(current.labels).length) segments.push(current);
      current = {lines: [], labels: Object.create(null)};
    }

    function appendText(value) {
      const pieces = String(value == null ? '' : value).split(/---+/);
      for (let i = 0; i < pieces.length; i++) {
        if (i > 0) pushSegment();
        const lines = pieces[i].split(/\r?\n/);
        for (let j = 0; j < lines.length; j++) {
          buffer += lines[j];
          if (j < lines.length - 1) flushLine();
        }
      }
    }

    function walk(node) {
      if (!node) return;
      if (node.nodeType === 3) {
        appendText(node.data);
        return;
      }
      if (node.nodeType !== 1) return;
      const name = tagOf(node);
      if (name === 'SCRIPT' || name === 'STYLE') return;
      if (name === 'BR') {
        flushLine();
        return;
      }
      const title = attr(node, 'title');
      const kind = title ? labelKind(title) : null;
      if (kind) {
        flushLine();
        const value = normalizeInline(node.textContent == null ? '' : node.textContent);
        need(value, '课程块存在空字段');
        need(!current.labels[kind], '课程块存在重复字段');
        current.labels[kind] = value;
        current.lines.push(value);
        flushLine();
        return;
      }
      const blockLike = BLOCK_TAGS.has(name);
      if (blockLike) flushLine();
      const children = Array.from(node.childNodes || []);
      for (const child of children) walk(child);
      if (blockLike) flushLine();
    }

    walk(block);
    pushSegment();
    return segments;
  }

  function parsePeriods(value) {
    let text = normalizeInline(value)
      .replace(/第/g, '')
      .replace(/节/g, '')
      .replace(/[（(]/g, '')
      .replace(/[）)]/g, '')
      .replace(/[，、;；]/g, ',')
      .replace(/[~～—–至－]/g, '-')
      .replace(/\s+/g, '');
    need(text && /^[0-9,\-]+$/.test(text), '上课节次格式无效');
    const result = new Set();
    for (const part of text.split(',')) {
      need(part, '上课节次格式无效');
      const match = /^(\d+)(?:-(\d+))?$/.exec(part);
      need(match, '上课节次格式无效');
      const first = Number(match[1]);
      const last = match[2] ? Number(match[2]) : first;
      need(Number.isInteger(first) && Number.isInteger(last) &&
        first >= 1 && last <= 24 && last >= first, '上课节次超出范围');
      for (let section = first; section <= last; section++) result.add(section);
    }
    need(result.size > 0, '上课节次为空');
    return result;
  }

  function parseRowPeriods(value) {
    let text = normalizeInline(value);
    if (!text) return null;
    text = text.replace(/^(上午|下午|晚上|中午)\s*/, '');
    if (!text) return null;
    if (!/^[0-9第节\s,，、\-~～—–至－]+$/.test(text)) return null;
    try {
      return parsePeriods(text);
    } catch (_) {
      return null;
    }
  }

  function parseWeeksStrict(value) {
    const text = normalizeInline(value);
    need(text && text.length < 500 && text.indexOf('周') >= 0, '缺少明确周次');
    need(text.indexOf('节') < 0, '周次字段包含节次');
    let weeks;
    try {
      weeks = K.weeks(text);
    } catch (_) {
      fail('无法识别周次');
    }
    need(Array.isArray(weeks) && weeks.length > 0, '缺少明确周次');
    for (const week of weeks) {
      need(Number.isInteger(week) && week >= 1 && week <= 40, '周次超出范围');
    }
    return weeks.slice();
  }

  function splitWeekPeriodField(value) {
    const raw = normalizeInline(value);
    need(raw && raw.length < 500 && raw.indexOf('周') >= 0, '缺少明确周次');
    const periodPattern = /(?:第\s*)?\d+(?:\s*[-~～—–－至]\s*\d+)?(?:\s*[,，、]\s*\d+(?:\s*[-~～—–－至]\s*\d+)?)*\s*节/g;
    const matches = [];
    let match;
    while ((match = periodPattern.exec(raw))) matches.push({index: match.index, text: match[0]});
    need(matches.length <= 1, '周次与节次字段不明确');
    let weekText = raw;
    let periods = null;
    if (matches.length === 1) {
      const found = matches[0];
      const tail = raw.slice(found.index + found.text.length).trim();
      need(!tail || /^[）)\]】]+$/.test(tail), '周次字段存在多余内容');
      weekText = raw.slice(0, found.index).trim().replace(/[（(\[【\s]+$/, '');
      need(weekText.indexOf('周') >= 0, '缺少明确周次');
      periods = parsePeriods(found.text);
    }
    return {weeks: parseWeeksStrict(weekText), periods: periods, weekText: weekText, raw: raw};
  }

  function splitSet(value) {
    const text = normalizeInline(value);
    if (!text) return new Set();
    return new Set(text.split(/[,，、;；\n]+/).map(function (part) {
      return normalizeInline(part);
    }).filter(Boolean));
  }

  function splitRoomsCell(cell) {
    const result = new Set();
    for (const line of linesOf(cell)) {
      for (const part of line.split(/[,，、;；]+/)) {
        const value = normalizeInline(part);
        if (value) result.add(value);
      }
    }
    return result;
  }

  function parseGridSegment(segment, day, rowPeriods) {
    const lines = segment.lines.map(normalizeInline).filter(Boolean);
    need(lines.length > 0, '课程块为空');
    const labels = segment.labels;
    let weekIndex = -1;
    let weekLine = '';

    if (labels.weeks) {
      weekLine = normalizeInline(labels.weeks);
      for (let i = 0; i < lines.length; i++) {
        if (lines[i] === weekLine) {
          weekIndex = i;
          break;
        }
      }
      if (weekIndex < 0) {
        for (let i = 0; i < lines.length; i++) {
          if (lines[i].indexOf(weekLine) >= 0) {
            weekIndex = i;
            weekLine = lines[i];
            break;
          }
        }
      }
      need(weekIndex >= 0, '周次字段未出现在课程块中');
    } else {
      const found = [];
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].indexOf('周') < 0) continue;
        try {
          splitWeekPeriodField(lines[i]);
          found.push(i);
        } catch (_) {
          // A non-week line containing "周" is ignored only for locating the
          // unique week line; ambiguity below still rejects the whole batch.
        }
      }
      need(found.length === 1, '无法唯一识别周次行');
      weekIndex = found[0];
      weekLine = lines[weekIndex];
    }

    const parsedWeek = splitWeekPeriodField(weekLine);
    let room = labels.room ? normalizeInline(labels.room) : '';
    if (!room) {
      need(weekIndex < lines.length - 1, '课程块缺少教室');
      room = lines[lines.length - 1];
    }
    let teacher = labels.teacher ? normalizeInline(labels.teacher) : '';
    if (!teacher && weekIndex >= 2) teacher = lines[weekIndex - 1];

    const remove = new Set();
    [weekLine, labels.weeks, room, labels.room, teacher, labels.teacher].forEach(function (value) {
      if (value) remove.add(normalizeInline(value));
    });
    const rest = lines.filter(function (line) {
      return !remove.has(normalizeInline(line));
    });
    need(rest.length === 1, '课程块字段数量不明确');
    const name = normalizeInline(rest[0]);
    need(name, '课程块缺少课程名称');
    need(room && !/[,，、;；]/.test(room), '课程块教室不唯一');

    let periods = parsedWeek.periods;
    if (periods && rowPeriods) {
      for (const section of periods) {
        need(rowPeriods.has(section), '课程块节次与网格行不一致');
      }
    }
    if (!periods) periods = rowPeriods;
    need(periods && periods.size > 0, '课程块缺少明确节次');

    return {
      name: name,
      teacher: teacher,
      teacherSet: splitSet(teacher),
      day: day,
      rowPeriods: rowPeriods ? new Set(rowPeriods) : null,
      explicitPeriods: parsedWeek.periods ? new Set(parsedWeek.periods) : null,
      periodSet: new Set(periods),
      weeks: parsedWeek.weeks,
      room: room
    };
  }

  function spanValue(element, name) {
    const raw = attr(element, name);
    if (raw == null || String(raw).trim() === '') return 1;
    const value = Number(String(raw).trim());
    need(Number.isInteger(value) && value >= 1 && value <= 100, '课表网格跨度无效');
    return value;
  }

  function buildLayout(rows) {
    const byRow = rows.map(function () { return []; });
    const occupied = new Map();
    let maxCol = 0;
    for (let rowIndex = 0; rowIndex < rows.length; rowIndex++) {
      let column = 0;
      for (const cell of directCells(rows[rowIndex])) {
        while (occupied.has(rowIndex + ':' + column)) column++;
        const rowspan = spanValue(cell, 'rowspan');
        const colspan = spanValue(cell, 'colspan');
        need(rowIndex + rowspan <= rows.length, '课表网格合并单元格超出表格');
        for (let dr = 0; dr < rowspan; dr++) {
          for (let dc = 0; dc < colspan; dc++) {
            const key = (rowIndex + dr) + ':' + (column + dc);
            need(!occupied.has(key), '课表网格单元格重叠');
            occupied.set(key, {cell: cell, row: rowIndex, column: column, rowspan: rowspan, colspan: colspan});
          }
        }
        byRow[rowIndex].push({
          cell: cell, row: rowIndex, column: column, rowspan: rowspan, colspan: colspan
        });
        maxCol = Math.max(maxCol, column + colspan);
        column += colspan;
      }
    }
    return {rows: rows, byRow: byRow, occupied: occupied, maxCol: maxCol};
  }

  function parseGrid(doc) {
    const table = uniqueElement(doc, '#kbtable', ' #kbtable');
    need(!table.querySelector('table'), '课表网格存在嵌套表格');
    const rows = directRows(table);
    need(rows.length >= 2, '课表网格没有数据行');
    const layout = buildLayout(rows);

    const candidates = [];
    const scanRows = Math.min(rows.length, 4);
    for (let rowIndex = 0; rowIndex < scanRows; rowIndex++) {
      const map = new Map();
      let dayCount = 0;
      let headerLike = true;
      for (const info of layout.byRow[rowIndex]) {
        const text = textOf(info.cell);
        if (text && !isHeaderText(text)) headerLike = false;
        for (let column = info.column; column < info.column + info.colspan; column++) {
          const day = parseDay(text);
          if (day) {
            dayCount++;
            map.set(column, day);
          }
        }
      }
      if (headerLike && dayCount >= 2) candidates.push({row: rowIndex, map: map});
    }
    need(candidates.length > 0, '无法识别星期表头');
    const headerRow = candidates[candidates.length - 1].row;
    const dayMap = new Map();
    for (const candidate of candidates) {
      if (candidate.row > headerRow) continue;
      for (const entry of candidate.map) {
        if (dayMap.has(entry[0]) && dayMap.get(entry[0]) !== entry[1]) {
          fail('星期表头存在冲突');
        }
        dayMap.set(entry[0], entry[1]);
      }
    }
    need(dayMap.size >= 2, '星期表头不完整');
    const dayColumns = new Set(dayMap.keys());

    const labelCandidates = [];
    for (let column = 0; column < layout.maxCol; column++) {
      if (dayColumns.has(column)) continue;
      let hasLabel = false;
      for (let rowIndex = headerRow + 1; rowIndex < rows.length; rowIndex++) {
        const info = layout.occupied.get(rowIndex + ':' + column);
        if (!info) continue;
        if (info.cell.querySelector('div.kbcontent')) {
          fail('存在未映射到星期的课程单元格');
        }
        const text = textOf(info.cell);
        if (text && !/^(上午|下午|晚上|中午)$/.test(text)) {
          hasLabel = true;
          break;
        }
      }
      if (hasLabel) labelCandidates.push(column);
    }
    need(labelCandidates.length <= 1, '存在多个非星期列');
    const labelColumn = labelCandidates.length ? labelCandidates[0] : null;

    const entries = [];
    for (let rowIndex = headerRow + 1; rowIndex < rows.length; rowIndex++) {
      for (const info of layout.byRow[rowIndex]) {
        const coveredDays = [];
        for (let column = info.column; column < info.column + info.colspan; column++) {
          if (dayColumns.has(column)) coveredDays.push(column);
        }
        if (!coveredDays.length) {
          if (info.column === labelColumn) continue;
          const unmapped = textOf(info.cell);
          if (unmapped && !/^(上午|下午|晚上|中午)$/.test(unmapped)) {
            fail('存在未映射到星期的内容');
          }
          continue;
        }
        need(coveredDays.length === info.colspan, '课程单元格跨越星期列');
        const day = dayMap.get(info.column);
        need(day, '课程单元格缺少星期表头');
        for (const column of coveredDays) {
          need(dayMap.get(column) === day, '课程单元格跨越多天');
        }

        const blocks = Array.from(info.cell.querySelectorAll('div.kbcontent'));
        if (!blocks.length) {
          if (textOf(info.cell)) fail('课程单元格缺少 div.kbcontent');
          continue;
        }
        for (const block of blocks) {
          need(nearestKbContent(block) === null, '课程单元格存在嵌套 div.kbcontent');
        }

        let rowPeriods = null;
        if (labelColumn != null) {
          const merged = new Set();
          for (let dr = 0; dr < info.rowspan; dr++) {
            const targetRow = rowIndex + dr;
            if (targetRow >= rows.length) break;
            const labelInfo = layout.occupied.get(targetRow + ':' + labelColumn);
            if (!labelInfo) continue;
            const parsed = parseRowPeriods(textOf(labelInfo.cell));
            if (parsed) parsed.forEach(function (section) { merged.add(section); });
          }
          if (merged.size) rowPeriods = merged;
        }

        for (const block of blocks) {
          const segments = readBlockSegments(block);
          for (const segment of segments) {
            if (!segment.lines.length && !Object.keys(segment.labels).length) continue;
            entries.push(parseGridSegment(segment, day, rowPeriods));
          }
        }
      }
    }

    need(entries.length > 0, '课表网格没有课程');
    need(entries.length <= MAX_COURSES, '课表网格课程数量异常');
    const seen = new Set();
    for (const entry of entries) {
      const key = JSON.stringify([
        entry.name,
        Array.from(entry.teacherSet).sort(),
        entry.day,
        Array.from(entry.periodSet).sort(function (a, b) { return a - b; }),
        entry.weeks,
        entry.room
      ]);
      need(!seen.has(key), '课表网格存在重复或歧义课程');
      seen.add(key);
    }
    return entries;
  }

  function parseTimeEntries(lines) {
    const entries = [];
    for (const rawLine of lines) {
      const line = normalizeInline(rawLine);
      if (!line) continue;
      const parts = line.split(/(?=星期[一二三四五六日天])/).filter(function (part) {
        return part.trim().length > 0;
      });
      need(parts.length > 0, '无法识别的上课时间');
      for (const part of parts) {
        const cleaned = part.replace(/^[\s,，;；、]+/, '');
        if (!cleaned) continue;
        const match = /^星期([一二三四五六日天])\s*(?:[（(]\s*([^）)]*)\s*[）)]|((?:第\s*)?\d[^;；\s]*?节))\s*(.*)$/.exec(cleaned);
        if (!match) fail('无法识别的上课时间');
        const day = WEEKDAY[match[1]];
        const periodText = match[2] != null ? match[2] : match[3];
        const periods = parsePeriods(periodText);
        let tail = normalizeInline(match[4] || '')
          .replace(/^[\s,，;；、]+/, '')
          .replace(/[\s,，;；、]+$/, '');
        const roomTails = [];
        if (tail) {
          need(tail.indexOf('星期') < 0, '上课时间格式不明确');
          for (const room of tail.split(/[,，、;；]+/)) {
            const value = normalizeInline(room);
            if (value) roomTails.push(value);
          }
        }
        entries.push({day: day, periods: periods, roomTails: roomTails});
      }
    }
    need(entries.length > 0, '缺少明确的上课时间');
    return entries;
  }

  function findHeaderIndex(headers, predicate) {
    const matches = [];
    for (let i = 0; i < headers.length; i++) {
      if (predicate(headers[i])) matches.push(i);
    }
    need(matches.length <= 1, '详情表表头字段重复');
    return matches.length ? matches[0] : -1;
  }

  function parseDetail(doc) {
    const table = uniqueElement(doc, '#dataList', ' #dataList');
    need(!table.querySelector('table'), '详情表存在嵌套表格');
    const rows = directRows(table);
    need(rows.length >= 2, '详情表没有课程行');

    let headerRow = -1;
    let headerWidth = -1;
    let columns = null;
    const scanRows = Math.min(rows.length, 3);
    for (let rowIndex = 0; rowIndex < scanRows; rowIndex++) {
      const cells = directCells(rows[rowIndex]);
      if (!cells.length) continue;
      let hasSpan = false;
      for (const cell of cells) {
        if (spanValue(cell, 'colspan') > 1 || spanValue(cell, 'rowspan') > 1) hasSpan = true;
      }
      if (hasSpan) fail('详情表存在合并单元格');
      const headers = cells.map(textOf);
      const nameIndex = findHeaderIndex(headers, function (header) {
        return /课程名称|课程名$/.test(header);
      });
      const timeIndex = findHeaderIndex(headers, function (header) {
        return /上课时间|时间/.test(header) && !/地点|教室/.test(header);
      });
      const roomIndex = findHeaderIndex(headers, function (header) {
        return /上课地点|地点|教室/.test(header) && !/时间/.test(header);
      });
      const teacherIndex = findHeaderIndex(headers, function (header) {
        return /任课教师|授课教师|教师/.test(header);
      });
      if (nameIndex >= 0 && timeIndex >= 0 && roomIndex >= 0 && teacherIndex >= 0) {
        headerRow = rowIndex;
        headerWidth = cells.length;
        columns = {
          name: nameIndex,
          time: timeIndex,
          room: roomIndex,
          teacher: teacherIndex,
          code: findHeaderIndex(headers, function (header) { return /课程编号|课程号|课程代码/.test(header); }),
          sequence: findHeaderIndex(headers, function (header) { return /课序号/.test(header); }),
          credits: findHeaderIndex(headers, function (header) { return /学分/.test(header); }),
          type: findHeaderIndex(headers, function (header) { return /课程属性|课程性质|课程类别/.test(header); })
        };
        break;
      }
    }
    need(headerRow >= 0 && columns && headerWidth >= 0, '无法识别详情表表头');

    const entries = [];
    for (let rowIndex = headerRow + 1; rowIndex < rows.length; rowIndex++) {
      const cells = directCells(rows[rowIndex]);
      if (!cells.length) continue;
      need(cells.length === headerWidth, '详情行列数与表头不一致');
      const texts = cells.map(textOf);
      if (!texts.some(Boolean)) continue;
      for (const cell of cells) {
        if (spanValue(cell, 'colspan') > 1 || spanValue(cell, 'rowspan') > 1) {
          fail('详情表存在合并单元格');
        }
      }
      function value(index) {
        return index >= 0 && index < texts.length ? texts[index] : '';
      }
      const name = value(columns.name);
      const teacher = normalizeInline(linesOf(cells[columns.teacher]).join(', '));
      const timeLines = linesOf(cells[columns.time]);
      const rooms = splitRoomsCell(cells[columns.room]);
      need(name, '详情行缺少课程名称');
      need(teacher, '详情行缺少教师');
      need(timeLines.length > 0, '详情行缺少上课时间');
      need(rooms.size > 0, '详情行缺少上课地点');

      const timeEntries = parseTimeEntries(timeLines);
      if (rooms.size > 1 && timeEntries.length > 1 &&
        timeEntries.some(function (entry) { return entry.roomTails.length === 0; })) {
        fail('详情行存在多个时间条目和多个地点但缺少逐条地点');
      }
      const seen = new Set();
      for (const entry of timeEntries) {
        const key = JSON.stringify([
          entry.day,
          Array.from(entry.periods).sort(function (a, b) { return a - b; })
        ]);
        need(!seen.has(key), '详情行存在重复时段');
        seen.add(key);
        for (const room of entry.roomTails) {
          need(rooms.has(room), '详情时间中的地点与地点列不一致');
        }
      }

      entries.push({
        name: name,
        teacher: teacher,
        teacherSet: splitSet(teacher),
        rooms: rooms,
        entries: timeEntries,
        code: value(columns.code),
        sequence: value(columns.sequence),
        credits: value(columns.credits),
        type: value(columns.type)
      });
    }
    need(entries.length > 0, '详情表没有课程');
    return entries;
  }

  function setIntersection(left, right) {
    const result = new Set();
    for (const value of left) if (right.has(value)) result.add(value);
    return result;
  }

  function setsEqual(left, right) {
    if (left.size !== right.size) return false;
    for (const value of left) if (!right.has(value)) return false;
    return true;
  }

  function teacherCompatible(gridEntry, detailEntry) {
    if (gridEntry.teacherSet.size && detailEntry.teacherSet.size) {
      return setsEqual(gridEntry.teacherSet, detailEntry.teacherSet);
    }
    return gridEntry.teacherSet.size > 0 || detailEntry.teacherSet.size > 0;
  }

  function contiguousRuns(sections) {
    const sorted = Array.from(sections).sort(function (a, b) { return a - b; });
    const runs = [];
    for (const section of sorted) {
      if (!runs.length || section !== runs[runs.length - 1][1] + 1) {
        runs.push([section, section]);
      } else {
        runs[runs.length - 1][1] = section;
      }
    }
    return runs;
  }

  function mergeAdjacent(courses) {
    const groups = new Map();
    for (const course of courses) {
      const key = JSON.stringify([
        course.name, course.teacher, course.position, course.day,
        course.weeks, course.courseCode || '', course.courseSequence || ''
      ]);
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(course);
    }
    const merged = [];
    for (const list of groups.values()) {
      list.sort(function (a, b) {
        return a.startSection - b.startSection || a.endSection - b.endSection;
      });
      let current = null;
      for (const course of list) {
        if (!current) {
          current = Object.assign({}, course, {weeks: course.weeks.slice()});
          continue;
        }
        if (course.startSection <= current.endSection + 1) {
          current.endSection = Math.max(current.endSection, course.endSection);
        } else {
          merged.push(current);
          current = Object.assign({}, course, {weeks: course.weeks.slice()});
        }
      }
      if (current) merged.push(current);
    }
    function compareText(left, right) {
      return left < right ? -1 : left > right ? 1 : 0;
    }
    merged.sort(function (a, b) {
      return a.day - b.day ||
        a.startSection - b.startSection ||
        a.endSection - b.endSection ||
        compareText(a.name, b.name) ||
        compareText(a.teacher, b.teacher) ||
        compareText(a.position, b.position) ||
        compareText(a.weeks.join(','), b.weeks.join(','));
    });
    return merged;
  }

  function matchAndEmit(gridEntries, detailEntries) {
    const matchedGrid = new Set();
    const emitted = [];

    for (const detail of detailEntries) {
      const rowMatches = [];
      for (const detailMeeting of detail.entries) {
        const explicitMeetingRooms = detailMeeting.roomTails.length
          ? new Set(detailMeeting.roomTails) : null;
        const allowedRooms = explicitMeetingRooms || detail.rooms;
        const candidates = [];
        for (const gridEntry of gridEntries) {
          if (matchedGrid.has(gridEntry)) continue;
          if (gridEntry.name !== detail.name) continue;
          if (!teacherCompatible(gridEntry, detail)) continue;
          if (gridEntry.day !== detailMeeting.day) continue;
          if (!allowedRooms.has(gridEntry.room)) continue;
          const overlap = setIntersection(gridEntry.periodSet, detailMeeting.periods);
          if (!overlap.size) continue;
          candidates.push({grid: gridEntry, overlap: overlap});
        }
        need(candidates.length > 0, '详情中的上课时间在网格中没有对应');

        if (explicitMeetingRooms) {
          const candidateRooms = new Set();
          for (const candidate of candidates) candidateRooms.add(candidate.grid.room);
          need(setsEqual(candidateRooms, explicitMeetingRooms),
            '详情逐条地点与网格不一致');
        }

        for (const candidate of candidates) {
          if (!candidate.grid.explicitPeriods) continue;
          for (const section of candidate.grid.explicitPeriods) {
            need(detailMeeting.periods.has(section), '网格明确节次与详情不一致');
          }
        }
        for (const section of detailMeeting.periods) {
          need(candidates.some(function (candidate) {
            return candidate.grid.periodSet.has(section);
          }), '详情节次在网格中缺少对应');
        }

        for (const candidate of candidates) {
          need(!matchedGrid.has(candidate.grid), '网格课程被重复匹配');
          matchedGrid.add(candidate.grid);
          rowMatches.push({grid: candidate.grid, overlap: candidate.overlap});
        }
      }
      need(rowMatches.length > 0, '详情课程没有对应网格');

      const matchedRooms = new Set();
      for (const match of rowMatches) matchedRooms.add(match.grid.room);
      need(setsEqual(matchedRooms, detail.rooms), '详情地点与网格不一致');

      for (let leftIndex = 0; leftIndex < rowMatches.length; leftIndex++) {
        for (let rightIndex = leftIndex + 1; rightIndex < rowMatches.length; rightIndex++) {
          const left = rowMatches[leftIndex].grid;
          const right = rowMatches[rightIndex].grid;
          if (left.day !== right.day) continue;
          if (!setIntersection(left.periodSet, right.periodSet).size) continue;
          if (!setIntersection(new Set(left.weeks), new Set(right.weeks)).size) continue;
          if (left.room !== right.room) fail('同一课程在重叠周次存在多个教室');
        }
      }

      for (const match of rowMatches) {
        for (const run of contiguousRuns(match.overlap)) {
          emitted.push({
            name: detail.name,
            teacher: detail.teacher || match.grid.teacher,
            position: match.grid.room,
            day: match.grid.day,
            startSection: run[0],
            endSection: run[1],
            weeks: match.grid.weeks.slice(),
            courseCode: detail.code,
            courseSequence: detail.sequence
          });
        }
      }
    }

    for (const gridEntry of gridEntries) {
      need(matchedGrid.has(gridEntry), '网格中存在未对应到详情的课程');
    }
    need(emitted.length > 0, '没有可导入的课程');
    return mergeAdjacent(emitted);
  }

  function parseTimetable(doc) {
    const gridEntries = parseGrid(doc);
    const detailEntries = parseDetail(doc);
    const courses = matchAndEmit(gridEntries, detailEntries);
    need(courses.length <= MAX_COURSES, '课程数量异常');
    let maxWeek = 0;
    for (const course of courses) {
      need(course.name.length <= 200, '课程名称过长');
      need(course.teacher.length <= 200, '教师名称过长');
      need(course.position && course.position.length <= 200, '教室名称无效');
      for (const week of course.weeks) maxWeek = Math.max(maxWeek, week);
    }
    need(maxWeek >= 1 && maxWeek <= 40, '课程周次超出范围');
    return {
      courses: courses,
      gridCount: gridEntries.length,
      detailCount: detailEntries.length,
      maxWeek: maxWeek
    };
  }

  function canonical(parsed) {
    const rows = parsed.courses.map(function (course) {
      return [
        course.name, course.teacher, course.position, course.day,
        course.startSection, course.endSection, course.weeks,
        course.courseCode || '', course.courseSequence || ''
      ];
    });
    rows.sort(function (left, right) {
      const a = JSON.stringify(left);
      const b = JSON.stringify(right);
      return a < b ? -1 : a > b ? 1 : 0;
    });
    return JSON.stringify({grid: parsed.gridCount, detail: parsed.detailCount, rows: rows});
  }

  function decodeHtml(buffer, contentType) {
    const match = /charset\s*=\s*["']?([\w-]+)/i.exec(contentType || '');
    if (match) {
      const charset = match[1].toLowerCase();
      need(/^(utf-8|utf8|gbk|gb2312|gb18030)$/.test(charset), '课表字符编码不受支持');
      try {
        return new TextDecoder(charset === 'utf8' ? 'utf-8' : charset, {fatal: true}).decode(buffer);
      } catch (_) {
        fail('课表页面无法按声明编码解码');
      }
    }
    try {
      return new TextDecoder('utf-8', {fatal: true}).decode(buffer);
    } catch (_) {
      try {
        return new TextDecoder('gb18030', {fatal: true}).decode(buffer);
      } catch (_) {
        fail('课表页面无法解码');
      }
    }
  }

  function parseDocument(html) {
    let doc;
    try {
      doc = new DOMParser().parseFromString(html, 'text/html');
    } catch (_) {
      fail('课表页面无法解析');
    }
    need(doc && typeof doc.querySelector === 'function', '课表页面解析结果无效');
    return doc;
  }

  function isLoginDoc(doc) {
    if (doc.querySelector('input[type="password"]') ||
      doc.querySelector('input[name="password"]') ||
      doc.querySelector('input[id="password"]')) {
      return true;
    }
    const body = doc.body ? doc.body.textContent : doc.textContent;
    const text = normalizeInline(body);
    return /统一身份认证|用户登录|登录系统|账号登录/.test(text) && !doc.querySelector('#kbtable');
  }

  function detectPagination(doc) {
    const elements = Array.from(doc.querySelectorAll('a,button,input'));
    for (const element of elements) {
      const text = normalizeInline(element.textContent);
      if (/^(下一页|下页|尾页|末页|后一页)$/.test(text)) {
        fail('课表存在分页，不能保证整表完整');
      }
      const name = normalizeInline(attr(element, 'name') || attr(element, 'id') || '');
      if (/^(page(?:Number|Index|No|Size)|totalSize|totalPage)$/i.test(name)) {
        fail('课表存在分页控件，不能保证整表完整');
      }
      const href = String(attr(element, 'href') || '');
      if (/[?&](?:page(?:Number|Index|No)|pageSize)=/i.test(href)) {
        fail('课表存在分页链接，不能保证整表完整');
      }
    }
  }

  function optionValue(option) {
    const raw = attr(option, 'value');
    return normalizeInline(raw == null ? textOf(option) : raw);
  }

  function selectedOption(select) {
    const options = Array.from(select.querySelectorAll('option'));
    if (!options.length) return null;
    const selected = options.filter(function (option) {
      return option.selected === true || hasAttr(option, 'selected');
    });
    need(selected.length <= 1, '选择框存在多个选中项');
    if (selected.length === 1) return selected[0];
    const current = String(select.value == null ? '' : select.value).trim();
    if (current) {
      const matches = options.filter(function (option) {
        return optionValue(option) === current;
      });
      if (matches.length === 1) return matches[0];
    }
    return options[0];
  }

  function readTerms(doc) {
    const select = uniqueElement(doc, 'select#xnxq01id', '服务器学期选择框');
    const options = Array.from(select.querySelectorAll('option'));
    const rows = [];
    const values = new Set();
    const labels = new Set();
    for (const option of options) {
      const value = optionValue(option);
      const label = textOf(option);
      if (!value) continue;
      need(label, '服务器学期选项缺少名称');
      need(!values.has(value), '服务器学期选项值重复');
      need(!labels.has(label), '服务器学期选项名称重复');
      values.add(value);
      labels.add(label);
      rows.push({value: value, label: label});
    }
    need(rows.length > 0, '服务器没有返回可选学期');
    return rows;
  }

  function compareTermOptions(before, after) {
    need(before.length === after.length, '学期列表在读取过程中发生变化');
    const left = new Map(before.map(function (item) { return [item.value, item.label]; }));
    for (const item of after) {
      need(left.has(item.value) && left.get(item.value) === item.label, '学期列表在读取过程中发生变化');
    }
  }

  function checkSelectedTerm(doc, chosen) {
    const select = uniqueElement(doc, 'select#xnxq01id', '服务器学期选择框');
    const terms = readTerms(doc);
    const matches = terms.filter(function (item) { return item.value === chosen.value; });
    need(matches.length === 1 && matches[0].label === chosen.label, '返回课表的学期与选择不一致');
    const selected = selectedOption(select);
    need(selected, '返回页面没有选中任何学期');
    need(optionValue(selected) === chosen.value, '返回课表的学期值与选择不一致');
    need(textOf(selected) === chosen.label, '返回课表的学期名称与选择不一致');
  }

  function checkOtherFilters(doc) {
    const selects = Array.from(doc.querySelectorAll('select'));
    for (const select of selects) {
      const id = normalizeInline(attr(select, 'id') || attr(select, 'name') || '');
      const lower = id.toLowerCase();
      if (lower === 'xnxq01id' || lower === 'zc') continue;
      const option = selectedOption(select);
      const value = option ? optionValue(option) : String(select.value == null ? '' : select.value).trim();
      need(!normalizeInline(value), '课表页面存在未清空的筛选器：' + (id || '未命名'));
    }
  }

  function checkZcEmpty(doc) {
    const zcMatches = Array.from(doc.querySelectorAll(
      'select#zc, select[name="zc"], input#zc, input[name="zc"]'
    ));
    need(zcMatches.length <= 1, '页面存在重复的周次筛选器');
    const zc = zcMatches.length ? zcMatches[0] : null;
    if (!zc) return;
    let value;
    if (tagOf(zc) === 'SELECT') {
      const option = selectedOption(zc);
      value = option ? optionValue(option) : String(zc.value == null ? '' : zc.value);
    } else {
      value = String(zc.value == null ? (attr(zc, 'value') || '') : zc.value);
    }
    need(!normalizeInline(value), '课表仍按周次筛选，无法保证整学期数据');
  }

  async function fetchHtml(method, fields) {
    const url = new URL(SCHEDULE_PATH, SCHEDULE_ORIGIN);
    if (method === 'POST') url.search = SCHEDULE_QUERY;
    const controller = new AbortController();
    const timer = setTimeout(function () { controller.abort(); }, 20000);
    try {
      const init = {
        method: method,
        credentials: 'same-origin',
        redirect: 'error',
        headers: {'Accept': 'text/html,application/xhtml+xml'},
        signal: controller.signal
      };
      if (fields !== undefined) init.body = new URLSearchParams(fields);

      let response;
      try {
        response = await fetch(url.href, init);
      } catch (_) {
        fail('课表请求失败，请检查网络、登录状态和是否被重定向');
      }
      need(response, '课表请求没有响应');
      need(response.ok === true, '课表请求失败，请检查登录状态');
      need(Number(response.status) === 200, '课表请求返回了异常状态');
      need(!response.redirected, '课表请求发生了跳转，请重新登录');

      let finalUrl = url;
      if (response.url) {
        try {
          finalUrl = new URL(response.url, url.href);
        } catch (_) {
          fail('课表请求返回了无效地址');
        }
      }
      need(finalUrl.origin === SCHEDULE_ORIGIN && !finalUrl.username && !finalUrl.password &&
        finalUrl.pathname === SCHEDULE_PATH,
        '课表请求跳转到了其他页面');

      const contentType = response.headers && typeof response.headers.get === 'function'
        ? String(response.headers.get('Content-Type') || '') : '';
      if (contentType) {
        need(/text\/html|application\/xhtml\+xml/i.test(contentType), '课表返回的不是 HTML 页面');
      }
      need(typeof response.arrayBuffer === 'function', '课表响应无法读取');
      const buffer = await response.arrayBuffer();
      need(buffer && buffer.byteLength > 0 && buffer.byteLength <= MAX_HTML, '课表页面大小异常');
      const doc = parseDocument(decodeHtml(buffer, contentType));
      if (isLoginDoc(doc)) fail('登录状态已失效，请重新登录后重试');
      need(doc.querySelectorAll('#kbtable').length === 1,
        '课表页面缺少或重复 #kbtable，可能未登录或页面结构已变化');
      need(doc.querySelectorAll('#dataList').length <= 1,
        '课表页面存在重复 #dataList，不能确定唯一详情表');
      return doc;
    } finally {
      clearTimeout(timer);
    }
  }

  function checkInitialPage(doc) {
    detectPagination(doc);
    checkOtherFilters(doc);
  }

  function checkSelectedPage(doc, chosen) {
    checkSelectedTerm(doc, chosen);
    checkZcEmpty(doc);
    checkOtherFilters(doc);
    detectPagination(doc);
  }

  async function fetchInitial() {
    const doc = await fetchHtml('GET');
    checkInitialPage(doc);
    return doc;
  }

  async function fetchSelected(chosen) {
    const doc = await fetchHtml('POST', {xnxq01id: chosen.value, zc: ''});
    checkSelectedPage(doc, chosen);
    return doc;
  }

  async function askTotalWeeks(maxWeek) {
    need(K.api && typeof K.api.showPrompt === 'function', '当前宿主不支持输入学期总周数');
    const value = await K.api.showPrompt(
      '学期总周数',
      '请按教务校历填写本学期总周数（1-40），且不少于已读取课程的最大周次 ' + maxWeek,
      ''
    );
    if (value === null || value === undefined) fail('已取消导入');
    const text = String(value).trim();
    need(/^\d+$/.test(text), '学期总周数必须是 1-40 的整数');
    const total = Number(text);
    need(Number.isSafeInteger(total) && total >= 1 && total <= 40, '学期总周数必须是 1-40 的整数');
    need(total >= maxWeek, '学期总周数不能小于课程最大周次');
    return total;
  }

  function validateEntry() {
    need(K.adapterId === 'ext_njust_personal', '适配器入口不匹配');
    K.host(['bkjw.njust.edu.cn']);
    need(location.protocol === 'https:' &&
      location.hostname === 'bkjw.njust.edu.cn' &&
      !location.port &&
      !location.username &&
      !location.password &&
      location.pathname === SCHEDULE_PATH,
      '请先进入南京理工大学 HTTPS 教务课表页面并完成登录');

  }

  async function main() {
    validateEntry();

    const initialDoc = await fetchInitial();
    const terms = readTerms(initialDoc);
    const chosen = await K.choose('选择教务学期', terms, 'value', 'label');
    need(chosen && terms.some(function (item) {
      return item.value === chosen.value && item.label === chosen.label;
    }), '学期选择无效');

    const firstDoc = await fetchSelected(chosen);
    const firstParsed = parseTimetable(firstDoc);

    // The week prompt is a user interaction. It must happen before the final
    // re-read so any change made while the prompt is open is observed.
    const totalWeeks = await askTotalWeeks(firstParsed.maxWeek);

    validateEntry();
    const originalAgain = await fetchInitial();
    const termsAgain = readTerms(originalAgain);
    compareTermOptions(terms, termsAgain);
    need(termsAgain.some(function (item) {
      return item.value === chosen.value && item.label === chosen.label;
    }), '所选学期在读取过程中消失');

    const secondDoc = await fetchSelected(chosen);
    const secondParsed = parseTimetable(secondDoc);
    need(canonical(firstParsed) === canonical(secondParsed), '重新读取的课表数据不一致');

    // Await the bridge: a rejected save must reach K.run's catch instead of
    // becoming an unhandled rejection after main() has already returned.
    validateEntry();
    await K.finish(secondParsed.courses, 0, totalWeeks);
  }

  window.kagendaNjustParsers = {
    parseTimetable: parseTimetable,
    parseGrid: parseGrid,
    parseDetail: parseDetail,
    matchAndEmit: matchAndEmit,
    splitWeekPeriodField: splitWeekPeriodField,
    parsePeriods: parsePeriods,
    parseRowPeriods: parseRowPeriods,
    parseTimeEntries: parseTimeEntries,
    parseDay: parseDay,
    canonical: canonical
  };

  K.run(main);
})();
