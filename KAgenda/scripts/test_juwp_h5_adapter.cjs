'use strict';
// Primary-reviewed synthetic regression against the production JUWP H5 asset.
// Original fixtures; no upstream execution. It uses only node:vm + a mock fetch,
// never school traffic, and contains no real account, token, profile or 课表.
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');

const ROOT = __dirname;
const COMMON = fs.readFileSync(path.join(ROOT, '../app/src/main/assets/academic/school-common.js'), 'utf8')
  .replace('__ADAPTER_ID__', JSON.stringify('ext_juwp_h5_personal'));
const ADAPTER = fs.readFileSync(path.join(ROOT, '../app/src/main/assets/shiguang/scripts/EXT/juwp-h5-personal.js'), 'utf8');

const ORIGIN = 'http://jiaowuh5.juwp.edu.cn';
const API = '/njwhd';
const TOKEN = 'SYNTHETIC-TOKEN-NOT-A-REAL-SESSION';
const STUDENT_ID = 'SYN-STUDENT-001';
const STUDENT_NAME = '合成学生';
const CLASS_NAME = '合成行政班';
const ACADEMY = '合成学院';
const SCHOOL_NAME = '江西水利电力大学';

function clone(value) { return JSON.parse(JSON.stringify(value)); }

function session(overrides) {
  overrides = overrides || {};
  const token = overrides.Token !== undefined ? overrides.Token : TOKEN;
  const info = Object.assign({
    userNo: STUDENT_ID,
    name: STUDENT_NAME,
    userType: 2,
    token: token
  }, overrides.info || {});
  let userInfo;
  if (overrides.userInfo !== undefined) {
    userInfo = typeof overrides.userInfo === 'string'
      ? overrides.userInfo : JSON.stringify(overrides.userInfo);
  } else {
    userInfo = JSON.stringify(info);
  }
  return {
    schoolCode: overrides.schoolCode !== undefined ? overrides.schoolCode : '11319',
    Identity: overrides.Identity !== undefined ? overrides.Identity : '2',
    Token: token,
    userInfo: userInfo
  };
}

function profile(overrides) {
  return Object.assign({
    studentID: STUDENT_ID,
    name: STUDENT_NAME,
    className: CLASS_NAME,
    academy: ACADEMY,
    schoolName: SCHOOL_NAME
  }, overrides || {});
}

function baseTerms() {
  return [
    {xnxq01id: 'SYN-TERM-A', xqmc: '2026-2027学年第一学期', isdqxq: true},
    {xnxq01id: 'SYN-TERM-B', xqmc: '2026-2027学年第二学期', isdqxq: false}
  ];
}

function baseModes() {
  return [
    {kbjcmsid: 'SYN-MODE-A', kbjcmsmc: '合成时间模式甲', mrms: 1},
    {kbjcmsid: 'SYN-MODE-B', kbjcmsmc: '合成时间模式乙', mrms: 0}
  ];
}

function rowOne(overrides) {
  return Object.assign({
    classWeek: '1-8周(单),12周',
    classWeekDetails: [1, 3, 5, 7, 12],
    teacherName: '合成教师甲、合成教师乙',
    weekNoteDetail: '10102,105',
    ktmc: '合成教学班A',
    classTime: '1010205',
    jx0408id: 'SYN-JX-1',
    kch: 'SYN-KCH-1',
    courseName: '合成课程一',
    zxs: '3.0',
    peopleNumber: '42',
    maxClassTime: '8',
    khfs: '合成考核',
    location: '合成楼A101'
  }, overrides || {});
}

function rowTwo(overrides) {
  return Object.assign({
    classWeek: '2-6周(双)',
    classWeekDetails: [2, 4, 6],
    teacherName: '合成教师丙',
    weekNoteDetail: '20304',
    ktmc: '合成教学班B',
    classTime: '20304',
    jx0408id: 'SYN-JX-2',
    kch: 'SYN-KCH-2',
    courseName: '合成课程二',
    zxs: '2',
    peopleNumber: '30',
    maxClassTime: '6',
    khfs: '合成考查',
    location: '合成楼B202',
    startTime: '08:00',
    endTIme: '09:40'
  }, overrides || {});
}

function makeBlock(items, overrides) {
  return Object.assign({
    week: 'all',
    nowWeeked: true,
    weekday: '二',
    date: [{mxrq:'2026-09-08',xqid:2}],
    item: items,
    jcdatalist: [],
    nkbList: []
  }, overrides || {});
}

function baseCourses() {
  return {
    code: 1,
    data: [
      makeBlock([rowOne()], {week: 'all', nowWeeked: true, weekday: '二', date: [{mxrq:'2026-09-08',xqid:2}]}),
      makeBlock([rowTwo()], {week: 'all', nowWeeked: false, weekday: '三', date: [{mxrq:'2026-09-16',xqid:3}]})
    ],
    jcdatalist: [],
    nkbList: []
  };
}

function materialize(value) {
  if (value && value.__response === true) {
    return {text: String(value.text), meta: value.meta || {}};
  }
  return {text: JSON.stringify(value), meta: {}};
}

function makeResponse(url, text, meta, signal) {
  const bytes = meta.bytes || new TextEncoder().encode(text);
  return {
    ok: meta.ok !== false,
    redirected: !!meta.redirected,
    url: meta.url !== undefined ? meta.url : url.href,
    headers: {
      get: function (name) {
        if (String(name).toLowerCase() === 'content-length') return meta.lengthHeader || null;
        if (String(name).toLowerCase() !== 'content-type') return null;
        return meta.contentType !== undefined
          ? meta.contentType : 'application/json; charset=UTF-8';
      }
    },
    body: { getReader: function () {
      let offset = 0;
      return { read: async function () {
        if (meta.hangRead) return new Promise(function(resolve,reject) {
          const abort = function() { reject(Object.assign(new Error('synthetic body abort'),{name:'AbortError'})); };
          if (signal.aborted) abort(); else signal.addEventListener('abort',abort,{once:true});
        });
        if (meta.readError) throw new Error('synthetic read error');
        if (offset >= bytes.length) return {done:true};
        const next = Math.min(bytes.length,offset+(meta.chunkSize || bytes.length));
        const value = bytes.slice(offset,next); offset=next;return {done:false,value:value};
      }, cancel: async function () {}, releaseLock: function () {} };
    } },
    arrayBuffer: async function () {
      if (meta.readError) throw new Error('synthetic read error');
      return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
    },
    text: async function () { return text; }
  };
}

async function runScenario(options) {
  options = options || {};
  const messages = [];
  const requests = [];
  const saves = [];
  const events = [];
  const state = {
    session: session(options.session),
    profile: profile(options.profile),
    terms: options.terms !== undefined ? clone(options.terms) : baseTerms(),
    modes: options.modes !== undefined ? clone(options.modes) : baseModes(),
    courses: options.courses !== undefined ? clone(options.courses) : baseCourses(),
    login: !!options.login
  };
  const location = new URL(options.url || ORIGIN + '/#/login');
  let profileReads = 0;
  let termsReads = 0;
  let modesReads = 0;
  let coursesReads = 0;
  let resolveDone;
  let finished = false;
  const done = new Promise(function (resolve) { resolveDone = resolve; });
  function finish() {
    if (finished) return;
    finished = true;
    resolveDone();
  }

  const sessionStorage = {
    getItem: function (key) {
      return Object.prototype.hasOwnProperty.call(state.session, key) ? state.session[key] : null;
    }
  };

  const document = {
    querySelector: function (selector) {
      return state.login && String(selector).indexOf('password') !== -1 ? {} : null;
    }
  };

  const api = {
    showAlert: async function (title, text) {
      messages.push({action: 'alert', title: String(title), text: String(text)});
    },
    showSingleSelection: async function (title, raw, initial) {
      const entry = {
        action: 'selection',
        title: String(title),
        options: JSON.parse(raw),
        initial: initial
      };
      messages.push(entry);
      if (typeof options.onSelection === 'function') {
        const result = options.onSelection(title, state, entry);
        if (result !== undefined) return result;
      }
      if (String(title).indexOf('学期') !== -1) {
        return options.termSelection !== undefined ? options.termSelection : 1;
      }
      return options.modeSelection !== undefined ? options.modeSelection : 1;
    },
    showPrompt: async function (title, tip, value, validator) {
      messages.push({
        action: 'prompt',
        title: String(title),
        tip: String(tip),
        value: value,
        validator: String(validator || '')
      });
      if (typeof options.onPrompt === 'function') {
        const result = options.onPrompt(title, state, location);
        if (result !== undefined) return result;
      }
      if (String(title).indexOf('周一') !== -1) {
        return options.monday !== undefined ? options.monday : '2026-09-07';
      }
      return options.weeks !== undefined ? options.weeks : '18';
    },
    savePresetTimeSlots: async function (value) {
      const entry = {action: 'times', data: JSON.parse(value)};
      saves.push(entry);
      events.push({type: 'save', action: 'times'});
    },
    saveCourseConfig: async function (value) {
      const entry = {action: 'config', data: JSON.parse(value)};
      saves.push(entry);
      events.push({type: 'save', action: 'config'});
    },
    saveImportedCourses: async function (value) {
      const entry = {action: 'courses', data: JSON.parse(value)};
      saves.push(entry);
      events.push({type: 'save', action: 'courses'});
    },
    notifyTaskCompletion: function () { finish(); }
  };

  async function fetchMock(rawUrl, init) {
    const url = new URL(rawUrl);
    const record = {
      url: url.href,
      path: url.pathname,
      params: Object.fromEntries(url.searchParams.entries()),
      method: init.method,
      headers: Object.assign({}, init.headers),
      body: init.body,
      credentials: init.credentials,
      redirect: init.redirect,
      cache: init.cache,
      referrer: init.referrer
    };
    requests.push(record);
    events.push({type: 'request', path: url.pathname});

    assert.equal(url.origin, ORIGIN, 'every request must target the official origin');
    assert.equal(init.method, 'POST', 'protocol uses POST with query params');
    assert.equal(init.credentials, 'same-origin');
    assert.equal(init.redirect, 'error');
    assert.equal(init.cache, 'no-store');
    assert.equal(init.body, undefined, 'no guessed body variant');
    assert.equal(typeof init.headers.token, 'string');
    assert.ok(init.headers.token.length > 0, 'token header must be present');

    let payload;
    if (url.pathname === API + '/student/my') {
      profileReads += 1;
      payload = options.profileResponse !== undefined
        ? (typeof options.profileResponse === 'function'
          ? options.profileResponse(profileReads) : options.profileResponse)
        : {code: 1, data: [state.profile]};
    } else if (url.pathname === API + '/getXnxqList') {
      termsReads += 1;
      payload = options.termsResponse !== undefined
        ? (typeof options.termsResponse === 'function'
          ? options.termsResponse(termsReads) : options.termsResponse)
        : clone(state.terms);
    } else if (url.pathname === API + '/Get_sjkbms') {
      modesReads += 1;
      payload = options.modesResponse !== undefined
        ? (typeof options.modesResponse === 'function'
          ? options.modesResponse(modesReads) : options.modesResponse)
        : {code: 1, data: clone(state.modes)};
    } else if (url.pathname === API + '/student/curriculum') {
      coursesReads += 1;
      assert.equal(url.searchParams.get('week'), 'all', 'week must be explicitly all');
      payload = options.coursesResponse !== undefined
        ? (typeof options.coursesResponse === 'function'
          ? options.coursesResponse(coursesReads, state) : options.coursesResponse)
        : clone(state.courses);
    } else {
      throw new Error('unexpected synthetic request ' + url.pathname);
    }

    const packed = materialize(payload);
    return makeResponse(url, packed.text, packed.meta, init.signal);
  }

  const context = vm.createContext({
    window: {
      AndroidBridgePromise: api,
      sessionStorage: sessionStorage,
      location: location,
      document: document
    },
    location: location,
    document: document,
    sessionStorage: sessionStorage,
    URL: URL,
    URLSearchParams: URLSearchParams,
    AbortController: AbortController,
    Uint8Array: Uint8Array,
    TextEncoder: TextEncoder,
    TextDecoder: TextDecoder,
    setTimeout: function(fn,ms) {return setTimeout(fn,ms === 20000 ? 30 : ms);},
    clearTimeout: clearTimeout,
    fetch: fetchMock,
    console: console
  });

  vm.runInContext(COMMON, context, {filename: 'school-common.js'});
  vm.runInContext(ADAPTER, context, {filename: 'juwp-h5-personal.js'});

  let timer;
  try {
    await Promise.race([
      done,
      new Promise(function (_, reject) {
        timer = setTimeout(function () { reject(new Error('adapter timeout')); }, 3000);
      })
    ]);
  } finally {
    clearTimeout(timer);
  }

  return {
    messages: messages,
    requests: requests,
    saves: saves,
    events: events,
    location: location,
    state: state,
    reads: {
      profile: profileReads,
      terms: termsReads,
      modes: modesReads,
      courses: coursesReads
    }
  };
}

let checks = 0;

function assertNoLeak(result) {
  const visible = JSON.stringify(result.messages) + JSON.stringify(result.saves);
  assert.equal(visible.indexOf(TOKEN), -1, 'session token leaked into UI/save data');
  assert.equal(visible.indexOf(STUDENT_ID), -1, 'studentID leaked into UI/save data');
  assert.equal(visible.indexOf(STUDENT_NAME), -1, 'student name leaked into UI/save data');
  assert.equal(visible.indexOf(CLASS_NAME), -1, 'class profile leaked into UI/save data');
  assert.equal(visible.indexOf(ACADEMY), -1, 'academy profile leaked into UI/save data');
  assert.equal(visible.indexOf(SCHOOL_NAME), -1, 'school profile leaked into UI/save data');
}

function assertRejected(result, name) {
  assert.equal(result.saves.length, 0, name + ': rejection staged config/courses/times');
  assert.equal(result.events.some(function (e) {
    return e.type === 'save';
  }), false, name + ': rejection reached a save call');
  assert.equal(result.messages.some(function (m) {
    return m.action === 'alert';
  }), true, name + ': rejection did not alert');
  assert.equal(JSON.stringify(result.messages).indexOf(TOKEN), -1,
    name + ': token leaked in rejection message');
  checks += 1;
}

async function rejectCase(name, options) {
  const result = await runScenario(options);
  assertRejected(result, name);
  return result;
}

async function acceptCase(name, options) {
  const result = await runScenario(options);
  assert.equal(result.saves.length, 2, name + ': expected exactly config+courses; ' + JSON.stringify(result.messages));
  assert.deepEqual(result.saves.map(function (s) { return s.action; }),
    ['config', 'courses'], name + ': config/courses staging order');
  assert.equal(result.events.filter(function (e) { return e.type === 'save'; })
    .map(function (e) { return e.action; }).join(','), 'config,courses');
  assertNoLeak(result);
  checks += 1;
  return result;
}

function summary(course) {
  return [course.name, course.teacher, course.position, course.day,
    course.startSection, course.endSection, course.weeks];
}

(async function () {
  // ---------------------------------------------------------------- happy path
  let r = await acceptCase('happy path', {termSelection: 1, modeSelection: 1});
  assert.deepEqual(r.saves[0].data,
    {semesterTotalWeeks: 18, semesterStartDate: '2026-09-07'});
  const courses = r.saves[1].data;
  assert.equal(courses.length, 3);
  assert.deepEqual(courses.map(summary), [
    ['合成课程一', '合成教师甲、合成教师乙', '合成楼A101', 1, 1, 2, [1, 3, 5, 7, 12]],
    ['合成课程一', '合成教师甲、合成教师乙', '合成楼A101', 1, 5, 5, [1, 3, 5, 7, 12]],
    ['合成课程二', '合成教师丙', '合成楼B202', 2, 3, 4, [2, 4, 6]]
  ]);
  assert.equal('isCustomTime' in courses[0], false);
  assert.equal(courses[0].isCustomTime, undefined);
  assert.equal(courses[2].isCustomTime, true);
  assert.equal(courses[2].customStartTime, '08:00');
  assert.equal(courses[2].customEndTime, '09:40');
  assert.equal(courses[2].startSection, 3);
  assert.equal(courses[2].endSection, 4);

  const extra0 = JSON.parse(courses[0].extraInfo);
  assert.equal(extra0.semester, '2026-2027学年第二学期');
  assert.equal(extra0.ktmc, '合成教学班A');
  assert.equal(extra0.kch, 'SYN-KCH-1');
  assert.equal(extra0.jx0408id, 'SYN-JX-1');
  assert.equal(extra0.sourceBlock, 1);
  assert.equal(extra0.sourceRow, 1);
  assert.equal(extra0.zxs, '3.0');
  assert.equal(extra0.peopleNumber, '42');
  assert.equal(extra0.maxClassTime, '8');
  assert.equal(extra0.khfs, '合成考核');
  assert.equal(courses[1].extraInfo, courses[0].extraInfo);
  assert.equal(JSON.parse(courses[2].extraInfo).ktmc, '合成教学班B');
  courses.forEach(function (course) {
    const extra = JSON.parse(course.extraInfo);
    ['token', 'userInfo', 'studentID', 'schoolCode', 'studentName', 'schoolName']
      .forEach(function (key) {
        assert.equal(Object.prototype.hasOwnProperty.call(extra, key), false,
          'forbidden key written to extraInfo: ' + key);
      });
  });

  const selections = r.messages.filter(function (m) { return m.action === 'selection'; });
  assert.equal(selections.length, 2);
  assert.equal(selections[0].title, '要导入的学期');
  assert.deepEqual(selections[0].options,
    ['2026-2027学年第一学期', '2026-2027学年第二学期']);
  assert.equal(selections[0].initial, -1);
  assert.equal(selections[1].title, '要导入的时间模式');
  assert.deepEqual(selections[1].options, ['合成时间模式甲', '合成时间模式乙']);
  assert.equal(selections[1].initial, -1);

  const prompts = r.messages.filter(function (m) { return m.action === 'prompt'; });
  assert.equal(prompts.length, 2);
  assert.equal(prompts[0].title, '第一周周一');
  assert.equal(prompts[0].value, '', 'Monday prompt must not be prefilled');
  assert.equal(prompts[1].title, '学期教学周数');
  assert.equal(prompts[1].value, '', 'week-count prompt must not be prefilled');

  assert.equal(r.requests.length, 8);
  assert.deepEqual(r.requests.map(function (req) { return req.path; }), [
    API + '/student/my', API + '/getXnxqList', API + '/Get_sjkbms',
    API + '/student/curriculum', API + '/student/curriculum',
    API + '/student/my', API + '/getXnxqList', API + '/Get_sjkbms'
  ]);
  assert.equal(r.requests.some(function (req) {
    return req.path === API + '/currentTerm';
  }), false, 'current term must not be requested');
  assert.equal(r.requests.some(function (req) {
    return /login|password|captcha|sso/i.test(req.path);
  }), false, 'adapter must not touch login/password routes');
  r.requests.forEach(function (req) {
    assert.equal(req.method, 'POST');
    assert.equal(req.body, undefined);
    assert.equal(req.credentials, 'same-origin');
    assert.equal(req.redirect, 'error');
    assert.equal(req.cache, 'no-store');
    assert.equal(typeof req.headers.token, 'string');
    assert.ok(req.headers.token.length > 0);
  });

  const curriculumRequests = r.requests.filter(function (req) {
    return req.path === API + '/student/curriculum';
  });
  assert.equal(curriculumRequests.length, 2);
  curriculumRequests.forEach(function (req) {
    assert.equal(req.params.week, 'all');
    assert.equal(req.params.xnxq01id, 'SYN-TERM-B');
    assert.equal(req.params.kbjcmsid, 'SYN-MODE-B');
    assert.equal(req.headers.token, TOKEN);
    assert.equal(Object.keys(req.params).sort().join(','),
      'kbjcmsid,week,xnxq01id');
  });
  ['/student/my', '/getXnxqList', '/Get_sjkbms'].forEach(function (suffix) {
    r.requests.filter(function (req) { return req.path === API + suffix; })
      .forEach(function (req) {
        assert.equal(Object.keys(req.params).length, 0,
          'identity/options requests carry no guessed query');
      });
  });

  const lastCourseRead = r.events.map(function (e) {
    return e.type === 'request' && e.path === API + '/student/curriculum';
  }).lastIndexOf(true);
  const firstSave = r.events.findIndex(function (e) { return e.type === 'save'; });
  assert.ok(firstSave > lastCourseRead, 'staging must happen after all raw reads');
  assert.equal(r.saves.some(function (s) { return s.action === 'times'; }), false,
    'adapter must not guess preset time slots');
  assert.equal(r.messages.some(function (m) { return m.action === 'times'; }), false);

  r = await acceptCase('40 week semester upper bound', {weeks: '40'});
  assert.equal(r.saves[0].data.semesterTotalWeeks, 40);

  // ------------------------------------------------- no implicit term/mode default
  await rejectCase('cancel term selection', {termSelection: null});
  await rejectCase('cancel mode selection', {modeSelection: null});
  await rejectCase('negative term index', {termSelection: -1});
  await rejectCase('term index out of range', {termSelection: 2});
  await rejectCase('mode index is not numeric', {modeSelection: 'x'});
  await rejectCase('empty Monday', {monday: ''});
  await rejectCase('Tuesday is not Monday', {monday: '2026-09-08'});
  await rejectCase('invalid calendar date', {monday: '2026-02-30'});
  await rejectCase('empty week count', {weeks: ''});
  await rejectCase('zero week count', {weeks: '0'});
  await rejectCase('41 week count', {weeks: '41'});
  await rejectCase('decimal week count', {weeks: '18.5'});
  await rejectCase('week count below course max', {weeks: '8'});

  // ------------------------------------- origin, school, role, token, profile
  await rejectCase('https origin', {url: 'https://jiaowuh5.juwp.edu.cn/#/login'});
  await rejectCase('non-root SPA path', {url: ORIGIN + '/njwhd/#/login'});
  await rejectCase('foreign origin', {url: 'http://evil.invalid/#/login'});
  await rejectCase('wrong schoolCode', {session: {schoolCode: '11320'}});
  await rejectCase('wrong Identity role', {session: {Identity: '1'}});
  await rejectCase('empty Token', {session: {Token: ''}});
  await rejectCase('teacher userType', {session: {info: {userType: 1}}});
  await rejectCase('userInfo token mismatch',
    {session: {info: {token: 'SYNTHETIC-OTHER-TOKEN'}}});
  await rejectCase('userInfo is not JSON', {session: {userInfo: 'not json'}});
  await rejectCase('userNo/studentID mismatch', {profile: {studentID: 'SYN-OTHER'}});
  await rejectCase('schoolName mismatch', {profile: {schoolName: '合成其他大学'}});
  await rejectCase('profile data is not unique', {
    profileResponse: {code: 1, data: [profile(), profile()]}
  });
  const loginGuard = await rejectCase('password form present', {login: true});
  assert.equal(loginGuard.requests.length, 0, 'guard must stop before any request');

  // --------------------------------------------- envelope and transport failures
  await rejectCase('profile code0', {profileResponse: {code: 0}});
  await rejectCase('profile code403', {profileResponse: {code: '403'}});
  await rejectCase('profile success false', {
    profileResponse: {code: 1, success: false, data: [profile()]}
  });
  await rejectCase('profile login message', {
    profileResponse: {code: 1, msg: '登录已失效，请重新登录', data: [profile()]}
  });
  await rejectCase('profile HTML', {
    profileResponse: {
      __response: true,
      text: '<html>登录</html>',
      meta: {contentType: 'text/html; charset=UTF-8'}
    }
  });
  await rejectCase('profile non-JSON text', {
    profileResponse: {__response: true, text: 'not json'}
  });
  await rejectCase('profile response URL changed', {
    profileResponse: {
      __response: true,
      text: JSON.stringify({code: 1, data: [profile()]}),
      meta: {url: ORIGIN + '/#/login'}
    }
  });
  await rejectCase('profile redirected flag', {
    profileResponse: {
      __response: true,
      text: JSON.stringify({code: 1, data: [profile()]}),
      meta: {redirected: true}
    }
  });
  await rejectCase('profile HTTP not ok', {
    profileResponse: {
      __response: true,
      text: JSON.stringify({code: 1, data: [profile()]}),
      meta: {ok: false}
    }
  });
  await rejectCase('profile GBK charset', {
    profileResponse: {
      __response: true,
      text: JSON.stringify({code: 1, data: [profile()]}),
      meta: {contentType: 'application/json; charset=GBK'}
    }
  });
  await rejectCase('profile invalid UTF-8', {
    profileResponse: {
      __response: true,
      text: 'x',
      meta: {bytes: Uint8Array.of(0x7b, 0xff, 0x7d)}
    }
  });
  await rejectCase('profile response oversized', {
    profileResponse: {__response: true, text: 'a'.repeat(1500001)}
  });
  await rejectCase('profile empty body', {
    profileResponse: {__response: true, text: ''}
  });
  await rejectCase('profile timeout', {
    profileResponse: function () {
      throw Object.assign(new Error('synthetic timeout'), {name: 'AbortError'});
    }
  });
  await rejectCase('profile network failure with token in message', {
    profileResponse: function () {
      throw new Error('synthetic network ' + TOKEN);
    }
  });

  await rejectCase('terms response is a code wrapper', {
    termsResponse: {code: 1, data: []}
  });
  await rejectCase('terms empty array', {termsResponse: []});
  await rejectCase('terms duplicate labels', {
    terms: [
      {xnxq01id: 'SYN-TERM-A', xqmc: '合成同名学期'},
      {xnxq01id: 'SYN-TERM-B', xqmc: '合成同名学期'}
    ]
  });
  await rejectCase('modes code0', {modesResponse: {code: 0}});
  await rejectCase('modes empty', {modesResponse: {code: 1, data: []}});
  await rejectCase('modes duplicate ids', {
    modes: [
      {kbjcmsid: 'SYN-MODE-A', kbjcmsmc: '合成模式甲'},
      {kbjcmsid: 'SYN-MODE-A', kbjcmsmc: '合成模式乙'}
    ]
  });

  // ----------------------------------------------- data blocks, rows, pagination
  await rejectCase('courses code0', {coursesResponse: {code: 0}});
  await rejectCase('courses empty data', {coursesResponse: {code: 1, data: []}});
  await rejectCase('courses all blocks empty',
    {coursesResponse: {code: 1, data: [makeBlock([]), makeBlock([])]}});
  await rejectCase('second data block has bad row', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne()]), makeBlock([rowOne({courseName: ''})])]
    }
  });
  await rejectCase('data block item is not an array', {
    coursesResponse: {code: 1, data: [{week: 'all', item: 'bad'}]}
  });
  await rejectCase('data block is not an object', {
    coursesResponse: {code: 1, data: ['bad']}
  });
  await rejectCase('root nkbList nonempty', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()])], nkbList: [{}]}
  });
  await rejectCase('block nkbList nonempty', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()], {nkbList: [{}]})]}
  });
  await rejectCase('hasMore true', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()])], hasMore: true}
  });
  await rejectCase('totalPages 2', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()])], totalPages: 2}
  });
  await rejectCase('pageNumber 2', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()])], pageNumber: 2}
  });
  await rejectCase('total mismatch', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne()])], total: 99}
  });
  await rejectCase('unknown root object', {
    coursesResponse: {
      code: 1,
      data: [makeBlock([rowOne()])],
      other: {rows: [{}]}
    }
  });
  await rejectCase('unknown block object', {
    coursesResponse: {
      code: 1,
      data: [makeBlock([rowOne()], {other: {rows: [{}]}})]
    }
  });
  await rejectCase('unknown row object', {
    coursesResponse: {code: 1, data: [makeBlock([rowOne({nested: {x: 1}})])]}
  });
  await rejectCase('second course read fails', {
    coursesResponse: function (index) {
      if (index > 1) throw new Error('synthetic second read failure');
      return baseCourses();
    }
  });
  await rejectCase('second course read changes metadata', {
    coursesResponse: function (index) {
      const value = baseCourses();
      if (index > 1) value.data[0].date = ['2026-09-09'];
      return value;
    }
  });
  await rejectCase('second course read changes row', {
    coursesResponse: function (index) {
      const value = baseCourses();
      if (index > 1) value.data[0].item[0].teacherName = '合成教师丁';
      return value;
    }
  });
  await rejectCase('second course read reorders blocks', {
    coursesResponse: function (index) {
      const value = baseCourses();
      if (index > 1) value.data.reverse();
      return value;
    }
  });
  await rejectCase('token changes during second course read', {
    coursesResponse: function (index, state) {
      if (index > 1) {
        state.session.Token = 'SYNTHETIC-CHANGED-TOKEN';
        state.session.userInfo = JSON.stringify({
          userNo: STUDENT_ID,
          name: STUDENT_NAME,
          userType: 2,
          token: 'SYNTHETIC-CHANGED-TOKEN'
        });
      }
      return baseCourses();
    }
  });

  // ---------------------------------------------------------- bad course rows
  const badRows = [
    ['missing courseName', {courseName: ''}],
    ['missing teacherName', {teacherName: ''}],
    ['missing location', {location: ''}],
    ['missing ktmc', {ktmc: ''}],
    ['missing kch', {kch: ''}],
    ['missing classTime', {classTime: ''}],
    ['missing both week fields', {classWeek: '', classWeekDetails: null}],
    ['classWeek/details conflict', {classWeek: '1-4', classWeekDetails: [1, 3]}],
    ['classWeekDetails range instead of list', {classWeekDetails: ['1-2']}],
    ['classWeekDetails duplicate', {classWeekDetails: [1, 1]}],
    ['classWeek out of range', {classWeek: '1-41', classWeekDetails: null}],
    ['classTime even length', {classTime: '1010'}],
    ['classTime day zero', {classTime: '00102'}],
    ['classTime day eight', {classTime: '80102'}],
    ['classTime period zero', {classTime: '10001'}],
    ['classTime period 99', {classTime: '1010299'}],
    ['classTime duplicate period', {classTime: '1010201'}],
    ['weekNote wrong day', {weekNoteDetail: '20102'}],
    ['weekNote missing fact', {weekNoteDetail: '10102'}],
    ['weekNote extra fact', {weekNoteDetail: '10102,105,107'}],
    ['clock missing end', {startTime: '08:00'}],
    ['clock missing start', {endTIme: '09:40'}],
    ['clock not HH:mm', {startTime: '8:00', endTIme: '09:40'}],
    ['clock equal', {startTime: '08:00', endTIme: '08:00'}],
    ['clock reversed', {startTime: '09:40', endTIme: '08:00'}],
    ['clock on discrete groups', {
      classTime: '1010205',
      weekNoteDetail: '10102,105',
      startTime: '08:00',
      endTIme: '09:40'
    }],
    ['clock on multiple days', {
      classTime: '10102,20102',
      weekNoteDetail: '10102,20102',
      startTime: '08:00',
      endTIme: '09:40'
    }]
  ];
  for (const item of badRows) {
    await rejectCase('row ' + item[0], {
      courses: {code: 1, data: [makeBlock([rowOne(item[1])])]}
    });
  }

  // ---------------------------------------------- details / classWeek variants
  r = await acceptCase('classWeekDetails only', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: null,
        classWeekDetails: [2, 4, 6],
        classTime: '10102',
        weekNoteDetail: '10102'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data[0].weeks, [2, 4, 6]);

  r = await acceptCase('classWeek only', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '3-5',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: '10102'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data[0].weeks, [3, 4, 5]);

  r = await acceptCase('string classWeekDetails list', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: null,
        classWeekDetails: '1,3,5',
        classTime: '10102',
        weekNoteDetail: '10102'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data[0].weeks, [1, 3, 5]);

  r = await acceptCase('parity weeks are not expanded by max', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-8周(单)',
        classWeekDetails: [1, 3, 5, 7],
        classTime: '10102',
        weekNoteDetail: '10102'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data[0].weeks, [1, 3, 5, 7]);

  r = await acceptCase('weekNoteDetail optional', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: null
      })])]
    }
  });
  assert.equal(r.saves[1].data.length, 1);

  // -------------------------------------------------------- discrete sections
  r = await acceptCase('discrete sections preserved', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10105',
        weekNoteDetail: '101,105'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data.map(function (course) {
    return [course.day, course.startSection, course.endSection];
  }), [[1, 1, 1], [1, 5, 5]]);
  assert.deepEqual(r.saves[1].data.map(function (course) { return course.weeks; }),
    [[1, 2], [1, 2]]);

  r = await acceptCase('contiguous sections stay one meeting', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '101020304',
        weekNoteDetail: '10102,10304'
      })])]
    }
  });
  assert.deepEqual(r.saves[1].data.map(function (course) {
    return [course.startSection, course.endSection];
  }), [[1, 4]]);

  // ----------------------------------------------------------- custom clock
  r = await acceptCase('single group custom clock', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: '10102',
        startTime: '08:00',
        endTIme: '09:40'
      })])]
    }
  });
  let custom = r.saves[1].data[0];
  assert.equal(custom.isCustomTime, true);
  assert.equal(custom.customStartTime, '08:00');
  assert.equal(custom.customEndTime, '09:40');
  assert.equal(custom.startSection, 1);
  assert.equal(custom.endSection, 2);

  r = await acceptCase('no clock leaves native preview', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: '10102',
        startTime: null,
        endTIme: null
      })])]
    }
  });
  assert.equal('isCustomTime' in r.saves[1].data[0], false);
  assert.equal(r.saves[1].data[0].customStartTime, undefined);
  assert.equal(r.saves[1].data[0].customEndTime, undefined);

  r = await acceptCase('empty clock strings mean no custom time', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: '10102',
        startTime: '',
        endTIme: ''
      })])]
    }
  });
  assert.equal('isCustomTime' in r.saves[1].data[0], false);

  // ---------------------------------- same course, different teacher/location
  r = await acceptCase('same course keeps distinct teacher/location/class', {
    courses: {
      code: 1,
      data: [makeBlock([
        rowOne({
          courseName: '合成同名课',
          kch: 'SYN-K-SAME',
          classWeek: '1-2',
          classWeekDetails: null,
          classTime: '10102',
          weekNoteDetail: '10102',
          teacherName: '合成教师甲、合成教师乙',
          location: '合成楼A101',
          ktmc: '合成班甲'
        }),
        rowOne({
          courseName: '合成同名课',
          kch: 'SYN-K-SAME',
          classWeek: '1-2',
          classWeekDetails: null,
          classTime: '10102',
          weekNoteDetail: '10102',
          teacherName: '合成教师丙',
          location: '合成楼B202',
          ktmc: '合成班乙'
        })
      ])]
    }
  });
  assert.equal(r.saves[1].data.length, 2);
  assert.deepEqual(r.saves[1].data.map(function (course) {
    return [course.name, course.teacher, course.position];
  }), [
    ['合成同名课', '合成教师甲、合成教师乙', '合成楼A101'],
    ['合成同名课', '合成教师丙', '合成楼B202']
  ]);
  assert.equal(JSON.parse(r.saves[1].data[0].extraInfo).ktmc, '合成班甲');
  assert.equal(JSON.parse(r.saves[1].data[1].extraInfo).ktmc, '合成班乙');

  // -------------------------- metadata/date is snapshot, not course weekday
  r = await acceptCase('metadata date never becomes weekday', {
    courses: {
      code: 1,
      data: [makeBlock([rowOne({
        classWeek: '1-2',
        classWeekDetails: null,
        classTime: '10102',
        weekNoteDetail: '10102'
      })], {
        week: 'all',
        nowWeeked: true,
        weekday: '二',
        date: [{mxrq:'2026-09-08',xqid:2}]
      })]
    }
  });
  assert.equal(r.saves[1].data[0].day, 1);
  assert.deepEqual(r.saves[1].data[0].weeks, [1, 2]);

  r = await acceptCase('complete pagination fields at page one', {
    courses: Object.assign(baseCourses(), {
      hasMore: false,
      pages: 1,
      totalPages: 1,
      pageNumber: 1,
      currentPage: 1,
      total: 2
    })
  });
  assert.equal(r.saves[1].data.length, 3);

  r = await acceptCase('non-empty jcdatalist is known snapshot metadata', {
    courses: Object.assign(baseCourses(), {
      jcdatalist: [{XJMC: '第一节', DJMC: '第一二节'}]
    })
  });
  assert.equal(r.saves[1].data.length, 3);

  // ------------------------------------------------ post-interaction changes
  await rejectCase('token changes during Monday prompt', {
    onPrompt: function (title, state) {
      if (title === '第一周周一') {
        state.session.Token = 'SYNTHETIC-CHANGED-TOKEN';
        state.session.userInfo = JSON.stringify({
          userNo: STUDENT_ID,
          name: STUDENT_NAME,
          userType: 2,
          token: 'SYNTHETIC-CHANGED-TOKEN'
        });
      }
    }
  });
  await rejectCase('userInfo changes during week prompt', {
    onPrompt: function (title, state) {
      if (title === '学期教学周数') {
        state.session.userInfo = JSON.stringify({
          userNo: STUDENT_ID,
          name: '合成改名',
          userType: 2,
          token: TOKEN
        });
      }
    }
  });
  await rejectCase('URL changes during prompt', {
    onPrompt: function (title, state, currentLocation) {
      if (title === '第一周周一') currentLocation.href = ORIGIN + '/#/changed';
    }
  });
  await rejectCase('password form appears during prompt', {
    onPrompt: function (title, state) {
      if (title === '第一周周一') state.login = true;
    }
  });
  await rejectCase('profile changes on second read', {
    profileResponse: function (index) {
      return {code: 1, data: [profile(index > 1 ? {name: '合成改名'} : {})]};
    }
  });
  await rejectCase('profile studentID changes on second read', {
    profileResponse: function (index) {
      return {code: 1, data: [profile(index > 1 ? {studentID: 'SYN-OTHER'} : {})]};
    }
  });
  await rejectCase('terms change on second read', {
    termsResponse: function (index) {
      if (index === 1) return baseTerms();
      const value = baseTerms();
      value[1].xqmc = '2026-2027学年第二学期（变更）';
      return value;
    }
  });
  await rejectCase('modes change on second read', {
    modesResponse: function (index) {
      const value = {code: 1, data: baseModes()};
      if (index > 1) value.data[1].mrms = 1;
      return value;
    }
  });

  // Primary-owned regression cases for stricter source-backed behavior.
  await rejectCase('server ignores all and returns one numeric week', {courses:{...baseCourses(),data:[makeBlock([rowOne()],{week:'1'})]}});
  await rejectCase('unknown scalar arrangement', {courses:{...baseCourses(),unparsed:'未核验实践'}});
  await rejectCase('unknown date arrangement', {courses:{...baseCourses(),data:[makeBlock([rowOne()],{date:[{rows:[rowOne()]}]})]}});
  await rejectCase('unknown section metadata', {courses:{...baseCourses(),jcdatalist:[{practice:[rowOne()]}]}});
  await rejectCase('another institution containing school name', {profile:{schoolName:'其他学院江西水利电力大学'}});
  await rejectCase('initial name mismatch', {profile:{name:'合成其他人'}});
  await acceptCase('official former school name', {profile:{schoolName:'南昌工程学院'}});
  await acceptCase('known nowWeek and uppercase Msg', {courses:{...baseCourses(),nowWeek:'3',Msg:'成功'}});
  await rejectCase('uppercase Msg login expiry', {courses:{...baseCourses(),Msg:'登录已过期'}});
  await acceptCase('UTF8 across small stream chunks', {coursesResponse:{__response:true,text:JSON.stringify(baseCourses()),meta:{chunkSize:7}}});
  await rejectCase('stream cumulative size cap', {coursesResponse:{__response:true,text:'a'.repeat(1500001),meta:{chunkSize:64000}}});
  await rejectCase('declared excessive length', {coursesResponse:{__response:true,text:JSON.stringify(baseCourses()),meta:{lengthHeader:'1500001'}}});
  await rejectCase('body remains stalled after headers', {coursesResponse:{__response:true,text:JSON.stringify(baseCourses()),meta:{hangRead:true}}});
  await rejectCase('session token echoed in a course field', {courses:{...baseCourses(),data:[makeBlock([rowOne({teacherName:TOKEN})])]}});
  await acceptCase('explicit comma-delimited weeks retain their full set', {courses:{...baseCourses(),data:[makeBlock([rowOne({classWeekDetails:','+rowOne().classWeekDetails.join(',')+','})])]}});
  console.log('PASS: ' + checks +
    ' synthetic JUWP H5 adapter scenarios; vm + mock fetch only, no school traffic.');
})().catch(function (error) {
  console.error(error);
  process.exitCode = 1;
});
