// KAgenda original implementation, MIT. Zero upstream implementation lines copied.
// Protocol facts only: SHST-SDUST/SHST-ULTRA@008e670031820a3ad4f56e8e6b2eace3cbb7882a,
// timetable/model.ts, timetable/parser.ts and utils/constant.ts (NOASSERTION).
// Bracket-period/week facts corroborated by Opingur/ScheduleClawer_SDUST@1ed117b7d7da525b7cc1ae73e3a4a269d23a5ee2.
(function(){
  'use strict';
  const host=window.kagendaSchool,k=Object.create(host),language=String(window.kagendaUiLanguage||'zh').slice(0,2);
  const t=(zh,en,fr)=>language==='fr'?fr:language==='en'?en:zh;
  const translated=message=>language==='zh'?message:t('', 'Import stopped. Check the selected semester, all weeks, complete course details and calendar.','Importation interrompue. Vérifiez le semestre sélectionné, toutes les semaines, les cours complets et le calendrier.');
  k.run=task=>host.run(async()=>{try{return await task();}catch(error){throw new Error(translated(String(error.message||error)));}});
  const ORIGIN='https://jwglxt.sdust.edu.cn',PATH='/jsxsd/xskb/xskb_list.do';
  const clean=value=>String(value??'').replace(/\u00a0/g,' ').replace(/\s+/g,' ').trim();
  function need(ok,message){k.requireValue(ok,message);}
  function text(value,label){const result=clean(value);need(result&&result.length<=200&&!/^(?:未知|待定|未安排|未排|暂无|无|null|undefined|[-—–]+)$/i.test(result),'课程缺少明确的'+label);return result;}
  function guard(){
    need(k.adapterId==='ext_sdust_qz_personal'&&location.origin===ORIGIN&&location.pathname===PATH&&!location.username&&!location.password,'请在本校强智学生个人课表页导入');
    const role=new URL(location.href).searchParams.get('jsdm');need(role==null||role==='xs','请使用学生个人课表入口');
    need(!document.querySelector('input[type="password"]')&&!document.querySelector('iframe,frame'),'请在完成登录的独立个人课表页面导入');
    need(clean(document.body?.textContent).includes('个人课表信息'),'没有取得本人的学生个人课表页面');
  }
  function option(doc,field,server){
    const fields=[...doc.querySelectorAll('select#'+field+',select[name="'+field+'"]')];need(fields.length===1,'课表学期或周次控件不唯一');
    const selected=[...fields[0].options].filter(o=>o.selected);need(selected.length===1&&!selected[0].disabled,'课表学期或周次没有明确选择');
    if(server)need(selected[0].hasAttribute('selected'),'服务器未明确返回所选学期或全部周次');
    return {value:selected[0].value,label:clean(selected[0].textContent)};
  }
  function selected(doc,server=false){
    const semester=option(doc,'xnxq01id',server),week=option(doc,'zc',server),m=semester.value.match(/^(\d{4})-(\d{4})-([123])$/);
    need(m&&Number(m[2])===Number(m[1])+1&&semester.label&&semester.label.length<=200,'学校学期名称或编码未核验');
    need(semester.label.includes(m[1])&&semester.label.includes(m[2]),'学期显示名称与编码冲突');
    const named=semester.label.match(/(?:第)?([123一二三])学期\s*$|[-]([123])\s*$/);if(named){const value=named[1]||named[2];need(({'一':'1','二':'2','三':'3'}[value]||value)===m[3],'学期名称与所选学期冲突');}
    need(week.value===''&&week.label==='全部','请先在个人课表网页选择全部周次');
    return {code:semester.value,label:semester.label,week:week.value,weekLabel:week.label};
  }
  function monday(value){need(typeof value==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(value),'请按校历填写第一周周一');const d=new Date(value+'T00:00:00Z');need(Number.isFinite(d.getTime())&&d.toISOString().slice(0,10)===value&&d.getUTCDay()===1,'请按校历填写有效的周一日期');return value;}
  function sections(value){
    const raw=clean(value).replace(/[第节\s]/g,'').replace(/[，、]/g,',');need(raw&&raw.length<150,'课程没有明确节次');const result=[];
    for(const part of raw.split(',')){const m=part.match(/^(\d{1,2})(?:[-~～至](\d{1,2}))?$/);need(m,'课程节次格式未核验');const first=k.integer(m[1],1,24,'节次'),last=m[2]?k.integer(m[2],first,24,'节次'):first;for(let n=first;n<=last;n++)result.push(n);}
    return [...new Set(result)].sort((a,b)=>a-b);
  }
  function card(li,day,term,index){
    need(!li.querySelector('script,style,iframe,frame,input,table'),'课程含未知嵌套结构');
    const title=li.querySelector('div');need(title,'课程缺少名称节点');const name=text(title.textContent,'名称'),copy=li.cloneNode(true);copy.querySelector('div').remove();
    for(const br of copy.querySelectorAll('br'))br.replaceWith('\n');const raw=clean(copy.textContent),pattern=/(老师|教师|地点|教室|时间|教学班|班级|课程号|课程编号|课序号|学分|考核方式|备注)\s*[:：]/g,matches=[...raw.matchAll(pattern)];
    need(matches.length>0&&clean(raw.slice(0,matches[0].index))==='','课程含未核验的附加安排');const fields={};
    matches.forEach((m,i)=>{const value=clean(raw.slice(m.index+m[0].length,i+1<matches.length?matches[i+1].index:raw.length));need(value&&!/[:：]/.test(value)&&!Object.hasOwn(fields,m[1]),'课程字段不完整、重复或含未知标记');fields[m[1]]=value;});
    const field=(names,label)=>{const values=names.filter(n=>fields[n]!=null).map(n=>fields[n]);need(values.length>0&&values.every(v=>v===values[0]),'课程缺少或冲突的'+label);return text(values[0],label);};
    const teacher=field(['老师','教师'],'教师'),room=field(['地点','教室'],'地点'),time=field(['时间'],'时间');
    const timing=time.match(/^(.+?)\s*\[\s*([\d\s,，、\-~～至第节]+)\s*\]\s*$/);need(timing,'课程没有可核验的周次与节次区间');
    const weekText=timing[1].replace(/[（]/g,'(').replace(/[）]/g,')').replace(/\(周\)/g,'周');const weeks=k.weeks(weekText),slots=sections(timing[2]);
    const rawCourse=clean(li.textContent);need(rawCourse.length<=3500,'课程完整信息过长');const extraInfo=JSON.stringify({semester:term.label,sourceRow:index+1,sourceCourse:rawCourse});need(extraInfo.length<=4000,'课程附加信息过长');
    const groups=[];for(const slot of slots){const previous=groups.at(-1);if(previous&&previous[1]+1===slot)previous[1]=slot;else groups.push([slot,slot]);}
    return groups.map(([first,last])=>Object.assign(k.course(name,teacher,room,day,first,last,weeks),{extraInfo}));
  }
  function parsed(raw,term){
    need(typeof raw==='string'&&raw.length>0&&raw.length<=1500000&&!raw.includes('\ufffd'),'课表页面为空、过大或编码无效');
    const doc=new DOMParser().parseFromString(raw,'text/html');need(!doc.querySelector('input[type="password"],iframe,frame'),'登录失效或存在未读取的课表框架');
    need(clean(doc.body?.textContent).includes('个人课表信息'),'没有取得学生个人课表');need(JSON.stringify(selected(doc,true))===JSON.stringify(term),'服务器返回的学期或周次与所选值不符');
    for(const m of clean(doc.body.textContent).matchAll(/共\s*(\d+)\s*页/g))need(Number(m[1])===1,'课表存在尚未读取的分页');
    const lists=[...doc.querySelectorAll('ul.qz-toolitiplists')];need(lists.length>0&&lists.length<=5000,'没有取得完整的课程记录');const tables=[...new Set(lists.map(el=>el.closest('table')))];need(tables.length===1&&tables[0],'课表存在多个或未知课程区域');const table=tables[0];
    for(const other of doc.querySelectorAll('table'))if(other!==table&&!other.contains(table))need(clean(other.textContent)==='','存在未排课或其他未核验的非空附加表');
    for(const node of doc.querySelectorAll('#bz_td,.remarks,.remark,#remarks'))need(clean(node.textContent)==='','存在未核验的非空备注安排');
    const rows=[...table.rows],headers=rows.map(row=>[...row.cells].map(cell=>{const m=clean(cell.textContent).match(/^(?:星期|周)([一二三四五六日天])$/);return m?{'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7,'天':7}[m[1]]:null;})).filter(values=>values.filter(Boolean).length===7);
    need(headers.length===1&&new Set(headers[0].filter(Boolean)).size===7,'没有取得完整明确的星期表头');const days=headers[0],courses=[],signatures=[];let count=0;
    for(const row of rows){
      const rowLists=[...row.querySelectorAll('ul.qz-toolitiplists')].filter(el=>el.closest('table')===table);
      if(!rowLists.length){const label=clean(row.textContent);if(/^(?:备注|未排课|实践课程|调整课程)/.test(label)){need(/^(?:备注|未排课|实践课程|调整课程)[:：]?$/.test(label),'存在未核验的非空附加课程行');continue;}for(const cell of row.cells)if(days[cell.cellIndex]&&clean(cell.textContent))need(/^(?:星期|周)[一二三四五六日天]$/.test(clean(cell.textContent)),'存在未核验的非空课程行');continue;}
      for(const cell of row.cells){
        need(cell.colSpan===1&&cell.rowSpan===1,'课程单元格合并导致星期不明确');const ownLists=[...cell.querySelectorAll('ul.qz-toolitiplists')].filter(el=>el.closest('table')===table);
        if(!ownLists.length){if(days[cell.cellIndex])need(clean(cell.textContent)==='','存在未识别的非空课程单元格');continue;}
        const day=days[cell.cellIndex];need(day,'课程没有明确的星期');const entries=ownLists.flatMap(el=>[...el.children]);need(entries.length>0&&entries.every(el=>el.tagName==='LI'),'课程列表含未知节点');
        for(const li of entries){courses.push(...card(li,day,term,count++));signatures.push(JSON.stringify({day,raw:clean(li.textContent)}));}
        const copy=cell.cloneNode(true);for(const el of copy.querySelectorAll('ul.qz-toolitiplists'))el.remove();const rawEntries=entries.map(el=>clean(el.textContent));
        const walker=doc.createTreeWalker(copy,4);while(walker.nextNode()){const value=clean(walker.currentNode.textContent);if(value)need(rawEntries.some(text=>text.includes(value)),'单元格存在未核验的附加课程信息');}
      }
    }
    need(count===lists.reduce((sum,list)=>sum+list.children.length,0)&&courses.length>0&&courses.length<=5000,'未能完整读取所有课程');
    for(const node of doc.querySelectorAll('.kbcontent,.kbcontent1'))if(!node.querySelector('ul.qz-toolitiplists')&&clean(node.textContent))need(courses.some(c=>clean(node.textContent)===c.name),'存在另一种未核验的课程记录');
    const identity=clean(doc.body.textContent).match(/学号\s*[:：]\s*([A-Za-z0-9_-]{2,80})(?![A-Za-z0-9_-])/);const signature=JSON.stringify({term,identity:identity?.[1]||null,rows:signatures.sort()});
    return {courses,signature,lastWeek:Math.max(...courses.flatMap(c=>c.weeks))};
  }
  async function fresh(term){
    guard();const url=new URL(PATH,ORIGIN),controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    const fields={xnxq01id:term.code,sfFD:'1',viweType:'0',showallprint:'0',showkchprint:'0',showkink:'0',showfzmprint:'0',baseUrl:'/jsxsd',xsflMapListJsonStr:encodeURIComponent('讲课学时,实践学时,实验学时,上机学时,课内实践学时'),zc:''};
    try{const response=await fetch(url.href,{method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,referrer:location.href,referrerPolicy:'same-origin',headers:{Accept:'text/html','Content-Type':'application/x-www-form-urlencoded; charset=UTF-8','X-Requested-With':'XMLHttpRequest'},body:new URLSearchParams(fields).toString()});need(response.ok&&!response.redirected,'教务请求失败或登录已失效');const actual=new URL(response.url);need(actual.origin===ORIGIN&&actual.pathname===PATH&&!actual.search,'教务响应页面发生变化');const charset=(response.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([^;"'\s]+)/i);need(!charset||/^(?:utf-8|utf8)$/i.test(charset[1]),'教务编码发生变化');return parsed(await response.text(),term);}finally{clearTimeout(timer);}
  }
  const validator=fn=>value=>{try{fn(value);return false;}catch(error){return translated(String(error.message||error));}};
  window.kagendaSdustQzParsers={selected,sections,monday,parsed};
  k.run(async()=>{
    guard();const page=location.href,term=selected(document);await k.choose(t('核对当前课表学期','Confirm selected semester','Confirmer le semestre sélectionné'),[term],'code','label');guard();need(location.href===page&&JSON.stringify(selected(document))===JSON.stringify(term),'确认期间网页或学期发生变化');const before=await fresh(term);
    window.kagendaSdustMondayValidator=validator(monday);const anchor=monday(await k.api.showPrompt(t('开学第一周的周一','Monday of teaching week one','Lundi de la première semaine de cours'),term.label+t('。请按校历填写第一周周一（YYYY-MM-DD）。','. Enter the first teaching-week Monday from the university calendar (YYYY-MM-DD).','. Saisissez le lundi de la première semaine de cours selon le calendrier officiel (YYYY-MM-DD).'),'','kagendaSdustMondayValidator'));
    window.kagendaSdustWeeksValidator=validator(value=>k.integer(value,before.lastWeek,40,'学期教学周数'));const answer=await k.api.showPrompt(t('按校历填写学期教学周数','Teaching weeks from the university calendar','Semaines de cours selon le calendrier officiel'),t('请按校历填写总教学周数，并在原生预览核对作息。','Enter the explicit teaching-week count and check lesson times in the native preview.','Saisissez le nombre exact de semaines de cours et vérifiez les horaires dans l’aperçu de l’application.'),'','kagendaSdustWeeksValidator');need(answer!=null,'已取消导入');const total=k.integer(answer,before.lastWeek,40,'学期教学周数');guard();need(location.href===page&&JSON.stringify(selected(document))===JSON.stringify(term),'确认期间网页或学期发生变化');
    const after=await fresh(term);need(before.signature===after.signature,'确认期间身份或课表变化，未导入部分结果');guard();need(location.href===page&&JSON.stringify(selected(document))===JSON.stringify(term),'读取期间网页或学期变化');await k.finish(after.courses,0,total,anchor);
  });
})();
