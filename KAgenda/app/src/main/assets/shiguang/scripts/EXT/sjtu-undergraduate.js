// Independent KAgenda personal-import host; SJTU undergraduate protocol reference:
// dujiajun/CourseBlock@20feb37e37b2cd5779a7d3e24ef49983df7bbd5e (MIT),
// downloader/UndergraduateDownloader.kt. Pinned paths/dates/license: school-provenance.json.
// No upstream clocks, 22-week limits, inferred semester or default Monday are copied.
(function () {
  'use strict';
  const k=window.kagendaSchool, endpoint='/kbcx/xskbcx_cxXsKb.html';
  function selected(){
    const controls=['xnm','xqm'].map(id=>{
      const nodes=document.querySelectorAll('#'+id);
      k.requireValue(nodes.length===1 && nodes[0].tagName==='SELECT' && !nodes[0].disabled && !nodes[0].multiple && nodes[0].selectedOptions.length===1,'请在个人课表选择唯一明确的学年学期');
      const node=nodes[0], option=node.selectedOptions[0];
      k.requireValue(!option.disabled && option.textContent.trim() && String(option.value)===String(node.value),'学期选项不完整');
      return {node,value:String(node.value).trim(),label:option.textContent.trim()};
    });
    k.requireValue(/^\d{4}$/.test(controls[0].value) && /^(3|12|16)$/.test(controls[1].value),'学年学期代码无效');
    k.requireValue(controls[0].label.includes(controls[0].value) && /(?:春|夏|秋|冬|第[一二三四1234])(?:季|学期)/.test(controls[1].label),'请明确选择页面提供的学年和季节学期');
    const filters=[];
    for(const node of document.querySelectorAll('input,select,textarea')){
      if(controls.some(x=>x.node===node))continue;
      const type=(node.type||'').toLowerCase(),name=node.name||node.id||'',value=String(node.value||'').trim();
      if(['button','submit','reset'].includes(type))continue;
      if(/^(?:csrf|csrfToken|_csrf|__RequestVerificationToken)$/i.test(name))continue;
      if(name==='gnmkdm' && value==='N2151')continue;
      k.requireValue(!['checkbox','radio','password','file'].includes(type),'发现未知课程开关或登录页面');
      const label=node.tagName==='SELECT' ? String(node.selectedOptions[0]?.textContent||'').trim() : '';
      k.requireValue((!value && !label) || (node.tagName==='SELECT' && /^(全部|所有|不限|全部周次|全学期)$/.test(label)),'当前有课程、周次、校区或未知筛选，不能当作完整学期');
      filters.push([name,value,label]);
    }
    return {xnm:controls[0].value,xqm:controls[1].value,labels:controls.map(x=>x.label),filters};
  }
  function sections(raw){
    k.requireValue(typeof raw==='string' && raw.length<=100,'课程缺少明确节次');
    const seen=new Set();
    for(const part of raw.replace(/\s/g,'').replace(/^第|节$/g,'').split(/[,，、]/)){
      const match=part.match(/^(\d+)(?:-(\d+))?$/);k.requireValue(match,'节次格式无法识别');
      const a=k.integer(match[1],1,24,'节次'),b=k.integer(match[2]||match[1],a,24,'节次');
      for(let n=a;n<=b;n++){k.requireValue(!seen.has(n),'课程节次重复');seen.add(n);}
    }
    return [...seen].sort((a,b)=>a-b);
  }
  function parse(data){
    k.requireValue(data && typeof data==='object' && !Array.isArray(data) && Array.isArray(data.kbList) && data.kbList.length>0 && data.kbList.length<=5000,'未取得完整个人课表');
    k.requireValue((data.success==null || data.success===true) && (data.code==null || [0,200,'0','200'].includes(data.code)),'课表接口返回失败');
    for(const key of ['hasNext','hasMore'])if(data[key]!=null)k.requireValue(data[key]===false,'课表分页未完整');
    for(const [name,value] of Object.entries(data))if(name!=='kbList' && Array.isArray(value))k.requireValue(value.length===0,'存在未支持的实践或其他课程列表，未导入部分课表');
    const totals=['total','totalSize','totalCount','recordsTotal'];
    for(const key of totals)if(data[key]!=null)k.requireValue(k.integer(data[key],1,5000,'总数')===data.kbList.length,'课程总数不符');
    for(const key of ['totalPages','pageCount','pages'])if(data[key]!=null)k.requireValue(Number(data[key])===1,'课表仍有分页');
    for(const key of ['page','pageNum','pageNumber','currentPage'])if(data[key]!=null)k.integer(data[key],0,1,'当前页');
    if(data.pageSize!=null)k.requireValue(k.integer(data.pageSize,1,5000,'每页数')>=data.kbList.length && totals.some(key=>Number(data[key])===data.kbList.length),'分页未声明完整总数');
    const facts=data.kbList.map(row=>{
      k.requireValue(row && typeof row==='object' && !Array.isArray(row),'课程行无效');
      const field=name=>{k.requireValue(typeof row[name]==='string' && row[name].trim() && row[name].length<=200,'课程字段缺失');return row[name].trim();};
      for(const key of ['xqumc','xqmc'])k.requireValue(row[key]==null || typeof row[key]==='string','校区格式无效');
      const campus=String(row.xqumc || row.xqmc || '').trim(),room=field('cdmc');
      const place=campus && !room.includes(campus) ? campus+' '+room : room;
      const periods=row.jcs;
      let extraInfo='课程标识：'+field('kch_id')+'\n教学班：'+field('jxbmc');
      if(row.xkbz!=null && row.xkbz!=='') {k.requireValue(typeof row.xkbz==='string' && row.xkbz.length<=2000,'选课备注格式无效');extraInfo+='\n选课备注：'+row.xkbz.trim();}
      return {classId:JSON.stringify([field('kch_id'),field('jxbmc')]),extraInfo,name:field('kcmc'),teacher:field('xm'),room:place,day:k.integer(row.xqj,1,7,'星期'),sections:sections(periods),weeks:k.weeks(field('zcd'))};
    });
    for(let i=0;i<facts.length;i++)for(let j=0;j<i;j++){
      const a=facts[i],b=facts[j];
      if(a.classId!==b.classId)continue;
      k.requireValue(a.name===b.name,'同教学班课程名称矛盾');
      if(a.day===b.day && a.sections.some(x=>b.sections.includes(x)) && a.weeks.some(x=>b.weeks.includes(x)))k.requireValue(a.room===b.room && a.teacher===b.teacher,'同教学班相交课次的教师或地点矛盾');
    }
    return facts.sort((a,b)=>JSON.stringify(a).localeCompare(JSON.stringify(b)));
  }
  const identity=value=>JSON.stringify(value && typeof value==='object' ? Object.fromEntries(Object.entries(value).sort(([a],[b])=>a.localeCompare(b))) : value??null);
  async function request(term){
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
    try{
      const response=await fetch(new URL(endpoint,location.href).href,{method:'POST',body:new URLSearchParams({xnm:term.xnm,xqm:term.xqm}),headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','X-Requested-With':'XMLHttpRequest','Accept':'application/json'},credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal});
      k.requireValue(response.ok && response.status===200 && !response.redirected,'学校会话失效或课表请求失败，请重新登录');
      if(response.url)k.requireValue(new URL(response.url).origin===location.origin && new URL(response.url).pathname==='/kbcx/xskbcx_cxXsKb.html','课表响应地址变化');
      const raw=await response.text();k.requireValue(raw.length>0 && raw.length<=1500000 && raw.trim().startsWith('{'),'课表接口未返回JSON，可能已退出登录');
      const data=JSON.parse(raw);
      return {facts:parse(data),identity:identity(data.xsxx)};
    }finally{clearTimeout(timer);}
  }
  k.run(async()=>{
    const entry=new URL(location.href);
    k.requireValue(k.adapterId==='ext_sjtu_ug' && entry.protocol==='https:' && entry.hostname==='i.sjtu.edu.cn' && !entry.username && !entry.password && !entry.port && /^\/kbcx\/xskbcx_cxXskbcxIndex\.html$/.test(entry.pathname),'请登录上海交通大学本科教务并打开个人课表');
    const term=selected(),stamp=JSON.stringify(term),guard=()=>k.requireValue(location.href===entry.href && JSON.stringify(selected())===stamp,'查询期间身份地址、学期或筛选变化');
    const first=await request(term);guard();
    const answer=await k.api.showPrompt('学期总周数','请按校历填写总教学周数；日期和逐节作息请在预览中核对。','',1);
    k.requireValue(answer!==null && answer!==undefined,'已取消导入');
    const count=k.integer(answer,Math.max(...first.facts.flatMap(x=>x.weeks)),40,'学期总周数');guard();
    const final=await request(term);guard();k.requireValue(JSON.stringify(first)===JSON.stringify(final),'两次完整学期课表不一致，请重新导入');
    const courses=final.facts.flatMap(m=>{const groups=[];for(const n of m.sections){const last=groups.at(-1);if(last && n===last[1]+1)last[1]=n;else groups.push([n,n]);}return groups.map(([a,b])=>Object.assign(k.course(m.name,m.teacher,m.room,m.day,a,b,m.weeks),{extraInfo:m.extraInfo+'\n学期：'+term.labels.join(' ')}));});
    await k.finish(courses,0,count);
  });
})();
