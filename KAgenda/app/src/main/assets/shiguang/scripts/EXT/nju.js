// Derived from WheretoSleepinNJU/NJU-Class-Shedule-Flutter, Apache-2.0.
// Upstream snapshot: 25125ef6c7092c47f9341adcfaa7e1dc1974b44d, api/tools/.
// Exact per-adapter files, hashes and dates: academic/school-provenance.json.
// KAgenda changes: async same-origin requests; explicit term choice; complete
// pagination; strict weeks/sections; no fixed semester or silent partial import;
// convert into the KAgenda/ShiGuang host protocol. Copyright upstream contributors.
// Undergraduate header/pager facts also reviewed against Fallforgotten/
// nju-timetable-desktop, MIT, 2380b4fbbf36e79424e0b3ecf3152658b3d4a61d.
// KAgenda independently rejects partial/filtered pages and rereads after prompts.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const DAY = {一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 日: 7, 天: 7};
  const text = node => String(node && (node.innerText || node.textContent) || '').trim();
  function scheduled(value) { return value != null && String(value).trim() && !/^(?:自由时间|暂无|未安排|待定)$/.test(String(value).trim()); }
  function parts(value) {
    return String(value).replace(/（/g, '(').replace(/）/g, ')').split(/[;；\n]+|[,，]\s*(?=(?:周|星期)[一二三四五六日天])/).map(s => s.trim()).filter(Boolean);
  }
  function timeFirst(value, name, teacher) {
    return parts(value).map(part => {
      const m = part.match(/^(?:周|星期)([一二三四五六日天])\s*(\d+)(?:-(\d+))?节\s+(.+)$/);
      k.requireValue(m, '无法识别课程时间：' + part);
      const tail = m[4].replace(/\s*([,，、-])\s*/g, '$1');
      const w = tail.match(/^([\d第周单双(),，、~至-]+)(?:\s+(.*))?$/);
      k.requireValue(w, '缺少明确周次：' + part);
      return k.course(name, teacher, w[2] || '', DAY[m[1]], m[2], m[3] || m[2], k.weeks(w[1]));
    });
  }
  function weeksFirst(value, name, teacher) {
    return parts(value).map(part => {
      const m = part.match(/^(.+?周(?:\([单双](?:周)?\))?)\s*(?:星期|周)([一二三四五六日天])\s*\[(\d+)(?:-(\d+))?节\]\s*(.*)$/);
      k.requireValue(m, '无法识别研究生排课：' + part);
      return k.course(name, teacher, m[5], DAY[m[2]], m[3], m[4] || m[3], k.weeks(m[1]));
    });
  }
  function termRows(data, key) {
    const block = data && data.datas && data.datas[key];
    k.requireValue(block && Array.isArray(block.rows) && block.rows.length > 0, '没有取得学期列表，请检查登录状态');
    if (block.totalSize != null) k.requireValue(Number(block.totalSize) === block.rows.length, '学期列表不完整');
    return block.rows;
  }
  async function emapRows(path, key, fields) {
    const result = []; let total = null;
    for (let page = 1; page <= 25; page++) {
      const data = await k.json(path, Object.assign({}, fields, {pageNumber: page, pageSize: 200}));
      const block = data && data.datas && data.datas[key];
      k.requireValue(block && Array.isArray(block.rows), '课表响应格式无效');
      k.requireValue(block.totalSize != null, '接口未提供课表总数，无法确认是否完整');
      const count = k.integer(block.totalSize, 0, 5000, '课程总数');
      if (total == null) total = count;
      k.requireValue(count === total, '采集期间课表发生变化，请重新导入');
      result.push(...block.rows);
      k.requireValue(result.length <= total, '课表分页数量不一致');
      if (result.length === total) return result;
      k.requireValue(block.rows.length > 0, '课表分页缺失，未导入部分结果');
    }
    throw new Error('课表分页超过支持范围');
  }
  async function njuUndergrad() {
    k.host(['ehallapp.nju.edu.cn']);
    const entry = location.href;
    k.requireValue(!location.username && !location.password && !location.port &&
      /^\/jwapp\/sys\/wdkb\/(?:\*default\/)?index\.do$/.test(location.pathname), '请打开本科个人“我的课表”，不能读取全校或其他页面');
    const compact = el => text(el).replace(/\s/g, '');
    const visible = el => !el.closest('[hidden],[aria-hidden="true"]') &&
      el.getClientRects().length > 0;
    function snapshot() {
      k.requireValue(location.href === entry, '查询期间页面地址变化');
      const terms = Array.from(document.querySelectorAll('#dqxnxqkclb'));
      k.requireValue(terms.length === 1, '没有唯一的本科个人学期标识');
      const term = compact(terms[0]);
      const tm = term.match(/^(\d{4})[-—](\d{4})学年第([123一二三])学期$/);
      k.requireValue(tm && Number(tm[2]) === Number(tm[1]) + 1, '缺少完整明确的学年学期');
      k.requireValue(!document.querySelector('input[type="password"]'), '登录已失效');
      const aliases = {code:['课程号','课程编号','课程代码'], name:['课程名','课程名称','教学班名称'],
        teacher:['教师','任课教师','授课教师','上课教师'],
        time:['上课时间及地点','上课时间地点','时间地点','上课时间/地点','上课时间与地点']};
      const candidates = [];
      for (const table of document.querySelectorAll('table')) {
        if (!visible(table)) continue;
        k.requireValue(!table.querySelector('table'), '不支持嵌套课程表');
        const rows = Array.from(table.rows);
        for (let h=0; h<Math.min(rows.length,3); h++) {
          const headers = Array.from(rows[h].cells), labels = headers.map(compact), indices = {};
          for (const [key,names] of Object.entries(aliases)) {
            const hits = labels.flatMap((label,i) => names.includes(label) ? [i] : []);
            if (hits.length > 1) throw Error('课程表头字段重复');
            indices[key] = hits.length ? hits[0] : -1;
          }
          if (Object.values(indices).some(i => i < 0)) continue;
          k.requireValue(headers.every(c => c.rowSpan===1 && c.colSpan===1), '课程表头包含合并列');
          const raw = rows.slice(h+1).map(row => {
            const cells = Array.from(row.cells);
            k.requireValue(cells.length===headers.length && cells.every(c => c.rowSpan===1 && c.colSpan===1), '课程行宽、跨度或完整性不符');
            const value = {};
            for (const [key,index] of Object.entries(indices)) {
              const cell = cells[index], short = text(cell);
              const title = cell.getAttribute('title') || cell.querySelector('[title]')?.getAttribute('title');
              value[key] = /\.\.\.|…/.test(short) && title ? title.trim() : short;
              k.requireValue(value[key] && value[key].length<=1000 && !/\.\.\.|…/.test(value[key]), '课程字段缺失、过长或被截断');
            }
            k.requireValue(!/自由时间|暂无|未安排|待定/.test(value.time), '存在未排课或不完整课程，不能导入部分课表');
            return value;
          });
          k.requireValue(raw.length>0 && raw.length<=5000, '没有完整课程列表');
          candidates.push(raw); break;
        }
      }
      // A table and a widget may represent the same rows; conflicting views remain ambiguous.
      const unique = new Map(candidates.map(rows => [JSON.stringify(rows),rows]));
      k.requireValue(unique.size===1, '未找到唯一的完整课程表头和数据表，请使用标准列表视图');
      const raw = [...unique.values()][0];
      k.requireValue(new Set(raw.map(r => JSON.stringify(r))).size===raw.length, '课程列表含重复行，无法确认总数');
      const roots = Array.from(document.querySelectorAll('div,nav,section,td')).filter(el => visible(el) &&
        compact(el).length<1500 && /\d+[-–—]\d+总记录数[:：]?\d+/.test(compact(el)) && /总页数/.test(compact(el)));
      const pagers = roots.filter(el => !roots.some(other => other!==el && el.contains(other)));
      k.requireValue(pagers.length===1, '没有唯一的分页总数，不能确认整学期完整性');
      const pager = pagers[0], ranges = [...compact(pager).matchAll(/(\d+)[-–—](\d+)总记录数[:：]?(\d+)/g)];
      const pages = compact(pager).match(/总页数[:：]?(\d+)/);
      k.requireValue(ranges.length===1 && pages && Number(pages[1])===1, '当前课表仍有分页，请先在学校页面完整展开所有课程');
      const [,start,end,total] = ranges[0].map(Number);
      k.requireValue(start===1 && end===total && total===raw.length, '当前课程行与完整分页总数不一致');
      const filters = Array.from(document.querySelectorAll('input,select,textarea')).map(el => {
        const kind = (el.type||'').toLowerCase(), name = el.name||el.id||'';
        if (pager.contains(el) || ['button','submit','reset'].includes(kind)) return null;
        const value = String(el.value||'').trim();
        const label = el.tagName==='SELECT' ? text(el.selectedOptions[0]) : '';
        // Anti-forgery tokens carry no course filter. Every other nonempty control needs evidence.
        if (/^(?:csrf|csrfToken|_csrf|__RequestVerificationToken)$/i.test(name)) return null;
        if (kind==='checkbox' || kind==='radio') throw Error('发现未经确认的课程开关，请使用无筛选完整学期列表');
        k.requireValue((!value && (!label || /^(?:全部|所有|不限|全部周次|全学期)$/.test(label))) ||
          (el.tagName==='SELECT' && (compact(el.selectedOptions[0])===term || /^(?:全部|所有|不限|全部周次|全学期)$/.test(label))),
          '存在课程、周次、校区或其他未经确认的筛选，不能当作完整课表');
        return [name,value,label];
      }).filter(Boolean);
      const courses = raw.flatMap(row => timeFirst(row.time,row.name,row.teacher));
      k.requireValue(courses.every(c => c.position && c.teacher), '课程教师或地点缺失');
      return {term,raw,filters,courses};
    }
    const first = snapshot();
    await new Promise(resolve => setTimeout(resolve,300));
    k.requireValue(JSON.stringify(snapshot())===JSON.stringify(first), '课程列表尚未稳定，请等待后重新导入');
    const weeks = await k.api.showPrompt('学期总周数','请按本学期校历填写总教学周数；不会根据最后一门课推测。','',1);
    k.requireValue(weeks!==null && weeks!==undefined, '已取消导入');
    const count = k.integer(weeks,Math.max(...first.courses.flatMap(c=>c.weeks)),40,'学期总周数');
    const final = snapshot();
    k.requireValue(JSON.stringify(final)===JSON.stringify(first), '交互期间学期、课程或筛选变化，请重新导入');
    await k.finish(final.courses,0,count);
  }
  async function njuSelection() {
    k.host(['xk.nju.edu.cn']);
    k.requireValue(text(document.querySelector('.currentTerm')), '请在选课系统打开“已选课程”窗口');
    const heads = document.querySelectorAll('.course-head'), bodies = document.querySelectorAll('.course-body');
    k.requireValue(heads.length && bodies.length, '请先打开“已选课程”列表');
    const labels = Array.from(heads[heads.length - 1].children[0].children).map(text);
    const ni = labels.findIndex(s => s.includes('课程名')), ti = labels.findIndex(s => s.includes('教师'));
    const si = labels.findIndex(s => s.includes('时间地点')), state = labels.findIndex(s => s === '状态');
    k.requireValue(ni >= 0 && si >= 0, '已选课程表头发生变化');
    const result = []; let omitted = 0;
    Array.from(bodies[bodies.length - 1].children).forEach(row => {
      if (row.classList.contains('wdbm-course-tr') || (state >= 0 && text(row.children[state]) === '已退选')) return;
      k.requireValue(row.children.length > Math.max(ni, si, ti), '已选课程行不完整');
      const name = text(row.children[ni]), teacher = ti >= 0 ? text(row.children[ti]) : '';
      const blocks = Array.from(row.children[si].children);
      const values = blocks.length ? blocks.map(text).filter(Boolean) : [text(row.children[si])];
      if (!values.length || values.every(v => !scheduled(v) || /自由时间/.test(v))) { omitted++; return; }
      values.forEach(info => { k.requireValue(!/自由时间/.test(info), '同一课程含未排课时段，请核对后改用教务列表'); result.push(...timeFirst(info, name, teacher)); });
    });
    await k.finish(result, omitted);
  }
  async function graduate(isRuc) {
    const hostname = isRuc ? 'yjs2.ruc.edu.cn' : 'ehallapp.nju.edu.cn';
    k.host([hostname]);
    const base = '/gsapp/sys/wdkbapp/';
    const terms = termRows(await k.json(base + 'modules/xskcb/kfdxnxqcx.do', {pageSize: 200}), 'kfdxnxqcx');
    const term = await k.choose('选择要导入的学期', terms, 'XNXQDM', 'XNXQDM_DISPLAY');
    let rows;
    if (isRuc) {
      const data = await k.json(base + 'bykb/loadXskbData.do', {XNXQDM: term.XNXQDM}, true);
      k.requireValue(data && Array.isArray(data.rwList), '没有取得研究生课表'); rows = data.rwList;
    } else rows = await emapRows(base + 'modules/xskcb/xsjxrwcx.do', 'xsjxrwcx', {XNXQDM: term.XNXQDM});
    k.requireValue(rows.length <= 5000, '课程数量超过范围');
    const result = []; let omitted = 0;
    rows.forEach(row => {
      if (!scheduled(row.PKSJDD)) { omitted++; return; }
      result.push(...weeksFirst(row.PKSJDD, row.KCMC, row.RKJS));
    });
    await k.finish(result, omitted);
  }
  async function seu() {
    k.host(['ehall.seu.edu.cn']);
    const base = '/jwapp/sys/bykb/modules/';
    const terms = termRows(await k.json(base + 'jshkcb/xnxqcx.do', {'*order': '-DM', pageSize: 200}), 'xnxqcx');
    const term = await k.choose('选择要导入的学期', terms, 'DM', 'MC');
    const rows = await emapRows(base + 'xskcb/cxxszhxqkb.do', 'cxxszhxqkb', {'*order': '+KSJC,+JSJC', XNXQDM: term.DM});
    const result = []; let omitted = 0;
    rows.forEach(row => {
      if (row.SKZC === '0' || /^0+$/.test(String(row.SKZC || ''))) { omitted++; return; }
      const active = row.SKZC != null && String(row.SKZC).trim() ? k.bitmap(row.SKZC, false) : k.weeks(row.ZCMC);
      result.push(k.course(row.KCM, row.SKJS, row.JASMC || row.JASMC_DISPLAY, row.SKXQ, row.KSJC, row.JSJC, active));
    });
    await k.finish(result, omitted);
  }
  async function sjtu() {
    k.host(['yjsxk.sjtu.edu.cn']);
    let data;
    try { data = JSON.parse(text(document.body)); } catch (_) { data = await k.json('/yjsxkapp/sys/xsxkapp/xsxkCourse/loadStdCourseInfo.do'); }
    k.requireValue(data && Array.isArray(data.results) && data.results.length <= 5000, '没有取得研究生选课数据');
    const names = Array.from(new Set(data.results.map(r => String(r.XNXQMC || '').trim()).filter(Boolean)));
    const term = await k.choose('选择要导入的学期', names.map(name => ({name})), 'name', 'name');
    const result = []; let omitted = 0;
    data.results.filter(row => row.XNXQMC === term.name).forEach(row => {
      if (!scheduled(row.PKSJDD)) { omitted++; return; }
      result.push(...weeksFirst(row.PKSJDD, row.KCMC, row.RKJS));
    });
    await k.finish(result, omitted);
  }
  async function ucas() {
    k.host(['kb.mooc.ucas.edu.cn']);
    const path = '/pc/curriculum/getMyLessons?curTime=' + Date.now() + '&week=';
    const first = await k.json(path + '1');
    k.requireValue(first && first.result === 1 && first.data && first.data.curriculum, '没有取得当前学期配置');
    const max = k.integer(first.data.curriculum.maxWeek, 1, 40, '学期周数');
    const result = []; const seen = new Map();
    for (let w = 1; w <= max; w++) {
      const response = w === 1 ? first : await k.json(path + w);
      k.requireValue(response && response.result === 1 && response.data && Array.isArray(response.data.lessonArray), '第 ' + w + ' 周获取失败，未保存部分结果');
      response.data.lessonArray.forEach(row => {
        const active = k.weeks(row.weeks);
        k.requireValue(active.includes(w), '接口返回了与请求周次不一致的课程');
        const start = k.integer(row.beginNumber, 1, 24, '起始节次');
        const length = k.integer(row.length, 1, 24, '连续节数');
        const course = k.course(row.name || row.displayCourseName, row.teacherName, row.location || row.onlineLocation,
          row.dayOfWeek, start, start + length - 1, [w]);
        const key = JSON.stringify([row.lessonConfigUuid || '', course.name, course.teacher, course.position, course.day, course.startSection, course.endSection]);
        const old = seen.get(key);
        if (old) old.weeks = Array.from(new Set(old.weeks.concat([w]))).sort((a, b) => a - b);
        else { seen.set(key, course); result.push(course); }
      });
    }
    await k.finish(result, 0, max);
  }
  const ports = {ext_nju_ug: njuUndergrad, ext_nju_xk: njuSelection,
    ext_nju_grad: () => graduate(false), ext_ruc_grad: () => graduate(true),
    ext_seu: seu, ext_sjtu_grad: sjtu, ext_ucas_grad: ucas};
  // Exposed only for deterministic host tests; these functions never execute
  // until the user presses import and the host has installed a session bridge.
  window.kagendaNjuParsers = {timeFirst, weeksFirst, emapRows};
  k.run(async () => { k.requireValue(ports[k.adapterId], '未知的学校适配器'); await ports[k.adapterId](); });
})();
