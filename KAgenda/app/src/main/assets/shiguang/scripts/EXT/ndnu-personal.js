// KAgenda original implementation, MIT; derived only from own FJNU parser.
// Public protocol facts only: flame-ray/kezhi
// @b527a12796a30c01636e0d2a4f5ba779550a2539, schoolCatalog.ts + src-tauri/src/lib.rs.
// No upstream code grant established; no upstream implementation copied.
// NDNU POST xnm/xqm/kzlx=ck; first term=3, second term=12. No index GET is inferred.
(function () {
  'use strict';
  const k=window.kagendaSchool, ORIGIN='https://jwgl.ndnu.edu.cn';
  const REFERER='/jwglxt/xtgl/login_slogin.html';
  const DATA='/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151';
  function need(ok,message){k.requireValue(ok,message);}
  function guard(){
    need(k.adapterId==='ext_ndnu_personal'&&location.origin===ORIGIN&&location.pathname.startsWith('/jwglxt/')&&
      !location.username&&!location.password,'请在本校 HTTPS 教务系统完成登录后重试');
    need(!document.querySelector('input[type="password"]'),'请先在教务系统完成登录');
  }
  const stable=value=>Array.isArray(value)?value.map(stable):value&&typeof value==='object'?
    Object.fromEntries(Object.keys(value).sort().map(key=>[key,stable(value[key])])):value;
  function empty(value){return value==null||value===''||(typeof value==='object'&&Object.keys(value).length===0);}
  async function fresh(term){
    const url=new URL(DATA,ORIGIN),controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const options={method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,
        headers:{Accept:'application/json','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},
        referrer:new URL(REFERER,ORIGIN).href,referrerPolicy:'same-origin',
        body:new URLSearchParams({xnm:term.xnm,xqm:term.xqm,kzlx:'ck'}).toString()};
      const response=await fetch(url.href,options);
      need(response.ok&&!response.redirected,'教务请求失败，请重新登录');
      if(response.url){const actual=new URL(response.url);need(actual.href===url.href,'教务响应来源或页面发生变化');}
      const charset=(response.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([^;"'\s]+)/i);
      need(!charset||/^(?:utf-8|utf8)$/i.test(charset[1]),'教务编码发生变化');
      const text=await response.text();need(text.length>0&&text.length<=1500000&&!text.includes('\ufffd'),'教务数据为空、过大或编码无效');
      need(!/^\s*</.test(text),'教务会话已失效，请重新登录');return JSON.parse(text);
    }finally{clearTimeout(timer);}
  }
  function year(value){
    need(typeof value==='string','请填写教务系统显示的学年');
    const m=value.trim().match(/^(\d{4})-(\d{4})$/);
    need(m&&Number(m[1])>=2000&&Number(m[1])<=2100&&Number(m[2])===Number(m[1])+1,'请填写连续的学年，格式 YYYY-YYYY');return m[1];
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
  // Alias values must agree; never pick one and silently drop a competing arrangement.
  function alias(row,keys,parse,label){
    const values=keys.filter(key=>!empty(row[key])).map(key=>parse(row[key]));
    need(values.length>0,'课程缺少明确的'+label);
    need(values.every(value=>JSON.stringify(value)===JSON.stringify(values[0])),'课程'+label+'字段冲突');return values[0];
  }
  function weekday(value){
    if(typeof value==='number'||typeof value==='string'&&/^\d+$/.test(value.trim()))return k.integer(value,1,7,'星期');
    need(typeof value==='string','课程缺少明确星期');
    const m=value.trim().match(/^(?:星期|周)([一二三四五六日天])$/);need(m,'课程星期格式发生变化');
    return m[1]==='天'?7:'一二三四五六日'.indexOf(m[1])+1;
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
    const rootKeys=new Set(['kbList','xsxx','success','code','error','errorCode','message','msg','hasMore','hasNext','nextPage','pages','totalPages','totalPage','currentPage','pageNumber','total','totalSize','totalCount','xnm','xqm']);
    for(const [key,value]of Object.entries(data))if(!rootKeys.has(key))need(empty(value),'存在未核验的附加课表内容，未导入部分结果');
    if(data.xsxx!=null){
      need(typeof data.xsxx==='object'&&!Array.isArray(data.xsxx),'课表身份格式发生变化');
      for(const value of Object.values(data.xsxx))need(empty(value)||typeof value==='string'||typeof value==='number'||typeof value==='boolean','课表身份含未核验的结构');
      for(const key of ['xnm','xqm'])if(data.xsxx[key]!=null)need(String(data.xsxx[key])===term[key],'个人课表学期冲突');
      if(data.xsxx.XNMC!=null)need(String(data.xsxx.XNMC).replace(/\s|学年/g,'')===term.xnm+'-'+(Number(term.xnm)+1),'个人课表学年冲突');
      if(data.xsxx.XQMMC!=null){
        const names=term.xqm==='3'?['1','第一学期','第1学期','1学期']:['2','第二学期','第2学期','2学期'];
        need(names.includes(String(data.xsxx.XQMMC).replace(/\s/g,'')),'个人课表学期冲突或名称未核验');
      }
    }
    const courses=[];
    data.kbList.forEach((row,index)=>{
      need(row&&typeof row==='object'&&!Array.isArray(row),'课程记录不完整');
      for(const value of Object.values(row))if(value&&typeof value==='object')need(empty(value),'课程含未核验的结构化安排');
      for(const key of ['xnm','xqm'])if(row[key]!=null)need(String(row[key])===term[key],'课程混入其他学期');
      need(empty(row.dsz),'出现未核验的独立单双周标志');
      const day=alias(row,['xqj','xqjmc'],weekday,'星期');
      const slots=alias(row,['jc','jcs'],sections,'节次');
      const weeks=alias(row,['zcd','zcdmc'],k.weeks,'周次'),name=text(row.kcmc,'名称');
      const teacher=alias(row,['xm','jsxm','jsxx'],value=>text(value,'教师'),'教师');
      const room=alias(row,['cdmc','jxdd'],value=>text(value,'地点'),'地点');
      let campus='';if(row.xqmc!=null){need(typeof row.xqmc==='string'&&row.xqmc.length<=200,'校区字段无效');campus=row.xqmc.trim();}
      const info={semester:term.label,sourceRow:index+1};
      for(const key of ['kch_id','kch','jxb_id','do_jxb_id','jxbmc','jxbzc','xqh_id','xkbz'])if(row[key]!=null){need((typeof row[key]==='string'||typeof row[key]==='number')&&String(row[key]).length<=1000,'课程标识或附加信息无效');info[key]=String(row[key]);}
      const extraInfo=JSON.stringify(info);need(extraInfo.length<=4000,'课程附加信息过大');
      const groups=[];for(const n of slots){const previous=groups[groups.length-1];if(previous&&previous[1]+1===n)previous[1]=n;else groups.push([n,n]);}
      for(const [first,last]of groups)courses.push(Object.assign(k.course(name,teacher,campus&&!room.includes(campus)?campus+' '+room:room,day,first,last,weeks),{extraInfo}));
    });
    need(courses.length<=5000,'课程安排过多，无法完整核对');
    const signature=JSON.stringify({term,response:stable({...data,kbList:data.kbList.map(row=>JSON.stringify(stable(row))).sort()})});
    return {courses,signature,lastWeek:Math.max(...courses.flatMap(c=>c.weeks))};
  }
  const validator=fn=>value=>{try{fn(value);return false;}catch(error){return String(error.message||error);}};
  window.kagendaNdnuParsers={year,monday,sections,parsed};
  k.run(async()=>{
    guard();const page=location.href;
    window.kagendaNdnuYearValidator=validator(year);
    const answer=await k.api.showPrompt('要导入的学年','请填写教务系统显示的学年，格式 YYYY-YYYY。','','kagendaNdnuYearValidator');
    const xnm=year(answer),choice=await k.choose('要导入的学期',[{code:'3',label:'第一学期'},{code:'12',label:'第二学期'}],'code','label');
    const term={xnm,xqm:choice.code,label:xnm+'-'+(Number(xnm)+1)+'学年'+choice.label};
    guard();need(location.href===page,'选择期间网页发生跳转');
    const before=parsed(await fresh(term),term);
    window.kagendaNdnuMondayValidator=validator(monday);
    const anchor=monday(await k.api.showPrompt('开学第一周的周一',term.label+'。请按学校校历填写教学第一周周一（YYYY-MM-DD）。','','kagendaNdnuMondayValidator'));
    window.kagendaNdnuWeeksValidator=validator(value=>k.integer(value,before.lastWeek,40,'学期教学周数'));
    const weeksAnswer=await k.api.showPrompt('按校历填写学期教学周数','请按所选学期校历填写总教学周数；作息请在预览中核对。','','kagendaNdnuWeeksValidator');
    need(weeksAnswer!=null,'已取消导入');const count=k.integer(weeksAnswer,before.lastWeek,40,'学期教学周数');
    guard();need(location.href===page,'确认期间网页发生跳转');
    const after=parsed(await fresh(term),term);
    need(before.signature===after.signature,'确认期间身份或课表发生变化，未导入部分结果');
    guard();need(location.href===page,'查询期间网页发生跳转');
    // No upstream dates, cached rows or guessed school clocks. Native preview
    // explicitly reviews course data and time slots before the user can save.
    await k.finish(after.courses,0,count,anchor);
  });
})();
