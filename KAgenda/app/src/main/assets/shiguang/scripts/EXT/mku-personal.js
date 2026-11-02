/*!
 * 闽南科技学院本科个人课表；KAgenda审阅修正版，真实账号验证另记。
 * 上游: https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/c4a35feb061a3f6db2d6c122927083c067c61309/resources/MKU/mku.js；原作者 Mutx163
 * MIT 改编实现，非零复制原创；版权主体按原 LICENSE。
 *
 * MIT License
 *
 * Copyright (c) 2025 星河欲转
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
(function () {
 "use strict";
 var BASE = "https://jwgl.mku.edu.cn";
 var FIRST = BASE + "/jsxsd/xskb/xskb_list.do?viweType=0&zc=&cj0701id=";
 var LABELS = ["老师", "时间", "地点", "校区", "教学班", "校区教学班"];

 function fail(m) { throw new Error(m); }
 function need(c, m) { if (!c) fail(m); }
 function txt(v) { return String(v == null ? "" : v).trim(); }
 function flat(v) { return String(v == null ? "" : v).replace(/\s+/g, ""); }
 function qa(r, s) { return Array.from(r.querySelectorAll(s)); }
 function q(r, s) { return r.querySelector(s); }
 function toast(m) { try { var b = window.shiguangBridge; if (b && b.showToast) b.showToast(String(m)); } catch (e) {} }

 function enc(v) {
  var s = txt(v).toLowerCase().replace(/["']/g, "");
  return s === "utf8" || s === "utf-8" ? "utf-8" :
    s === "gbk" || s === "gb2312" ? "gbk" : s === "gb18030" ? "gb18030" : "";
 }
 function decode(buf, ct) {
  var h = (ct.match(/charset\s*=\s*([^;\s]+)/i) || [])[1], head = "";
  try { head = new TextDecoder("latin1").decode(buf.slice(0, 2048)); } catch (e) { fail("编码失败"); }
  var m = (head.match(/<meta[^>]+charset\s*=\s*["']?\s*([^"'\s/>]+)/i) || [])[1];
  var he = h ? enc(h) : "", me = m ? enc(m) : "";
  need(!h || he, "编码不支持"); need(!m || me, "编码不支持"); need(!he || !me || he === me, "编码冲突");
  try { return new TextDecoder(he || me || "utf-8", { fatal: true }).decode(buf); }
  catch (e) { fail("编码无效"); }
 }
 async function getDoc(url) {
  var parsedUrl=new URL(url);need(parsedUrl.origin===BASE&&parsedUrl.pathname==='/jsxsd/xskb/xskb_list.do','请求来源不符');
  var controller=new AbortController(),timer=setTimeout(function(){controller.abort();},20000);
  try {
  var r;
  try {
    r = await fetch(url, { method: "GET", credentials: "same-origin", cache: "no-store", redirect: "error",signal:controller.signal,referrer:location.href,referrerPolicy:'same-origin' });
  } catch (e) { fail("网络或重定向失败"); }
  need(r.status === 200 && r.ok && !r.redirected, "HTTP异常");
  var actual;
  try { actual = new URL(r.url).href; } catch (e) { fail("URL异常"); }
  need(actual === new URL(url).href, "URL不一致");
  var ct = r.headers.get("content-type") || "";
  need(/^text\/html\b/i.test(ct), "非HTML");
  var buf;
  try { buf = await r.arrayBuffer(); } catch (e) { fail("响应不完整"); }
  need(buf.byteLength>0&&buf.byteLength<=2000000, "响应为空或过大");
  var html = decode(buf, ct);
  need(/<html[\s>]/i.test(html) && /<body[\s>]/i.test(html) && /<\/html\s*>/i.test(html), "HTML不完整");
  // Template content stays inert, including resource elements outside course cells.
  var template=document.createElement('template');template.innerHTML=html;
  var doc=template.content;need(doc&&doc.querySelector,'解析失败');
  need(!q(doc,'input[type="password"]'),'请重新登录教务系统');
  return doc;
  }finally{clearTimeout(timer);}
 }

 function semester(doc) {
  var ss = doc.querySelectorAll("#xnxq01id");
  need(ss.length === 1, "学期选择器异常");
  var s = ss[0];
  need(s.tagName === "SELECT" && !s.multiple&&!s.disabled, "学期非有效单选");
  need(s.selectedIndex >= 0 && s.selectedIndex < s.options.length, "无当前学期");
  var o = s.options[s.selectedIndex];
  need(o && o.selected&&!o.disabled, "学期未选中");
  var n = 0;
  for (var i = 0; i < s.options.length; i++) if (s.options[i].selected) n++;
  need(n === 1, "学期选中不唯一");
  var v = o.value, t = txt(o.text);
  need(v && v === v.trim() && v.length<=80&&!/[\u0000-\u001f]/.test(v)&&t&&t.length<=200, "学期值或名称无效");
  return { value: v, text: t };
 }
 function current(){
  need(window.kagendaSchool&&window.kagendaSchool.adapterId==='ext_mku_personal','学校适配入口不符');
  var u=new URL(location.href);need(u.origin===BASE&&u.pathname==='/jsxsd/xskb/xskb_list.do'&&!u.username&&!u.password,'请在本校学生个人课表页选择学期后导入');
  need(!q(document,'input[type="password"]'),'请先完成教务登录');
  var s=semester(document);
  for(var pair of u.searchParams){need(['viweType','zc','cj0701id','xnxq01id'].includes(pair[0]),'页面筛选条件未核验');if(pair[0]==='viweType')need(pair[1]==='0','课表视图未核验');else if(pair[0]==='xnxq01id')need(pair[1]===s.value,'页面学期字段冲突');else need(pair[1]==='','请使用完整的本人学期课表');}
  var selector=q(document,'#xnxq01id');return {semester:s,signature:JSON.stringify({href:u.href,selected:s,options:Array.from(selector.options).map(function(o){return[o.value,o.text,!!o.selected,!!o.disabled];})})};
 }
 function stable(page){need(current().signature===page.signature,'确认期间页面或所选学期发生变化');}
 function rawSignature(doc){return JSON.stringify(qa(doc,'td[name="kbDataTd"]').map(function(td){return td.outerHTML;}));}

 function dayOf(v) {
  var m = /^(?:星期|周)([一二三四五六日天])$/.exec(flat(v));
  return m ? (m[1] === "日" || m[1] === "天" ? 7 : "一二三四五六".indexOf(m[1]) + 1) : 0;
 }
 function gridOf(table) {
  var rows = Array.from(table.rows), g = [];
  need(rows.length >= 2 && rows.length <= 500, "行数异常");
  for (var r = 0; r < rows.length; r++) {
    g[r] = g[r] || [];
    var c = 0, cells = Array.from(rows[r].cells);
    for (var k = 0; k < cells.length; k++) {
      var td = cells[k];
      while (g[r][c]) c++;
      var rs = Number(td.rowSpan), cs = Number(td.colSpan);
      need(Number.isInteger(rs) && Number.isInteger(cs) && rs > 0 && cs > 0 && rs <= 100 && cs <= 8, "跨度异常");
      need(r + rs <= rows.length && c + cs <= 8, "跨度越界");
      for (var a = 0; a < rs; a++) for (var b = 0; b < cs; b++) {
        g[r + a] = g[r + a] || [];
        need(!g[r + a][c + b], "重叠");
        g[r + a][c + b] = td;
      }
      c += cs;
    }
  }
  for (r = 0; r < rows.length; r++) {
    need(g[r] && g[r].length === 8, "列数异常");
    for (c = 0; c < 8; c++) need(g[r][c], "空洞");
  }
  return g;
 }

 function headOf(g) {
  var hr = -1, map = null;
  for (var r = 0; r < g.length; r++) {
    var f = [], seen = {};
    for (var c = 0; c < 8; c++) {
      var d = dayOf(g[r][c].textContent);
      if (!d) continue;
      need(!seen[d], "表头重复"); seen[d] = 1; f.push([c, d]);
    }
    if (!f.length) continue;
    need(hr < 0, "多个表头"); need(f.length === 7, "表头不完整");
    var cols = {};
    for (var i = 0; i < 7; i++) cols[f[i][0]] = f[i][1];
    need(Object.keys(cols).length === 7, "表头列重复");
    var non = [];
    for (var j = 0; j < 8; j++) if (!(j in cols)) non.push(j);
    need(non.length === 1 && non[0] === 0, "表头布局异常");
    hr = r; map = cols;
  }
  need(hr >= 0 && map, "缺少表头");
  return { row: hr, byCol: map };
 }

 function rangeOf(v) {
  var m = /^(\d{1,2})(?:\s*[-~]\s*(\d{1,2}))?$/.exec(txt(v));
  if (!m) return null;
  var a = +m[1], b = m[2] ? +m[2] : a;
  return a >= 1 && b <= 24 && a <= b ? { start: a, end: b } : null;
 }
 function list(v, lo, hi) {
  var out = [], ts = String(v).split(/[,，]/);
  for (var i = 0; i < ts.length; i++) {
    var m = /^(\d{1,2})(?:\s*[-~]\s*(\d{1,2}))?$/.exec(ts[i].trim());
    if (!m) return null;
    var a = +m[1], b = m[2] ? +m[2] : a;
    if (a < lo || b > hi || a > b) return null;
    for (var n = a; n <= b; n++) out.push(n);
  }
  return out;
 }
 function unique(a, m) {
  return Array.from(new Set(a)).sort(function (x,y){return x-y;});
 }
 function timeOf(v) {
  var m = /^(\d{1,2}(?:\s*[-~]\s*\d{1,2})?(?:\s*[,，]\s*\d{1,2}(?:\s*[-~]\s*\d{1,2})?)*)\s*(单|双)?周(?:[（(]\s*(单|双)\s*周?\s*[）)])?\s*\[(\d{1,2}(?:\s*[-~]\s*\d{1,2})?(?:\s*[,，]\s*\d{1,2}(?:\s*[-~]\s*\d{1,2})?)*)\s*节\]$/.exec(String(v));
  need(m, "时间格式不支持");
  need(!m[2] || !m[3] || m[2] === m[3], "单双周冲突");
  var ew = list(m[1], 1, 40), es = list(m[4], 1, 24);
  need(ew && es, "时间格式不支持");
  var w = unique(ew, "周次重复"), s = unique(es, "节次重复"), p = m[2] || m[3];
  if (p === "单") w = w.filter(function (x) { return x % 2 === 1; });
  if (p === "双") w = w.filter(function (x) { return x % 2 === 0; });
  need(w.length, "单双周为空");
  var groups=[];s.forEach(function(n){var last=groups[groups.length-1];if(last&&last[1]+1===n)last[1]=n;else groups.push([n,n]);});
  return { weeks:w,sections:s,groups:groups };
 }
 function detailOf(v) {
  var ps = String(v).split(/[;；]/), map = {};
  need(txt(v) && !ps.some(function (p) { return !p.trim(); }), "详情空段");
  for (var i = 0; i < ps.length; i++) {
    var p = ps[i], k = p.search(/[:：]/);
    need(k > 0, "详情标签异常");
    var n = txt(p.slice(0, k)), val = txt(p.slice(k + 1));
    need(n && val, "详情空值"); need(map[n] === undefined, "标签重复");
    need(LABELS.indexOf(n) >= 0, "未知标签"); map[n] = val;
  }
  need(map["老师"] && map["时间"] && map["地点"], "缺详情字段");
  for(var key of ['老师','地点'])need(map[key].length<=200&&!/^(?:未知(?:教师|地点|教室)?|待定|未安排|暂无|未排|未分配|null|undefined|[-—–]+)$/i.test(map[key]),'课程缺少明确教师或地点');
  need(!map['校区']||map['校区'].length<=200,'校区过长');
  return { teacher: map["老师"], time: map["时间"], position: map["地点"],campus:map['校区']||'',fields:map };
 }
 function covered(rows, s, e) {
  var cur = s;
  for (var i = 0; i < rows.length; i++) {
    var r = rows[i];
    if (r.end < cur) continue;
    if (r.start > cur) return false;
    cur = r.end + 1;
    if (cur > e) return true;
  }
  return cur > e;
 }

 function parsePage(doc) {
  var cells = qa(doc, 'td[name="kbDataTd"]');
  need(cells.length, "无课表单元格");
  var table = cells[0].closest("table");
  need(table, "无table");
  for (var i = 0; i < cells.length; i++) need(cells[i].closest("table") === table, "多table");
  var tabs = qa(doc, "table");
  for (i = 0; i < tabs.length; i++) {
    var tb = tabs[i];
    if (tb === table || tb.contains(table) || table.contains(tb)) continue;
    need(!q(tb, 'td[name="kbDataTd"],.qz-hasCourse-title,.qz-hasCourse-abbrinfo'), "其他课表");
    var tx = flat(tb.textContent);
    need(!tx, "存在未核验的非空附加表，未导入部分结果");
  }
  var qz = qa(doc, ".qz-hasCourse-title,.qz-hasCourse-abbrinfo");
  for (i = 0; i < qz.length; i++) need(qz[i].closest('td[name="kbDataTd"]') &&
    qz[i].closest("table") === table, "课程标记越界");

  var g = gridOf(table), hd = headOf(g), data = {}, count = 0, prev = 0;
  for (var r = 0; r < g.length; r++) {
    if (r === hd.row) continue;
    var label = g[r][0];
    if (r < hd.row) {
      var cap = new Set(g[r]);
      if (cap.size === 1 && label.colSpan === 8) {
        need(label.getAttribute("name") !== "kbDataTd" &&
          !q(label, "table,.index-title,li,.qz-hasCourse-title,.qz-hasCourse-abbrinfo"), "表头前内容");
        continue;
      }
      need(label.getAttribute("name") !== "kbDataTd", "表头前内容");
      for (var c = 1; c < 8; c++) need(g[r][c].getAttribute("name") !== "kbDataTd" &&
        !flat(g[r][c].textContent), "表头前内容");
      continue;
    }
    var idx = label.querySelectorAll(".index-title");
    need(idx.length < 2, "行标签重复");
    if (idx.length === 1) {
      need(label.rowSpan === 1 && label.colSpan === 1, "行标签跨度");
      var rg = rangeOf(idx[0].textContent);
      need(rg, "行标签范围"); need(rg.start > prev, "节次乱序");
      prev = rg.end; data[r] = rg; count++;
    } else {
      need(label.getAttribute("name") !== "kbDataTd" &&
        !q(label, 'td[name="kbDataTd"],.qz-hasCourse-title,.qz-hasCourse-abbrinfo'), "无节次行有课程");
      if (label.colSpan === 8 && label.rowSpan === 1) continue;
      for (c = 1; c < 8; c++) need(!flat(g[r][c].textContent), "无节次行非空");
    }
    for (c = 1; c < 8; c++) {
      var cell = g[r][c];
      if (cell === label || cell.getAttribute("name") === "kbDataTd") continue;
      need(!flat(cell.textContent), "日格非空");
    }
  }
  need(count, "无节次行");

  var pos = new Map();
  for (r = 0; r < g.length; r++) for (c = 0; c < 8; c++) {
    var cl = g[r][c];
    if (cl.getAttribute("name") !== "kbDataTd") continue;
    if (!pos.has(cl)) pos.set(cl, []);
    pos.get(cl).push([r, c]);
  }
  var seq = 0, out = [];
  for (i = 0; i < cells.length; i++) {
    var td = cells[i], ps = pos.get(td);
    need(ps && ps.length, "单元格不在网格");
    need(td.colSpan === 1, "课程跨列");
    var col = ps[0][1], day = hd.byCol[col];
    need(day, "课程不在星期列");
    var rows = [], seen = {};
    for (var p = 0; p < ps.length; p++) {
      need(ps[p][1] === col, "课程跨星期");
      if (!seen[ps[p][0]]) {
        seen[ps[p][0]] = 1;
        need(data[ps[p][0]], "覆盖非节次行");
        rows.push(data[ps[p][0]]);
      }
    }
    need(!q(td, "table,li li,script,iframe,object,embed,img,svg,math,audio,video,style,link"), "课程含未知内容");
    var lis = qa(td, "li");
    if (!lis.length) {
      need(!flat(td.textContent), "非空课程无li");
      continue;
    }
    var outside = td.cloneNode(true);
    qa(outside, "li").forEach(function (x) { x.remove(); });
    need(!flat(outside.textContent), "课程多余文本");
    for (var j = 0; j < lis.length; j++) {
      var li = lis[j];
      var ts = li.querySelectorAll(".qz-hasCourse-title");
      var infos = li.querySelectorAll(".qz-hasCourse-abbrinfo");
      need(ts.length === 1 && infos.length === 1, "课程字段异常");
      var name = txt(ts[0].textContent), info = txt(infos[0].textContent);
      need(name && name.length<=200&&info, "课程名称或详情无效");
      var rest = li.cloneNode(true);
      qa(rest, ".qz-hasCourse-title,.qz-hasCourse-abbrinfo").forEach(function (x) { x.remove(); });
      need(!flat(rest.textContent), "条目多余文本");
      var d = detailOf(info), tm = timeOf(d.time);
      tm.sections.forEach(function(n){need(covered(rows,n,n),'节次超出');});
      seq++;
      var ex = JSON.stringify({semester:semester(doc).text,sourceRow:seq,rawDetails:info,fields:d.fields});
      need(ex.length <= 4000, "extraInfo超长");
      var room=d.campus&&!d.position.includes(d.campus)?d.campus+' '+d.position:d.position;
      tm.groups.forEach(function(group){out.push({name:name,teacher:d.teacher,position:room,day:day,startSection:group[0],endSection:group[1],weeks:tm.weeks,extraInfo:ex});});
    }
  }
  need(out.length&&out.length<=5000, "未解析到完整课程或数量超限");
  return out;
 }

 function dateErr(v) {
  var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v);
  if (!m) return "日期格式";
  var y = +m[1], mo = +m[2], d = +m[3], dt = new Date(Date.UTC(y, mo - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== mo - 1 || dt.getUTCDate() !== d) return "日期不存在";
  return dt.getUTCDay() === 1 ? "" : "必须周一";
 }
 function weekErr(v, max) {
  return /^(?:[1-9]|[1-3][0-9]|40)$/.test(v) ? (+v < max ? "周数小于" + max : "") : "周数1..40";
 }
 async function ask(b, title, msg, check) {
  for (var i = 0; i < 3; i++) {
    var v = await b.showPrompt(title, msg, "");
    if (v == null) return null;
    v = txt(v);
    var e = check(v);
    if (!e) return v;
    if ((await b.showAlert("输入无效", e, "重新输入")) === false) return null;
  }
  return null;
 }

 async function run() {
  try {
    var b = window.shiguangBridgePromise;
    need(b && typeof b.showAlert === "function" && typeof b.showPrompt === "function" &&
      typeof b.showSingleSelection==='function'&&
      typeof b.saveCourseConfig === "function" && typeof b.saveImportedCourses === "function",
      "宿主桥接不可用");
    var page=current(),picked=await b.showSingleSelection('核对网页当前课表学期',JSON.stringify([page.semester.text]),-1);
    need(picked===0,'未确认学期，已取消导入');stable(page);
    toast("读取当前学期...");
    var url=FIRST+'&xnxq01id='+encodeURIComponent(page.semester.value);
    var d1 = await getDoc(url), s1 = semester(d1);need(JSON.stringify(s1)===JSON.stringify(page.semester),'教务返回其他学期');
    var c1 = parsePage(d1), raw1=rawSignature(d1),max = 0;stable(page);
    c1.forEach(function (x) { x.weeks.forEach(function (w) { if (w > max) max = w; }); });
    var start = await ask(b, "校历第一周周一",
      "当前学期：" + s1.text + "\n输入该学期真实第一周周一（YYYY-MM-DD，周一）；勿用本机日期反推或固定校历。", dateErr);
    if (start === null) { toast("校历未确认，未暂存"); return false; }
    var weeks = await ask(b, "教学周数",
      "输入总教学周数（1..40，且不少于课程最大周 " + max + "）；勿用默认20周，作息看原生预览。",
      function (v) { return weekErr(v, max); });
    if (weeks === null) { toast("教学周数未确认，未暂存"); return false; }
    var total = +weeks;
    need(total >= max, "周数不足");
    toast("同源复核...");
    stable(page);var d2 = await getDoc(url), s2 = semester(d2);
    need(s2.value === s1.value && s2.text === s1.text, "学期不稳定");
    var c2 = parsePage(d2);
    need(JSON.stringify(c1) === JSON.stringify(c2)&&raw1===rawSignature(d2), "课程原始记录不稳定");stable(page);
    var cfg = { semesterStartDate: start, semesterTotalWeeks: total };
    var ok = await b.saveCourseConfig(JSON.stringify(cfg));
    if (ok === false) fail("配置保存失败");
    ok = await b.saveImportedCourses(JSON.stringify(c2));
    if (ok === false) fail("课程保存失败");
    toast("已暂存 " + c2.length + " 条课程，请核对原生预览");
    return true;
  } catch (e) {
    try{if(b&&b.showAlert)await b.showAlert('课表导入未完成',String(e&&e.message||'课表读取失败').slice(0,500),'关闭');}catch(_){}
    return false;
  }finally{
    try{var nb=window.shiguangBridge;if(nb&&nb.notifyTaskCompletion)nb.notifyTaskCompletion();}catch(_){}
  }
 }

 window.mkuCandidateRun = run;
 window.kagendaMkuParsers={timeOf:timeOf,detailOf:detailOf,dayOf:dayOf,rangeOf:rangeOf,weekErr:weekErr,dateErr:dateErr,semester:semester,parsePage:parsePage,current:current};
 run();
})();
