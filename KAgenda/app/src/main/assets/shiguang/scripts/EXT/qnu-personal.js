// KAgenda original implementation. MIT protocol/parser reference:
// huanmen/qsschedule@4ac3d71f103dd649dcd3de02f441d12c1480020e
// QnuTimetableParser/Importer, QnuEndpointPolicy, QnuImportScreen.
// Uses the actual personal-page button in the visible signed-in session.
// No upstream default dates, clocks or warning-level skips are imported.
(function () {
  'use strict';
  const k=window.kagendaSchool, ORIGIN='https://jw.qztc.edu.cn';
  const selectionPath='/academic/student/currcourse/currcourse.jsdo';
  const timetablePath='/academic/manager/coursearrange/showTimetable.do';
  const need=(ok,message)=>k.requireValue(ok,message);
  const compact=value=>String(value==null?'':value).replace(/[\s\u00a0　]/g,'');
  const clean=value=>String(value==null?'':value).replace(/\u00a0/g,' ').trim();
  const nameKey=value=>compact(value).replace(/（/g,'(').replace(/）/g,')');
  const day=value=>{
    const text=compact(value).replace(/^(?:星期|周)/,'');
    const n={'一':1,'二':2,'三':3,'四':4,'五':5,'六':6,'日':7,'天':7}[text];
    return n||k.integer(text,1,7,'星期');
  };
  function textLines(cell,empty) {
    const result=[];let text='';
    function br(explicit){if(text.trim()||(explicit&&empty))result.push(clean(text));text='';}
    function walk(node){
      if(node.nodeType===3)text+=node.nodeValue;
      else if(node.nodeType===1&&node.tagName==='BR')br(true);
      else if(node.nodeType===1&&['DIV','P','LI'].includes(node.tagName)){br(false);Array.from(node.childNodes).forEach(walk);br(false);}
      else Array.from(node.childNodes||[]).forEach(walk);
    }
    Array.from(cell.childNodes).forEach(walk);br(false);return result;
  }
  function required(value,label){const text=clean(value);need(text&&text.length<=200&&!/^(?:待定|未安排|暂无|未排|[-—]+)$/.test(text),'课程缺少明确的'+label);return text;}
  function weeks(value){
    const digits={零:0,'〇':0,'○':0,一:1,壹:1,'①':1,二:2,贰:2,貳:2,兩:2,两:2,'②':2,三:3,叁:3,參:3,'③':3,四:4,肆:4,'④':4,五:5,伍:5,'⑤':5,六:6,陆:6,陸:6,'⑥':6,七:7,柒:7,'⑦':7,八:8,捌:8,'⑧':8,九:9,玖:9,'⑨':9};
    const text=String(value==null?'':value).replace(/[零〇○一壹①二贰貳兩两②三叁參③四肆④五伍⑤六陆陸⑥七柒⑦八捌⑧九玖⑨十拾百佰]+/g,piece=>{
      if(piece.length===1&&digits[piece]!=null)return String(digits[piece]);
      const m=piece.match(/^(.)?[十拾](.)?$/);
      need(m&&(!m[1]||digits[m[1]]!=null)&&(!m[2]||digits[m[2]]!=null),'中文周次无法明确识别');
      return String((m[1]?digits[m[1]]:1)*10+(m[2]?digits[m[2]]:0));
    });
    return k.weeks(text);
  }
  function term(doc){
    const title=clean((doc.querySelector('#title,[id*=title i]')||{}).textContent||doc.title);
    const years=Array.from(new Set((title.match(/\d{4}/g)||[])));
    const terms=Array.from(new Set((title.match(/[春夏秋]/g)||[])));
    need(years.length===1&&terms.length===1&&!/班级课表/.test(title),'没有取得个人课表的明确自然年与春夏秋学期');
    return {year:years[0],season:terms[0],title};
  }
  function expanded(table){
    const rows=Array.from(table.rows).filter(r=>r.closest('table')===table),carry=new Map(),out=[];
    for(const row of rows){
      const cells=new Map();
      for(const [col,item]of Array.from(carry)){cells.set(col,item.cell);if(--item.left===0)carry.delete(col);}
      let col=0;
      for(const cell of Array.from(row.cells)){
        while(cells.has(col))col++;
        const width=k.integer(cell.getAttribute('colspan')||1,1,64,'表格列跨度');
        const height=k.integer(cell.getAttribute('rowspan')||1,1,200,'表格行跨度');
        for(let n=0;n<width;n++,col++){need(!cells.has(col),'表格跨度重叠');cells.set(col,cell);if(height>1)carry.set(col,{cell,left:height-1});}
      }
      need(cells.size<=64,'表格列数过多');out.push(Array.from({length:Math.max(...cells.keys(),-1)+1},(_,i)=>cells.get(i)||null));
    }
    need(carry.size===0,'表格行跨度未完整展开');return out;
  }
  function periods(grid){
    const mapping=new Map(),slots=new Map();
    const rows=Array.from(grid.rows).filter(r=>r.closest('table')===grid);
    need(rows.length>1&&rows.length<=26,'课表行数不完整');
    const headers=Array.from(rows[0].cells).map(c=>compact(c.textContent));
    need([1,2,3,4,5,6,7].every(d=>headers.some(t=>{try{return day(t)===d;}catch(_){return false;}})),'课表缺少完整七日表头');
    for(let raw=1;raw<rows.length;raw++){
      const row=rows[raw],th=Array.from(row.cells).filter(c=>c.tagName==='TH');
      need(th.length===1,'课表节次表头缺失或发生变化');
      const lines=textLines(th[0],false),label=compact(lines[0]),m=label.match(/^第?(\d{1,2})节$/);
      const reserve=/^午(?:间|休)/.test(label);
      need(m||reserve,'不能识别真实节次标签：'+label);
      const matches=Array.from(th[0].textContent.matchAll(/\d{1,2}[:：]\d{2}/g)).map(x=>k.clock(x[0].replace('：',':')));
      need(matches.length===2&&matches[0]<matches[1],'节次缺少明确的起止钟点');
      let no=null;
      if(m){no=k.integer(m[1],1,24,'实际节次');need(!slots.has(no),'节次标签重复');slots.set(no,{number:no,startTime:matches[0],endTime:matches[1]});}
      mapping.set(raw,{no,row,reserve});
    }
    const count=slots.size;need(count>0&&count<=24,'没有完整作息表');
    const times=[];
    for(let n=1;n<=count;n++){need(slots.has(n),'节次表不连续');times.push(slots.get(n));}
    for(let n=1;n<times.length;n++)need(times[n-1].endTime<=times[n].startTime,'实际节次钟点重叠或顺序冲突');
    return {mapping,times};
  }
  function gridCourses(grid,scheme){
    const entries=[];
    for(const [raw,{no,row,reserve}]of scheme.mapping){
      const cells=Array.from(row.cells).filter(c=>c.tagName==='TD');need(cells.length===7,'课表某行缺少七日课程格');
      const seen=new Set();
      for(const cell of cells){
        need((cell.colSpan||1)===1&&(cell.rowSpan||1)===1,'课程格合并范围尚未核验');
        const m=cell.id.match(/^([1-7])[-_](\d{1,2})$/);need(m&&Number(m[2])===raw&&!seen.has(m[1]),'课程坐标与实际节次行冲突');seen.add(m[1]);
        const lines=textLines(cell,true);if(!lines.length)continue;
        need(!reserve&&no!=null,'午间预留格出现课程，现有节次表示不能完整核对，未导入部分结果');
        let at=0;
        while(at<lines.length){
          const start=lines[at].match(/^<<(.+)>>;?(\S*)$/);need(start,'课程格存在无法归属的文本');
          let end=at+1;while(end<lines.length&&!/^<<(.+)>>;?(\S*)$/.test(lines[end]))end++;
          const b=lines.slice(at,end);need(b.length>=5,'课程格记录不完整');
          const status=b.slice(4).flatMap(x=>x.split(/[\s　]+/)).filter(Boolean);
          need(status.length>0&&status.every(x=>['停课','调课','补课','代课'].includes(x)||/^(?:\d+(?:\.\d+)?学时|讲课(?:学时)?|实验(?:学时)?|上机(?:学时)?|实践(?:学时)?)$/.test(x)),'出现未核验的课程状态');
          entries.push({name:required(start[1],'名称'),sequence:clean(start[2]),room:required(b[1],'地点'),teacher:required(b[2],'教师'),
            day:Number(m[1]),period:no,weeks:weeks(b[3]),status});at=end;
        }
      }
    }
    need(entries.length>0&&entries.length<=5000,'没有完整的个人课程安排');return entries;
  }
  function details(doc,grid){
    const result=[];
    for(const table of Array.from(doc.querySelectorAll('table'))){
      if(table===grid||table.contains(grid)||grid.contains(table))continue;
      const rows=expanded(table),texts=rows.map(r=>r.map(c=>compact(c?.textContent)));
      const first=rows.findIndex(r=>r.some(c=>c?.tagName==='TD'&&['停课','调课','补课','代课'].includes(compact(c.textContent))));
      const marker=/(?:停\/代课|停代课|原安排|停课时间|补课.*日期)/.test(texts.slice(0,Math.max(first,2)).flat().join(''));
      if(first<0){need(!marker||texts.length<=2||texts.slice(2).every(r=>r.every(t=>!t||/^(?:暂无.*|无.*|没有.*)$/.test(t))),'调停补明细无法完整识别');continue;}
      need(first>0,'调停补缺少明确表头');
      const heads=Array.from({length:Math.max(...rows.map(r=>r.length))},(_,i)=>Array.from(new Set(texts.slice(0,first).map(r=>r[i]).filter(Boolean))).join(''));
      const one=(test,label,optional)=>{const ids=heads.flatMap((v,i)=>test(v)?[i]:[]);need(ids.length===1||(optional&&ids.length===0),'调停补'+label+'列缺失或冲突');return ids.length?ids[0]:-1;};
      const type=one(x=>x==='类型'||x==='变更类型','类型'),name=one(x=>/课程名/.test(x),'课程名称');
      const seq=one(x=>/课序|教学班号/.test(x),'课序号',true),code=one(x=>/课程号|课程代码/.test(x),'课程号',true);
      const teacher=one(x=>/教师|任课/.test(x)&&!/代课/.test(x),'教师',true),sub=one(x=>/代课人|代课教师/.test(x),'代课教师',true);
      function group(makeup){
        const base=one(x=>(makeup?/补课/:/停\/代课|停代课|原安排|停课时间/).test(x)&&/日期/.test(x),(makeup?'补课':'原安排')+'日期',true);
        if(base<0)return null;
        need(base+4<heads.length,'调停补时间地点列不完整');
        need(/周/.test(heads[base+1])&&/星期|周几/.test(heads[base+2])&&/节/.test(heads[base+3])&&/教室|地点|场地/.test(heads[base+4]),'调停补时间地点字段顺序发生变化');return base;
      }
      const original=group(false),makeup=group(true);
      function place(cells,base){
        if(base==null||cells.slice(base,base+5).every(c=>!clean(c?.textContent)))return null;
        const v=cells.slice(base,base+5).map(c=>clean(c?.textContent)),w=weeks(v[1]);need(w.length===1,'变更明细未提供唯一周次');
        const p=compact(v[3]).replace(/^第/,'').replace(/节$/,'').match(/^(\d{1,2})(?:[-~～至](\d{1,2}))?$/);
        need(p,'变更明细节次无效');const begin=k.integer(p[1],1,24,'变更节次'),end=k.integer(p[2]||p[1],begin,24,'变更节次');
        return {date:v[0],week:w[0],day:day(v[2]),begin,end,room:v[4]};
      }
      for(let n=first;n<rows.length;n++){
        const cells=rows[n],values=cells.map(c=>clean(c?.textContent));if(values.every(x=>!x))continue;
        need(cells.length===heads.length&&['停课','调课','补课','代课'].includes(compact(values[type])),'存在未识别或缺列的变更明细');
        const value=required(values[name],'变更课程名'),titles=Array.from(cells[name]?.querySelectorAll('[title]')||[]).map(e=>clean(e.title));
        if(cells[name]?.getAttribute('title'))titles.push(clean(cells[name].getAttribute('title')));
        const full=Array.from(new Set(titles.filter(t=>t.startsWith(value.replace(/[.…]+$/,''))&&t.length>=value.length)));
        need(full.length<=1,'变更课程全名不唯一');
        result.push({type:compact(values[type]),name:full[0]||value,sequence:seq<0?'':values[seq],code:code<0?'':values[code],teacher:teacher<0?'':values[teacher],
          substitute:sub<0?'':values[sub],original:place(cells,original),makeup:place(cells,makeup)});
      }
    }
    return Array.from(new Map(result.map(r=>[JSON.stringify(r),r])).values());
  }
  function apply(entries,changes,scheme,anchor){
    const atoms=[];for(const e of entries)for(const week of e.weeks)atoms.push({...e,week});
    const statusCovered=new Set();
    function checkDate(place){
      if(!place)return;need(place.end<=scheme.times.length,'变更节次不在真实作息内');
      if(place.date){
        const d=new Date(anchor+'T00:00:00Z');d.setUTCDate(d.getUTCDate()+7*(place.week-1)+place.day-1);
        const expected=d.toISOString().slice(0,10),m=compact(place.date).match(/^(?:(\d{4})[-年./])?(\d{1,2})[-月./](\d{1,2})日?$/);
        need(m&&(!m[1]||m[1]===expected.slice(0,4))&&Number(m[2])===Number(expected.slice(5,7))&&Number(m[3])===Number(expected.slice(8,10)),'变更日期与所填校历周次不一致');
      }
    }
    for(const change of changes){
      checkDate(change.original);checkDate(change.makeup);
      const eligible=entries.filter(e=>nameKey(e.name)===nameKey(change.name)&&(!change.sequence||e.sequence===change.sequence));
      const identities=Array.from(new Set(eligible.map(e=>JSON.stringify([e.name,e.sequence]))));
      need(identities.length<=1,'变更课程身份不唯一');
      let identity=identities.length?JSON.parse(identities[0]):null;
      need(identity||change.type==='补课','变更未匹配当前个人课程');
      if(!identity)identity=[change.name,change.sequence];
      const matches=e=>e.name===identity[0]&&e.sequence===identity[1];
      let originalTeacher='';
      if(change.type!=='补课'){
        const p=change.original;need(p,'变更缺少原时间');
        for(let period=p.begin;period<=p.end;period++){
          const found=atoms.filter(e=>matches(e)&&e.week===p.week&&e.day===p.day&&e.period===period&&(!p.room||e.room===p.room)&&(!change.teacher||e.teacher===change.teacher));
          need(found.length===1,'原安排未唯一匹配，不能保存部分变更');originalTeacher=found[0].teacher;
          if(change.type==='代课')found[0].teacher=required(change.substitute,'代课教师');else atoms.splice(atoms.indexOf(found[0]),1);
        }
      }
      if(change.type==='调课'||change.type==='补课'){
        const p=change.makeup;need(p,'变更缺少新时间');
        const teachers=Array.from(new Set(eligible.map(e=>e.teacher)));
        const teacher=required(change.substitute||change.teacher||originalTeacher||(teachers.length===1?teachers[0]:''),'变更教师'),room=required(p.room,'变更地点');
        for(let period=p.begin;period<=p.end;period++){
          const same=atoms.filter(e=>matches(e)&&e.week===p.week&&e.day===p.day&&e.period===period);
          need(same.every(e=>e.teacher===teacher&&e.room===room),'补调课与现有课程教师或地点冲突');
          if(!same.length)atoms.push({name:identity[0],sequence:identity[1],teacher,room,day:p.day,period,week:p.week,status:[]});
        }
      }
      for(const e of eligible)statusCovered.add(e);
    }
    for(const e of entries)need(!e.status.some(x=>['停课','调课','补课','代课'].includes(x))||statusCovered.has(e),'课程有变更标记但缺少对应明细');
    const bySlot=new Map();
    for(const a of atoms){const identity=JSON.stringify([a.name,a.sequence,a.teacher,a.room,a.day,a.period]);if(!bySlot.has(identity))bySlot.set(identity,{...a,weeks:new Set()});bySlot.get(identity).weeks.add(a.week);}
    const groups=new Map();
    for(const a of bySlot.values()){
      const weeks=Array.from(a.weeks).sort((a,b)=>a-b),info=JSON.stringify({sequence:a.sequence});
      const key=JSON.stringify([a.name,a.teacher,a.room,a.day,weeks,info]);if(!groups.has(key))groups.set(key,{...a,weeks,info,periods:[]});groups.get(key).periods.push(a.period);
    }
    const courses=[];
    for(const g of groups.values()){
      const sorted=g.periods.sort((a,b)=>a-b),ranges=[];for(const p of sorted){const r=ranges[ranges.length-1];if(r&&r[1]+1===p)r[1]=p;else ranges.push([p,p]);}
      for(const r of ranges)courses.push({...k.course(g.name,g.teacher,g.room,g.day,r[0],r[1],g.weeks),extraInfo:g.info});
    }
    need(courses.length>0&&courses.length<=5000,'没有完整可导入课程');return courses;
  }
  function parsed(html,selected,anchor){
    need(typeof html==='string'&&html.length>0&&html.length<=2000000&&!html.includes('\ufffd'),'课表编码或大小无效');
    const doc=new DOMParser().parseFromString(html,'text/html');need(!doc.querySelector('input[type=password]'),'登录已失效');
    const t=term(doc);need(t.year===selected.year&&t.season===selected.season,'课表与当前选定的自然年/学期不一致');
    const grids=doc.querySelectorAll('table#timetable');need(grids.length===1,'没有唯一完整的课表网格');
    need(!/(?:第\s*\d+\s*页|下一页|下页|未安排|未排课|待排课)/.test(doc.body.textContent),'页面存在分页或未完整排课');
    const scheme=periods(grids[0]),entries=gridCourses(grids[0],scheme),changes=details(doc,grids[0]);
    const courses=apply(entries,changes,scheme,anchor);
    return {courses,times:scheme.times,signature:JSON.stringify({t,courses:courses.map(c=>JSON.stringify(c)).sort(),times:scheme.times})};
  }
  function selectedDocument(){
    need(k.adapterId==='ext_qnu_personal'&&location.origin===ORIGIN,'请先打开泉师 HTTPS 教务系统');
    const docs=[],found=[];
    function visit(doc){
      if(!doc||docs.includes(doc))return;need(docs.length<32,'页面框架过多，不能核对完整入口');docs.push(doc);
      if(doc.querySelector('select[name=year],select[name=term]')){
        const u=new URL(doc.URL);need(u.origin===ORIGIN&&u.pathname===selectionPath,'学期选择不在本人课程安排页面');found.push(doc);
      }
      for(const f of doc.querySelectorAll('iframe,frame')){let child=null;try{child=f.contentDocument;}catch(_){}if(child)visit(child);}
    }
    visit(document);need(found.length===1,'请打开唯一的本人课程安排/学期选择页面');return found[0];
  }
  function current(){
    const doc=selectedDocument();need(!doc.querySelector('input[type=password]'),'请先完成登录');
    const y=doc.querySelector('select[name=year]'),t=doc.querySelector('select[name=term]');
    const yl=clean(y?.selectedOptions[0]?.textContent),tl=clean(t?.selectedOptions[0]?.textContent);
    need(y&&t&&/^\d{4}年?$/.test(yl)&&/^[春夏秋](?:季)?(?:学期)?$/.test(tl)&&/^\d+$/.test(y.value)&&/^\d+$/.test(t.value),'没有取得明确学期选择');
    return {year:yl.slice(0,4),season:tl[0],yearId:y.value,termId:t.value};
  }
  async function personalUrl(){
    const doc=selectedDocument(),frame=doc.defaultView||window;
    const buttons=Array.from(doc.querySelectorAll('input,button,a')).filter(e=>compact(e.value||e.textContent)==='个人课表');
    need(buttons.length===1,'未找到唯一的本人课表入口');
    const found=[],before=location.href,original=frame.open;
    try{frame.open=url=>{found.push(new URL(String(url),doc.baseURI).href);return {focus(){},closed:false};};buttons[0].click();await new Promise(resolve=>setTimeout(resolve,100));}
    finally{frame.open=original;}
    need(location.href===before&&found.length===1,'未能核对本人课表入口，请重新打开课程安排页');
    const u=new URL(found[0]);need(u.origin===ORIGIN&&u.pathname===timetablePath&&!u.username&&!u.password,'本人课表地址未通过核验');return u.href;
  }
  async function fresh(url){
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const r=await fetch(url,{method:'GET',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal});
      need(r.ok&&!r.redirected,'课表请求失败，请重新登录');if(r.url)need(new URL(r.url).origin===ORIGIN&&new URL(r.url).pathname===timetablePath,'课表响应地址发生变化');
      const bytes=await r.arrayBuffer();need(bytes.byteLength>0&&bytes.byteLength<=2000000,'课表响应大小无效');
      const charset=(r.headers?.get('Content-Type')||'').match(/charset\s*=\s*["']?([\w-]+)/i);
      if(charset){need(/^(utf-8|utf8|gbk|gb2312|gb18030)$/i.test(charset[1]),'课表字符编码不受支持');return new TextDecoder(charset[1],{fatal:true}).decode(bytes);}
      try{return new TextDecoder('utf-8',{fatal:true}).decode(bytes);}catch(_){return new TextDecoder('gb18030',{fatal:true}).decode(bytes);}
    }finally{clearTimeout(timer);}
  }
  window.kagendaQnuParsers={parsed,term,expanded,textLines,periods,gridCourses,details,apply,weeks};
  k.run(async()=>{
    const selected=current(),url=await personalUrl();need(JSON.stringify(current())===JSON.stringify(selected),'准备期间学期发生变化');
    window.kagendaQnuMondayValidator=value=>{try{const d=new Date(String(value)+'T00:00:00Z');need(/^\d{4}-\d{2}-\d{2}$/.test(value)&&d.toISOString().slice(0,10)===value&&d.getUTCDay()===1,'请按校历填写第一周周一');return false;}catch(e){return String(e.message||e);}};
    const anchor=await k.api.showPrompt('开学第一周的周一',selected.year+'年'+selected.season+'学期。请按学校校历填写（YYYY-MM-DD）。','','kagendaQnuMondayValidator');
    need(anchor!=null&&window.kagendaQnuMondayValidator(anchor)===false,'已取消或校历周一无效');
    const before=parsed(await fresh(url),selected,anchor),last=Math.max(...before.courses.flatMap(c=>c.weeks));
    window.kagendaQnuWeeksValidator=value=>{try{k.integer(value,last,40,'学期教学周数');return false;}catch(e){return String(e.message||e);}};
    const answer=await k.api.showPrompt('按校历填写学期教学周数','请填写该学期总教学周数，须覆盖全部课程和调停补课周次。','','kagendaQnuWeeksValidator');
    need(answer!=null,'已取消导入');const count=k.integer(answer,last,40,'学期教学周数');
    need(JSON.stringify(current())===JSON.stringify(selected),'确认期间学期发生变化');
    const again=await personalUrl();need(again===url,'本人课表入口发生变化');
    const after=parsed(await fresh(url),selected,anchor);need(after.signature===before.signature,'确认期间本人课表发生变化，未导入部分结果');
    need(JSON.stringify(current())===JSON.stringify(selected),'确认期间学期发生变化');
    await k.api.savePresetTimeSlots(JSON.stringify(after.times));await k.finish(after.courses,0,count,anchor);
  });
})();
