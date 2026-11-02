// NUIST personal weekly timetable protocol/fields: zyc-816/NUISTTable (MIT).
// Pinned paths, dates and hashes: academic/school-provenance.json.
// Original copyright/permission: licenses/jiangsu-school-bundle.txt.
// KAgenda changes: visible login, explicit current-term selection and calendar
// week count, complete weekly requests, strict rows/totals, final context check.
// No source fixed 20-week range, stored cookies, clock presets or silent skips.
(function () {
  'use strict';
  const k=window.kagendaSchool, base='/jwapp/sys/wdkb/modules/';
  function rows(response,key) {
    k.requireValue(response && typeof response==='object' && !Array.isArray(response) &&
      response.success!==false && response.success!=='false' &&
      (response.code==null || String(response.code)==='0'), '教务接口返回异常，请重新登录后查询');
    const data=response.datas && response.datas[key];
    k.requireValue(data && Array.isArray(data.rows) && data.rows.length<=5000, '没有取得完整课表数据');
    if(data.totalSize!=null) k.requireValue(k.integer(data.totalSize,0,5000,'记录总数')===data.rows.length,
      '接口只返回了部分记录，未保存课程');
    return data.rows;
  }
  async function currentTerm() {
    const list=rows(await k.json(base+'jshkcb/dqxnxq.do',{}),'dqxnxq');
    k.requireValue(list.length===1 && list[0] && typeof list[0].DM==='string', '没有取得唯一的当前学期');
    const term=list[0], match=term.DM.match(/^(\d{4})-(\d{4})-([1-9]\d?)$/);
    k.requireValue(match && Number(match[2])===Number(match[1])+1, '教务学期代码无效');
    return {DM:term.DM,MC:typeof term.MC==='string' && term.MC.trim() ? term.MC : term.DM,
      year:match[1]+'-'+match[2],part:match[3]};
  }
  async function startDate(term) {
    const list=rows(await k.json(base+'jshkcb/cxjcs.do',{XN:term.year,XQ:term.part}),'cxjcs');
    k.requireValue(list.length===1 && list[0] && typeof list[0].XQKSRQ==='string', '没有取得学期开始日期');
    const match=list[0].XQKSRQ.trim().match(/^(\d{4}-\d{2}-\d{2})(?:\s+\d{2}:\d{2}:\d{2})?$/);
    const value=match && match[1], date=new Date(value+'T00:00:00Z');
    k.requireValue(value && Number.isFinite(date.getTime()) && date.toISOString().slice(0,10)===value,
      '教务提供的学期开始日期无效');
    return value;
  }
  k.run(async()=>{
    k.requireValue(k.adapterId==='ext_nuist_weekly' && location.origin==='https://jwxt.nuist.edu.cn' &&
      !location.username && !location.password &&
      location.pathname.startsWith('/jwapp/sys/wdkb/'), '请进入本校我的课表页面并完成登录');
    const term=await currentTerm();
    await k.choose('选择教务系统提供的当前学期',[term],'DM','MC');
    const answer=await k.api.showPrompt('本学期总周数',
      '请按本校本学期校历填写总周数（1–40）。本入口逐周读取已排课课程，不包含未排课课程。','', '');
    k.requireValue(answer!==null && answer!==undefined, '已取消导入');
    const max=k.integer(answer,1,40,'本学期总周数'), start=await startDate(term);
    const result=[],seen=new Map(), deadline=Date.now()+90000;
    for(let week=1;week<=max;week++) {
      k.requireValue(Date.now()<deadline, '完整课表查询超时，未保存部分课程');
      const list=rows(await k.json(base+'xskcb/cxxszhxqkb.do',{XNXQDM:term.DM,SKZC:String(week)}),'cxxszhxqkb');
      list.forEach(row=>{
        k.requireValue(row && typeof row==='object' && !Array.isArray(row) && typeof row.KCM==='string',
          '存在无法识别的已排课课程');
        const course=k.course(row.KCM,row.SKJS,row.JASMC,row.SKXQ,row.KSJC,row.JSJC,[week]);
        const key=JSON.stringify([course.name,course.teacher,course.position,course.day,course.startSection,course.endSection]);
        const previous=seen.get(key);
        if(previous) { if(!previous.weeks.includes(week)) previous.weeks.push(week); }
        else { seen.set(key,course);result.push(course); }
      });
    }
    k.requireValue((await currentTerm()).DM===term.DM && await startDate(term)===start,
      '查询期间学期或开始日期变化，未保存课程');
    await k.finish(result,0,max,start);
  });
})();
