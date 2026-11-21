// KAgenda original strict JUWP H5 personal adapter, MIT; primary-reviewed.
// Protocol facts: player0729/juwp-CourseWidgetApp@296e779b5d2d238e32ae096953e16c910c156d4b
// and official jiaowuh5.juwp.edu.cn public app snapshots (2026-11-12); no upstream implementation copied.
// No upstream implementation, no login/password/captcha, no proxy, no guessed
// calendar, section clock, week expansion or time-mode default is copied.
(function () {
  'use strict';

  const k = window.kagendaSchool;
  if (!k || typeof k.requireValue !== 'function') throw new Error('课表导入组件未加载');
  const need = k.requireValue;

  const ADAPTER_ID = 'ext_juwp_h5_personal';
  const ORIGIN = 'http://jiaowuh5.juwp.edu.cn';
  const API_PREFIX = '/njwhd';
  const SCHOOL_CODE = '11319';
  const STUDENT_ROLE = '2';
  const SCHOOL_NAME = '江西水利电力大学';
  const TIMEOUT_MS = 20000;
  const MAX_BYTES = 1500000;
  const MAX_COURSES = 5000;
  const MAX_WEEKS = 40;
  const MAX_SELECTIONS = 200;

  const LOGIN_PATTERN = /(?:未登录|未授权|无权限|没有权限|请.{0,12}登录|登录.{0,12}(?:失效|过期|失败)|会话.{0,12}(?:失效|过期)|session.{0,12}(?:expired|invalid))/i;

  function isObject(value) {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  function isEmpty(value) {
    if (value == null || value === '') return true;
    if (Array.isArray(value)) return value.length === 0;
    if (isObject(value)) return Object.keys(value).length === 0;
    return false;
  }

  // Stable in-memory snapshot only. Never log or export session/profile data.
  function stable(value) {
    if (Array.isArray(value)) return '[' + value.map(stable).join(',') + ']';
    if (isObject(value)) {
      return '{' + Object.keys(value).sort().map(function (key) {
        return JSON.stringify(key) + ':' + stable(value[key]);
      }).join(',') + '}';
    }
    if (typeof value === 'undefined') return 'null';
    return JSON.stringify(value);
  }

  function requiredText(value, message, maxLength) {
    need(typeof value === 'string' && value.trim().length > 0 && value.trim().length <= maxLength, message);
    const text = value.trim();
    need(!/^(?:未知|待定|未安排|暂无|未排|未分配|null|undefined|[-—–]+)$/i.test(text), message);
    return text;
  }

  function requiredScalarText(value, message, maxLength) {
    need((typeof value === 'string' || typeof value === 'number') &&
      String(value).trim().length > 0 && String(value).trim().length <= maxLength, message);
    return String(value).trim();
  }

  function optionalScalarText(value, message, maxLength) {
    if (value == null || value === '') return '';
    need(typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean', message);
    const text = String(value).trim();
    need(text.length <= maxLength, message);
    return text;
  }

  function guard() {
    need(k.adapterId === ADAPTER_ID, '课表导入组件与当前页面不匹配');
    need(location.protocol === 'http:' && location.origin === ORIGIN,
      '请进入本校官方课表页面后重试');
    need(location.pathname === '/', '请进入本校官方课表根页面后重试');
    need(!location.username && !location.password, '地址中不能包含账号或密码');
    need(document && typeof document.querySelector === 'function' &&
      !document.querySelector('input[type="password"]'), '请先在官方页面完成登录');
  }

  function readSession() {
    const store = window.sessionStorage;
    need(store && typeof store.getItem === 'function', '无法读取当前登录会话，请重新登录');
    const schoolCode = store.getItem('schoolCode');
    const identity = store.getItem('Identity');
    const token = store.getItem('Token');
    const rawInfo = store.getItem('userInfo');

    need(String(schoolCode == null ? '' : schoolCode).trim() === SCHOOL_CODE,
      '当前页面不是本校学生课表');
    need(String(identity == null ? '' : identity).trim() === STUDENT_ROLE,
      '请使用学生账号进入本校课表');
    need(typeof token === 'string' && token.trim().length > 0 &&
      token.length <= 8192 && !/[\r\n]/.test(token), '登录会话已失效，请重新登录');
    need(typeof rawInfo === 'string' && rawInfo.length > 0 && rawInfo.length <= 100000,
      '登录信息不完整，请重新登录');

    let info;
    try { info = JSON.parse(rawInfo); } catch (_) { throw new Error('登录信息格式无效，请重新登录'); }
    need(isObject(info), '登录信息格式无效，请重新登录');

    const userNo = requiredScalarText(info.userNo, '登录信息缺少账号标识，请重新登录', 100);
    const name = requiredText(info.name, '登录信息缺少姓名，请重新登录', 100);
    need(String(info.userType) === STUDENT_ROLE, '请使用学生账号进入本校课表');
    need(typeof info.token === 'string' && info.token === token,
      '登录会话与用户信息不一致，请重新登录');

    return {
      token: token,
      schoolCode: String(schoolCode).trim(),
      identity: String(identity).trim(),
      info: info,
      rawInfo: rawInfo,
      userNo: userNo,
      name: name
    };
  }

  function checkIdentity(session, profile) {
    need(profile.studentID === session.userNo, '账号与本人信息不一致，请重新登录');
    need(profile.name === session.name, '登录资料与本人姓名不一致，请重新登录');
  }

  function checkPagination(value, label) {
    if (!isObject(value)) return;
    function flag(item) { return String(item == null ? '' : item).trim().toLowerCase(); }
    ['hasMore', 'hasNext', 'hasNextPage'].forEach(function (key) {
      if (value[key] != null) {
        need(['false', '0', ''].indexOf(flag(value[key])) !== -1, label + '还有未读取的页面');
      }
    });
    if (value.nextPage != null) {
      need(['', '0', 'false', 'null', 'undefined'].indexOf(flag(value.nextPage)) !== -1,
        label + '还有未读取的页面');
    }
    if (value.isLast != null) {
      need(['true', '1'].indexOf(flag(value.isLast)) !== -1, label + '还有未读取的页面');
    }
    ['pages', 'totalPages', 'totalPage', 'currentPage', 'pageNumber', 'pageNum', 'pageNo', 'page']
      .forEach(function (key) {
        if (value[key] != null) {
          need(k.integer(value[key], 1, 5000, '页码') === 1, label + '还有未读取的页面');
        }
      });
  }

  function requireCode1(root, label) {
    need(isObject(root), label + '返回格式发生变化');
    need(root.code === 1 || root.code === '1', '教务返回失败或登录已过期');
    if (root.success != null) {
      const success = String(root.success).toLowerCase();
      need(success === 'true' || success === '1', '教务返回失败');
    }
    if (root.error != null) {
      const error = String(root.error).trim().toLowerCase();
      need(['', '0', 'false', 'null', 'undefined'].indexOf(error) !== -1, '教务返回错误');
    }
    if (root.errorCode != null) {
      const errorCode = String(root.errorCode).trim().toLowerCase();
      need(['', '0', 'false', 'null', 'undefined'].indexOf(errorCode) !== -1, '教务返回错误');
    }
    ['Msg', 'msg', 'message'].forEach(function (key) {
      if (typeof root[key] === 'string') {
        need(!LOGIN_PATTERN.test(root[key]), '登录状态已失效或没有课表权限，请重新登录');
      }
    });
    checkPagination(root, label);
  }

  const PAGE_FIELDS = ['hasMore','hasNext','hasNextPage','nextPage','isLast','pages','totalPages','totalPage','currentPage','pageNumber','pageNum','pageNo','page'];
  function checkUnknownFields(root, allowed, label) {
    Object.keys(root).forEach(function (key) {
      if (allowed.has(key) || PAGE_FIELDS.includes(key) || ['error','errorCode'].includes(key)) return;
      need(isEmpty(root[key]), label + '存在未核验的附加内容，未导入部分结果');
    });
  }

  function checkNkb(value, label) {
    if (value == null || value === '') return;
    if (Array.isArray(value)) {
      need(value.length === 0, label + '存在未排课或未定位安排，未导入部分结果');
      return;
    }
    if (isObject(value)) {
      need(Object.keys(value).length === 0, label + '存在未排课或未定位安排，未导入部分结果');
      return;
    }
    need(!value, label + '存在未排课或未定位安排，未导入部分结果');
  }

  function checkJcd(value, label) {
    if (value == null || value === '') return;
    // Known section metadata is snapshot-only. Native schedule preview owns
    // section clocks; this adapter never guesses a school clock from it.
    need(Array.isArray(value), label + '节次元数据格式发生变化');
    value.forEach(function(row) {
      need(isObject(row), '节次元数据无效');
      Object.keys(row).forEach(function(key) {
        need(['XJMC','DJMC'].includes(key) || isEmpty(row[key]), '节次元数据含未知内容');
        if (['XJMC','DJMC'].includes(key)) need(typeof row[key] === 'string' && row[key].length <= 200, '节次标签无效');
      });
    });
  }

  async function requestJson(target, token) {
    const url = typeof target === 'string'
      ? new URL(API_PREFIX + target, ORIGIN)
      : new URL(target.href);
    need(url.origin === ORIGIN, '不能跨站读取课表');
    need(url.pathname.indexOf(API_PREFIX + '/') === 0, '课表接口路径不在官方范围内');

    const controller = new AbortController();
    const timer = setTimeout(function () { controller.abort(); }, TIMEOUT_MS);
    try {
    let response;
    try {
      response = await fetch(url.href, {
        method: 'POST',
        headers: {
          token: token,
          Accept: 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin',
        redirect: 'error',
        cache: 'no-store',
        referrer: location.href,
        referrerPolicy: 'same-origin',
        signal: controller.signal
      });
    } catch (error) {
      if (error && error.name === 'AbortError') throw new Error('课表请求超时，请重试');
      throw new Error('课表请求失败，请检查网络或登录状态');
    }

    need(response && typeof response === 'object', '课表请求失败，请重新登录');
    need(response.ok === true, '课表请求失败，请重新登录');
    need(response.redirected !== true, '课表请求被重定向，请重新登录');
    need(typeof response.url === 'string' && response.url.length > 0, '课表响应来源发生变化');
    let actual;
    try { actual = new URL(response.url); } catch (_) { throw new Error('课表响应来源发生变化'); }
    need(actual.origin === url.origin && actual.pathname === url.pathname &&
      actual.search === url.search, '课表响应来源发生变化');

    const contentType = response.headers && typeof response.headers.get === 'function'
      ? response.headers.get('Content-Type') : '';
    need(typeof contentType === 'string' &&
      /^application\/json(?:\s*;|\s*$)/i.test(contentType.trim()), '课表接口未返回 JSON 数据');
    const charset = contentType.match(/charset\s*=\s*["']?([^;"'\s]+)/i);
    need(!charset || /^(?:utf-8|utf8)$/i.test(charset[1]), '课表字符编码不受支持');

    const declared = response.headers.get('Content-Length');
    if (declared != null && declared !== '') {
      need(/^\d+$/.test(declared) && Number(declared) > 0 && Number(declared) <= MAX_BYTES,
        '课表响应长度无效或过大');
    }
    need(response.body && typeof response.body.getReader === 'function', '当前浏览器不支持有界课表读取');
    const reader = response.body.getReader();
    const chunks = [];
    let total = 0;
    try {
      while (true) {
        const part = await reader.read();
        if (part.done) break;
        need(part.value instanceof Uint8Array, '课表响应编码无效');
        total += part.value.byteLength;
        need(total <= MAX_BYTES, '课表响应过大');
        chunks.push(part.value);
      }
    } catch (_) {
      throw new Error('课表响应读取失败、超时或过大');
    } finally {
      try { reader.cancel().catch(function () {}); } catch (_) {}
      try { reader.releaseLock(); } catch (_) {}
    }
    need(total > 0, '课表响应为空');
    const bytes = new Uint8Array(total);
    let offset = 0;
    chunks.forEach(function (chunk) { bytes.set(chunk, offset); offset += chunk.byteLength; });
    let text;
    try { text = new TextDecoder('utf-8', {fatal: true}).decode(bytes); }
    catch (_) { throw new Error('课表响应编码无效'); }
    need(text.indexOf('\uFFFD') === -1, '课表响应编码无效');
    need(!/^\s*</.test(text), '课表接口返回页面而不是数据，请重新登录');
    try { return JSON.parse(text); } catch (_) { throw new Error('课表接口返回的数据格式无效'); }
    } finally { clearTimeout(timer); }
  }

  async function fetchProfile(token) {
    const root = await requestJson('/student/my', token);
    requireCode1(root, '本人信息');
    checkUnknownFields(root, new Set(['code', 'success', 'Msg', 'msg', 'message', 'data']), '本人信息');
    // The protocol states one authoritative self record. Read exactly one and
    // reject a multi-record response instead of taking an unverified first item.
    need(Array.isArray(root.data) && root.data.length === 1, '本人信息不完整或不是唯一本人记录');
    const raw = root.data[0]; // length is exactly one, validated above
    need(isObject(raw), '本人信息格式发生变化');
    Object.keys(raw).forEach(function (key) {
      const value = raw[key];
      if (value && typeof value === 'object') need(isEmpty(value), '本人信息含未核验的结构');
    });

    const studentID = requiredScalarText(raw.studentID, '本人信息缺少学号', 100);
    const name = requiredText(raw.name, '本人信息缺少姓名', 100);
    const schoolName = requiredText(raw.schoolName, '本人信息缺少学校名称', 200);
    need([SCHOOL_NAME, '南昌工程学院'].includes(schoolName.replace(/\s+/g, '')),
      '本人信息与当前学校不一致');
    const className = optionalScalarText(raw.className, '本人信息班级无效', 200);
    const academy = optionalScalarText(raw.academy, '本人信息学院无效', 200);

    return {
      raw: raw,
      studentID: studentID,
      name: name,
      className: className,
      academy: academy,
      schoolName: schoolName
    };
  }

  async function fetchTerms(token) {
    // Protocol: bare array, no code wrapper. Never auto-pick isdqxq or first.
    const raw = await requestJson('/getXnxqList', token);
    need(Array.isArray(raw), '学期列表格式发生变化');
    need(raw.length > 0 && raw.length <= MAX_SELECTIONS, '没有取得学期列表');
    const rows = raw.map(function (row) {
      need(isObject(row), '学期列表格式无效');
      Object.keys(row).forEach(function (key) {
        const value = row[key];
        if (value && typeof value === 'object') need(isEmpty(value), '学期列表含未核验的结构');
      });
      return {
        id: requiredScalarText(row.xnxq01id, '学期列表格式无效', 100),
        label: requiredText(row.xqmc, '学期列表格式无效', 200)
      };
    });
    const ids = new Set();
    const labels = new Set();
    rows.forEach(function (row) {
      need(!ids.has(row.id), '学期列表存在重复项');
      need(!labels.has(row.label), '学期列表存在重复名称');
      ids.add(row.id);
      labels.add(row.label);
    });
    return {raw: raw, rows: rows};
  }

  async function fetchModes(token) {
    const root = await requestJson('/Get_sjkbms', token);
    requireCode1(root, '时间模式列表');
    checkUnknownFields(root, new Set(['code', 'success', 'Msg', 'msg', 'message', 'data']), '时间模式列表');
    need(Array.isArray(root.data) && root.data.length > 0 &&
      root.data.length <= MAX_SELECTIONS, '没有取得时间模式列表');
    const rows = root.data.map(function (row) {
      need(isObject(row), '时间模式列表格式无效');
      Object.keys(row).forEach(function (key) {
        const value = row[key];
        if (value && typeof value === 'object') need(isEmpty(value), '时间模式列表含未核验的结构');
      });
      return {
        id: requiredScalarText(row.kbjcmsid, '时间模式列表格式无效', 100),
        label: requiredText(row.kbjcmsmc, '时间模式列表格式无效', 200)
      };
    });
    const ids = new Set();
    const labels = new Set();
    rows.forEach(function (row) {
      need(!ids.has(row.id), '时间模式列表存在重复项');
      need(!labels.has(row.label), '时间模式列表存在重复名称');
      ids.add(row.id);
      labels.add(row.label);
    });
    // mrms is intentionally ignored: the user must choose explicitly.
    return {raw: root, rows: rows};
  }

  async function pick(title, rows) {
    const index = await k.api.showSingleSelection(
      title,
      JSON.stringify(rows.map(function (row) { return row.label; })),
      -1
    );
    need(index !== null && index !== undefined, '已取消导入');
    return rows[k.integer(index, 0, rows.length - 1, '选择')];
  }

  function monday(value) {
    need(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.trim()),
      '请按校历填写第一周周一');
    const text = value.trim();
    const date = new Date(text + 'T00:00:00Z');
    need(Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === text &&
      date.getUTCDay() === 1, '请按校历填写有效的周一日期');
    return text;
  }

  function validator(fn) {
    return function (value) {
      try { fn(value); return false; } catch (error) {
        return String(error && error.message || error).slice(0, 500);
      }
    };
  }

  function parseClassTimeToken(value) {
    const text = String(value == null ? '' : value).trim();
    need(/^\d{3,49}$/.test(text) && text.length % 2 === 1, '课程节次编码格式发生变化');
    const day = Number(text.charAt(0));
    need(day >= 1 && day <= 7, '课程节次编码中的星期无效');
    const periods = [];
    for (let i = 1; i < text.length; i += 2) {
      const period = Number(text.slice(i, i + 2));
      need(Number.isInteger(period) && period >= 1 && period <= 24,
        '课程节次编码中的节次无效');
      periods.push(period);
    }
    need(periods.length > 0, '课程缺少明确的节次');
    need(new Set(periods).size === periods.length, '课程节次编码存在重复节次');
    return {day: day, periods: periods};
  }

  function parseClassTimeField(value) {
    need(typeof value === 'string' || typeof value === 'number', '课程缺少明确的节次编码');
    const raw = String(value).trim();
    need(raw.length > 0 && raw.length <= 120, '课程缺少明确的节次编码');
    const tokens = raw.split(/[,，]/).map(function (part) { return part.trim(); });
    need(tokens.length > 0 && tokens.every(Boolean), '课程节次编码格式发生变化');
    const facts = new Set();
    tokens.forEach(function (token) {
      const parsed = parseClassTimeToken(token);
      parsed.periods.forEach(function (period) {
        const key = parsed.day + ':' + period;
        need(!facts.has(key), '课程节次编码存在重复节次');
        facts.add(key);
      });
    });
    need(facts.size > 0, '课程缺少明确的节次');
    return {facts: facts};
  }

  // weekNoteDetail is comma-separated classTime groups: exact day/period
  // agreement is required, but it must never supply a mixed-day guess.
  function checkWeekNote(value, classFacts) {
    if (value == null || value === '') return;
    need(typeof value === 'string' || typeof value === 'number',
      '课程节次说明格式发生变化');
    const raw = String(value).trim();
    if (!raw) return;
    need(raw.length <= 200, '课程节次说明格式发生变化');
    const tokens = raw.split(/[,，]/).map(function (part) { return part.trim(); });
    need(tokens.length > 0 && tokens.every(Boolean), '课程节次说明格式发生变化');
    const facts = new Set();
    tokens.forEach(function (token) {
      const parsed = parseClassTimeToken(token);
      parsed.periods.forEach(function (period) {
        const key = parsed.day + ':' + period;
        need(!facts.has(key), '课程节次说明存在重复节次');
        need(classFacts.has(key), '课程节次说明与节次编码冲突');
        facts.add(key);
      });
    });
    need(facts.size === classFacts.size, '课程节次说明与节次编码冲突');
    classFacts.forEach(function (key) {
      need(facts.has(key), '课程节次说明与节次编码冲突');
    });
  }

  function meetingsFromFacts(facts) {
    const days = new Map();
    facts.forEach(function (key) {
      const parts = key.split(':');
      const day = Number(parts[0]);
      const period = Number(parts[1]);
      if (!days.has(day)) days.set(day, []);
      days.get(day).push(period);
    });
    const meetings = [];
    Array.from(days.keys()).sort(function (a, b) { return a - b; }).forEach(function (day) {
      const periods = days.get(day).sort(function (a, b) { return a - b; });
      let first = periods[0];
      let last = periods[0];
      for (let i = 1; i < periods.length; i++) {
        const period = periods[i];
        if (period === last + 1) {
          last = period;
        } else {
          meetings.push({day: day, first: first, last: last});
          first = period;
          last = period;
        }
      }
      meetings.push({day: day, first: first, last: last});
    });
    return meetings;
  }

  function parseWeekDetails(value) {
    let values;
    if (Array.isArray(value)) {
      need(value.length > 0 && value.length <= MAX_WEEKS, '课程缺少明确的周次列表');
      values = value.map(function (item) { return k.integer(item, 1, MAX_WEEKS, '周次'); });
    } else if (typeof value === 'string') {
      const raw = value.trim().replace(/^,|,$/g, '');
      need(raw.length > 0 && raw.length <= 200, '课程缺少明确的周次列表');
      const parts = raw.split(/[,，]/).map(function (part) { return part.trim(); });
      need(parts.length > 0 && parts.every(function (part) {
        return /^\d{1,2}$/.test(part);
      }), '课程周次列表格式发生变化');
      values = parts.map(function (part) { return k.integer(part, 1, MAX_WEEKS, '周次'); });
    } else {
      need(false, '课程周次列表格式发生变化');
    }
    need(new Set(values).size === values.length, '课程周次列表存在重复项');
    return values.sort(function (a, b) { return a - b; });
  }

  function parseWeeks(row) {
    const detailsPresent = row.classWeekDetails != null &&
      !(typeof row.classWeekDetails === 'string' && row.classWeekDetails.trim() === '');
    const classWeekPresent = row.classWeek != null &&
      !(typeof row.classWeek === 'string' && row.classWeek.trim() === '');
    const details = detailsPresent ? parseWeekDetails(row.classWeekDetails) : null;

    if (classWeekPresent) {
      need(typeof row.classWeek === 'string' || typeof row.classWeek === 'number',
        '课程缺少明确的周次');
      const weeks = k.weeks(row.classWeek);
      need(weeks.length > 0 && weeks.length <= MAX_WEEKS, '课程周次超出范围');
      if (details) {
        // Explicit list wins only when it agrees exactly. A conflict is an
        // ambiguity, never a reason to silently choose one representation.
        need(JSON.stringify(weeks) === JSON.stringify(details), '课程周次字段冲突');
      }
      return weeks;
    }

    need(details, '课程缺少明确的周次');
    return details;
  }

  function strictClock(value, message) {
    need(typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value.trim()), message);
    return value.trim();
  }

  function parseClock(row) {
    const startPresent = row.startTime != null && String(row.startTime).trim() !== '';
    const endPresent = row.endTIme != null && String(row.endTIme).trim() !== '';
    need(startPresent === endPresent, '课程自定义时间缺少开始或结束时间');
    if (!startPresent) return null;
    const start = strictClock(row.startTime, '课程开始时间格式无效');
    const end = strictClock(row.endTIme, '课程结束时间格式无效');
    need(start < end, '课程结束时间早于开始时间');
    return {start: start, end: end};
  }

  const ROW_FIELDS = new Set([
    'classWeek', 'teacherName', 'weekNoteDetail', 'ktmc', 'classTime', 'jx0408id',
    'kch', 'courseName', 'zxs', 'peopleNumber', 'maxClassTime', 'khfs',
    'startTime', 'endTIme', 'location', 'classWeekDetails'
  ]);

  function parseRow(row, blockIndex, rowIndex, term) {
    need(isObject(row), '课程记录不完整');
    Object.keys(row).forEach(function (key) {
      const value = row[key];
      if (!ROW_FIELDS.has(key)) need(isEmpty(value), '课程含未核验的字段');
      if (key === 'classWeekDetails') return;
      if (value && typeof value === 'object') need(isEmpty(value), '课程含未核验的结构化安排');
    });

    const courseName = requiredText(row.courseName, '课程缺少明确的名称', 200);
    const teacherName = requiredText(row.teacherName, '课程缺少明确的教师', 500);
    const location = requiredText(row.location, '课程缺少明确的地点', 500);
    const ktmc = requiredText(row.ktmc, '课程缺少明确的教学班', 500);
    const kch = requiredScalarText(row.kch, '课程缺少明确的课号', 200);
    const jx0408id = optionalScalarText(row.jx0408id, '课程编号无效', 200);

    const classTime = parseClassTimeField(row.classTime);
    checkWeekNote(row.weekNoteDetail, classTime.facts);
    const weeks = parseWeeks(row);
    const clock = parseClock(row);
    const meetings = meetingsFromFacts(classTime.facts);
    need(meetings.length > 0, '课程缺少明确的节次');

    // One whole-row clock cannot be assigned to two separate meetings. Only a
    // single meeting may carry it; per-group clock evidence is not in facts.
    if (clock) need(meetings.length === 1, '课程存在多个离散节次组，无法对应同一段时间');

    const extra = {
      semester: term.label,
      sourceBlock: blockIndex + 1,
      sourceRow: rowIndex + 1,
      kch: kch,
      ktmc: ktmc
    };
    if (jx0408id) extra.jx0408id = jx0408id;
    [['zxs', '总学时'], ['peopleNumber', '人数'], ['maxClassTime', '最大节次'], ['khfs', '考核方式']]
      .forEach(function (pair) {
        const value = optionalScalarText(row[pair[0]], '课程' + pair[1] + '字段无效', 200);
        if (value) extra[pair[0]] = value;
      });
    const extraInfo = JSON.stringify(extra);
    need(extraInfo.length <= 4000, '课程附加信息过大');

    return meetings.map(function (meeting) {
      const course = k.course(
        courseName, teacherName, location, meeting.day, meeting.first, meeting.last, weeks
      );
      course.extraInfo = extraInfo;
      if (clock) {
        course.isCustomTime = true;
        course.customStartTime = clock.start;
        course.customEndTime = clock.end;
      }
      return course;
    });
  }

  const ROOT_FIELDS = new Set([
    'code', 'success', 'Msg', 'msg', 'message', 'data', 'jcdatalist', 'nkbList', 'total', 'totalSize', 'totalCount', 'nowWeek'
  ]);
  const BLOCK_FIELDS = new Set([
    'week', 'nowWeeked', 'weekday', 'date', 'item', 'jcdatalist', 'nkbList'
  ]);

  function parseCourses(root, term) {
    requireCode1(root, '课表');
    checkUnknownFields(root, ROOT_FIELDS, '课表');
    checkNkb(root.nkbList, '课表');
    checkJcd(root.jcdatalist, '课表');
    need(Array.isArray(root.data) && root.data.length > 0, '未取得完整个人课表');

    if (root.nowWeek != null) need(typeof root.nowWeek === 'string' || typeof root.nowWeek === 'number', '当前周元数据格式无效');
    const courses = [];
    let rowCount = 0;
    root.data.forEach(function (block, blockIndex) {
      need(isObject(block), '课表数据块格式发生变化');
      need(block.week === 'all', '教务没有返回完整学期，未导入部分结果');
      checkNkb(block.nkbList, '课表');
      checkJcd(block.jcdatalist, '课表');
      checkPagination(block, '课表');
      checkUnknownFields(block, BLOCK_FIELDS, '课表');
      ['week', 'nowWeeked', 'weekday'].forEach(function (key) {
        if (block[key] != null) {
          need(typeof block[key] === 'string' || typeof block[key] === 'number' ||
            typeof block[key] === 'boolean', '课表元数据格式发生变化');
        }
      });
      if (block.date != null) {
        need(Array.isArray(block.date), '课表日期元数据格式发生变化');
        block.date.forEach(function(date) {
          need(isObject(date), '课表日期元数据无效');
          Object.keys(date).forEach(function(key) {
            need(['xqmc','mxrq','zc','xqid'].includes(key) || isEmpty(date[key]), '课表日期含未知内容');
            need(isEmpty(date[key]) || typeof date[key] === 'string' || typeof date[key] === 'number', '课表日期含未知结构');
          });
        });
      }
      need(Array.isArray(block.item), '课表数据块缺少课程列表');

      block.item.forEach(function (row, rowIndex) {
        rowCount += 1;
        const parsed = parseRow(row, blockIndex, rowIndex, term);
        parsed.forEach(function (course) {
          courses.push(course);
          need(courses.length <= MAX_COURSES, '课程安排过多，无法完整核对');
        });
      });
    });

    need(rowCount > 0 && courses.length > 0, '没有可导入的已排课课程');
    ['total', 'totalSize', 'totalCount'].forEach(function (key) {
      if (root[key] != null) {
        need(k.integer(root[key], 0, MAX_COURSES, '课程总数') === rowCount,
          '课程总数与读取结果不符');
      }
    });

    let maxWeek = 0;
    courses.forEach(function (course) {
      course.weeks.forEach(function (week) { if (week > maxWeek) maxWeek = week; });
    });
    need(maxWeek >= 1 && maxWeek <= MAX_WEEKS, '课程周次超出范围');

    return {courses: courses, maxWeek: maxWeek, rowCount: rowCount, signature: stable(root)};
  }

  async function fetchCourses(token, term, mode) {
    const url = new URL(API_PREFIX + '/student/curriculum', ORIGIN);
    url.searchParams.set('xnxq01id', term.id);
    url.searchParams.set('kbjcmsid', mode.id);
    url.searchParams.set('week', 'all');
    const root = await requestJson(url, token);
    return parseCourses(root, term);
  }

  async function stage(config, courses) {
    need(Array.isArray(courses) && courses.length > 0 && courses.length <= MAX_COURSES,
      '没有可导入的已排课课程');
    const configText = JSON.stringify(config);
    const coursesText = JSON.stringify(courses);
    need(configText.length > 0 && configText.length <= 4000, '学期配置无效');
    // Prepare both payloads before touching native storage. This adapter never
    // stages rows while it is still parsing, and never saves partial results.
    await k.api.saveCourseConfig(configText);
    await k.api.saveImportedCourses(coursesText);
    k.api.notifyTaskCompletion();
  }

  k.run(async function () {
    guard();
    const pageUrl = location.href;

    const session0 = readSession();
    const profile0 = await fetchProfile(session0.token);
    checkIdentity(session0, profile0);
    const terms0 = await fetchTerms(session0.token);
    const modes0 = await fetchModes(session0.token);

    // Both choices start at -1: no current/first/default option is implied.
    const term = await pick('要导入的学期', terms0.rows);
    const mode = await pick('要导入的时间模式', modes0.rows);

    window.kagendaJuwpMondayValidator = validator(monday);
    const mondayAnswer = await k.api.showPrompt(
      '第一周周一',
      '请按学校校历填写教学第一周的周一（YYYY-MM-DD）。',
      '',
      'kagendaJuwpMondayValidator'
    );
    need(mondayAnswer !== null && mondayAnswer !== undefined, '已取消导入');
    const mondayDate = monday(mondayAnswer);

    window.kagendaJuwpWeeksValidator = validator(function (value) {
      k.integer(value, 1, MAX_WEEKS, '学期教学周数');
    });
    const weeksAnswer = await k.api.showPrompt(
      '学期教学周数',
      '请按学校校历填写本学期总教学周数（1–40 周）。',
      '',
      'kagendaJuwpWeeksValidator'
    );
    need(weeksAnswer !== null && weeksAnswer !== undefined, '已取消导入');
    const totalWeeks = k.integer(weeksAnswer, 1, MAX_WEEKS, '学期教学周数');

    guard();
    need(location.href === pageUrl, '确认期间网页发生跳转');
    const session1 = readSession();
    need(stable(session0) === stable(session1), '确认期间登录身份或 Token 发生变化');

    // All raw course reads happen after the user's term/mode/calendar input.
    // Two complete reads must be byte-stable before anything is staged.
    const first = await fetchCourses(session1.token, term, mode);
    const second = await fetchCourses(session1.token, term, mode);
    need(first.signature === second.signature,
      '两次课表原始响应不一致，未导入部分结果');
    need(stable(first.courses) === stable(second.courses),
      '两次课表解析结果不一致，未导入部分结果');
    need(first.maxWeek === second.maxWeek, '两次课表周次不一致，未导入部分结果');

    guard();
    need(location.href === pageUrl, '确认期间网页发生跳转');

    // Re-read identity, profile and both option lists after all interaction.
    const profile1 = await fetchProfile(session1.token);
    checkIdentity(session1, profile1);
    need(stable(profile0.raw) === stable(profile1.raw), '确认期间本人信息发生变化');
    const terms1 = await fetchTerms(session1.token);
    need(stable(terms0.raw) === stable(terms1.raw), '确认期间学期选项发生变化');
    const modes1 = await fetchModes(session1.token);
    need(stable(modes0.raw) === stable(modes1.raw), '确认期间时间模式选项发生变化');

    guard();
    need(location.href === pageUrl, '确认期间网页发生跳转');
    const session2 = readSession();
    need(stable(session1) === stable(session2), '确认期间登录身份或 Token 发生变化');

    need(second.maxWeek <= totalWeeks, '课程周次超出学期总周数，未导入部分结果');

    need(JSON.stringify(second.courses).indexOf(session2.token) === -1, '课表响应包含会话数据，未导入');

    // Metadata/date and nowWeeked were never used as course weekday or semester
    // length. The calendar anchor and total weeks are exactly the user's input.
    await stage(
      {semesterTotalWeeks: totalWeeks, semesterStartDate: mondayDate},
      second.courses
    );
  });
})();
