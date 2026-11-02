// KAgenda independent SEU wdkb port, using protocol/field facts from
// Haa1024/SEU-Timetable (MIT) and sakimidare/SEUTimetable (Apache-2.0).
// Pinned source files/dates/hashes: academic/school-provenance.json.
// Original notices: licenses/jiangsu-school-bundle.txt.
// Changes: explicit semester; unfiltered full-term scheduled rows; strict
// contexts/counts; snapshot recheck; no guessed dates, times or 20-week default.
(function () {
  'use strict';
  const k=window.kagendaSchool, base='/jwapp/sys/wdkb/modules/';
  function rows(response,key) {
    k.requireValue(response && typeof response==='object' && !Array.isArray(response) &&
      response.success!==false && response.success!=='false' &&
      (response.code==null || String(response.code)==='0'), '教务接口返回异常，请重新登录查询');
    const data=response.datas && response.datas[key];
    k.requireValue(data && Array.isArray(data.rows) && data.rows.length<=5000, '未取得完整教务记录');
    if(data.totalSize!=null) k.requireValue(k.integer(data.totalSize,0,5000,'记录总数')===data.rows.length,
      '教务只返回了部分记录，未保存任何课程');
    return data.rows;
  }
  async function semesters() {
    const list=rows(await k.json(base+'jshkcb/xnxqcx.do'),'xnxqcx');
    k.requireValue(list.length>0 && list.length<=200 && list.every(r=>r &&
      typeof r.DM==='string' && r.DM.trim() && typeof r.MC==='string' && r.MC.trim()), '学期列表无效');
    k.requireValue(new Set(list.map(r=>r.DM)).size===list.length, '教务返回了重复学期代码');
    return list;
  }
  async function context(term) {
    const code=term.DM.match(/^(\d{4})-(\d{4})-([1-9]\d?)$/);
    k.requireValue(code && Number(code[2])===Number(code[1])+1, '不支持此学期代码格式');
    const list=rows(await k.json(base+'jshkcb/cxjcs.do',{XN:code[1]+'-'+code[2],XQ:code[3]}),'cxjcs');
    k.requireValue(list.length===1 && list[0], '没有取得唯一的学期配置');
    const value=String(list[0].XQKSRQ || '').trim();
    const match=value.match(/^(\d{4}-\d{2}-\d{2})(?:\s+\d{2}:\d{2}:\d{2})?$/);
    const start=match && match[1], date=new Date(start+'T00:00:00Z');
    k.requireValue(start && Number.isFinite(date.getTime()) && date.toISOString().slice(0,10)===start,
      '学期开始日期无效');
    return {start,weeks:k.integer(list[0].ZZC,1,40,'学期总周数')};
  }
  async function scheduled(term,calendar) {
    // The reviewed endpoint returns the full selected term when SKZC is absent.
    // YPSJDD may repeat several meetings on each row; never generate from it.
    const list=rows(await k.json(base+'xskcb/xskcb.do?XNXQDM='+encodeURIComponent(term.DM)),'xskcb');
    k.requireValue(list.length>0, '此学期没有可导入的已排课课程');
    const courses=[],signatures=[];
    list.forEach(row=>{
      k.requireValue(row && typeof row==='object' && !Array.isArray(row) &&
        typeof row.KCM==='string' && typeof row.SKZC==='string' &&
        (row.SKJS==null || typeof row.SKJS==='string') &&
        (row.JASMC==null || typeof row.JASMC==='string'), '存在无法识别的已排课记录');
      k.requireValue(row.XNXQDM==null || row.XNXQDM===term.DM, '课程所属学期不一致');
      const active=k.bitmap(row.SKZC,false);
      k.requireValue(Math.max(...active)<=calendar.weeks, '课程周次超过教务返回的学期范围');
      const course=k.course(row.KCM,row.SKJS,row.JASMC,row.SKXQ,row.KSJC,row.JSJC,active);
      courses.push(course);
      signatures.push(JSON.stringify([row.JXBID || '',row.KBID || '',course]));
    });
    return {courses,signature:JSON.stringify(signatures.sort())};
  }
  k.run(async()=>{
    k.requireValue(k.adapterId==='ext_seu_wdkb' && location.origin==='https://ehall.seu.edu.cn' &&
      !location.username && !location.password && location.pathname.startsWith('/jwapp/sys/wdkb/'),
      '请进入东南大学我的课表页面完成登录');
    const term=await k.choose('选择已排课课表的学期',await semesters(),'DM','MC');
    const deadline=Date.now()+90000, calendar=await context(term);
    const first=await scheduled(term,calendar), final=await scheduled(term,calendar);
    const after=await context(term), current=(await semesters()).find(r=>r.DM===term.DM);
    k.requireValue(Date.now()<deadline && first.signature===final.signature &&
      after.start===calendar.start && after.weeks===calendar.weeks && current && current.MC===term.MC,
      '查询期间课表或学期配置变化，未保存课程，请重新查询');
    await k.finish(first.courses,0,calendar.weeks,calendar.start);
  });
})();
