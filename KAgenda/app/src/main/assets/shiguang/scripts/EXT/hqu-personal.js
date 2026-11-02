// Original KAgenda implementation (MIT), not copied from the references.
// Public protocol/field facts: Lee70020/HuaYuanTimetable@be54e11ffe445b91f6d8414b08b7822e724e0b34
// WdkbClient.java / schedule.py (no upstream license established), plus the
// existing MIT shiguang_warehouse HQU/hquadap.js pinned by this project.
// Original host only; no upstream login/password/cache/default clock code is packaged.
(function () {
  'use strict';
  const k=window.kagendaSchool;
  const origins={ext_hqu_personal:'https://jwapp.hqu.edu.cn',ext_hqu_atrust_personal:'https://jwapp-hqu-edu-cn-s.atrust.hqu.edu.cn:9443'};
  const base='/jwapp/sys/wdkb/modules/xskcb/';
  const need=(ok,message)=>k.requireValue(ok,message);
  function guard(){
    need(origins[k.adapterId]&&location.origin===origins[k.adapterId]&&location.pathname.startsWith('/jwapp/'),'请先进入所选入口的本校个人课表页面');
    need(!location.username&&!location.password&&!document.querySelector('input[type=password]'),'请先完成本校登录');
  }
  function stable(value){
    if(Array.isArray(value))return '['+value.map(stable).join(',')+']';
    if(value&&typeof value==='object')return '{'+Object.keys(value).sort().map(key=>JSON.stringify(key)+':'+stable(value[key])).join(',')+'}';
    return JSON.stringify(value);
  }
  async function fresh(action,fields){
    guard();const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const url=new URL(base+action+'.do',location.origin);
      if(k.adapterId==='ext_hqu_atrust_personal')url.searchParams.set('sf_request_type','ajax');
      const response=await fetch(url.href,{method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,
        headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','Accept':'application/json','X-Requested-With':'XMLHttpRequest'},body:new URLSearchParams(fields||{})});
      need(response.ok&&!response.redirected,'教务请求失败，请重新登录');
      if(response.url)need(new URL(response.url).origin===location.origin&&new URL(response.url).pathname===url.pathname,'教务响应地址发生变化');
      const text=await response.text();need(text.length>0&&text.length<=4000000&&!/^\s*</.test(text)&&!text.includes('\ufffd'),'教务未返回有效课表数据，请重新登录');
      return JSON.parse(text);
    }finally{clearTimeout(timer);}
  }
  function filled(value){return value!=null&&value!==''&&(!Array.isArray(value)||value.length>0)&&(typeof value!=='object'||Object.keys(value).length>0);}
  function complete(data,action){
    need(data&&typeof data==='object'&&!Array.isArray(data),'教务返回失败');
    if(data.success!=null)need(data.success===true||data.success===1||data.success==='true'||data.success==='1','教务返回失败');
    if(data.code!=null)need(['0','200'].includes(String(data.code)),'教务返回未知或失败状态，未导入部分结果');
    need(!filled(data.error),'教务返回错误');
    if(data.errorCode!=null)need(['0',''].includes(String(data.errorCode)),'教务返回错误');
    const datas=data.datas;need(datas&&typeof datas==='object'&&!Array.isArray(datas),'缺少教务结果');
    for(const key of Object.keys(datas))if(key!==action)need(!filled(datas[key]),'出现尚未核验的其他课表分支');
    const module=datas[action];need(module&&typeof module==='object'&&!Array.isArray(module),'没有取得指定教务结果');
    const rows=module.rows;need(Array.isArray(rows)&&rows.length>0&&rows.length<=5000,'未取得完整课程或学期记录');
    need(module.totalSize!=null,'接口未提供可核对的总条数，未导入部分结果');
    need(k.integer(module.totalSize,1,5000,'总条数')===rows.length,'接口存在未读取的记录');
    for(const field of ['total','totalCount'])if(module[field]!=null)need(k.integer(module[field],1,5000,'总条数')===rows.length,'接口总条数相互冲突');
    for(const field of ['pageNumber','currentPage','pages','totalPages'])if(module[field]!=null)need(k.integer(module[field],1,5000,'页码')===1,'接口存在未读取页面');
    for(const field of ['hasMore','hasNext','nextPage'])if(module[field]!=null)need(['false','0',''].includes(String(module[field])),'接口还有未读取页面');
    for(const field of Object.keys(module))if(field!=='rows'&&module[field]&&typeof module[field]==='object')need(!filled(module[field]),'接口出现尚未核验的结构化安排');
    for(const field of Object.keys(data))if(!['datas','code','success'].includes(field)&&data[field]&&typeof data[field]==='object')need(!filled(data[field]),'教务出现尚未核验的附加安排');
    return rows;
  }
  function date(value){
    need(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}(?::\d{2}(?:\.\d{1,6})?)?(?:Z|[+-]\d{2}:?\d{2})?)?$/.test(value),'缺少明确学期日期');
    const day=value.slice(0,10),d=new Date(day+'T00:00:00Z');need(Number.isFinite(d.getTime())&&d.toISOString().slice(0,10)===day,'教务学期日期无效');return day;
  }
  function calendar(rows){
    const terms=new Map();
    for(const row of rows){
      need(row&&typeof row==='object'&&!Array.isArray(row),'学期记录不完整');
      const year=String(row.XN==null?'':row.XN).trim(),q=String(row.XQ==null?'':row.XQ).trim(),m=year.match(/^(\d{4})(?:-(\d{4}))?$/);
      need(m&&(!m[2]||Number(m[2])===Number(m[1])+1)&&/^\d{1,2}$/.test(q),'学期字段发生变化');
      const start=date(row.XQKSRQ),code=year+'-'+q;
      const value={code,year,q,start,label:year+'学年第'+q+'学期（教务起始日 '+start+'）'};
      if(terms.has(code))need(terms.get(code).start===start,'同一学期有冲突的起始日期');else terms.set(code,value);
    }
    const result=Array.from(terms.values()).sort((a,b)=>b.start.localeCompare(a.start));need(result.length<=200,'可选学期过多，无法完整核对');return result;
  }
  function text(value,label){need(typeof value==='string'&&value.trim()&&value.length<=200&&!/^(?:未安排|待定|暂无|未知|[-—]+)$/.test(value.trim()),'课程缺少明确的'+label);return value.trim();}
  function bitmap(value){
    need(typeof value==='string'&&/^[01]{1,80}$/.test(value),'课程周位图无效');
    const result=[];for(let n=0;n<value.length;n++)if(value[n]==='1')result.push(k.integer(n+1,1,40,'周次'));
    need(result.length>0,'课程缺少明确周次');return result;
  }
  function courses(rows,term){
    const result=[];
    for(const row of rows){
      need(row&&typeof row==='object'&&!Array.isArray(row),'课程记录不完整');
      if(row.KBLB!=null)need(String(row.KBLB).trim()==='1','存在尚未核验的特殊类别安排，未导入部分结果');
      for(const [key,value]of Object.entries(row))if(value&&typeof value==='object')need(!filled(value),'课程含尚未核验的结构化安排');
      if(row.XNXQDM!=null)need(String(row.XNXQDM)===term.code,'课表混入其他学期');
      const name=text(row.KCM,'名称'),teacher=text(row.SKJS,'教师'),room=text(row.JASMC,'地点');
      const day=k.integer(row.SKXQ,1,7,'星期'),first=k.integer(row.KSJC,1,24,'起始节次'),last=k.integer(row.JSJC,first,24,'结束节次'),active=bitmap(row.SKZC);
      if(row.ZCMC!=null&&String(row.ZCMC).trim())need(stable(k.weeks(row.ZCMC))===stable(active),'周次说明与周位图冲突');
      const info=['学期：'+term.label.replace(/（.*$/,'')];
      for(const [key,label]of [['YPSJDD','原始安排'],['XF','学分'],['KCXZDM_DISPLAY','课程性质'],['TYXMDM_DISPLAY','体育项目'],['KCH','课程号'],['KCHDM','课程代码'],['JXBID','教学班标识'],['JXBH','教学班号'],['JXBMC','教学班']]){
        if(row[key]==null||String(row[key]).trim()==='')continue;
        need(['string','number'].includes(typeof row[key])&&String(row[key]).length<=800,'课程附加信息格式发生变化');
        if(key==='YPSJDD')need(!/(?:停课|调课|补课|换课|待排|未安排|未排课)/.test(String(row[key])),'原始安排含尚未完整核验的变更');
        info.push(label+'：'+String(row[key]).trim());
      }
      const extra=info.join('\n');need(extra.length<=4000,'课程附加信息过长');
      result.push({...k.course(name,teacher,room,day,first,last,active),extraInfo:extra});
    }
    return {courses:result,signature:stable(rows.map(stable).sort()),lastWeek:Math.max(...result.flatMap(c=>c.weeks))};
  }
  window.kagendaHquParsers={complete,calendar,bitmap,courses};
  k.run(async()=>{
    guard();const page=location.href;
    const beforeRows=complete(await fresh('cxxljc'),'cxxljc'),options=calendar(beforeRows);
    const selected=await k.choose('选择教务返回的学期',options,'code','label');
    const fields={XNXQDM:selected.code,XNXQDM2:selected.code,XNXQDM3:selected.code};
    const first=courses(complete(await fresh('cxxszhxqkb',fields),'cxxszhxqkb'),selected);
    window.kagendaHquMondayValidator=value=>{try{const d=date(String(value));need(d===value&&new Date(d+'T00:00:00Z').getUTCDay()===1,'请按校历填写第一周周一');return false;}catch(e){return String(e.message||e);}};
    const anchor=await k.api.showPrompt('开学第一周的周一',selected.label+'。请按校历填写教学第一周周一（YYYY-MM-DD）。','','kagendaHquMondayValidator');
    need(anchor!=null&&window.kagendaHquMondayValidator(anchor)===false,'已取消或校历周一无效');
    window.kagendaHquWeeksValidator=value=>{try{k.integer(value,first.lastWeek,40,'学期教学周数');return false;}catch(e){return String(e.message||e);}};
    const answer=await k.api.showPrompt('按校历填写学期教学周数','请按学校校历填写总教学周数；作息请在原生预览中核对。','','kagendaHquWeeksValidator');
    need(answer!=null,'已取消导入');const count=k.integer(answer,first.lastWeek,40,'学期教学周数');
    guard();need(location.href===page,'确认期间网页发生跳转');
    const afterRows=complete(await fresh('cxxljc'),'cxxljc');
    need(stable(beforeRows.map(stable).sort())===stable(afterRows.map(stable).sort()),'确认期间教务学期记录发生变化');
    const after=courses(complete(await fresh('cxxszhxqkb',fields),'cxxszhxqkb'),selected);
    need(after.signature===first.signature,'确认期间课表记录发生变化，未导入部分结果');
    guard();need(location.href===page,'查询期间网页发生跳转');
    // No historical or invented school time table is supplied. Existing native
    // preview requires explicit user review/confirmation of current time slots.
    await k.finish(after.courses,0,count,anchor);
  });
})();
