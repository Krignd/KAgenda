// Original KAgenda implementation, MIT; no upstream implementation copied.
// Protocol facts: official XMU xskcb.js/xskcbBS.js, pinned SHA-256 in research.
// The shared official page supports undergraduate and graduate rows. No stage
// or vendor inference is made from gsapp, a parser class name, or a clock code.
(function () {
  'use strict';
  const k=window.kagendaSchool,origin='https://jw.xmu.edu.cn',base='/gsapp/sys/wdkbapp/';
  const need=(ok,message)=>k.requireValue(ok,message);
  const endpoints={identity:'wdkcb/initXsxx.do',terms:'modules/xskcb/kfdxnxqcx.do',weeks:'wdkcb/getZcxx.do',
    clocks:'wdkcb/queryXsskjc.do',courses:'wdkcb/queryXspkjg.do',dates:'modules/xskcb/rqapjghbcx.do',unarranged:'wdkcb/queryXswsckbkc.do'};
  function plain(x){return x&&typeof x==='object'&&!Array.isArray(x);}
  function filled(x){return x!=null&&x!==''&&(typeof x!=='object'||Object.keys(x).length>0);}
  function stable(x){
    if(Array.isArray(x))return '['+x.map(stable).join(',')+']';
    if(plain(x))return '{'+Object.keys(x).sort().map(key=>JSON.stringify(key)+':'+stable(x[key])).join(',')+'}';
    return JSON.stringify(x);
  }
  function guard(){
    need(k.adapterId==='ext_xmu_personal'&&location.origin===origin&&location.pathname.startsWith(base),'请先打开本校学生个人课表并完成登录');
    need(!location.username&&!location.password&&!document.querySelector('input[type=password]'),'请先完成本校登录');
    const url=new URL(location.href),hash=new URLSearchParams(url.hash.includes('?')?url.hash.slice(url.hash.indexOf('?')+1):'');
    need(!url.searchParams.get('reqXH')&&!hash.get('reqXH'),'请打开本人课表，不支持代查其他学生');
  }
  async function fresh(action,fields){
    guard();const url=new URL(base+endpoints[action],origin),get=action==='identity';
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    const body=new URLSearchParams(fields||{});if(get)url.search=body.toString();
    try{
      const response=await fetch(url.href,{method:get?'GET':'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,
        headers:{Accept:'application/json','Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','X-Requested-With':'XMLHttpRequest'},...(get?{}:{body})});
      need(response.ok&&!response.redirected,'教务请求失败，请重新登录');
      if(response.url){const actual=new URL(response.url);need(actual.origin===origin&&actual.pathname===url.pathname,'教务响应地址发生变化');}
      const encoding=(response.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([^;"'\s]+)/i);
      need(!encoding||/^(utf-8|utf8)$/i.test(encoding[1]),'教务响应编码发生变化');
      const text=await response.text();need(text.length>0&&text.length<=4000000&&!/^\s*</.test(text)&&!text.includes('\ufffd'),'教务未返回有效数据，请重新登录');
      try{return JSON.parse(text);}catch(error){throw Error('教务未返回有效 JSON 数据');}
    }finally{clearTimeout(timer);}
  }
  function status(data){
    need(plain(data),'教务返回格式发生变化');
    if(data.success!=null)need([true,1,'true','1'].includes(data.success),'教务返回失败');
    if(data.code!=null)need(['0','200'].includes(String(data.code)),'教务返回未知或失败状态');
    need(!filled(data.error),'教务返回错误');
    if(data.errorCode!=null)need(['0',''].includes(String(data.errorCode)),'教务返回错误');
    for(const key of ['msg','message'])if(filled(data[key]))need(typeof data[key]==='string'&&!/(?:登录|登陆|失效|失败|错误|无权限|没有权限|无权|拒绝|未认证|过期|timeout|error|denied|unauthori[sz]ed|forbidden|invalid session)/i.test(data[key]),'教务会话或查询状态无效');
  }
  const meta=['success','code','error','errorCode','msg','message','total','totalSize','totalCount','pageNumber','currentPage','pageSize','pages','totalPages','hasMore','hasNext','nextPage'];
  function pages(data,count){
    for(const key of ['total','totalSize','totalCount'])if(data[key]!=null)need(k.integer(data[key],0,5000,'总条数')===count,'教务条数不完整');
    for(const key of ['pageNumber','currentPage','pages','totalPages'])if(data[key]!=null){const value=k.integer(data[key],0,5000,'页码');need(value===1||(count===0&&value===0),'教务存在未读取页面');}
    for(const key of ['hasMore','hasNext','nextPage'])if(data[key]!=null)need(['false','0',''].includes(String(data[key])),'教务存在后续页面');
    // A page-size setting is not a returned-row count. Actual total/next-page
    // metadata above establishes completeness; a full response may exceed its setting.
    if(data.pageSize!=null)k.integer(data.pageSize,0,5000,'每页条数');
  }
  function branches(data,allowed){
    for(const key of Object.keys(data))if(!allowed.includes(key)&&!meta.includes(key))need(!filled(data[key]),'出现尚未完整核验的教务分支，未导入部分结果');
  }
  function records(data,key,empty){
    status(data);need(Array.isArray(data[key])&&data[key].length<=5000&&(empty||data[key].length>0),'缺少完整教务记录');
    pages(data,data[key].length);branches(data,[key]);return data[key];
  }
  function moduleRows(data,action,empty){
    status(data);need(plain(data.datas),'缺少教务数据集');branches(data,['datas']);
    for(const key of Object.keys(data.datas))if(key!==action)need(!filled(data.datas[key]),'存在其他未核验的数据集');
    const mod=data.datas[action];need(plain(mod)&&Array.isArray(mod.rows)&&mod.rows.length<=5000&&(empty||mod.rows.length>0),'教务数据集不完整');
    need(mod.totalSize!=null,'教务没有返回可核对的总条数');pages(mod,mod.rows.length);branches(mod,['rows']);return mod.rows;
  }
  function text(x,label,max=300){need(typeof x==='string'&&x.trim()&&x.length<=max&&!/^(?:未安排|待定|暂无|未知|未分配|[-—–]+|null|undefined)$/i.test(x.trim()),'缺少明确的'+label);return x.trim();}
  function id(x,label){need(['string','number'].includes(typeof x),'缺少有效的'+label);return text(String(x),label,150);}
  function identity(data){const rows=records(data,'data',false);need(rows.length===1&&plain(rows[0]),'没有取得唯一的本人学生身份');id(rows[0].XH,'本人学生身份');return rows[0];}
  function terms(rows){
    const result=[],seen=new Set();
    for(const row of rows){
      need(plain(row),'学期记录不完整');const code=id(row.XNXQDM,'学期代码');
      const label=text(row.XNXQDM_DISPLAY||row.MC,'学期名称');need(!seen.has(code),'教务学期重复，无法唯一选择');seen.add(code);result.push({code,label});
    }
    need(result.length<=200,'学期列表过大，未完整核对');return result;
  }
  function calendar(data){
    status(data);need(data.success===true,'没有取得成功的教务周历');
    need(Array.isArray(data.zcList)&&data.zcList.length>0&&data.zcList.length<=40,'没有取得完整教务周历');
    branches(data,['zcList','currentZc']);pages(data,data.zcList.length);
    const weeks=data.zcList.map(row=>{need(plain(row),'教务周次记录不完整');return k.integer(row.ZC,1,40,'教务周次');}).sort((a,b)=>a-b);
    need(weeks.every((w,i)=>w===i+1),'教务周历缺周或重复，未导入部分课表');return weeks.length;
  }
  function clock(x){
    const raw=String(x==null?'':x).trim(),match=raw.match(/^(\d{1,2}):?(\d{2})(?::00)?$/);need(match,'教务作息时间格式无效');
    const h=k.integer(match[1],0,23,'小时'),m=k.integer(match[2],0,59,'分钟');return {text:String(h).padStart(2,'0')+':'+String(m).padStart(2,'0'),minute:h*60+m};
  }
  function lesson(x){
    const raw=String(x==null?'':x).trim(),match=raw.match(/^(?:第)?(\d{1,2}|[一二三四五六七八九十]{1,3})(?:节)?$/);need(match,'节次方案缺少明确的实际节次名称');
    let value=match[1];if(!/^\d+$/.test(value)){
      const chars='一二三四五六七八九',split=value.split('十');
      if(split.length===1)value=String(chars.indexOf(value)+1);
      else{need(split.length===2&&(!split[0]||chars.includes(split[0]))&&(!split[1]||chars.includes(split[1])),'中文节次无效');value=String((split[0]?chars.indexOf(split[0])+1:1)*10+(split[1]?chars.indexOf(split[1])+1:0));}
    }
    return k.integer(value,1,24,'实际节次');
  }
  function plans(rows,used){
    const found=new Map();
    for(const row of rows){
      need(plain(row),'作息记录不完整');const scheme=id(row.JCFADM,'节次方案');if(!used.has(scheme))continue;
      for(const value of Object.values(row))if(value&&typeof value==='object')need(!filled(value),'作息含尚未核验的结构化安排');
      const number=lesson(row.MC),start=clock(row.KSSJ),end=clock(row.JSSJ),code=id(row.DM,'节次代码');
      need(start.minute<end.minute,'作息起止时间无效');
      if(!found.has(scheme))found.set(scheme,[]);
      found.get(scheme).push({number,startTime:start.text,endTime:end.text,start:start.minute,end:end.minute,code});
    }
    for(const scheme of used){
      const list=found.get(scheme);need(list&&list.length>0&&list.length<=24,'课程节次方案缺失');list.sort((a,b)=>a.number-b.number);
      need(list.every((s,i)=>s.number===i+1&&(i===0||s.start>=list[i-1].end)),'作息方案缺节、重复或重叠');
      need(new Set(list.map(s=>s.code)).size===list.length,'作息节次代码重复');
    }
    const signatures=Array.from(used,scheme=>stable(found.get(scheme).map(s=>[s.number,s.startTime,s.endTime])));
    need(new Set(signatures).size===1,'课程使用不同作息方案，当前不能完整保存为同一张课表');return found;
  }
  function parsed(snapshot,term){
    const data=snapshot.courses;status(data);need(Array.isArray(data.pkjgList)&&data.pkjgList.length>0&&data.pkjgList.length<=5000,'未取得完整个人学期课表');
    need(Array.isArray(data.ttbList)&&data.ttbList.length===0,'存在尚未完整核验的调停补课，未导入部分课表');
    pages(data,data.pkjgList.length);branches(data,['pkjgList','ttbList']);
    const dates=moduleRows(snapshot.dates,'rqapjghbcx',true);need(dates.length===0,'存在尚未完整核验的按日期排课，未导入部分课表');
    const unarranged=records(snapshot.unarranged,'data',true);need(unarranged.length===0,'存在未生成完整课表的课程，未导入部分结果');
    const count=calendar(snapshot.weeks),used=new Set();
    for(const row of data.pkjgList){need(plain(row),'课程记录不完整');used.add(id(row.JCFADM,'课程节次方案'));}
    const schemes=plans(records(snapshot.clocks,'data',false),used),courses=[];
    for(const row of data.pkjgList){
      for(const [key,value]of Object.entries(row))if(value&&typeof value==='object')need(!filled(value),'课程含尚未核验的结构化安排');
      const scheme=id(row.JCFADM,'课程节次方案'),slots=schemes.get(scheme),start=clock(row.KSSJ),end=clock(row.JSSJ);
      need(start.minute<end.minute,'课程起止时间无效');
      const covered=slots.filter(s=>s.start>=start.minute&&s.end<=end.minute);
      need(covered.length>0&&covered[0].start===start.minute&&covered[covered.length-1].end===end.minute&&covered.every((s,i)=>i===0||s.number===covered[i-1].number+1),'课程时间不能完整对应实际节次方案');
      if(row.XNXQDM!=null)need(String(row.XNXQDM)===term.code,'课程混入其他学期');
      if(row.XH!=null)need(plain(snapshot.student)&&String(row.XH)===String(snapshot.student.XH),'课程身份与本人学生身份不一致');
      const active=k.weeks(row.ZCMC);need(active.every(w=>w<=count),'课程周次超出教务周历');
      const name=text(row.KCMC,'课程名称',200),teacher=text(row.JSXM,'教师'),room=text(row.JASMC,'教室'),day=k.integer(row.XQ,1,7,'星期');
      const classId=id(row.BJDM,'教学班标识'),arrangeId=id(row.WID,'排课标识');
      let campus='';if(filled(row.XQDM_DISPLAY)||filled(row.XQMC))campus=text(row.XQDM_DISPLAY||row.XQMC,'校区');
      const info=['学期：'+term.label,'教学班标识：'+classId,'排课标识：'+arrangeId,'节次方案：'+scheme];
      for(const [key,label]of [['KCDM','课程代码'],['BJMC','教学班'],['SJLY','教务来源标识'],['KBBZ','课表备注']])if(filled(row[key])){
        const value=id(row[key],label);if(key==='KBBZ')need(!/(?:停课|调课|补课|换课|未排|待排)/.test(value),'备注含尚未完整核验的变更');info.push(label+'：'+value);
      }
      const extraInfo=info.join('\n');need(extraInfo.length<=4000,'课程附加信息过长');
      courses.push({...k.course(name,teacher,campus&&!room.includes(campus)?campus+' '+room:room,day,covered[0].number,covered[covered.length-1].number,active),extraInfo});
    }
    const first=schemes.get(used.values().next().value),times=first.map(s=>({number:s.number,startTime:s.startTime,endTime:s.endTime}));
    const sorted=rows=>rows.map(stable).sort();
    return {courses,times,count,signature:stable({student:snapshot.student,weekRows:sorted(snapshot.weeks.zcList),
      clockRows:sorted(snapshot.clocks.data),courseRows:sorted(data.pkjgList),dateRows:sorted(dates),unarranged:sorted(unarranged)})};
  }
  async function snapshot(term,student){
    const fields={XNXQDM:term.code,XH:''};
    const [weeks,clocks,courses,dates,unarranged]=await Promise.all([
      fresh('weeks',{XNXQDM:term.code}),fresh('clocks',{XNXQDM:term.code,XH:student.XH}),
      fresh('courses',{...fields,ZC:''}),fresh('dates',{XNXQDM:term.code,ZC:''}),fresh('unarranged',fields)]);
    return {weeks,clocks,courses,dates,unarranged,student:{XH:student.XH}};
  }
  function monday(value){
    need(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value),'请填写第一周周一日期');const d=new Date(value+'T00:00:00Z');
    need(Number.isFinite(d.getTime())&&d.toISOString().slice(0,10)===value&&d.getUTCDay()===1,'请按校历填写有效的第一周周一');return value;
  }
  window.kagendaXmuParsers={records,moduleRows,identity,terms,calendar,clock,lesson,plans,parsed,monday};
  k.run(async()=>{
    guard();const page=location.href,who=identity(await fresh('identity',{XH:''}));
    const rawTerms=moduleRows(await fresh('terms',{}),'kfdxnxqcx',false),term=await k.choose('选择教务返回的学期',terms(rawTerms),'code','label');
    const before=parsed(await snapshot(term,who),term);
    window.kagendaXmuMondayValidator=value=>{try{monday(value);return false;}catch(error){return String(error.message||error);}};
    const answer=await k.api.showPrompt('开学第一周的周一',term.label+'，教务周历共'+before.count+'周。请按学校校历填写第一周周一（YYYY-MM-DD），作息来自当前教务节次方案。','','kagendaXmuMondayValidator');
    need(answer!=null,'已取消导入');const anchor=monday(answer);guard();need(location.href===page,'确认期间网页发生跳转');
    const afterWho=identity(await fresh('identity',{XH:''}));need(stable(afterWho)===stable(who),'确认期间本人身份发生变化');
    const afterTerms=moduleRows(await fresh('terms',{}),'kfdxnxqcx',false);need(stable(afterTerms.map(stable).sort())===stable(rawTerms.map(stable).sort()),'确认期间学期列表发生变化');
    const after=parsed(await snapshot(term,afterWho),term);need(after.signature===before.signature,'确认期间课表或校历作息发生变化，未导入部分结果');
    guard();need(location.href===page,'查询期间网页发生跳转');
    await k.api.savePresetTimeSlots(JSON.stringify(after.times));await k.finish(after.courses,0,after.count,anchor);
  });
})();
