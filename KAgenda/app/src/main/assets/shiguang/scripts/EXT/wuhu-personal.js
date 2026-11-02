(function () {
"use strict";
// SPDX-License-Identifier: MIT
// Original, independent implementation based only on the public protocol facts.
const ROW_KEYS = new Set(["kcm", "jxbid", "kbid", "jxblx", "jxlmc", "jasmc", "skxq", "ksjc", "jsjc"]);
const PAGE = /^(?:pagination|paging|page|pageNo|pageNum|pageIndex|pageNumber|currentPage|pageSize|pageCount|pages|total|totalSize|totalCount|recordsTotal|totalResult|totalPages|hasMore|hasNext|isLastPage|next|nextPage|limit|offset)$/i;
function fail(message) { throw new Error("课程数据无效：" + message); }
function isObject(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
function text(value, what) {
  if (typeof value !== "string" || value.trim() === "" || value.length > 200) fail(what + "必须是有效的非空字符串");
  return value.trim();
}
function idText(value, what) {
  if (typeof value === "string" && value.trim() === value && value.length > 0 && value.length <= 200) return value;
  if (typeof value === "number" && Number.isSafeInteger(value) && value >= 0) return String(value);
  fail(what + "必须是非空标量");
}
function integer(value, min, max, what) {
  let number;
  if (typeof value === "number" && Number.isInteger(value)) number = value;
  else if (typeof value === "string" && /^\d+$/.test(value.trim())) number = Number(value.trim());
  else fail(what + "必须是整数");
  if (number < min || number > max) fail(what + "必须在 " + min + ".." + max + " 之间");
  return number;
}
function statusOk(response, where) {
  if (!isObject(response)) fail(where + "响应不是对象");
  for (const key of ['success','ok']) if (response[key] != null && response[key] !== true) fail(where + '响应状态失败');
  if(response.code != null && ![0,200,'0','200'].includes(response.code)) fail(where + '响应状态失败');
  if(response.status != null && ![0,200,'0','200',true].includes(response.status)) fail(where + '响应状态失败');
  if (response.success === false || response.ok === false || response.status === false) {
    fail(where + "响应状态为失败");
  }
  const error = response.error;
  if (error !== undefined && error !== null && error !== false && error !== "" &&
      !(Array.isArray(error) && error.length === 0)) fail(where + "响应包含错误信息");
  if (response.errors != null && response.errors !== false && response.errors !== '' &&
      !(Array.isArray(response.errors) && response.errors.length === 0)) fail(where + "响应包含错误信息");
  if ((typeof response.status === "number" && response.status >= 400) ||
      (typeof response.status === "string" && /^(?:fail(?:ed)?|error|denied|invalid|[45]\d\d)$/i.test(response.status.trim()))) {
    fail(where + "响应状态为失败");
  }
  if ((typeof response.code === "number" && response.code >= 400) ||
      (typeof response.code === "string" && /^(?:fail(?:ed)?|error|[45]\d\d)$/i.test(response.code.trim()))) {
    fail(where + "响应状态码为失败");
  }
}
// Unknown nonempty arrays/objects are unsafe structured branches. Pagination or
// total declarations are also rejected: the protocol requires the full result.
function envelope(object, allowed, where, count = null) {
  const totalNames=/^(?:total|totalSize|totalCount|recordsTotal|totalResult)$/i;
  const hasTotal=Object.keys(object).some(key=>totalNames.test(key) && object[key]!=null && object[key]!=='');
  for (const key of Object.keys(object)) {
    if (allowed.has(key)) continue;
    const value = object[key];
    if (PAGE.test(key) && value != null && value !== '') {
      if (/^(?:hasMore|hasNext)$/.test(key)) { if(value!==false)fail(where+'仍有后续分页'); }
      else if (/^isLastPage$/i.test(key)) { if(value!==true)fail(where+'不是最后一页'); }
      else if (/^(?:next|nextPage)$/i.test(key)) { if(value!==false && value!==0)fail(where+'仍有后续分页'); }
      else if(totalNames.test(key)) { if(count==null || integer(value,0,5000,'分页总数')!==count)fail(where+'分页总数不符'); }
      else if(/^(?:pages|totalPages|pageCount)$/i.test(key)) { if(count==null || (Number(value)!==1 && !(count===0 && Number(value)===0)))fail(where+'分页未完整'); }
      else if(/^(?:page|pageNo|pageNum|pageIndex|pageNumber|currentPage)$/i.test(key))integer(value,0,1,'当前页');
      else if(/^offset$/i.test(key)) { if(Number(value)!==0)fail(where+'分页偏移非零'); }
      else if(/^(?:pageSize|limit)$/i.test(key)) { if(count==null || !hasTotal || integer(value,1,5000,'每页数')<count)fail(where+'未能核对分页完整总数'); }
      else fail(where+'存在未支持分页结构');
    }
    if (Array.isArray(value) && value.length > 0) fail(where + "存在未知结构化字段 " + key);
    if (isObject(value) && Object.keys(value).length > 0) fail(where + "存在未知结构化字段 " + key);
  }
}
function readCalendar(calendar) {
  if (!Array.isArray(calendar) || calendar.length === 0 || calendar.length > 1000) fail("weekCalendar 必须是非空字符串数组");
  for (const item of calendar) text(item, "weekCalendar 项");
  const first = calendar[0];
  const cut = first.indexOf(":");
  const prefix = cut < 0 ? first : first.slice(0, cut);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(prefix)) fail("weekCalendar 首项前缀必须是 YYYY-MM-DD");
  const year = Number(prefix.slice(0, 4));
  const month = Number(prefix.slice(5, 7));
  const day = Number(prefix.slice(8, 10));
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (year < 2000 || year > 2100 || month < 1 || month > 12 || day < 1 || day > days[month - 1]) {
    fail("weekCalendar 首项前缀不是有效日期");
  }
  const date = new Date(Date.UTC(2000, 0, 1));
  date.setUTCFullYear(year, month - 1, day);
  date.setUTCHours(0, 0, 0, 0);
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 ||
      date.getUTCDate() !== day || date.getUTCDay() !== 1) {
    fail("weekCalendar 首项日期不是周一，拒绝猜测校历或周偏移");
  }
  return prefix;
}
function readSchool(school) {
  if (!isObject(school)) fail("schoolTime 必须是对象");
  statusOk(school,'校历');
  envelope(school,new Set(['maxWeekNum','maxSection','xnxqdm','weekCalendar']),'校历');
  return {
    maxWeekNum: integer(school.maxWeekNum, 1, 40, "maxWeekNum"),
    maxSection: integer(school.maxSection, 1, 24, "maxSection"),
    semesterCode: text(school.xnxqdm, "xnxqdm"),
    monday: readCalendar(school.weekCalendar)
  };
}
function clock(value, what) {
  if (typeof value !== "string" || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value)) {
    fail(what + "必须是实际时钟 HH:mm");
  }
  return value;
}
function readSlots(response, maxSection) {
  statusOk(response, "课时");
  envelope(response, new Set(["datas"]), "课时响应",maxSection);
  if (!isObject(response.datas)) fail("课时响应缺少 datas 对象");
  statusOk(response.datas,'课时数据');
  envelope(response.datas, new Set(["jc"]), "课时响应 datas",maxSection);
  if (!isObject(response.datas.jc)) fail("课时响应缺少 datas.jc 对象");
  statusOk(response.datas.jc,'课时列表');
  envelope(response.datas.jc, new Set(["rows"]), "课时响应 datas.jc",maxSection);
  const rows = response.datas.jc.rows;
  if (!Array.isArray(rows)) fail("datas.jc.rows 必须是数组");
  if (rows.length !== maxSection) fail("课时行数必须恰好等于 maxSection（" + maxSection + "）");
  const byNumber = new Map();
  for (const row of rows) {
    if (!isObject(row)) fail("课时行必须是对象");
    envelope(row, new Set(["DM", "KSSJ", "JSSJ"]), "课时行");
    const number = integer(row.DM, 1, maxSection, "DM");
    const startTime = clock(row.KSSJ, "KSSJ");
    const endTime = clock(row.JSSJ, "JSSJ");
    if (startTime >= endTime) fail("第 " + number + " 节开始时间必须早于结束时间");
    if (byNumber.has(number)) fail("课时 DM 重复：" + number);
    byNumber.set(number, { number, startTime, endTime });
  }
  const slots = [];
  for (let number = 1; number <= maxSection; number++) {
    if (!byNumber.has(number)) fail("缺少第 " + number + " 节课时");
    const slot = byNumber.get(number);
    if (slots.length > 0 && slot.startTime < slots[slots.length - 1].endTime) {
      fail("课时时间重叠：" + slot.startTime);
    }
    slots.push(slot);
  }
  return slots;
}
function readDetails(details) {
  if (!Array.isArray(details)) fail("details 必须是数组");
  const map = new Map();
  for (const entry of details) {
    if (!isObject(entry)) fail("教师详情项必须是对象");
    envelope(entry, new Set(["identity", "response"]), "教师详情项");
    if (!Array.isArray(entry.identity) || entry.identity.length !== 3) {
      fail("教师详情 identity 必须是 [jxbid,kbid,jxblx]");
    }
    const ids = entry.identity.map((value, index) => idText(value, "identity[" + index + "]"));
    const key = JSON.stringify(ids);
    if (map.has(key)) fail("教师详情 identity 重复：" + ids.join("/"));
    const response = entry.response;
    statusOk(response, "教师");
    if (!isObject(response.data)) fail("教师响应缺少 data 对象");
    statusOk(response.data,'教师数据');
    const count=Array.isArray(response.data.teacherInfo)?response.data.teacherInfo.length:null;
    envelope(response, new Set(["data"]), "教师响应",count);
    envelope(response.data, new Set(["teacherInfo"]), "教师响应 data",count);
    const info = response.data.teacherInfo;
    if (!Array.isArray(info) || info.length === 0) fail("data.teacherInfo 必须是非空数组");
    const names = [];
    for (const member of info) {
      if (!isObject(member)) fail("教师条目必须是对象");
      envelope(member, new Set(["xm"]), "教师条目");
      const name = text(member.xm, "教师姓名 xm");
      if (!names.includes(name)) names.push(name);
    }
    map.set(key, { ids, names });
  }
  return map;
}
function identityOf(row) {
  return [idText(row.jxbid, "jxbid"), idText(row.kbid, "kbid"), idText(row.jxblx, "jxblx")];
}
function positionOf(building, room) { return room.includes(building) ? room : building + room; }
function readRows(list, week, maxSection, details) {
  if (!Array.isArray(list)) fail("第 " + week + " 周 data.theorySchedule 必须是数组");
  const rows = [];
  for (const raw of list) {
    if (!isObject(raw)) fail("第 " + week + " 周课程行必须是对象");
    envelope(raw, ROW_KEYS, "第 " + week + " 周课程行");
    const name = text(raw.kcm, "kcm");
    const building = text(raw.jxlmc, "jxlmc");
    const room = text(raw.jasmc, "jasmc");
    const day = integer(raw.skxq, 1, 7, "skxq");
    const start = integer(raw.ksjc, 1, maxSection, "ksjc");
    const end = integer(raw.jsjc, 1, maxSection, "jsjc");
    if (end < start) fail("jsjc 不能小于 ksjc");
    const ids = identityOf(raw);
    const key = JSON.stringify(ids);
    if (!details.has(key)) {
      fail("第 " + week + " 周课程 " + name + " 缺少教师详情（" + ids.join("/") + "）");
    }
    const extra = Object.keys(raw).filter(key=>!ROW_KEYS.has(key) && raw[key] != null && raw[key] !== '')
      .sort().map(key=>{
        const value=raw[key];
        if (!['string','number','boolean'].includes(typeof value) || (typeof value==='number' && !Number.isFinite(value))) fail('校方附加字段格式不明');
        return key + '：' + String(value);
      }).join('\n');
    if(extra.length>2000) fail('校方附加字段过长');
    rows.push({ week, key, ids, name, position: positionOf(building, room), day, start, end, extra });
  }
  return rows;
}
function readWeekResponse(response, week, school) {
  const where = "第 " + week + " 周";
  statusOk(response, where);
  if (!isObject(response.data)) fail(where + "响应缺少 data 对象");
  statusOk(response.data,where+'数据');
  const count=Array.isArray(response.data.theorySchedule)?response.data.theorySchedule.length:null;
  envelope(response, new Set(["data", "skzc", "xnxqdm"]), where + "响应",count);
  envelope(response.data, new Set(["theorySchedule", "skzc", "xnxqdm"]), where + "响应 data",count);
  if (response.skzc !== undefined && response.data.skzc !== undefined &&
      String(response.skzc) !== String(response.data.skzc)) fail(where + "响应的 skzc 自相矛盾");
  if (response.xnxqdm !== undefined && response.data.xnxqdm !== undefined &&
      response.xnxqdm !== response.data.xnxqdm) fail(where + "响应的 xnxqdm 自相矛盾");
  const skzc = response.skzc !== undefined ? response.skzc : response.data.skzc;
  const semester = response.xnxqdm !== undefined ? response.xnxqdm : response.data.xnxqdm;
  // The public request contract does not promise echo fields in responses.
  // Validate them if present, without inventing mandatory response properties.
  if (skzc !== undefined && integer(skzc, 1, school.maxWeekNum, "skzc") !== week) fail(where + "响应 skzc 与查询周次不一致");
  if (semester !== undefined && text(semester, "响应 xnxqdm") !== school.semesterCode) fail(where + "响应 xnxqdm 与 schoolTime 不一致");
  return response.data.theorySchedule;
}
function readWeeks(weeks, school, details) {
  if (!Array.isArray(weeks)) fail("weeks 必须是数组");
  if (weeks.length !== school.maxWeekNum) fail("weeks 必须恰好包含 " + school.maxWeekNum + " 项");
  const seen = new Set();
  const rows = [];
  for (const item of weeks) {
    if (!isObject(item)) fail("weeks 项必须是对象");
    envelope(item, new Set(["week", "response"]), "weeks 项");
    const week = integer(item.week, 1, school.maxWeekNum, "week");
    if (seen.has(week)) fail("weeks 周次重复：" + week);
    seen.add(week);
    for (const row of readRows(readWeekResponse(item.response, week, school), week, school.maxSection, details)) {
      rows.push(row);
      if (rows.length > 5000) fail("课程行总数超过 5000");
    }
  }
  if (rows.length === 0) fail("整个学期没有任何课程");
  return rows;
}
function sameFact(a, b) {
  return a.name === b.name && a.position === b.position &&
    a.day === b.day && a.start === b.start && a.end === b.end && a.extra === b.extra;
}
function buildCourses(rows, details, semester) {
  const byIdentity = new Map();
  for (const row of rows) {
    let group = byIdentity.get(row.key);
    if (!group) {
      group = { rows: [], title: row.name, ids: row.ids };
      byIdentity.set(row.key, group);
    }
    if (group.title !== row.name) fail("同一教学班 " + row.ids.join("/") + " 存在冲突标题");
    group.rows.push(row);
  }
  const courses = [];
  for (const [key, group] of byIdentity) {
    const people = details.get(key);
    if (!people) fail("课程缺少教师详情");
    for (let i = 0; i < group.rows.length; i++) {
      for (let j = i + 1; j < group.rows.length; j++) {
        const a = group.rows[i];
        const b = group.rows[j];
        if (a.week === b.week && a.day === b.day && a.start <= b.end && b.start <= a.end) {
          if (!sameFact(a, b)) fail("同一教学班 " + group.ids.join("/") + " 存在重叠的上课事实");
          if (a.week === b.week) fail("同一教学班 " + group.ids.join("/") + " 在同一周重复出现");
        }
      }
    }
    const facts = new Map();
    for (const row of group.rows) {
      const signature = JSON.stringify([row.position, row.day, row.start, row.end, row.extra]);
      let fact = facts.get(signature);
      if (!fact) {
        fact = { row, weeks: new Set() };
        facts.set(signature, fact);
      }
      fact.weeks.add(row.week);
    }
    for (const fact of facts.values()) {
      courses.push({
        name: group.title,
        teacher: people.names.join("、"),
        position: fact.row.position,
        day: fact.row.day,
        startSection: fact.row.start,
        endSection: fact.row.end,
        weeks: Array.from(fact.weeks).sort((a, b) => a - b),
        extraInfo: "学期：" + semester + "\n教学班：" + group.ids[0] +
          "\n课次标识：" + group.ids[1] + "\n教学班类型：" + group.ids[2] +
          (fact.row.extra ? '\n校方附加信息：\n' + fact.row.extra : '')
      });
    }
  }
  const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
  courses.sort((a, b) => a.day - b.day || a.startSection - b.startSection ||
    a.endSection - b.endSection || compare(a.name, b.name) ||
    compare(a.position, b.position) || compare(a.extraInfo, b.extraInfo));
  return courses;
}
function normalize(input) {
  if (!isObject(input)) fail("输入必须是对象");
  const school = readSchool(input.schoolTime);
  const slots = readSlots(input.slotsResponse, school.maxSection);
  const details = readDetails(input.details);
  const rows = readWeeks(input.weeks, school, details);
  const used = new Set(rows.map((row) => row.key));
  for (const key of details.keys()) {
    if (!used.has(key)) fail("教师详情包含周课程中不存在的教学班：" + key);
  }
  const courses=buildCourses(rows, details, school.semesterCode);
  const equivalent=new Map();
  for(const course of courses){
    if(course.extraInfo.length>4000)fail('课程附加信息过长');
    const key=JSON.stringify([course.name,course.teacher,course.position,course.day,course.startSection,course.endSection,course.weeks]);
    if(!equivalent.has(key))equivalent.set(key,new Set());
    equivalent.get(key).add(course.extraInfo);
  }
  for(const blocks of equivalent.values())if([...blocks].join('\n\n').length>4000)fail('合并教学班附加信息过长');
  return {
    semesterCode: school.semesterCode,
    monday: school.monday,
    totalWeeks: school.maxWeekNum,
    slots,
    courses
  };
}
if (typeof module !== 'undefined' && module.exports) module.exports={normalize};
if (typeof window !== 'undefined') window.wuhuNormalize=normalize;
})();

// Original KAgenda host (MIT); public protocol facts: skreon/wuhuAiSchedule
// @06aee566f8f46bebff2d4dad531a28a018b50b17. No upstream code copied.
// The reference has no license; its code is not included or relicensed.
(function () {
  'use strict';
  if (typeof window === 'undefined' || !window.kagendaSchool) return;
  const k = window.kagendaSchool;
  const base = '/jwmobile/biz/v410/';
  const paths = {
    calendar: base + 'common/school/time',
    week: base + 'schedule/querySchedule',
    detail: base + 'schedule/queryScheduleDetail',
    slots: '/jwapp/sys/wdkb/modules/jshkcb/jc.do',
  };
  function stamp(value) {
    if (Array.isArray(value)) return '[' + value.map(stamp).join(',') + ']';
    if (value && typeof value === 'object') return '{' + Object.keys(value).sort()
      .map(key => JSON.stringify(key) + ':' + stamp(value[key])).join(',') + '}';
    return JSON.stringify(value);
  }
  function identity(row) {
    k.requireValue(row && typeof row === 'object' && !Array.isArray(row), '课次格式不完整');
    return ['jxbid', 'kbid', 'jxblx'].map(key => {
      const value = row[key];
      k.requireValue((typeof value === 'string' && value.trim() === value && value.length > 0 && value.length <= 200) ||
        (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0), '缺少明确的教学班或课次标识');
      return value;
    });
  }
  function controls() {
    k.requireValue(document.querySelectorAll('iframe').length === 0, '请在芜湖学院教务管理页面本身导入，不能从嵌套登录页采集');
    k.requireValue(!/(?:下一页|next\s+page|第\s*\d+\s*\/\s*\d+\s*页)/i.test(document.body?.innerText || ''), '页面仍有分页，不能确认完整课表');
    const nodes = Array.from(document.querySelectorAll('select,input,textarea'));
    k.requireValue(nodes.length <= 100, '页面控件过多，无法确认个人完整课表');
    return nodes.map(node => {
      const type = String(node.type || '').toLowerCase();
      k.requireValue(!['password', 'file', 'checkbox', 'radio'].includes(type), '请完成登录，并清除其他筛选后再导入');
      const name = String(node.name || node.id || '');
      if (/^(?:csrf|csrftoken|_csrf|_csrf_token)$/i.test(name)) return ['csrf', name];
      const value = String(node.value || '').trim();
      const label = node.tagName === 'SELECT' ? String(node.selectedOptions?.[0]?.textContent || '').trim() : '';
      k.requireValue(!node.disabled && !node.multiple, '页面含未确认的学期或筛选控件');
      return [name, value, label];
    });
  }
  function checkControls(rows, semester) {
    for (const [name, value] of rows) {
      if (name === 'csrf' || value === '') continue;
      k.requireValue(/^(?:xnxqdm|semester|semesterId|currentSemester)$/i.test(name) && value === semester,
        '页面学期与校方当前学期不一致，或仍有未知筛选；请回到当前个人课表');
    }
  }
  async function request(path, kind, fields) {
    const url = new URL(path, location.href);
    k.requireValue(url.origin === location.origin && Object.values(paths).includes(url.pathname), '课表请求地址不在已确认的学校范围');
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 20000);
    const headers = {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest'};
    if (kind === 'json') headers['Content-Type'] = 'application/json;charset=UTF-8';
    if (kind === 'form') headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
    try {
      const response = await fetch(url.href, {
        method: kind === 'get' ? 'GET' : 'POST',
        body: kind === 'json' ? JSON.stringify(fields) : undefined,
        headers, credentials: 'same-origin', redirect: 'error', cache: 'no-store', signal: controller.signal,
      });
      k.requireValue(response.ok && response.status === 200 && !response.redirected, '课表请求失败或登录失效，请重新登录');
      if (response.url) k.requireValue(new URL(response.url).href === url.href, '学校响应地址发生变化，请重新登录');
      const text = await response.text();
      k.requireValue(text.length > 0 && text.length <= 1500000 && text.trim().startsWith('{'), '学校未返回课表 JSON，可能已退出登录');
      const value = JSON.parse(text);
      k.requireValue(value && typeof value === 'object' && !Array.isArray(value), '学校课表响应格式不完整');
      return value;
    } finally { clearTimeout(timer); }
  }
  async function capture(guard) {
    const schoolTime = await request(paths.calendar, 'get'); guard();
    const total = k.integer(schoolTime.maxWeekNum, 1, 40, '校方总周数');
    k.requireValue(typeof schoolTime.xnxqdm === 'string' && schoolTime.xnxqdm.trim() && schoolTime.xnxqdm.length <= 100, '校方缺少当前学期标识');
    checkControls(controls(), schoolTime.xnxqdm);
    const slotsResponse = await request(paths.slots, 'form'); guard();
    const weeks = [], requested = new Map(); let rowCount = 0;
    for (let week = 1; week <= total; week++) {
      const response = await request(paths.week, 'json', {skzc: week, xnxqdm: schoolTime.xnxqdm}); guard();
      const rows = response.data?.theorySchedule;
      k.requireValue(Array.isArray(rows), '某周没有返回明确课表列表，已停止整批导入');
      rowCount += rows.length; k.requireValue(rowCount <= 5000, '完整学期课次数量超过支持范围');
      weeks.push({week, response});
      for (const row of rows) {
        const id = identity(row), key = JSON.stringify(id);
        if (!requested.has(key)) requested.set(key, id);
      }
      k.requireValue(requested.size <= 300, '教学班和课次详情过多，未导入部分课程');
    }
    const details = [];
    for (const id of requested.values()) {
      const response = await request(paths.detail, 'json', {jxbid: id[0], kbid: id[1], jxblx: id[2], wid: ''}); guard();
      details.push({identity: id, response});
    }
    return {schoolTime, slotsResponse, weeks, details};
  }
  k.run(async () => {
    const entry = new URL(location.href);
    k.requireValue(k.adapterId === 'ext_wuhu_personal' && entry.protocol === 'https:' &&
      entry.hostname === 'ehall.uwh.edu.cn' && !entry.port && !entry.username && !entry.password &&
      /^\/(?:jwmobile|jwapp)(?:\/|$)/.test(entry.pathname),
      '请登录芜湖学院门户，下滑进入教务管理后，在手机个人课表页面导入');
    const initial = stamp(controls());
    const guard = () => k.requireValue(location.href === entry.href && stamp(controls()) === initial, '查询期间页面、学期或筛选发生变化，请重试');
    const first = await capture(guard);
    const normalized = window.wuhuNormalize(first);
    const answer = await k.api.showPrompt('核对第1教学周周一',
      '校方学期：' + normalized.semesterCode + '，共 ' + normalized.totalWeeks + ' 周。请按校历明确输入第1教学周周一（YYYY-MM-DD），应与校方校历首日 ' + normalized.monday + ' 一致。', '', 1);
    k.requireValue(typeof answer === 'string' && answer.trim() === normalized.monday, '日期未确认、与校方校历不一致，或已取消导入'); guard();
    const final = await capture(guard);
    k.requireValue(stamp(first) === stamp(final), '两次完整学期、教师详情或作息不一致，请重新导入');
    const checked = window.wuhuNormalize(final); guard();
    // All parsing, prompts and final snapshots finish before the first staging call.
    const accepted = await k.api.savePresetTimeSlots(JSON.stringify(checked.slots));
    k.requireValue(accepted !== false, '无法保存校方逐节作息，未导入课程');
    await k.finish(checked.courses, 0, checked.totalWeeks, checked.monday);
  });
})();
