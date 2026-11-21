// KAgenda original implementation, MIT; own strict row parser reused from selected-zf-personal.js.
// Protocol facts only: Matt-wzy/jiaowu-adapters@152c95b90b66b2a994f99718102bea8e91840704,
// jiaowu_adapter_qlu/lib/src/qlu_api.dart and jiaowu_adapter_sdk/lib/src/models.dart (MIT).
// No upstream implementation copied. Visible login only; current full semester confirmed from school page.
(function(){
  'use strict';
  const host=window.kagendaSchool,k=Object.create(host),language=String(window.kagendaUiLanguage||'zh').slice(0,2);
  const t=(zh,en,fr)=>language==='fr'?fr:language==='en'?en:zh;
  function translated(message){
    if(language!=='en'&&language!=='fr')return message;
    const messages={
      '请在本校教务网页登录后重试':['Sign in on this university academic website and retry.','Connectez-vous au site de scolarité de cet établissement, puis réessayez.'],
      '请返回教务首页后导入当前学期':['Return to the academic home page to import the current semester.','Revenez à la page d’accueil de la scolarité pour importer le semestre actuel.'],
      '请使用学生个人教务账号':['Use your personal student account.','Utilisez votre compte étudiant personnel.'],
      '请先完成教务登录':['Complete academic sign-in first.','Terminez d’abord la connexion à la scolarité.'],
      '没有取得唯一明确的学校当前学期':['The university did not return one unambiguous current semester.','L’établissement n’a pas fourni un semestre actuel unique et explicite.'],
      '学校当前学期在确认期间发生变化':['The university current semester changed during confirmation.','Le semestre actuel a changé pendant la confirmation.'],
      '已取消导入':['Import cancelled.','Importation annulée.'],
      '存在尚未核验的附加结果，未导入部分课程':['Unverified additional results: no partial timetable was imported.','Résultats supplémentaires non vérifiés : aucun emploi du temps partiel n’a été importé.'],
      '确认期间身份或课表发生变化，未导入部分结果':['Account or timetable changed: no partial results were imported.','Le compte ou l’emploi du temps a changé : aucun résultat partiel n’a été importé.']
    };
    return messages[message]?.[language==='fr'?1:0]||t('', 'Import stopped. Check the semester, complete course data and calendar inputs. '+(/[\u3400-\u9fff]/.test(message)?'':message),'Importation interrompue. Vérifiez le semestre, les données complètes des cours et le calendrier. '+(/[\u3400-\u9fff]/.test(message)?'':message));
  }
  k.run=task=>host.run(async()=>{try{return await task();}catch(error){throw new Error(translated(String(error.message||error)));}});
  const ORIGIN='https://jw.qlu.edu.cn', DATA='/jwglxt/kbcx/xskbcx_cxXsKb.html', TERM='/jwglxt/xtgl/index_cxAreaSix.html';
  const profile={rowFacts:['xf','khfsmc']};
  function need(ok,message){k.requireValue(ok,message);}
  function guard(){
    need(k.adapterId==='ext_qlu_personal'&&location.origin===ORIGIN&&!location.username&&!location.password,'请在本校教务网页登录后重试');
    need(location.pathname.startsWith('/jwglxt/')&&(location.pathname.endsWith('/index_initMenu.html')||location.pathname===DATA),'请返回教务首页后导入当前学期');
    const role=new URL(location.href).searchParams.get('jsdm'),domRole=document.querySelector('#jsdm')?.value;
    need((role==null||role==='xs')&&(domRole==null||domRole==='xs'),'请使用学生个人教务账号');
    need(!document.querySelector('input[type="password"]'),'请先完成教务登录');
  }
  const stable=value=>Array.isArray(value)?value.map(stable):value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort().map(key=>[key,stable(value[key])])):value;
  function empty(value){return value==null||value===''||(typeof value==='object'&&Object.keys(value).length===0);}
  async function request(path,fields,query){
    guard();const url=new URL(path,ORIGIN);for(const [key,value]of Object.entries(query))url.searchParams.set(key,value);
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const response=await fetch(url.href,{method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,referrer:location.href,referrerPolicy:'same-origin',headers:{Accept:'text/html,application/json','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},body:new URLSearchParams(fields).toString()});
      need(response.ok&&!response.redirected,'教务请求失败或会话已失效');
      const actual=new URL(response.url);need(actual.origin===ORIGIN&&actual.pathname===path&&actual.search===url.search,'教务响应页面发生变化');
      const charset=(response.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([^;"'\s]+)/i);need(!charset||/^(?:utf-8|utf8)$/i.test(charset[1]),'教务编码发生变化');
      const raw=await response.text();need(raw&&raw.length<=1500000&&!raw.includes('\ufffd'),'教务数据为空、过大或编码无效');return raw;
    }finally{clearTimeout(timer);}
  }
  function date(value){need(/^\d{4}-\d{2}-\d{2}$/.test(value),'学校学期日期无效');const d=new Date(value+'T00:00:00Z');need(Number.isFinite(d.getTime())&&d.toISOString().slice(0,10)===value,'学校学期日期无效');return value;}
  function termHtml(raw){
    need(!/(?:请先登录|登录已失效|会话已过期)/.test(raw),'教务登录已失效');
    const doc=new DOMParser().parseFromString(raw,'text/html');need(!doc.querySelector('input[type="password"]'),'教务登录已失效');
    for(const el of doc.querySelectorAll('script,style'))el.remove();
    const text=doc.body?.textContent||'',pattern=/(\d{4})-(\d{4})学年([12])学期\((\d{4}-\d{2}-\d{2})至(\d{4}-\d{2}-\d{2})\)/g;
    const matches=[...text.matchAll(pattern)],unique=[...new Map(matches.map(m=>[m[0],m])).values()];need(unique.length===1,'没有取得唯一明确的学校当前学期');const m=unique[0];
    need(Number(m[2])===Number(m[1])+1,'学校学年名称冲突');const start=date(m[4]),end=date(m[5]);need(start<end,'学校学期日期顺序无效');
    const xnm=m[1],ordinal=m[3],xqm=ordinal==='1'?'3':'12',yearLabel=m[1]+'-'+m[2]+'学年',termLabel='第'+ordinal+'学期';
    return {xnm,xqm,ordinal,yearLabel,termLabel,code:xnm+':'+xqm,label:yearLabel+' '+termLabel,module:'N2151',start,end};
  }
  async function current(){return termHtml(await request(TERM,{}, {localeKey:'zh_CN',gnmkdm:'index'}));}
  async function fresh(term){const raw=await request(DATA,{xnm:term.xnm,xqm:term.xqm},{gnmkdm:'N2151'});need(!/^\s*</.test(raw),'教务会话失效或课表页面发生变化');return JSON.parse(raw);}
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
    const knownRoot=new Set(['kbList','xsxx','success','code','error','errorCode','message','msg','hasMore','hasNext','nextPage','pages','totalPages','totalPage','currentPage','pageNumber','total','totalSize','totalCount','xnm','xqm']);
    for(const [key,value]of Object.entries(data))if(!knownRoot.has(key))need(empty(value),'存在尚未核验的附加结果，未导入部分课程');
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
      row=Object.assign({},row);
      need(row.jc!=null&&String(row.jc).trim(),'课程缺少学校返回的明确节次');
      if(row.jcs!=null&&String(row.jcs).trim())need(JSON.stringify(sections(row.jcs))===JSON.stringify(sections(row.jc)),'课程节次字段冲突');
      row.jcs=row.jc;
      if(row.jsxm!=null&&String(row.jsxm).trim()){
        need(typeof row.jsxm==='string','课程教师格式无效');
        if(row.xm!=null&&String(row.xm).trim())need(row.xm.trim()===row.jsxm.trim(),'课程教师字段冲突');
        else row.xm=row.jsxm;
      }
      for(const value of Object.values(row))if(value&&typeof value==='object')need(empty(value),'课程含未核验的结构化安排');
      for(const key of ['xnm','xqm'])if(row[key]!=null)need(String(row[key])===term[key],'课程混入其他学期');
      need(empty(row.dsz),'出现未核验的独立单双周标志');
      const day=k.integer(row.xqj,1,7,'星期');
      if(row.xqjmc!=null)need(row.xqjmc===['星期一','星期二','星期三','星期四','星期五','星期六','星期日'][day-1],'课程星期字段冲突');
      const slots=sections(row.jcs);
      if(row.jcs!=null&&row.jcs!==''&&row.jc!=null&&String(row.jc).trim())need(JSON.stringify(sections(row.jc))===JSON.stringify(slots),'课程节次字段冲突');
      const weeks=k.weeks(row.zcd),name=text(row.kcmc,'名称'),teacher=text(row.xm,'教师'),room=text(row.cdmc,'地点');
      let campus='';if(row.xqmc!=null){need(typeof row.xqmc==='string'&&row.xqmc.length<=200,'校区字段无效');campus=row.xqmc.trim();}
      const info={semester:term.label,sourceSemesterRange:term.start+"至"+term.end,sourceRow:index+1};
      for(const key of ['kch_id','kch','jxb_id','jxbmc','jxbzc','xqh_id','xkbz'])if(row[key]!=null){need((typeof row[key]==='string'||typeof row[key]==='number')&&String(row[key]).length<=1000,'课程标识或附加信息无效');info[key]=String(row[key]);}
      for(const key of profile.rowFacts||[])if(row[key]!=null){need((typeof row[key]==='string'||typeof row[key]==='number')&&String(row[key]).length<=1000,'课程附加信息无效');info[key]=String(row[key]);}
      const extraInfo=JSON.stringify(info);need(extraInfo.length<=4000,'课程附加信息过大');
      const groups=[];for(const n of slots){const previous=groups[groups.length-1];if(previous&&previous[1]+1===n)previous[1]=n;else groups.push([n,n]);}
      for(const [first,last]of groups)courses.push(Object.assign(k.course(name,teacher,campus&&!room.includes(campus)?campus+' '+room:room,day,first,last,weeks),{extraInfo}));
    });
    need(courses.length<=5000,'课程安排过多，无法完整核对');
    const signature=JSON.stringify({term,identity:stable(data.xsxx??null),rows:data.kbList.map(row=>JSON.stringify(stable(row))).sort()});
    return {courses,signature,lastWeek:Math.max(...courses.flatMap(c=>c.weeks))};
  }
  const validator=fn=>value=>{try{fn(value);return false;}catch(error){return translated(String(error.message||error));}};
  window.kagendaQluParsers={termHtml,monday,sections,parsed};
  k.run(async()=>{
    guard();const page=location.href,term=await current();await k.choose(t('核对学校当前学期','Confirm the university current semester','Confirmer le semestre actuel'),[term],'code','label');guard();need(location.href===page,'确认期间教务页面发生变化');
    const before=parsed(await fresh(term),term);
    window.kagendaQluMondayValidator=validator(monday);const anchor=monday(await k.api.showPrompt(t('开学第一周的周一','Monday of teaching week one','Lundi de la première semaine de cours'),term.label+t('。请按校历填写教学第一周周一（YYYY-MM-DD），学校学期起止日期不等同于第一周周一。','. Enter the first teaching-week Monday from the university calendar (YYYY-MM-DD). The published semester date range does not define this Monday.','. Saisissez le lundi de la première semaine de cours selon le calendrier officiel (YYYY-MM-DD). Les dates du semestre ne définissent pas ce lundi.'),'','kagendaQluMondayValidator'));
    window.kagendaQluWeeksValidator=validator(value=>k.integer(value,before.lastWeek,40,'学期教学周数'));
    const answer=await k.api.showPrompt(t('按校历填写学期教学周数','Teaching weeks from the university calendar','Semaines de cours selon le calendrier officiel'),t('请按校历明确填写总教学周数，并在原生预览核对作息。','Enter the explicit number of teaching weeks and check lesson times in the native preview.','Saisissez le nombre exact de semaines de cours et vérifiez les horaires dans l’aperçu de l’application.'),'','kagendaQluWeeksValidator');need(answer!=null,'已取消导入');const count=k.integer(answer,before.lastWeek,40,'学期教学周数');
    guard();need(location.href===page,'确认期间教务页面发生变化');const now=await current();need(JSON.stringify(now)===JSON.stringify(term),'学校当前学期在确认期间发生变化');
    const after=parsed(await fresh(term),term);need(before.signature===after.signature,'确认期间身份或课表发生变化，未导入部分结果');guard();need(location.href===page,'读取期间教务页面发生变化');await k.finish(after.courses,0,count,anchor);
  });
})();
