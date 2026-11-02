// DOM layout, arrangement fields and full-week audit adapted from
// Miao-30/NJUCM-weekdesk (MIT): src/extract.ts, src/core.ts, electron/main.ts.
// Pinned revisions, source dates and hashes: academic/school-provenance.json.
// Complete original permission: licenses/jiangsu-school-bundle.txt.
// KAgenda changes: visible browser login; same-origin frame discovery; strict
// atomic validation; page-provided dates/times; no storage before native preview.
(function () {
  'use strict';
  const k = window.kagendaSchool, origin = 'https://jwgl.njucm.edu.cn';
  const text = node => String(node?.textContent || '').trim();
  function date(value) {
    const parsed = new Date(value+'T00:00:00Z');
    k.requireValue(/^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(parsed.getTime()) &&
      parsed.toISOString().slice(0,10) === value, '未读到有效的学期起始日期');
    return value;
  }
  function details(raw,name,code,day,cellStart,cellEnd) {
    const m = raw.trim().match(/^[（(](.+?)周[）)]\s*[（(](\d+)(?:[-~～](\d+))?节[）)]([\s\S]*)$/);
    k.requireValue(m, '无法识别课程安排：'+name+' '+raw);
    const first = k.integer(m[2],cellStart,cellEnd,'起始节次');
    const last = m[3] ? k.integer(m[3],first,cellEnd,'结束节次') : first;
    const tail = m[4].replace(/\u00a0/g,' ').trim(), parts = tail.split(/\s{2,}/).filter(Boolean);
    let teacher, room;
    if (parts.length >= 2) { teacher=parts.pop(); room=parts.join(' '); }
    else { const tokens=tail.split(/\s+/); teacher=tokens.pop(); room=tokens.join(' '); }
    k.requireValue(teacher, '课程缺少教师字段：'+name);
    room = room.replace(/自定义教室[：:]\s*/g,'').trim();
    return {...k.course(name,teacher,room,day,first,last,k.weeks(m[1])),code};
  }
  function snapshot(doc, allowWeek) {
    const table=doc.querySelector('table.courseTable'), term=doc.querySelector('#allSemesters');
    const week=doc.querySelector('#weeks'), experiments=doc.querySelector('#hasExperiment');
    k.requireValue(table && term && week && experiments, '请打开完整个人课表并等待加载');
    const semesterId=String(term.value || '').trim(), semester=text(term.selectedOptions?.[0]);
    k.requireValue(semesterId && semester, '请选择明确的学期');
    const selectedWeek=String(week.value || '');
    k.requireValue(allowWeek ? /^\d+$/.test(selectedWeek) : selectedWeek==='all', '请先在网页选择全部周次');
    k.requireValue(experiments.checked===true, '请先勾选包含实验安排并完成网页查询');
    const match=text(doc.body).match(/学期起始日期\s*[:：]\s*(\d{4}-\d{2}-\d{2})/);
    k.requireValue(match, '网页未提供学期起始日期');
    const startDate=date(match[1]);
    const options=Array.from(week.options || []).map(o=>String(o.value));
    if (week.selectize?.options) options.push(...Object.keys(week.selectize.options));
    const weeks=Array.from(new Set(options.filter(v=>v!=='all' && v!=='').map(v=>k.integer(v,1,40,'教学周')))).sort((a,b)=>a-b);
    k.requireValue(weeks.length && weeks.every((v,i)=>v===i+1), '没有取得完整的教学周列表');
    if (allowWeek) k.requireValue(weeks.includes(Number(selectedWeek)), '网页返回了无效教学周');
    const rows=Array.from(table.tBodies?.[0]?.rows || []);
    k.requireValue(rows.length===12, '学校逐节作息或表格结构不完整');
    const times=[], entries=[], occupied=[];
    rows.forEach((row,index)=>{
      const clock=text(row.querySelector('.dayPartUnit')).match(/(\d{1,2}:\d{2})\s*[-~～]\s*(\d{1,2}:\d{2})/);
      k.requireValue(clock, '未取得第 '+(index+1)+' 节的作息时间');
      const start=k.clock(clock[1]), end=k.clock(clock[2]);
      k.requireValue(start<end && (!times.length || start>=times[times.length-1].endTime), '逐节作息发生重叠或倒序');
      times.push({number:index+1,startTime:start,endTime:end});
      let column=0;
      Array.from(row.cells).forEach(cell=>{
        while ((occupied[column] || 0)>index) column++;
        const day=column, span=k.integer(cell.rowSpan || 1,1,12-index,'表格跨行');
        const width=k.integer(cell.colSpan || 1,1,8,'表格跨列');
        k.requireValue(column+width<=8, '个人课表星期列数量无效');
        for (let n=column;n<column+width;n++) occupied[n]=index+span;
        column+=width;
        const names=Array.from(cell.querySelectorAll('.course-name'));
        if (!names.length) return;
        k.requireValue(day>=1 && day<=7 && width===1, '课程星期列不完整');
        names.forEach(node=>{
          const name=text(node), lines=[];
          let sibling=node.nextSibling;
          while (sibling && !(sibling.nodeType===1 && sibling.classList.contains('course-name'))) {
            const value=text(sibling); if (value) lines.push(value); sibling=sibling.nextSibling;
          }
          const code=lines.shift();
          k.requireValue(name && code, '课程名称或代码不完整');
          const arrangements=lines.filter(line=>/[（(].*周[）)]/.test(line));
          k.requireValue(arrangements.length>0, '课程缺少明确排课时段：'+name);
          const classes=lines.filter(line=>!arrangements.includes(line)).join('；');
          arrangements.forEach(raw=>entries.push({...details(raw,name,code,day,index+1,index+span),classes}));
        });
      });
      // Account for cells spanning into this row's last columns.
      while ((occupied[column] || 0)>index) column++;
      k.requireValue(column===8, '个人课表缺少星期列');
    });
    k.requireValue(entries.length<=5000, '课程数量超过范围');
    const maxWeek=weeks[weeks.length-1];
    k.requireValue(entries.every(e=>e.weeks.every(w=>w<=maxWeek)), '课程周次超过网页教学周范围');
    const unscheduled=Array.from(doc.querySelectorAll('#lessons tr')).filter(r=>r.querySelector('.courseInfo')).map(row=>{
      const info=row.querySelector('.courseInfo'), name=text(info.querySelector('p'));
      k.requireValue(name, '未排课程记录不完整');
      return {name,raw:text(row)};
    });
    k.requireValue(unscheduled.length<=5000 && (allowWeek || entries.length>0 || unscheduled.length>0), '课表为空，无法确认读取完整性');
    return {semesterId,semester,selectedWeek,startDate,maxWeek,times,entries,unscheduled};
  }
  function signature(page,week) {
    return page.entries.filter(e=>e.weeks.includes(week)).flatMap(e=>{
      const result=[];
      for (let n=e.startSection;n<=e.endSection;n++) result.push(JSON.stringify([e.code,e.name,e.day,n,e.teacher,e.position,e.classes]));
      return result;
    }).sort();
  }
  function courseDocument() {
    const found=[];
    function visit(doc,depth) {
      k.requireValue(depth<=3, '课表嵌套层级超过范围');
      if (doc.querySelector('table.courseTable')) found.push(doc);
      for (const frame of Array.from(doc.querySelectorAll('iframe,frame'))) {
        let child;
        try {
          if (frame.contentWindow.location.origin===origin) child=frame.contentDocument;
        } catch (_) { continue; }
        if (child) visit(child,depth+1);
      }
    }
    visit(document,0);
    k.requireValue(found.length===1, '未找到唯一的完整个人课表，请打开课表页后重试');
    return found[0];
  }
  window.kagendaNjucmParsers={snapshot,signature,details};
  k.run(async()=>{
    k.requireValue(k.adapterId==='ext_njucm_dom' && location.origin===origin &&
      location.pathname.startsWith('/student/for-std/course-table'), '请先进入南京中医药大学个人课表');
    const doc=courseDocument(), win=doc.defaultView;
    const deadline=Date.now()+90000;
    function guard() {
      k.requireValue(Date.now()<deadline && win.location.origin===origin && doc===win.document,
        '课表页面已变化或读取超时，请重新导入');
    }
    async function stable(allowWeek,expected,updated=()=>true) {
      let previous='', repeats=0, lastError;
      for (let n=0;n<40;n++) {
        await new Promise(resolve=>setTimeout(resolve,150)); guard();
        if (!updated()) continue;
        let page;
        try { page=snapshot(doc,allowWeek); }
        catch (error) { lastError=error; previous=''; repeats=0; continue; }
        const value=JSON.stringify(page);
        if (page.selectedWeek===expected && value===previous) repeats++; else repeats=0;
        if (repeats>=2) return page;
        previous=value;
      }
      throw Error(lastError ? lastError.message : '课表持续变化或尚未加载完成，请完成网页查询后重试');
    }
    function select(value) {
      guard();
      const control=doc.querySelector('#weeks');
      k.requireValue(control, '教学周控件已失效');
      if (control.selectize) control.selectize.setValue(value);
      else { control.value=value; control.dispatchEvent(new win.Event('change',{bubbles:true})); }
    }
    async function readWeek(value,allowWeek) {
      const table=doc.querySelector('table.courseTable');
      k.requireValue(table?.parentNode && win.MutationObserver, '当前页面不支持完整教学周核对');
      let updated=false;
      const observer=new win.MutationObserver(records=>{
        if (records.some(r=>r.target===table.parentNode || table.contains(r.target))) updated=true;
      });
      observer.observe(table.parentNode,{childList:true,subtree:true,characterData:true});
      try { select(value); return await stable(allowWeek,value,()=>updated); }
      finally { observer.disconnect(); }
    }
    // Require the student's explicit "all weeks + experiments" selection;
    // then verify every displayed week and restore the complete view.
    snapshot(doc,false);
    const full=await stable(false,'all');
    let final;
    try {
      for (let week=1;week<=full.maxWeek;week++) {
        const current=await readWeek(String(week),true);
        k.requireValue(current.semesterId===full.semesterId && current.startDate===full.startDate &&
          current.maxWeek===full.maxWeek && JSON.stringify(current.times)===JSON.stringify(full.times) &&
          JSON.stringify(signature(current,week))===JSON.stringify(signature(full,week)),
          '第 '+week+' 周与全部课表不一致，未保存任何课程');
      }
      final=await readWeek('all',false);
      k.requireValue(JSON.stringify(final)===JSON.stringify(full), '核对期间课表发生变化，请重新导入');
    } finally {
      try { if (doc.querySelector('#weeks')?.value!=='all') select('all'); }
      catch (_) { /* Never mask the original validation failure. */ }
    }
    k.requireValue(final.entries.length>0, '没有可导入的已排课课程');
    k.requireValue(final.unscheduled.length === 0, '存在未排课课程，未导入部分结果');
    guard();
    k.requireValue(JSON.stringify(snapshot(doc,false))===JSON.stringify(final), '确认期间课表发生变化，请重新导入');
    const courses=final.entries.map(e=>k.course(e.name,e.teacher,e.position,e.day,e.startSection,e.endSection,e.weeks));
    await k.api.savePresetTimeSlots(JSON.stringify(final.times));
    await k.finish(courses,0,final.maxWeek,final.startDate);
  });
})();
