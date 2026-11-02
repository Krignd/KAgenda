// Independent KAgenda importer for NUAA EAMS (adapter id: ext_nuaa_eams).
//
// Protocol facts were independently re-derived from shiywhh/i-niuaa at
// 2acccb997836be11c220bf3e91e25a249fdf0d08 (MIT; see sources/LICENSE). No
// source code is copied here. The upstream course-selection / Xuanke_v2 code
// and the Sked-derived period-time editor, presets, and repair values are
// intentionally excluded. Protocol source, fixed file hashes, dates and MIT
// notice: academic/school-provenance.json and licenses/jiangsu-school-bundle.txt.
// DeepSeek prepared the independent draft; KAgenda reviewed and corrected
// completeness, explicit calendar length and final full-semester snapshots.
// Synthetic validation is separate from real school account validation.
//
// Boundary summary:
//   - exactly https://aao-eas.nuaa.edu.cn/eams/courseTableForStd.action;
//   - GET form -> IDs and semesterBar control -> POST semester list -> user
//     explicitly selects -> POST complete term table -> static parsing;
//   - static roster audit; unmatched entries reject the whole batch;
//   - explicit total-weeks prompt; native preview totalWeeks is not editable;
//   - no eval / Function / DOMParser script execution;
//   - no course/period/config write before the final identity + semester check;
//   - no account, cookie, internal ID, or response body is logged or stored.

(function () {
  'use strict';

  const ADAPTER_ID = 'ext_nuaa_eams';
  const HOST = 'aao-eas.nuaa.edu.cn';
  const ORIGIN = 'https://' + HOST;
  const FORM_PATH = '/eams/courseTableForStd.action';
  const DATA_PATH = '/eams/dataQuery.action';
  const TABLE_PATH = '/eams/courseTableForStd!courseTable.action';
  const REQUEST_TIMEOUT_MS = 20000;
  const LIMITS = Object.freeze({
    form: 1500000,
    semesters: 1000000,
    table: 3000000
  });

  // Evidence for these positions: upstream eams_client.dart (2026-09 fixture)
  // reads TaskActivity argument 3 as name, 6 as room, and 7 as the week bitmap,
  // and skips constructors with fewer than 14 arguments. Extra arguments after
  // that evidenced prefix are tolerated only up to a safety bound; the three
  // known positions must still be complete literals. See draft-review.md.
  const MIN_ACTIVITY_ARGS = 14;
  const MAX_ACTIVITY_ARGS = 32;
  const ARG_NAME = 3;
  const ARG_ROOM = 6;
  const ARG_WEEK_BITMAP = 7;
  const REQUIRED_UNIT_COUNT = 13;
  const MAX_ROSTER_ENTRIES = 500;

  const k = window.kagendaSchool;
  if (!k) throw new Error('KAgenda 学校桥接未初始化');

  function fail(message) {
    throw new Error(message);
  }

  function requireValue(ok, message) {
    if (!ok) fail(message);
  }

  function isIdentifierStart(ch) {
    return /[A-Za-z_$]/.test(ch);
  }

  function isIdentifierPart(ch) {
    return /[A-Za-z0-9_$]/.test(ch);
  }

  function readIdentifierAt(text, index, end) {
    if (index >= end || !isIdentifierStart(text[index])) return null;
    let i = index + 1;
    while (i < end && isIdentifierPart(text[i])) i++;
    return {word: text.slice(index, i), start: index, end: i};
  }

  function skipTrivia(text, index, end) {
    let i = index;
    while (i < end) {
      const ch = text[i];
      if (/\s/.test(ch)) {
        i++;
        continue;
      }
      if (ch === '/' && text[i + 1] === '/') {
        i += 2;
        while (i < end && text[i] !== '\n' && text[i] !== '\r') i++;
        continue;
      }
      if (ch === '/' && text[i + 1] === '*') {
        const close = text.indexOf('*/', i + 2);
        if (close < 0 || close + 2 > end) fail('页面脚本注释不完整');
        i = close + 2;
        continue;
      }
      break;
    }
    return i;
  }

  // Static string reader. It decodes JS/JSON escapes but never executes code.
  function readJsStringAt(text, index, end) {
    const quote = text[index];
    if (quote !== '"' && quote !== "'") fail('字符串起始引号无效');
    let i = index + 1;
    let value = '';
    while (i < end) {
      const ch = text[i];
      if (ch === quote) return {value: value, next: i + 1};
      if (ch === '\\') {
        i++;
        if (i >= end) fail('字符串转义不完整');
        const esc = text[i];
        if (esc === '\r') {
          i++;
          if (i < end && text[i] === '\n') i++;
          continue;
        }
        if (esc === '\n' || esc === '\u2028' || esc === '\u2029') {
          i++;
          continue;
        }
        if (esc === 'x') {
          const hex = text.slice(i + 1, i + 3);
          requireValue(/^[0-9a-fA-F]{2}$/.test(hex), '字符串十六进制转义无效');
          value += String.fromCharCode(parseInt(hex, 16));
          i += 3;
          continue;
        }
        if (esc === 'u') {
          if (text[i + 1] === '{') {
            const close = text.indexOf('}', i + 2);
            requireValue(close >= 0 && close < end, '字符串 Unicode 转义不完整');
            const hex = text.slice(i + 2, close);
            requireValue(/^[0-9a-fA-F]{1,6}$/.test(hex), '字符串 Unicode 转义无效');
            const codePoint = parseInt(hex, 16);
            requireValue(codePoint <= 0x10ffff &&
              !(codePoint >= 0xd800 && codePoint <= 0xdfff), '字符串 Unicode 值无效');
            value += String.fromCodePoint(codePoint);
            i = close + 1;
            continue;
          }
          const hex = text.slice(i + 1, i + 5);
          requireValue(/^[0-9a-fA-F]{4}$/.test(hex), '字符串 Unicode 转义无效');
          value += String.fromCharCode(parseInt(hex, 16));
          i += 5;
          continue;
        }
        if (esc >= '0' && esc <= '7') {
          if (esc === '0' && !(text[i + 1] >= '0' && text[i + 1] <= '9')) {
            value += '\0';
            i++;
            continue;
          }
          fail('不支持八进制转义');
        }
        switch (esc) {
          case 'b': value += '\b'; i++; break;
          case 'f': value += '\f'; i++; break;
          case 'n': value += '\n'; i++; break;
          case 'r': value += '\r'; i++; break;
          case 't': value += '\t'; i++; break;
          case 'v': value += '\v'; i++; break;
          case '\\': value += '\\'; i++; break;
          case "'": value += "'"; i++; break;
          case '"': value += '"'; i++; break;
          default: value += esc; i++; break;
        }
        continue;
      }
      if (ch === '\n' || ch === '\r' || ch === '\u2028' || ch === '\u2029') {
        fail('字符串中出现未转义换行');
      }
      value += ch;
      i++;
    }
    fail('字符串未闭合');
  }

  function parseStringLiteral(raw) {
    const end = raw.length;
    let i = skipTrivia(raw, 0, end);
    requireValue(i < end && (raw[i] === '"' || raw[i] === "'"),
      '关键字段不是字符串字面量');
    const read = readJsStringAt(raw, i, end);
    i = skipTrivia(raw, read.next, end);
    requireValue(i === end, '关键字段包含无法识别的表达式');
    return read.value;
  }

  function isRegexStart(text, index, floor) {
    let i = index - 1;
    while (i >= floor && /\s/.test(text[i])) i--;
    if (i < floor) return true;
    return '([{=,:;!&|?+-*%^~<>'.indexOf(text[i]) >= 0;
  }

  function skipRegex(text, index, end) {
    let i = index + 1;
    let inClass = false;
    while (i < end) {
      const ch = text[i];
      if (ch === '\\') {
        i += 2;
        continue;
      }
      if (ch === '\n' || ch === '\r' || ch === '\u2028' || ch === '\u2029') {
        fail('页面脚本正则表达式未闭合');
      }
      if (ch === '[') inClass = true;
      else if (ch === ']') inClass = false;
      else if (ch === '/' && !inClass) {
        i++;
        while (i < end && /[A-Za-z]/.test(text[i])) i++;
        return i;
      }
      i++;
    }
    fail('页面脚本正则表达式未闭合');
  }

  function matchingClose(text, openIndex, end, context) {
    const pair = {'(': ')', '[': ']', '{': '}'}[text[openIndex]];
    requireValue(!!pair, context + '起始括号无效');
    const stack = [pair];
    let i = openIndex + 1;
    while (i < end) {
      i = skipTrivia(text, i, end);
      if (i >= end) break;
      const ch = text[i];
      if (ch === '"' || ch === "'") {
        const read = readJsStringAt(text, i, end);
        i = read.next;
        continue;
      }
      if (ch === '`') fail(context + '包含不支持的模板字符串');
      if (ch === '/' && isRegexStart(text, i, openIndex)) {
        i = skipRegex(text, i, end);
        continue;
      }
      if (ch === '(' || ch === '[' || ch === '{') {
        stack.push(ch === '(' ? ')' : ch === '[' ? ']' : '}');
        i++;
        continue;
      }
      if (ch === ')' || ch === ']' || ch === '}') {
        const expected = stack.pop();
        if (expected !== ch) fail(context + '括号不匹配');
        if (stack.length === 0) return i;
        i++;
        continue;
      }
      i++;
    }
    fail(context + '括号未闭合');
  }

  function splitTopLevel(raw, separator, context) {
    if (raw.trim() === '') return [];
    const parts = [];
    const stack = [];
    const end = raw.length;
    let start = 0;
    let i = 0;
    while (i < end) {
      i = skipTrivia(raw, i, end);
      if (i >= end) break;
      const ch = raw[i];
      if (ch === '"' || ch === "'") {
        const read = readJsStringAt(raw, i, end);
        i = read.next;
        continue;
      }
      if (ch === '`') fail(context + '包含不支持的模板字符串');
      if (ch === '/' && isRegexStart(raw, i, 0)) {
        i = skipRegex(raw, i, end);
        continue;
      }
      if (ch === '(' || ch === '[' || ch === '{') {
        stack.push(ch === '(' ? ')' : ch === '[' ? ']' : '}');
        i++;
        continue;
      }
      if (ch === ')' || ch === ']' || ch === '}') {
        const expected = stack.pop();
        if (expected !== ch) fail(context + '括号不匹配');
        i++;
        continue;
      }
      if (ch === separator && stack.length === 0) {
        parts.push(raw.slice(start, i));
        start = i + 1;
        i++;
        continue;
      }
      i++;
    }
    requireValue(stack.length === 0, context + '括号未闭合');
    parts.push(raw.slice(start));
    for (const part of parts) {
      requireValue(part.trim() !== '', context + '存在空参数');
    }
    return parts;
  }

  function scanCode(text, start, end, visit) {
    let i = start;
    while (i < end) {
      i = skipTrivia(text, i, end);
      if (i >= end) break;
      const ch = text[i];
      if (ch === '"' || ch === "'") {
        const read = readJsStringAt(text, i, end);
        i = read.next;
        continue;
      }
      if (ch === '`') fail('页面脚本包含不支持的模板字符串');
      if (ch === '/' && isRegexStart(text, i, start)) {
        i = skipRegex(text, i, end);
        continue;
      }
      if (isIdentifierStart(ch)) {
        const id = readIdentifierAt(text, i, end);
        visit(id.word, id.start, id.end);
        i = id.end;
        continue;
      }
      i++;
    }
  }

  function findVariableStarts(text, variable, start, end) {
    const found = [];
    scanCode(text, start, end, function (word, wordStart, wordEnd) {
      if (word !== 'var') return;
      let i = skipTrivia(text, wordEnd, end);
      const id = readIdentifierAt(text, i, end);
      if (!id || id.word !== variable) return;
      i = skipTrivia(text, id.end, end);
      if (text[i] === '=' && text[i + 1] !== '=' && text[i + 1] !== '>') {
        found.push({start: wordStart, afterEquals: i + 1});
      }
    });
    return found;
  }

  function readSmallInteger(text, index, end) {
    const match = /^(0|[1-9][0-9]{0,3})/.exec(text.slice(index, end));
    if (!match) return null;
    return {value: Number(match[1]), end: index + match[0].length};
  }

  function requireUnitCount(text, start, end) {
    const values = [];
    scanCode(text, start, end, function (word, wordStart, wordEnd) {
      if (word !== 'unitCount') return;
      let before = wordStart - 1;
      while (before >= start && /\s/.test(text[before])) before--;
      if (before >= start && text[before] === '.') return;
      let i = skipTrivia(text, wordEnd, end);
      if (text[i] !== '=' || text[i + 1] === '=' || text[i + 1] === '>') return;
      i = skipTrivia(text, i + 1, end);
      const number = readSmallInteger(text, i, end);
      if (!number) {
        values.push(null);
        return;
      }
      i = skipTrivia(text, number.end, end);
      values.push(text[i] === ';' ? number.value : null);
    });
    requireValue(values.length === 1 && values[0] === REQUIRED_UNIT_COUNT,
      '页面节次网格不是受支持的 13 单元，已拒绝导入');
  }

  function findConstructors(text, start, end) {
    const found = [];
    scanCode(text, start, end, function (word, wordStart, wordEnd) {
      if (word !== 'new') return;
      let i = skipTrivia(text, wordEnd, end);
      const id = readIdentifierAt(text, i, end);
      if (!id || id.word !== 'TaskActivity') return;
      i = skipTrivia(text, id.end, end);
      requireValue(text[i] === '(', 'TaskActivity 构造缺少参数列表');
      found.push({start: wordStart, open: i});
    });
    return found;
  }

  function findTeacherBlocks(text, start, end) {
    // Upstream evidence is the literal declaration shape `var teachers = [`.
    return findVariableStarts(text, 'teachers', start, end);
  }

  function parseTeacherNames(arrayBody) {
    const items = splitTopLevel(arrayBody, ',', '教师列表');
    const names = [];
    for (const item of items) {
      let i = skipTrivia(item, 0, item.length);
      requireValue(item[i] === '{', '教师条目不是对象字面量');
      const braceOpen = i;
      const close = matchingClose(item, i, item.length, '教师条目');
      i = skipTrivia(item, close + 1, item.length);
      requireValue(i === item.length, '教师条目包含无法识别的尾部内容');
      const body = item.slice(braceOpen + 1, close);
      const fields = splitTopLevel(body, ',', '教师字段');
      let hasName = false;
      for (const field of fields) {
        let p = skipTrivia(field, 0, field.length);
        let key;
        if (field[p] === '"' || field[p] === "'") {
          const keyRead = readJsStringAt(field, p, field.length);
          key = keyRead.value;
          p = keyRead.next;
        } else {
          const keyId = readIdentifierAt(field, p, field.length);
          requireValue(!!keyId, '教师字段名无效');
          key = keyId.word;
          p = keyId.end;
        }
        p = skipTrivia(field, p, field.length);
        requireValue(field[p] === ':', '教师字段缺少冒号');
        if (key === 'name') {
          requireValue(!hasName, '教师 name 字段重复');
          names.push(parseStringLiteral(field.slice(p + 1)));
          hasName = true;
        }
      }
      requireValue(hasName, '教师条目缺少 name 字段');
    }
    return names.map(function (name) { return name.trim(); })
      .filter(function (name) { return name !== ''; })
      .join(',');
  }

  function parseWeekBitmap(value) {
    const bitmap = String(value);
    requireValue(/^0[01]{1,40}$/.test(bitmap) && bitmap.indexOf('1') >= 0,
      '周次位图无效');
    const weeks = [];
    for (let i = 1; i < bitmap.length; i++) {
      if (bitmap[i] === '1') weeks.push(i);
    }
    requireValue(weeks.length > 0, '周次位图为空');
    return k.weeks(weeks);
  }

  function unitToPeriod(unit) {
    // models.dart: unit is 0-based and 4/5 are lunch placeholders.
    // timetable_ics.dart: 0..3 -> periods 1..4, 6..12 -> periods 5..11.
    if (unit >= 0 && unit <= 3) return unit + 1;
    if (unit >= 6 && unit <= 12) return unit - 1;
    fail('出现午休占位或越界单元，已拒绝整批课表');
  }

  function parseActivityArguments(argsRaw, teacher) {
    const args = splitTopLevel(argsRaw, ',', 'TaskActivity 参数');
    requireValue(args.length >= MIN_ACTIVITY_ARGS &&
      args.length <= MAX_ACTIVITY_ARGS,
      'TaskActivity 参数数量超出受支持范围');
    const name = parseStringLiteral(args[ARG_NAME]).trim();
    const room = parseStringLiteral(args[ARG_ROOM]).trim();
    const bitmap = parseStringLiteral(args[ARG_WEEK_BITMAP]);
    requireValue(name.length > 0 && name.length <= 200, '课程名称无效');
    requireValue(room.length <= 200, '教室名称过长');
    return {
      name: name,
      teacher: teacher,
      room: room,
      weeks: parseWeekBitmap(bitmap)
    };
  }

  function parseSlotExpression(text, index) {
    const end = text.length;
    let i = skipTrivia(text, index, end);
    const day = readSmallInteger(text, i, end);
    requireValue(!!day, '课表格子坐标表达式无效');
    i = skipTrivia(text, day.end, end);
    requireValue(text[i] === '*', '课表格子坐标缺少 unitCount 乘法');
    i = skipTrivia(text, i + 1, end);
    const unitCount = readIdentifierAt(text, i, end);
    requireValue(!!unitCount && unitCount.word === 'unitCount',
      '课表格子坐标未使用 unitCount');
    i = skipTrivia(text, unitCount.end, end);
    requireValue(text[i] === '+', '课表格子坐标缺少加法');
    i = skipTrivia(text, i + 1, end);
    const unit = readSmallInteger(text, i, end);
    requireValue(!!unit, '课表格子坐标缺少单元号');
    i = skipTrivia(text, unit.end, end);
    requireValue(text[i] === ';', '课表格子坐标不是完整赋值语句');
    return {day: day.value, unit: unit.value};
  }

  function collectSlotAssignments(text, start, end) {
    const slots = [];
    scanCode(text, start, end, function (word, wordStart, wordEnd) {
      if (word !== 'index') return;
      let i = skipTrivia(text, wordEnd, end);
      if (text[i] !== '=' || text[i + 1] === '=' || text[i + 1] === '>') return;
      slots.push(parseSlotExpression(text, i + 1));
    });
    return slots;
  }

  function readSlotAssignments(tail) {
    const assignments = collectSlotAssignments(tail, 0, tail.length);
    requireValue(assignments.length > 0, 'TaskActivity 缺少排课时段块');
    const seen = new Set();
    const slots = assignments.filter(function (slot) {
      const key = slot.day + ':' + slot.unit;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
    return {slots: slots, count: assignments.length};
  }

  function coursesFromSlots(activity, slots) {
    const byDay = new Map();
    for (const slot of slots) {
      requireValue(slot.day >= 0 && slot.day <= 6, '课表星期坐标越界');
      requireValue(slot.unit >= 0 && slot.unit <= 12, '课表单元坐标越界');
      requireValue(slot.unit !== 4 && slot.unit !== 5,
        '出现午休占位单元，已拒绝整批课表');
      if (!byDay.has(slot.day)) byDay.set(slot.day, new Set());
      byDay.get(slot.day).add(slot.unit);
    }

    const courses = [];
    const days = Array.from(byDay.keys()).sort(function (a, b) { return a - b; });
    for (const day of days) {
      const units = Array.from(byDay.get(day)).sort(function (a, b) { return a - b; });
      let runStart = units[0];
      let previous = units[0];
      function emitRun(first, last) {
        const firstPeriod = unitToPeriod(first);
        const lastPeriod = unitToPeriod(last);
        requireValue(firstPeriod <= lastPeriod, '课表节次换算无效');
        courses.push(k.course(activity.name, activity.teacher, activity.room,
          day + 1, firstPeriod, lastPeriod, activity.weeks));
      }
      for (let i = 1; i < units.length; i++) {
        if (units[i] === previous + 1) {
          previous = units[i];
          continue;
        }
        emitRun(runStart, previous);
        runStart = units[i];
        previous = units[i];
      }
      emitRun(runStart, previous);
    }
    return courses;
  }

  function assertNotLoginOrError(text) {
    requireValue(typeof text === 'string', '课表响应格式无效');
    if (/casLoginForm/i.test(text)) fail('登录已过期，请重新登录');
    if (/<form\b[^>]*\baction\s*=\s*["'][^"']*authserver\.nuaa\.edu\.cn/i.test(text)) {
      fail('登录已过期，请重新登录');
    }
    if (/authserver\.nuaa\.edu\.cn/i.test(text) &&
      /\bname\s*=\s*["']password["']/i.test(text)) {
      fail('登录已过期，请重新登录');
    }
    if (/<title[^>]*>\s*(?:统一身份认证|用户登录|登录)\s*<\/title>/i.test(text)) {
      fail('登录已过期，请重新登录');
    }
    if (/error\.courseTable\.[A-Za-z0-9_.-]+/i.test(text)) {
      fail('教务系统拒绝了课表请求，请重新登录后重试');
    }
  }

  function inlineScripts(html) {
    const scripts = [];
    const pattern = /<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi;
    let match;
    while ((match = pattern.exec(html)) !== null) {
      if (/(?:^|\s)src\s*=/i.test(match[1])) continue;
      scripts.push(match[2]);
    }
    return scripts;
  }

  function decodeHtmlEntities(value) {
    return String(value).replace(
      /&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);/gi,
      function (entity, body) {
        const lower = body.toLowerCase();
        if (lower === 'amp') return '&';
        if (lower === 'lt') return '<';
        if (lower === 'gt') return '>';
        if (lower === 'quot') return '"';
        if (lower === 'apos') return "'";
        if (lower === 'nbsp') return ' ';
        if (lower[0] === '#') {
          const codePoint = lower[1] === 'x'
            ? parseInt(lower.slice(2), 16)
            : parseInt(lower.slice(1), 10);
          if (Number.isInteger(codePoint) && codePoint >= 0 &&
            codePoint <= 0x10ffff &&
            !(codePoint >= 0xd800 && codePoint <= 0xdfff)) {
            return String.fromCodePoint(codePoint);
          }
        }
        return entity;
      });
  }

  function normalizeLooseText(value) {
    return String(value).replace(/\s+/g, ' ').trim();
  }

  function normalizeTeacherValue(value) {
    return normalizeLooseText(value)
      .replace(/[，、；;]/g, ',')
      .replace(/\s*,\s*/g, ',');
  }

  function normalizeRosterCell(value) {
    return normalizeLooseText(decodeHtmlEntities(value));
  }

  function readSpanAttribute(rawTag, name) {
    const valuePattern = new RegExp('(?:^|\\s)' + name +
      '\\s*=\\s*(?:"([^"]*)"|\'([^\']*)\'|([^\\s>]+))', 'i');
    const presentPattern = new RegExp('(?:^|\\s)' + name +
      '(?=\\s|=|/|>)', 'i');
    const match = valuePattern.exec(rawTag);
    if (!match) return {value: 1, ok: !presentPattern.test(rawTag)};
    const rawValue = match[1] !== undefined ? match[1]
      : match[2] !== undefined ? match[2] : (match[3] || '');
    const text = rawValue.trim();
    if (!/^\d+$/.test(text)) return {value: 1, ok: false};
    const value = Number(text);
    return {value: value, ok: Number.isSafeInteger(value) && value >= 1};
  }

  // A deliberately small, static HTML table model. It never executes scripts
  // and does not depend on a browser DOM implementation. Script/style bodies and
  // comments are skipped. Nested tables remain separate table models; the parent
  // table is marked so only a selected roster table can reject nesting/spans.
  function extractHtmlTables(html) {
    const tables = [];
    const stack = [];
    const tokenPattern = /<!--[\s\S]*?-->|<(script|style)\b[^>]*>[\s\S]*?<\/\1\s*>|<\s*(\/?)\s*([A-Za-z][A-Za-z0-9:_-]*)(?:\s[^>]*)?\s*\/?>/gi;
    let last = 0;
    let match;

    function context() {
      return stack.length > 0 ? stack[stack.length - 1] : null;
    }

    function appendText(raw) {
      const ctx = context();
      if (ctx && ctx.cell) ctx.cell.text += raw;
    }

    function finishCell(ctx) {
      if (!ctx || !ctx.cell) return;
      if (ctx.row) ctx.row.cells.push(ctx.cell);
      ctx.cell = null;
    }

    function finishRow(ctx) {
      if (!ctx) return;
      finishCell(ctx);
      ctx.row = null;
    }

    while ((match = tokenPattern.exec(html)) !== null) {
      appendText(html.slice(last, match.index));
      last = tokenPattern.lastIndex;
      if (match[0].slice(0, 4) === '<!--') continue;
      if (match[1]) continue;

      const closing = match[2] === '/';
      const name = match[3].toLowerCase();
      if ((name === 'script' || name === 'style') && !closing) {
        fail('课表页面脚本标签结构异常');
      }
      if (name === 'table') {
        if (closing) {
          if (stack.length === 0) continue;
          finishRow(stack.pop());
        } else {
          if (stack.length > 0) stack[stack.length - 1].table.hasNested = true;
          const ctx = {rows: [], row: null, cell: null, table: null};
          const table = {rows: ctx.rows, hasNested: false};
          ctx.table = table;
          tables.push(table);
          stack.push(ctx);
        }
        continue;
      }

      const ctx = context();
      if (!ctx) continue;
      if (name === 'tr') {
        if (closing) {
          finishRow(ctx);
        } else {
          if (ctx.row) finishRow(ctx);
          ctx.row = {cells: []};
          ctx.rows.push(ctx.row);
        }
        continue;
      }
      if (name === 'td' || name === 'th') {
        if (closing) {
          finishCell(ctx);
        } else {
          if (!ctx.row) fail('课程列表表格行结构异常');
          if (ctx.cell) finishCell(ctx);
          const colspan = readSpanAttribute(match[0], 'colspan');
          const rowspan = readSpanAttribute(match[0], 'rowspan');
          ctx.cell = {
            text: '',
            colspan: colspan.value,
            rowspan: rowspan.value,
            spanOk: colspan.ok && rowspan.ok
          };
        }
        continue;
      }
      if (name === 'br' && !closing) {
        if (ctx.cell) ctx.cell.text += ' ';
      }
    }
    appendText(html.slice(last));
    requireValue(stack.length === 0, '课程列表表格结构不完整');
    return tables;
  }

  const ROSTER_LABELS = Object.freeze({
    sequence: '课程序号',
    code: '课程代码',
    name: '课程名称',
    teacher: '教师',
    teachingClass: '教学班'
  });
  const OPTIONAL_ROSTER_KEYS = ['sequence', 'teacher', 'teachingClass'];

  function rosterHeaderIndexes(cells) {
    const hits = {};
    for (const key of Object.keys(ROSTER_LABELS)) {
      const label = ROSTER_LABELS[key];
      const found = [];
      for (let i = 0; i < cells.length; i++) {
        const compact = cells[i].replace(/\s+/g, '');
        if (compact.indexOf(label) >= 0) found.push(i);
      }
      hits[key] = found;
    }
    if (hits.code.length === 0 || hits.name.length === 0) return null;
    if (hits.code.length !== 1 || hits.name.length !== 1) {
      fail('课程列表列头重复或混淆');
    }
    const indexes = {code: hits.code[0], name: hits.name[0]};
    const used = new Set([indexes.code, indexes.name]);
    requireValue(used.size === 2, '课程列表列头重复或混淆');
    for (const key of OPTIONAL_ROSTER_KEYS) {
      const found = hits[key];
      if (found.length > 1) fail('课程列表列头重复或混淆');
      if (found.length === 1) {
        requireValue(!used.has(found[0]), '课程列表列头重复或混淆');
        used.add(found[0]);
        indexes[key] = found[0];
      }
    }
    return indexes;
  }

  function validateRosterTable(table) {
    requireValue(!table.hasNested, '课程列表表格包含嵌套表格，无法可靠审计');
    for (const row of table.rows) {
      for (const cell of row.cells) {
        requireValue(cell.spanOk && cell.colspan === 1 && cell.rowspan === 1,
          '课程列表表格包含合并单元格，无法可靠审计');
      }
    }
  }

  function rosterTupleKey(name, teacher) {
    return name + '\u0001' + teacher;
  }

  function parseRoster(html) {
    const tables = extractHtmlTables(html);
    const headers = [];
    for (const table of tables) {
      for (let r = 0; r < table.rows.length; r++) {
        const cells = table.rows[r].cells.map(function (cell) {
          return normalizeRosterCell(cell.text);
        });
        const indexes = rosterHeaderIndexes(cells);
        if (indexes) headers.push({table: table, row: r, indexes: indexes});
      }
    }
    requireValue(headers.length === 1,
      headers.length === 0 ? '课程列表结构缺失' : '课程列表表头不唯一');
    const header = headers[0];
    validateRosterTable(header.table);
    const indexes = header.indexes;
    const headerCellCount = header.table.rows[header.row].cells.length;
    const hasTeacher = indexes.teacher !== undefined;
    const entries = [];
    const seenFull = new Set();
    const seenMatch = new Set();

    for (let r = header.row + 1; r < header.table.rows.length; r++) {
      const cells = header.table.rows[r].cells.map(function (cell) {
        return normalizeRosterCell(cell.text);
      });
      if (cells.length === 0 || cells.every(function (cell) { return cell === ''; })) {
        continue;
      }
      requireValue(cells.length === headerCellCount,
        '课程列表行与表头无法可靠对齐');
      const code = cells[indexes.code];
      const name = cells[indexes.name];
      const sequence = indexes.sequence === undefined ? ''
        : cells[indexes.sequence];
      const teacher = indexes.teacher === undefined ? ''
        : normalizeTeacherValue(cells[indexes.teacher]);
      const teachingClass = indexes.teachingClass === undefined ? ''
        : cells[indexes.teachingClass];
      requireValue(code !== '' && name !== '',
        '课程列表存在缺少关键字段的行');
      requireValue(sequence.length <= 64 && code.length <= 64 &&
        name.length <= 200 && teacher.length <= 400 &&
        teachingClass.length <= 200, '课程列表字段长度异常');

      const fullKey = [sequence, code, name, teacher, teachingClass].join('\u0001');
      const matchKey = hasTeacher ? rosterTupleKey(name, teacher) : name;
      requireValue(!seenFull.has(fullKey),
        '课程列表存在重复或无法区分的班级身份');
      requireValue(!seenMatch.has(matchKey),
        hasTeacher ? '课程列表存在无法区分的同名/同教师条目'
          : '缺少教师列且课程列表存在同名条目，无法唯一关联');
      seenFull.add(fullKey);
      seenMatch.add(matchKey);
      entries.push({
        sequence: sequence,
        code: code,
        name: name,
        teacher: teacher,
        teachingClass: teachingClass,
        matchKey: matchKey
      });
      requireValue(entries.length <= MAX_ROSTER_ENTRIES, '课程列表条目过多');
    }
    requireValue(entries.length > 0, '课程列表没有可审计条目');
    return {entries: entries, count: entries.length, hasTeacher: hasTeacher};
  }

  function auditRoster(courses, roster) {
    const rosterByMatch = new Map();
    for (const entry of roster.entries) rosterByMatch.set(entry.matchKey, entry);
    const scheduled = new Set();
    for (const course of courses) {
      const name = normalizeLooseText(course.name);
      const key = roster.hasTeacher
        ? rosterTupleKey(name, normalizeTeacherValue(course.teacher))
        : name;
      requireValue(rosterByMatch.has(key),
        '已排课课程未出现在课程列表中，已拒绝导入');
      scheduled.add(key);
    }
    const unmatched = roster.entries.filter(function (entry) {
      return !scheduled.has(entry.matchKey);
    });
    return {
      rosterCount: roster.count,
      hasTeacher: roster.hasTeacher,
      unmatched: unmatched.map(function (entry) {
        return {name: entry.name, teacher: entry.teacher};
      })
    };
  }

  function describeUnmatched(entries, hasTeacher) {
    return entries.map(function (entry) {
      return hasTeacher && entry.teacher !== ''
        ? entry.name + '（' + entry.teacher + '）'
        : entry.name;
    }).join('、');
  }

  function parseTaskTable(html) {
    requireValue(typeof html === 'string' && html.length > 0 && html.length <= 4000000,
      '课表页面格式无效');
    assertNotLoginOrError(html);
    const roster = parseRoster(html);
    const scripts = inlineScripts(html);
    requireValue(scripts.length > 0, '课表页面没有可解析的内联脚本');
    const code = scripts.join('\n');

    const tableVars = findVariableStarts(code, 'table0', 0, code.length);
    requireValue(tableVars.length === 1, '课表脚本 table0 标记不唯一');
    const marker = tableVars[0].start;

    requireUnitCount(code, 0, code.length);

    const allConstructors = findConstructors(code, 0, code.length);
    const scheduleConstructors = findConstructors(code, marker, code.length);
    requireValue(allConstructors.length > 0, '课表脚本中没有 TaskActivity');
    requireValue(allConstructors.length === scheduleConstructors.length,
      '存在无法归属的 TaskActivity 构造，已拒绝整批课表');

    const blocks = findTeacherBlocks(code, marker, code.length);
    requireValue(blocks.length > 0, '课表脚本缺少教师/时段块');
    const allSlotAssignments = collectSlotAssignments(code, marker, code.length);

    const courses = [];
    let parsed = 0;
    let parsedSlotAssignments = 0;
    for (let b = 0; b < blocks.length; b++) {
      const block = blocks[b];
      const blockEnd = b + 1 < blocks.length ? blocks[b + 1].start : code.length;
      let i = skipTrivia(code, block.afterEquals, blockEnd);
      requireValue(code[i] === '[', '教师列表不是数组');
      const arrayClose = matchingClose(code, i, blockEnd, '教师列表');
      const teacher = parseTeacherNames(code.slice(i + 1, arrayClose));

      const constructors = findConstructors(code, arrayClose + 1, blockEnd);
      requireValue(constructors.length === 1,
        '每个教师块必须且只能包含一个 TaskActivity 构造');
      const constructor = constructors[0];
      const argsClose = matchingClose(code, constructor.open, blockEnd,
        'TaskActivity 参数');
      const activity = parseActivityArguments(
        code.slice(constructor.open + 1, argsClose), teacher);
      const slots = readSlotAssignments(code.slice(argsClose + 1, blockEnd));
      parsedSlotAssignments += slots.count;
      for (const course of coursesFromSlots(activity, slots.slots)) courses.push(course);
      parsed++;
    }

    requireValue(parsed === allConstructors.length && parsed === blocks.length,
      'TaskActivity 构造数与成功解析数不一致，已拒绝整批课表');
    requireValue(parsedSlotAssignments === allSlotAssignments.length,
      '存在无法归属的课表格子坐标，已拒绝整批课表');
    requireValue(courses.length > 0 && courses.length <= 5000,
      '没有可导入的已排课课程');
    return {courses: courses, audit: auditRoster(courses, roster),
      rosterSignature: JSON.stringify(roster.entries.map(function (entry) {
        return [entry.sequence, entry.code, entry.name, entry.teacher, entry.teachingClass];
      }).map(JSON.stringify).sort())};
  }

  function assertAllowedLocation() {
    requireValue(k.adapterId === ADAPTER_ID,
      '当前课表入口不是南京航空航天大学适配器，已拒绝');
    const current = location;
    requireValue(current && typeof current.href === 'string', '课表页面地址无效');
    let pageUrl;
    try {
      pageUrl = new URL(current.href);
    } catch (_) {
      fail('课表页面地址无效');
    }
    requireValue(pageUrl.username === '' && pageUrl.password === '',
      '请勿在课表 URL 中携带账号或口令');
    requireValue(pageUrl.protocol === 'https:' &&
      pageUrl.hostname === HOST && (pageUrl.port === '' || pageUrl.port == null) &&
      pageUrl.origin === ORIGIN,
      '请先进入南京航空航天大学教务系统 HTTPS 课表页面并完成登录');
    requireValue(pageUrl.pathname === FORM_PATH,
      '请先进入教务系统课表页面后重试');
  }

  function decodeBytes(buffer, contentType) {
    let charset = null;
    if (contentType) {
      const match = /charset\s*=\s*["']?([^;"'\s]+)/i.exec(contentType);
      if (match) charset = match[1].toLowerCase();
    }
    function decode(label) {
      return new TextDecoder(label, {fatal: true}).decode(buffer);
    }
    if (charset) {
      requireValue(/^(?:utf-8|utf8|gbk|gb2312|gb18030)$/i.test(charset),
        '课表字符编码不受支持');
      try {
        return decode(charset);
      } catch (_) {
        fail('课表页面解码失败');
      }
    }
    try {
      return decode('utf-8');
    } catch (_) {
      try {
        return decode('gb18030');
      } catch (_) {
        fail('课表页面解码失败');
      }
    }
  }

  async function requestText(kind, path, init, maxBytes) {
    const url = new URL(path, ORIGIN);
    requireValue(url.origin === ORIGIN, '拒绝跨站读取课表');
    const controller = new AbortController();
    const timer = setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MS);
    try {
      let response;
      try {
        response = await fetch(url.href, Object.assign({}, init, {
          credentials: 'same-origin',
          redirect: 'error',
          cache: 'no-store',
          signal: controller.signal
        }));
      } catch (error) {
        if (error && error.name === 'AbortError') {
          fail('课表请求超时，请检查网络后重试');
        }
        fail('课表请求失败，请检查登录状态后重试');
      }

      requireValue(response && response.ok === true,
        '课表请求失败，请检查登录状态后重试');
      if (response.redirected === true || response.type === 'opaqueredirect') {
        fail('课表请求被重定向，请重新登录');
      }
      if (response.url) {
        let finalUrl;
        try {
          finalUrl = new URL(response.url, ORIGIN);
        } catch (_) {
          fail('课表响应地址无效');
        }
        requireValue(finalUrl.origin === ORIGIN, '课表响应跳转到其他站点，已拒绝');
      }

      const headers = response.headers;
      if (headers && typeof headers.get === 'function') {
        const declared = headers.get('Content-Length');
        if (declared != null && declared !== '') {
          requireValue(/^\d+$/.test(declared) && Number(declared) <= maxBytes,
            '课表响应大小超出限制');
        }
      }

      let buffer;
      try {
        buffer = await response.arrayBuffer();
      } catch (error) {
        if (error && error.name === 'AbortError') {
          fail('课表请求超时，请检查网络后重试');
        }
        fail('课表响应读取失败');
      }
      requireValue(buffer && typeof buffer.byteLength === 'number' &&
        buffer.byteLength > 0 && buffer.byteLength <= maxBytes,
        '课表响应大小无效或超出限制');

      const contentType = headers && typeof headers.get === 'function'
        ? (headers.get('Content-Type') || '') : '';
      const text = decodeBytes(buffer, contentType);
      requireValue(text.length > 0, '课表响应内容为空');
      return text;
    } finally {
      clearTimeout(timer);
    }
  }

  function checkSessionPayload(text, kind) {
    assertNotLoginOrError(text);
    if (kind === 'semesters') {
      requireValue(!/^\s*</.test(text), '学期列表格式无效');
    }
  }

  async function fetchForm() {
    const text = await requestText('form', FORM_PATH, {
      method: 'GET',
      headers: {'Accept': 'text/html,application/xhtml+xml'}
    }, LIMITS.form);
    checkSessionPayload(text, 'form');
    return text;
  }

  async function fetchSemesters(tagId) {
    const body = new URLSearchParams();
    body.set('tagId', tagId);
    body.set('dataType', 'semesterCalendar');
    body.set('value', '');
    body.set('empty', 'false');
    const text = await requestText('semesters', DATA_PATH, {
      method: 'POST',
      headers: {
        'Accept': 'application/json, text/javascript, */*; q=0.01',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest'
      },
      body: body
    }, LIMITS.semesters);
    checkSessionPayload(text, 'semesters');
    return parseSemesterPayload(text);
  }

  async function fetchCourseTable(semesterId, studentId) {
    const body = new URLSearchParams();
    body.set('ignoreHead', '1');
    body.set('setting.kind', 'std');
    body.set('startWeek', '');
    body.set('project.id', '');
    body.set('semester.id', semesterId);
    body.set('ids', studentId);
    const text = await requestText('table', TABLE_PATH, {
      method: 'POST',
      headers: {
        'Accept': 'text/html,application/xhtml+xml',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
      },
      body: body
    }, LIMITS.table);
    checkSessionPayload(text, 'table');
    return text;
  }

  function parseFormMeta(html) {
    requireValue(typeof html === 'string' && html.length > 0 &&
      html.length <= LIMITS.form, '课表表单格式无效');
    assertNotLoginOrError(html);

    const idCalls = [];
    const idPattern = /\baddInput\s*\(\s*form\s*,\s*(["'])ids\1\s*,\s*([^)]*)\)/g;
    let match;
    while ((match = idPattern.exec(html)) !== null) idCalls.push(match[2]);
    requireValue(idCalls.length === 1, '无法唯一识别学生课表身份，请重新登录');
    const studentId = parseStringLiteral(idCalls[0]);
    requireValue(/^\d{1,32}$/.test(studentId), '学生内部 ID 格式无效');

    const semesterControls = [];
    const semesterPattern = /(?:^|[\s<])id\s*=\s*(["'])semesterBar(\d{1,32})Semester\1/g;
    while ((match = semesterPattern.exec(html)) !== null) {
      semesterControls.push(match[2]);
    }
    requireValue(semesterControls.length === 1, '学期控件标识不唯一');
    return {
      studentId: studentId,
      tagId: 'semesterBar' + semesterControls[0] + 'Semester'
    };
  }

  class SemesterLiteralReader {
    constructor(text) {
      this.text = text;
      this.length = text.length;
      this.index = 0;
    }

    bad() {
      fail('学期列表格式无效');
    }

    skip() {
      this.index = skipTrivia(this.text, this.index, this.length);
    }

    read() {
      this.skip();
      const value = this.value();
      this.skip();
      if (this.index !== this.length) this.bad();
      return value;
    }

    value() {
      this.skip();
      if (this.index >= this.length) this.bad();
      const ch = this.text[this.index];
      if (ch === '[') return this.array();
      if (ch === '{') return this.object();
      if (ch === '"' || ch === "'") {
        const read = readJsStringAt(this.text, this.index, this.length);
        this.index = read.next;
        return read.value;
      }
      if (ch === '-' || /[0-9]/.test(ch)) return this.number();
      if (this.text.startsWith('true', this.index)) {
        this.index += 4;
        return true;
      }
      if (this.text.startsWith('false', this.index)) {
        this.index += 5;
        return false;
      }
      if (this.text.startsWith('null', this.index)) {
        this.index += 4;
        return null;
      }
      this.bad();
    }

    array() {
      this.index++;
      this.skip();
      const out = [];
      if (this.text[this.index] === ']') {
        this.index++;
        return out;
      }
      while (this.index < this.length) {
        out.push(this.value());
        this.skip();
        const ch = this.text[this.index];
        if (ch === ']') {
          this.index++;
          return out;
        }
        if (ch !== ',') this.bad();
        this.index++;
        this.skip();
        if (this.text[this.index] === ']') this.bad();
      }
      this.bad();
    }

    object() {
      this.index++;
      this.skip();
      const out = Object.create(null);
      const seen = new Set();
      if (this.text[this.index] === '}') {
        this.index++;
        return out;
      }
      while (this.index < this.length) {
        this.skip();
        let key;
        const ch = this.text[this.index];
        if (ch === '"' || ch === "'") {
          const read = readJsStringAt(this.text, this.index, this.length);
          key = read.value;
          this.index = read.next;
        } else {
          const id = readIdentifierAt(this.text, this.index, this.length);
          if (!id) this.bad();
          key = id.word;
          this.index = id.end;
        }
        this.skip();
        if (this.text[this.index] !== ':') this.bad();
        this.index++;
        if (seen.has(key)) this.bad();
        seen.add(key);
        out[key] = this.value();
        this.skip();
        const next = this.text[this.index];
        if (next === '}') {
          this.index++;
          return out;
        }
        if (next !== ',') this.bad();
        this.index++;
        this.skip();
        if (this.text[this.index] === '}') this.bad();
      }
      this.bad();
    }

    number() {
      const pattern = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/;
      const match = pattern.exec(this.text.slice(this.index));
      if (!match) this.bad();
      const value = Number(match[0]);
      if (!Number.isFinite(value)) this.bad();
      this.index += match[0].length;
      return value;
    }
  }

  function displayText(value, field) {
    const text = String(value).trim();
    requireValue(text.length > 0 && text.length <= 64 &&
      !/[\u0000-\u001f\u007f]/.test(text), '学期' + field + '无效');
    return text;
  }

  function parseSemesterPayload(text) {
    requireValue(typeof text === 'string' && text.length > 0 &&
      text.length <= LIMITS.semesters, '学期列表格式无效');
    let body = text.replace(/^\uFEFF/, '').trim();
    if (body.endsWith(';')) body = body.slice(0, -1).trim();
    requireValue(body.length > 0 && body[0] === '[', '学期列表格式无效');
    let items;
    try {
      items = new SemesterLiteralReader(body).read();
    } catch (_) {
      fail('学期列表格式无效');
    }
    requireValue(Array.isArray(items) && items.length > 0 && items.length <= 200,
      '没有取得有效学期列表');

    const seenIds = new Set();
    const seenLabels = new Set();
    const semesters = [];
    for (const item of items) {
      requireValue(item && typeof item === 'object' && !Array.isArray(item),
        '学期条目格式无效');
      requireValue(Object.prototype.hasOwnProperty.call(item, 'id') &&
        Object.prototype.hasOwnProperty.call(item, 'schoolYear') &&
        Object.prototype.hasOwnProperty.call(item, 'name'), '学期条目缺少字段');
      let id;
      if (typeof item.id === 'string') {
        requireValue(/^\d{1,32}$/.test(item.id), '学期 ID 格式无效');
        id = item.id;
      } else if (typeof item.id === 'number' && Number.isSafeInteger(item.id) &&
        item.id >= 0) {
        id = String(item.id);
      } else {
        fail('学期 ID 格式无效');
      }
      const schoolYear = typeof item.schoolYear === 'string'
        ? displayText(item.schoolYear, '学年') : '';
      requireValue(schoolYear !== '', '学期学年无效');
      const name = typeof item.name === 'string' ? displayText(item.name, '学期') : '';
      requireValue(name !== '', '学期名称无效');
      const label = schoolYear + ' 第' + name + '学期';
      requireValue(label.length <= 100, '学期显示名称过长');
      requireValue(!seenIds.has(id), '学期 ID 重复');
      requireValue(!seenLabels.has(label), '学期显示名称重复');
      seenIds.add(id);
      seenLabels.add(label);
      semesters.push({id: id, label: label});
    }
    return semesters;
  }

  async function promptTotalWeeks(courses) {
    const raw = await k.api.showPrompt(
      '输入学期总周数',
      '请按所选学期校历输入总周数（1..40）。留空或取消将放弃导入。',
      ''
    );
    requireValue(raw !== null && raw !== undefined, '已取消导入');
    const text = String(raw).trim();
    requireValue(/^\d{1,2}$/.test(text),
      '学期总周数必须是 1..40 的整数');
    const totalWeeks = Number(text);
    requireValue(totalWeeks >= 1 && totalWeeks <= 40,
      '学期总周数必须是 1..40 的整数');
    let maxWeek = 0;
    for (const course of courses) {
      for (const week of course.weeks) {
        if (week > maxWeek) maxWeek = week;
      }
    }
    requireValue(totalWeeks >= maxWeek, '学期总周数不能小于课程最大周次');
    return totalWeeks;
  }

  async function importCourseTable() {
    assertAllowedLocation();

    const initialForm = parseFormMeta(await fetchForm());
    const semesters = await fetchSemesters(initialForm.tagId);
    const rows = semesters.map(function (semester) {
      return {id: semester.id, label: semester.label};
    });
    const selected = await k.choose('选择南京航空航天大学学期', rows, 'id', 'label');
    requireValue(!!selected && rows.some(function (row) {
      return row.id === selected.id;
    }), '未选择有效学期，已取消导入');

    const tableHtml = await fetchCourseTable(selected.id, initialForm.studentId);
    const parsedTable = parseTaskTable(tableHtml);
    const courses = parsedTable.courses;

    if (parsedTable.audit.unmatched.length > 0) {
      const observed = describeUnmatched(parsedTable.audit.unmatched,
        parsedTable.audit.hasTeacher);
      fail('课程列表中有 ' + parsedTable.audit.unmatched.length +
        ' 条记录未在已解析的排课块中出现：' + observed +
        '。请回教务系统核对后重试。');
    }

    const totalWeeks = await promptTotalWeeks(courses);

    // No staging has happened so far. Re-read the same-origin form and list
    // after all user interactions; identity and term (including its displayed
    // label) must still agree immediately before finish().
    const finalForm = parseFormMeta(await fetchForm());
    requireValue(finalForm.studentId === initialForm.studentId,
      '学生身份已变化，已取消导入');
    const finalSemesters = await fetchSemesters(finalForm.tagId);
    const finalSelected = finalSemesters.find(function (semester) {
      return semester.id === selected.id;
    });
    requireValue(!!finalSelected, '所选学期已不在服务端列表，已取消导入');
    requireValue(finalSelected.label === selected.label,
      '所选学期显示名称已变化，已取消导入');

    // Re-read the complete chosen semester after user interaction. Comparing
    // sorted semantic rows tolerates a harmless row reorder, never a room,
    // teacher, week, class identity or period change. No response is persisted.
    const finalTable = parseTaskTable(await fetchCourseTable(selected.id, finalForm.studentId));
    const signature = function (items) { return JSON.stringify(items.map(JSON.stringify).sort()); };
    requireValue(finalTable.audit.unmatched.length === 0 &&
      finalTable.rosterSignature === parsedTable.rosterSignature &&
      signature(finalTable.courses) === signature(courses),
      '课表或课程列表在查询期间发生变化，请核对后重新导入');
    assertAllowedLocation();
    // No fixed semester start date or clock times are invented. The user has
    // already supplied the term length; native Preview totalWeeks is read-only.
    await k.finish(courses, 0, totalWeeks);
  }

  window.kagendaNuaaParsers = Object.freeze({
    adapterId: ADAPTER_ID,
    parseFormMeta: parseFormMeta,
    parseSemesterPayload: parseSemesterPayload,
    parseWeekBitmap: parseWeekBitmap,
    parseTaskTable: parseTaskTable,
    splitArguments: function (raw) {
      return splitTopLevel(raw, ',', 'TaskActivity 参数').map(function (token) {
        return token.trim();
      });
    },
    unitToPeriod: unitToPeriod
  });

  k.run(importCourseTable);
})();
