// KAgenda original implementation, MIT. Three explicitly registered school profiles only.
// Protocol facts: kqakqakqa/xiaoai-schedule-parser@3a4bbfced68fcda9c4bc0ead5b5996e772164e58
// school-named FZFU branch, 新正方V9.0_request/provider.js (2025-03-27);
// zaochih/zf-to-ics@f9a8cfab8c4c79e4f56d1e8d583a9ae439ec86a1 extension/content.js (2026-10-12), SMU deployment example.
// No upstream implementation copied. No grant inferred for either reference.
// FIT protocol facts: Haooz, ShiGuangSchedule/shiguang_warehouse@c4a35feb061a3f6db2d6c122927083c067c61309,
// resources/FIT/fit.js (2026-08-31, MIT). Public request facts only; no upstream implementation copied.
(function(){
  'use strict';
  const k=window.kagendaSchool, profiles={
    ext_fzfu_personal:{origins:['http://portal.fzfu.edu.cn','https://portal.fzfu.edu.cn','http://jw.fzfu.edu.cn','https://jw.fzfu.edu.cn'],moduleInBody:true},
    ext_smu_personal:{origins:['http://jwxxt.fjsmu.edu.cn','https://jwxxt.fjsmu.edu.cn'],moduleInBody:false},
    ext_fit_personal:{origins:['http://oaa.fitedu.net'],moduleInBody:false,requireSelect:true,fixedModule:'N2151'}
  },profile=profiles[k.adapterId],ORIGIN=location.origin;
  const PATH='/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html',DATA='/jwglxt/kbcx/xskbcx_cxXsgrkb.html';
  function need(ok,message){k.requireValue(ok,message);}
  function guard(){need(profile&&profile.origins.includes(location.origin)&&location.origin===ORIGIN&&location.pathname===PATH&&!location.username&&!location.password,'请在本校学生个人课表页面选择学期后重试');const role=new URL(location.href).searchParams.get('jsdm');need(role==null||role==='xs','请使用学生个人课表入口');need(!document.querySelector('input[type="password"]'),'请先完成教务登录');}
  const stable=value=>Array.isArray(value)?value.map(stable):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,stable(value[key])])):value;
  function empty(value){return value==null||value===''||(typeof value==='object'&&Object.keys(value).length===0);}
  function selected(id){
    let el=document.querySelector('#'+id);if(el?.value==null)el=document.querySelector('#'+id+'_hide');
    need(el&&typeof el.value==='string'&&el.value.trim()===el.value&&el.value.length>0&&el.value.length<=80&&!/[\u0000-\u001f]/.test(el.value),'没有取得当前课表的学年或学期');
    let label='';
    need(!profile.requireSelect||el.tagName==='SELECT','请在学校个人课表页明确选择学年学期');
    if(el.tagName==='SELECT'){
      const options=Array.from(el.options||[]).filter(o=>o.selected);
      need(options.length===1&&!options[0].disabled,'学年或学期选择不明确');
      label=String(options[0].textContent||'').replace(/\s+/g,' ').trim();
      need(label&&label.length<=200&&!/(?:请选择|未选择|未确定|未知)/.test(label),'学年或学期名称不明确');
    }
    return {value:el.value,label};
  }
  function current(){
    guard();const y=selected('xnm'),q=selected('xqm'),hidden=document.querySelector('#gnmkdm');
    const fromPage=hidden?.value,fromUrl=new URL(location.href).searchParams.get('gnmkdm');
    if(fromPage&&fromUrl)need(fromPage===fromUrl,'课表模块字段冲突');
    const module=fromPage||fromUrl||'N2151';need(/^N\d{3,12}$/.test(module),'课表模块格式未核验');
    need(!profile.fixedModule||module===profile.fixedModule,'当前课表模块未在本校来源中核验');
    const raw={xnm:y.value,xqm:q.value,yearLabel:y.label,termLabel:q.label,module};
    return Object.assign(raw,{snapshot:JSON.stringify(raw)});
  }
  async function fresh(term){
    const url=new URL(DATA,ORIGIN);if(!profile.moduleInBody)url.searchParams.set('gnmkdm',term.module);
    const fields={xnm:term.xnm,xqm:term.xqm};if(profile.moduleInBody)fields.gnmkdm=term.module;
    fields.kzlx='ck';fields.xsdm='';if(!profile.moduleInBody)fields.kclbdm='';
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const response=await fetch(url.href,{method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,referrer:location.href,referrerPolicy:'same-origin',headers:{Accept:'application/json','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},body:new URLSearchParams(fields).toString()});
      need(response.ok&&!response.redirected,'教务请求失败，请重新登录');
      if(response.url){const actual=new URL(response.url);need(actual.origin===ORIGIN&&actual.pathname===DATA,'教务响应来源或页面发生变化');}
      const charset=(response.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([^;"'\s]+)/i);need(!charset||/^(?:utf-8|utf8)$/i.test(charset[1]),'教务编码发生变化');
      const text=await response.text();need(text.length>0&&text.length<=1500000&&!text.includes('\ufffd'),'教务数据为空、过大或编码无效');need(!/^\s*</.test(text),'教务会话已失效，请重新登录');return JSON.parse(text);
    }finally{clearTimeout(timer);}
  }
  function monday(value){
    need(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value),'请按校历填写第一周周一');
    const d=new Date(value+'T00:00:00Z');need(Number.isFinite(d.getTime())&&d.toISOString().slice(0,10)===value&&d.getUTCDay()===1,'请按校历填写有效的周一日期');return value;
  }
  function text(value,label){need(typeof value==='string'&&value.trim()&&value.length<=200&&!/^(?:未知|待定|未安排|暂无|未排|未分配|null|undefined|[-—–]+)$/i.test(value.trim()),'课程缺少明确的'+label);return value.trim();}
  function sections(value){
    need(typeof value==='string'||typeof value==='number','课程缺少明确节次');
    const raw=String(value).replace(/[第节\s]/g,'').replace(/[，、]/g,',');need(raw&&raw.length<150,'课程缺少明确节次');
    const result=[];
    for(const part of raw.split(',')){
      const m=part.match(/^(\d+)(?:[-~～至](\d+))?$/);need(m,'课程节次格式发生变化');
      const first=k.integer(m[1],1,24,'节次'),last=m[2]?k.integer(m[2],first,24,'节次'):first;
      for(let n=first;n<=last;n++)result.push(n);
    }
    return Array.from(new Set(result)).sort((a,b)=>a-b);
  }
  function parsed(data,term){
    need(data&&typeof data==='object'&&!Array.isArray(data),'教务结果格式发生变化');
    if(data.success!=null)need(['true','1'].includes(String(data.success)),'教务返回失败');
    if(data.code!=null)need(['0','200'].includes(String(data.code)),'教务返回失败或未知状态');
    if(data.error!=null)need(['','false','0'].includes(String(data.error)),'教务返回错误');
    if(data.errorCode!=null)need(['','0'].includes(String(data.errorCode)),'教务返回错误');
    for(const key of ['message','msg'])if(typeof data[key]==='string')need(!/(?:未登录|未授权|无权限|没有权限|请.{0,12}登录|登录.{0,12}(?:失效|过期|失败)|会话.{0,12}(?:失效|过期)|session.{0,12}(?:expired|invalid))/i.test(data[key]),'教务会话失效或没有课表权限');
    need(Array.isArray(data.kbList)&&data.kbList.length>0&&data.kbList.length<=5000,'未取得完整个人课表');
    for(const key of ['hasMore','hasNext','nextPage'])if(data[key]!=null)need(['false','0',''].includes(String(data[key])),'课表还有未读取的页面');
    for(const key of ['pages','totalPages','totalPage','currentPage','pageNumber'])if(data[key]!=null)need(k.integer(data[key],1,5000,'页码')===1,'课表还有未读取的页面');
    for(const key of ['total','totalSize','totalCount'])if(data[key]!=null)need(k.integer(data[key],0,5000,'课程总数')===data.kbList.length,'课程总数与读取结果不符');
    for(const key of ['xnm','xqm'])if(data[key]!=null)need(String(data[key])===term[key],'课表学期与所选学期冲突');
    for(const [key,value]of Object.entries(data))if(key!=='kbList'&&key!=='xsxx'&&value&&typeof value==='object')need(empty(value),'存在尚未完整核验的附加安排，未导入部分结果');
    if(data.xsxx!=null){
      need(typeof data.xsxx==='object'&&!Array.isArray(data.xsxx),'课表身份格式发生变化');
      for(const value of Object.values(data.xsxx))need(empty(value)||typeof value==='string'||typeof value==='number'||typeof value==='boolean','课表身份含未核验的结构');
      for(const key of ['xnm','xqm'])if(data.xsxx[key]!=null)need(String(data.xsxx[key])===term[key],'个人课表学期冲突');
      if(data.xsxx.XNMC!=null){
        const namedYear=(term.yearLabel||term.label).match(/(\d{4})\s*-\s*(\d{4})/);
        if(namedYear&&/^\d{4}$/.test(term.xnm))need(namedYear[1]===term.xnm,'学年显示名称与所选值冲突');
        const expected=namedYear?namedYear[1]+'-'+namedYear[2]:/^\d{4}$/.test(term.xnm)?term.xnm+'-'+(Number(term.xnm)+1):null;
        if(expected)need(String(data.xsxx.XNMC).replace(/\s|学年/g,'')===expected,'个人课表学年冲突');
      }
      if(data.xsxx.XQMMC!=null){
        const ordinal=term.ordinal, chinese={'1':'一','2':'二','3':'三'}[ordinal];
        const names=[term.termLabel.replace(/\s/g,'')];
        if(ordinal)names.push(ordinal,'第'+ordinal+'学期',ordinal+'学期','第'+chinese+'学期');
        need(names.includes(String(data.xsxx.XQMMC).replace(/\s/g,'')),'个人课表学期冲突或名称未核验');
      }
    }
    const courses=[];
    data.kbList.forEach((row,index)=>{
      need(row&&typeof row==='object'&&!Array.isArray(row),'课程记录不完整');
      for(const value of Object.values(row))if(value&&typeof value==='object')need(empty(value),'课程含未核验的结构化安排');
      for(const key of ['xnm','xqm'])if(row[key]!=null)need(String(row[key])===term[key],'课程混入其他学期');
      need(empty(row.dsz),'出现未核验的独立单双周标志');
      const day=k.integer(row.xqj,1,7,'星期');
      if(row.xqjmc!=null)need(row.xqjmc===['星期一','星期二','星期三','星期四','星期五','星期六','星期日'][day-1],'课程星期字段冲突');
      const slots=sections(row.jcs);
      if(row.jcs!=null&&row.jcs!==''&&row.jc!=null&&String(row.jc).trim())need(JSON.stringify(sections(row.jc))===JSON.stringify(slots),'课程节次字段冲突');
      const weeks=k.weeks(row.zcd),name=text(row.kcmc,'名称'),teacher=text(row.xm,'教师'),room=text(row.cdmc,'地点');
      let campus='';if(row.xqmc!=null){need(typeof row.xqmc==='string'&&row.xqmc.length<=200,'校区字段无效');campus=row.xqmc.trim();}
      const info={semester:term.label,sourceRow:index+1};
      for(const key of ['kch_id','kch','jxb_id','jxbmc','jxbzc','xqh_id','xkbz'])if(row[key]!=null){need((typeof row[key]==='string'||typeof row[key]==='number')&&String(row[key]).length<=1000,'课程标识或附加信息无效');info[key]=String(row[key]);}
      const extraInfo=JSON.stringify(info);need(extraInfo.length<=4000,'课程附加信息过大');
      const groups=[];for(const n of slots){const previous=groups[groups.length-1];if(previous&&previous[1]+1===n)previous[1]=n;else groups.push([n,n]);}
      for(const [first,last]of groups)courses.push(Object.assign(k.course(name,teacher,campus&&!room.includes(campus)?campus+' '+room:room,day,first,last,weeks),{extraInfo}));
    });
    need(courses.length<=5000,'课程安排过多，无法完整核对');
    const signature=JSON.stringify({term,identity:stable(data.xsxx??null),rows:data.kbList.map(row=>JSON.stringify(stable(row))).sort()});
    return {courses,signature,lastWeek:Math.max(...courses.flatMap(c=>c.weeks))};
  }
  const validator=fn=>value=>{try{fn(value);return false;}catch(error){return String(error.message||error);}};
  window.kagendaSelectedZfParsers={current,monday,sections,parsed};
  k.run(async()=>{
    guard();const page=location.href,beforeFields=current();let display;
    if(beforeFields.yearLabel&&beforeFields.termLabel)display=beforeFields.yearLabel+' '+beforeFields.termLabel;
    else {
      window.kagendaSelectedZfLabelValidator=validator(value=>text(value,'学期名称'));
      display=text(await k.api.showPrompt('当前课表学期名称','请按当前网页填写完整学期名称。读取的是网页当前选择的学期；需要其他学期时，请先在教务页面切换。','','kagendaSelectedZfLabelValidator'),'学期名称');
    }
    const explicitNames=display.match(/第?[123一二三]学期/g)||[];
    const effectiveLabel=beforeFields.termLabel|| (explicitNames.length===1?explicitNames[0]:display);
    const term=Object.assign({},beforeFields,{code:beforeFields.xnm+':'+beforeFields.xqm,label:display,termLabel:effectiveLabel});
    const named=effectiveLabel.replace(/\s/g,'').match(/^(?:第)?([123一二三])(?:学期)?$/);
    term.ordinal=named?({'一':'1','二':'2','三':'3'}[named[1]]||named[1]):null;
    await k.choose('核对当前课表学期',[term],'code','label');
    guard();need(location.href===page&&current().snapshot===beforeFields.snapshot,'确认期间网页或所选学期发生变化');
    const before=parsed(await fresh(term),term);
    window.kagendaSelectedZfMondayValidator=validator(monday);
    const anchor=monday(await k.api.showPrompt('开学第一周的周一',display+'。请按校历填写教学第一周周一（YYYY-MM-DD）。','','kagendaSelectedZfMondayValidator'));
    window.kagendaSelectedZfWeeksValidator=validator(value=>k.integer(value,before.lastWeek,40,'学期教学周数'));
    const answer=await k.api.showPrompt('按校历填写学期教学周数','请按当前所选学期校历填写总教学周数，并在原生预览核对作息。','','kagendaSelectedZfWeeksValidator');
    need(answer!=null,'已取消导入');const count=k.integer(answer,before.lastWeek,40,'学期教学周数');
    guard();need(location.href===page&&current().snapshot===beforeFields.snapshot,'确认期间网页或所选学期发生变化');
    const after=parsed(await fresh(term),term);need(before.signature===after.signature,'确认期间身份或课表发生变化，未导入部分结果');
    guard();need(location.href===page&&current().snapshot===beforeFields.snapshot,'读取期间网页或所选学期发生变化');
    await k.finish(after.courses,0,count,anchor);
  });
})();
