/*
MIT License

Copyright (c) 2021 MoeShin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

/*
Source: https://github.com/moeshin/ai-schedule-chaoxing (MIT)
Revision: 4650f59887d119b9c9828fb19447f736b549654e
Tree: b950c05d20dafb77c715598a3795625412cefcb4
Copyright (c) 2021 MoeShin; full MIT text above.
Source path/SHA256/gitBlob/pathDate are recorded in contract.json.
Rewritten strict draft, not upstream: no PASS/HTML-table/getKbxx/format merge;
name=kcmc only; category/campus/scalars in extraInfo; strict gates. No real
account or real-school validation.
*/

(function(){
'use strict';
// Modified by KAgenda, 2026. Reviewed MIT-based DSH draft; no upstream execution.

const ORIGIN = 'https://jw.lmu.cn';
const PAGE_PATH = '/admin/pkgl/xskb/queryKbForXsd';
const API_PATH = '/admin/pkgl/xskb/sdpkkbList';
const TYPES = ['其他', '理论', '实验', '上机', '实践', '环节'];
const CORE = new Set(['kcmc', 'tmc', 'croommc', 'xqmc', 'type', 'djc', 'zc', 'xingqi']);
const SECRET = /^(xhid|xh|xqdm|userid|username|password|passwd|token|session|cookie|idcard|sfzh)$/i;
const MAX_DJC = 24;
const MAX_WEEKS = 40;
const MAX_BYTES = 2 * 1024 * 1024;
const MAX_EXTRA = 4000;
const TIMEOUT_MS = 20000;

class Cancelled extends Error {}
class SafeError extends Error {}
function fail(msg) { throw new SafeError(msg); }
function cancel() { throw new Cancelled('cancelled'); }

async function getBridge() {
  if (typeof window === 'undefined' || !window.shiguangBridgePromise) fail('桥接不可用');
  const b = await window.shiguangBridgePromise;
  if (!b) fail('桥接不可用');
  for (const n of ['showAlert', 'showSingleSelection', 'showPrompt', 'saveCourseConfig', 'saveImportedCourses']) {
    if (typeof b[n] !== 'function') fail('桥接方法缺失');
  }
  return b;
}

function toast(s) { try { window.shiguangBridge.showToast(s); } catch (_) {} }
function notify() { try { window.shiguangBridge.notifyTaskCompletion(); } catch (_) {} }
function normPath(p) { return String(p).replace(/\/+/g, '/').replace(/\/$/, ''); }

function one(doc, sel) {
  const list = doc.querySelectorAll(sel);
  if (!list || list.length !== 1) fail(`页面元素 ${sel} 缺失或重复`);
  return list[0];
}

function isHiddenInput(el) {
  return !!el && String(el.tagName).toUpperCase() === 'INPUT' && String(el.type).toLowerCase() === 'hidden';
}

function token(v, label) {
  if (typeof v !== 'string' || !v || v !== v.trim() || v.length > 128 || /[\u0000-\u001f\u007f]/.test(v)) {
    fail(`${label}为空或含非法字符`);
  }
  return v;
}

function readPage() {
  if (typeof window === 'undefined' || !window.document || !window.location) fail('不在学校页面运行');
  if(window.kagendaSchool?.adapterId!=='ext_lmu_personal')fail('学校适配入口不符');
  const loc = window.location;
  const doc = window.document;
  if (loc.origin !== ORIGIN) fail('页面 origin 不符');
  if (loc.pathname !== PAGE_PATH || loc.search || loc.username || loc.password) fail('请打开本人学生个人课表页');
  if (doc.querySelector('input[type="password"]')) fail('检测到登录页');
  const xhidEl = one(doc, '#xhid');
  const xqdmEl = one(doc, '#xqdm');
  if (!isHiddenInput(xhidEl) || !isHiddenInput(xqdmEl)) fail('身份字段不是 input[type=hidden]');
  const sel = one(doc, '#xnxq1');
  if (!sel || String(sel.tagName).toUpperCase() !== 'SELECT') fail('学期下拉缺失');
  if (sel.disabled) fail('学期选择不可用');
  const xhid = token(xhidEl.value, '身份字段');
  const xqdm = token(xqdmEl.value, '校区字段');
  const opts = [];
  const seen = new Set();
  for (const opt of Array.from(sel.options || [])) {
    const v = opt.value;
    if (v === '') continue;
    if (typeof v !== 'string' || v.length > 64 || /[\u0000-\u001f\u007f]/.test(v)) fail('学期选项非法');
    if (seen.has(v)) fail('学期选项重复');
    seen.add(v);
    const text=String(opt.textContent || '').trim();
    if (!text || text.length>200 || v!==v.trim()) fail('学期名称或值无效');
    opts.push({ value: v, text, disabled:!!opt.disabled });
  }
  if (!opts.length) fail('学期选项为空');
  if (opts.length > 200) fail('学期选项过多');
  const idx = sel.selectedIndex;
  if (!Number.isInteger(idx) || idx < 0 || !sel.options[idx]) fail('当前未选中学期');
  const current = sel.options[idx].value;
  if (typeof current !== 'string' || !current || !seen.has(current)) fail('当前学期不是真实选项');
  if (sel.options[idx].disabled) fail('当前学期不可选择');
  return { xhid, xqdm, opts, current, href:loc.href };
}

function samePage(a, b) {
  const key = p => JSON.stringify([p.href,p.xhid,p.xqdm,p.opts,p.current]);
  if (key(a) !== key(b)) fail('页面身份或学期选项发生变化');
}

function textField(v, n, k) {
  if (typeof v !== 'string' || v.indexOf('\u0000') >= 0) fail(`第 ${n} 行字段 ${k} 非字符串`);
  return v;
}

function intField(v, n, k, min, max) {
  if(typeof v==='string'&&/^\d+$/.test(v))v=Number(v);
  if (!Number.isInteger(v) || v < min || v > max) fail(`第 ${n} 行字段 ${k} 非 ${min}..${max} 整数`);
  return v;
}

function isScalar(v) {
  return v === null || typeof v === 'string' || typeof v === 'boolean' || (typeof v === 'number' && Number.isFinite(v));
}

function cleanTag(v) {
  if(v.length>10000||/<\s*(?:script|iframe|img|link|style|meta|object|embed|svg|math|input|form|audio|video|source|base)\b|\bon[a-z]+\s*=|url\s*\(/i.test(v))fail('课程文本含未核验的活动内容');
  const P = typeof DOMParser === 'function' ? DOMParser : (typeof window !== 'undefined' ? window.DOMParser : null);
  if (typeof P !== 'function') fail('缺少隔离 DOMParser');
  let doc;
  try { doc = new P().parseFromString(v, 'text/html'); } catch (_) { fail('HTML 解析失败'); }
  if (!doc || !doc.body) fail('HTML 解析失败');
  const text = String(doc.body.textContent || '').trim();
  if (text.indexOf('\u0000') >= 0) fail('文本字段含 NUL');
  return text;
}

function parseWeeksString(s, n) {
  let t = s.trim();
  if (t.endsWith('周')) t = t.slice(0, -1);
  if (!t) fail(`第 ${n} 行周次为空`);
  const out = [];
  for (const part of t.split(',')) {
    const m = /^(\d+)(?:-(\d+)(?:\(([单双])\))?)?$/.exec(part);
    if (!m) fail(`第 ${n} 行周次格式非法`);
    let a = Number(m[1]);
    let b = m[2] === undefined ? a : Number(m[2]);
    if (!Number.isSafeInteger(a) || !Number.isSafeInteger(b) || a < 1 || b < 1 || a > MAX_WEEKS || b > MAX_WEEKS || a > b) {
      fail(`第 ${n} 行周次越界`);
    }
    if (m[3] === '单' && a % 2 === 0) a++;
    if (m[3] === '双' && a % 2 === 1) a++;
    for (let w = a; w <= b; w += m[3] ? 2 : 1) out.push(w);
  }
  if (!out.length) fail(`第 ${n} 行周次为空`);
  return out;
}

function parseWeeks(v, n) {
  let arr;
  if (typeof v === 'string') {
    arr = parseWeeksString(v, n);
  } else if (Array.isArray(v)) {
    if (!v.length) fail(`第 ${n} 行周次数组为空`);
    arr = [];
    for (const x of v) {
      arr.push(intField(x,n,'zc',1,MAX_WEEKS));
    }
  } else {
    fail(`第 ${n} 行周次类型非法`);
  }
  arr.sort((a, b) => a - b);
  for (let i = 1; i < arr.length; i++) if (arr[i] === arr[i - 1]) fail(`第 ${n} 行周次重复`);
  if (!arr.length) fail(`第 ${n} 行周次为空`);
  return arr;
}

function normalizeRow(row, i, term) {
  const n = i + 1;
  if (!row || typeof row !== 'object' || Array.isArray(row)) fail(`第 ${n} 行不是对象`);
  for (const k of CORE) if (k!=='xqmc'&&!Object.prototype.hasOwnProperty.call(row, k)) fail(`第 ${n} 行缺少 ${k}`);
  const name = cleanTag(textField(row.kcmc, n, 'kcmc'));
  if (!name||name.length>200) fail(`第 ${n} 行课程名为空或过长`);
  const teacher = cleanTag(textField(row.tmc, n, 'tmc'));
  const position = cleanTag(textField(row.croommc, n, 'croommc'));
  const campus = row.xqmc==null?'':cleanTag(textField(row.xqmc, n, 'xqmc'));
  for(const [key,value]of [['教师',teacher],['地点',position]])if(!value||value.length>200||/^(?:未知(?:教师|地点|教室)?|待定|未安排|暂无|未排|未分配|null|undefined|[-—–]+)$/i.test(value))fail(`第 ${n} 行缺少明确${key}`);
  if(campus.length>200)fail(`第 ${n} 行校区过长`);
  if(row.xnxq!=null&&row.xnxq!==term)fail(`第 ${n} 行学期冲突`);
  const type = intField(row.type, n, 'type', 0, 5);
  const djc = intField(row.djc, n, 'djc', 1, MAX_DJC);
  const day = intField(row.xingqi, n, 'xingqi', 1, 7);
  const weeks = parseWeeks(row.zc, n);
  const extra = [];
  for (const k of Object.keys(row)) {
    if (CORE.has(k)) continue;
    if (SECRET.test(k)) fail(`第 ${n} 行含身份/凭据字段`);
    const v = row[k];
    if (!isScalar(v)) fail(`第 ${n} 行含非标量额外字段`);
    extra.push([k, v]);
  }
  const extraInfo = JSON.stringify(Object.fromEntries([
    ['category', TYPES[type]],
    ['semester', term],
    ['sourceRow', n],
    ['type', type],
    ['campus', campus],
    ['fields', Object.fromEntries(extra)]
  ]));
  if (extraInfo.length > MAX_EXTRA) fail(`第 ${n} 行 extraInfo 超限`);
  return { name, teacher, position:campus&&!position.includes(campus)?campus+' '+position:position, day, startSection: djc, endSection: djc, weeks, extraInfo };
}

function finalizeCourse(c, total) {
  for (const w of c.weeks) if (w > total) fail('课程周次超过总周数');
  return c;
}

function mondayError(raw) {
  if (typeof raw !== 'string') return '输入必须是字符串。';
  const s = raw.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return '格式必须为 YYYY-MM-DD。';
  const y = Number(s.slice(0, 4));
  const m = Number(s.slice(5, 7));
  const d = Number(s.slice(8, 10));
  const dt = new Date(0);
  dt.setUTCHours(0, 0, 0, 0);
  dt.setUTCFullYear(y, m - 1, d);
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return '日期不存在。';
  if (dt.getUTCDay() !== 1) return '该日期不是周一。';
  return '';
}

async function promptMonday(bridge) {
  const msg = '请输入所选学期第一周周一（YYYY-MM-DD），必须真实存在且为周一；不要填开学日。';
  for (let i = 0; i < 3; i++) {
    const raw = await bridge.showPrompt('第一周周一', msg, '');
    if (raw === null) cancel();
    const err = mondayError(raw);
    if (!err) return String(raw).trim();
    await bridge.showAlert('日期无效', err, '确定');
  }
  fail('第一周周一连续 3 次无效');
}

async function promptTotalWeeks(bridge, maxWeek) {
  const msg = `请输入所选学期总教学周数（正整数，必须≥课程最大周 ${maxWeek}；不要默认 20）。`;
  for (let i = 0; i < 3; i++) {
    const raw = await bridge.showPrompt('总教学周数', msg, '');
    if (raw === null) cancel();
    const s = typeof raw === 'string' ? raw.trim() : '';
    if (!/^[1-9]\d*$/.test(s)) {
      await bridge.showAlert('周数无效', '请输入正整数。', '确定');
      continue;
    }
    const n = Number(s);
    if (!Number.isSafeInteger(n) || n < 1 || n > MAX_WEEKS) {
      await bridge.showAlert('周数无效', `请输入 1..${MAX_WEEKS} 的整数。`, '确定');
      continue;
    }
    if (n < maxWeek) {
      await bridge.showAlert('周数不足', `必须≥课程最大周 ${maxWeek}。`, '确定');
      continue;
    }
    return n;
  }
  fail('总教学周数连续 3 次无效');
}

async function fetchRows(term, xhid, xqdm) {
  if (typeof fetch !== 'function' || typeof AbortController !== 'function' || typeof TextDecoder !== 'function') {
    fail('缺少同源请求能力');
  }
  const url = new URL(API_PATH, ORIGIN);
  url.searchParams.set('xnxq', term);
  url.searchParams.set('xhid', xhid);
  url.searchParams.set('xqdm', xqdm);
  const want = { xnxq: term, xhid, xqdm };
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  let text;
  try {
    const res = await fetch(url.href, {
      method: 'GET',
      credentials: 'same-origin',
      redirect: 'error',
      cache: 'no-store',
      signal: ctrl.signal,
      headers: { Accept: 'application/json' }
    });
    if (!res || res.status !== 200) fail('服务器返回非 200');
    if (res.redirected) fail('发生重定向');
    const finalUrl = new URL(res.url || url.href, ORIGIN);
    if (finalUrl.origin !== ORIGIN || finalUrl.pathname !== API_PATH) fail('响应 origin/path 不符');
    for (const k of ['xnxq', 'xhid', 'xqdm']) if (finalUrl.searchParams.get(k) !== want[k]) fail('响应查询参数不符');
    const ct = String(res.headers.get('content-type') || '').toLowerCase();
    const cm = /charset\s*=\s*"?([^";]+)"?/.exec(ct);
    if (cm) {
      const cs = cm[1].trim();
      if (cs !== 'utf-8' && cs !== 'utf8' && cs !== 'us-ascii') fail('响应编码非 UTF-8');
    }
    if (ct && !/^application\/json(?:\s*;|$)/.test(ct) && !/^text\/plain(?:\s*;|$)/.test(ct)) fail('响应类型非 JSON');
    const buf = await res.arrayBuffer();
    if (buf.byteLength > MAX_BYTES) fail('响应超过 2 MiB');
    text = new TextDecoder('utf-8', { fatal: true }).decode(buf);
  } catch (e) {
    if (e instanceof SafeError) throw e;
    fail('课表请求失败');
  } finally {
    clearTimeout(timer);
  }
  const trimmed = text.trim();
  if (!trimmed || trimmed.charAt(0) !== '[' || trimmed.charAt(trimmed.length - 1) !== ']') fail('响应非原始数组或疑似登录页');
  let raw;
  try { raw = JSON.parse(trimmed); } catch (_) { fail('课表响应 JSON 无效'); }
  if (!Array.isArray(raw)) fail('课表响应不是数组');
  if (raw.length < 1) fail('课表为空');
  if (raw.length > 5000) fail('课表行数异常');
  return { rows: raw };
}

async function run() {
  let bridge = null;
  try {
    bridge = await getBridge();
    toast('正在核对个人课表页...');
    const p1 = readPage();

    const selected=p1.opts.find(o=>o.value===p1.current);
    if (!selected) fail('当前学期索引无效');
    const picked = await bridge.showSingleSelection('核对网页当前课表学期', JSON.stringify([selected.text]), -1);
    if (picked === null) cancel();
    if (picked!==0) fail('学期确认无效');
    const term = p1.current;
    if (!term) fail('所选学期值为空');

    samePage(p1, readPage());

    toast('正在读取课表...');
    const a1 = await fetchRows(term, p1.xhid, p1.xqdm);
    const parsed1 = a1.rows.map((r, i) => normalizeRow(r, i,term));
    let maxWeek = 0;
    for (const c of parsed1) for (const w of c.weeks) if (w > maxWeek) maxWeek = w;
    if (maxWeek < 1) fail('课程周次为空');

    const monday = await promptMonday(bridge);
    const totalWeeks = await promptTotalWeeks(bridge, maxWeek);

    samePage(p1, readPage());

    const a2 = await fetchRows(term, p1.xhid, p1.xqdm);
    if (JSON.stringify(a1.rows) !== JSON.stringify(a2.rows)) fail('两次课表原始行不一致');

    samePage(p1, readPage());

    const courses = parsed1.map(c => finalizeCourse(c, totalWeeks));
    if(await bridge.saveCourseConfig(JSON.stringify({ semesterStartDate: monday, semesterTotalWeeks: totalWeeks }))===false)fail('课表配置暂存失败');
    if(await bridge.saveImportedCourses(JSON.stringify(courses))===false)fail('课程暂存失败');
    toast('已暂存，请在原生预览核对作息');
    return { ok: true, courseCount: courses.length, totalWeeks };
  } catch (e) {
    if (e instanceof Cancelled) {
      toast('已取消，未暂存');
      return { ok: false, cancelled: true };
    }
    const msg = e instanceof SafeError ? e.message : '课表导入未完成';
    try {
      if (!bridge) bridge = await getBridge();
      await bridge.showAlert('导入中止', msg, '确定');
    } catch (_) {}
    return { ok: false, cancelled: false, error: msg };
  } finally {
    notify();
  }
}

window.kagendaLmuParsers={readPage,normalizeRow,parseWeeks,mondayError};
run();
})();
