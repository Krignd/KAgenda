'use strict';
// Main-authored synthetic DOM/protocol data; no real timetable samples or school requests.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=__dirname+'/..',common=fs.readFileSync(root+'/app/src/main/assets/academic/school-common.js','utf8');
const source=fs.readFileSync(root+'/app/src/main/assets/shiguang/scripts/EXT/selected-zf-personal.js','utf8');
let checks=0;const eq=(a,b)=>{assert.deepEqual(JSON.parse(JSON.stringify(a)),b);checks++;};
async function run(o={}){
 const smu=o.school==='smu',fit=o.school==='fit',qust=o.school==='qust',sdut=o.school==='sdut',strict=qust||sdut,id=o.id||(sdut?'ext_sdut_personal':qust?'ext_qust_personal':fit?'ext_fit_personal':smu?'ext_smu_personal':'ext_fzfu_personal');
 const location=new URL(o.url||(sdut?'https://jw.sdut.edu.cn':qust?'https://jwglxt1.qust.edu.cn':fit?'http://oaa.fitedu.net':smu?'http://jwxxt.fjsmu.edu.cn':'http://portal.fzfu.edu.cn')+'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151');
 const messages=[],requests=[];let resolve,reads=0,changed=false;const done=new Promise(r=>resolve=r);
 const fields={xnm:o.yearValue===undefined?'2026':o.yearValue,xqm:o.termValue===undefined?'3':o.termValue,gnmkdm:'N2151'};
 function element(key){
  if(o.missing===key)return null;
  const label=key==='xnm'?'2026-2027学年':o.termLabel||'第一学期',options=[{textContent:label,selected:true,disabled:!!o.disabled}];
  if(o.multiple)options.push({textContent:'另一项',selected:true});
  return {tagName:o.hidden?'INPUT':'SELECT',get value(){return changed&&o.changeField===key?'CHANGED':fields[key];},options};
 }
 const doc={querySelector(selector){
  if(selector==='input[type="password"]')return o.login?{}:null;
  if(selector==='#gnmkdm')return o.noModule?null:{get value(){return changed&&o.changeModule?'N253508':o.module||'N2151';}};
  if(selector.endsWith('_hide'))return o.hidden?element(selector.slice(1,-5)):null;
  if(selector==='#xnm'||selector==='#xqm')return o.hidden?null:element(selector.slice(1));
  return null;
 }};
 const api={showAlert:async(title,text)=>{messages.push({action:'alert',text});return true;},showSingleSelection:async(title,raw,initial)=>{messages.push({action:'selection',options:JSON.parse(raw),initial});return o.selection===undefined?0:o.selection;},
 showPrompt:async(title,tip,value,validator)=>{messages.push({action:'prompt',title,value,validator});if(title==='按校历填写学期教学周数'){changed=true;if(o.navigate)location.href+='&changed=1';}
  return title==='当前课表学期名称'?(o.label===undefined?'2026-2027学年第一学期':o.label):title==='开学第一周的周一'?(o.monday===undefined?'2026-09-07':o.monday):(o.weeks===undefined?'18':o.weeks);},
 savePresetTimeSlots:async value=>messages.push({action:'times',data:JSON.parse(value)}),saveCourseConfig:async value=>messages.push({action:'config',data:JSON.parse(value)}),saveImportedCourses:async value=>messages.push({action:'courses',data:JSON.parse(value)}),notifyTaskCompletion:()=>resolve()};
 const context=vm.createContext({window:{AndroidBridgePromise:api},location,document:doc,URL,URLSearchParams,AbortController,setTimeout,clearTimeout,console,fetch:async(raw,init)=>{
  const u=new URL(raw);requests.push(u.href);assert.equal(u.origin,location.origin);assert.equal(u.pathname,'/jwglxt/kbcx/xskbcx_cxXsgrkb.html');assert.equal(init.method,'POST');assert.equal(init.credentials,'same-origin');assert.equal(init.redirect,'error');assert.equal(init.cache,'no-store');assert.equal(init.referrerPolicy,'same-origin');assert.equal(init.referrer,location.href);
  const f=new URLSearchParams(init.body);assert.equal(f.get('xnm'),fields.xnm);assert.equal(f.get('xqm'),fields.xqm);assert.equal(f.get('kzlx'),'ck');assert.equal(f.get('xsdm'),'');assert.equal(f.has('su'),false);assert.equal(f.size,strict?6:5);if(strict)assert.equal(f.get('kclxdm'),'');
  const module=o.module||new URL(location.href).searchParams.get('gnmkdm')||'N2151';
  if(smu||fit||strict){assert.equal(u.searchParams.get('gnmkdm'),module);assert.equal(f.has('gnmkdm'),false);assert.equal(f.get('kclbdm'),'');}else{assert.equal(u.search,'');assert.equal(f.get('gnmkdm'),module);assert.equal(f.has('kclbdm'),false);}
  reads++;if(o.secondFail&&reads>1)throw Error('Synthetic second read failure');
  const row={kcmc:'合成高数',xm:'合成甲、合成乙',cdmc:'A101',xqmc:'合成校区',xqj:'2',jcs:'1-2,5',zcd:'1-8周(单),12周',jxb_id:'SYN-T1',jxbmc:'合成教学班',...o.row};
  const rows=[row];if(o.secondRow)rows.push({...row,...o.secondRow});if(o.change&&reads>1)row[o.change]='CHANGED';
  const data={kbList:rows,xsxx:{XH:o.identityChange&&reads>1?'SYN-OTHER':'SYN-SELF'},...o.data};if(o.reorder&&reads>1)data.kbList=rows.reverse().map(r=>Object.fromEntries(Object.entries(r).reverse()));
  const text=o.json===undefined?JSON.stringify(data):o.json;
  return {ok:!o.http,redirected:!!o.redirect,url:o.responseUrl||u.href,headers:{get:()=>o.encoding||'application/json; charset=UTF-8'},text:async()=>o.replacement?text+'\ufffd':text};
 }});
 vm.runInContext(common.replace('__ADAPTER_ID__',JSON.stringify(id)),context);vm.runInContext(source,context);
 let timer;try{await Promise.race([done,new Promise((_,reject)=>timer=setTimeout(()=>reject(Error('adapter timeout')),2000))]);}finally{clearTimeout(timer);}
 return {context,messages,requests,courses:messages.find(x=>x.action==='courses')?.data,config:messages.find(x=>x.action==='config')?.data};
}
(async()=>{
 for(const school of ['fzfu','smu','fit','qust','sdut']){
  let r=await run({school});eq(r.courses.map(c=>[c.name,c.teacher,c.position,c.startSection,c.endSection,c.weeks]),[['合成高数','合成甲、合成乙','合成校区 A101',1,2,[1,3,5,7,12]],['合成高数','合成甲、合成乙','合成校区 A101',5,5,[1,3,5,7,12]]]);eq(r.config,{semesterTotalWeeks:18,semesterStartDate:'2026-09-07'});eq(r.requests.length,2);eq(r.messages.find(m=>m.action==='selection').initial,-1);eq(r.messages.filter(m=>m.action==='prompt').every(m=>m.value===''),true);eq(r.messages.some(m=>m.action==='times'),false);eq(JSON.stringify(r.courses).includes('SYN-SELF'),false);
  r=await run({school,secondRow:{xm:'另一教师',cdmc:'B202',jxb_id:'SYN-T2'},reorder:true});eq(r.courses.length,4);
  r=await run({school,termValue:'16',termLabel:'第三学期',data:{xsxx:{XQMMC:'3',XNMC:'2026-2027'}}});eq(r.courses.length,2);
  r=await run({school,termValue:'Q-OPAQUE',yearValue:'Y-OPAQUE',termLabel:'第二学期',data:{xsxx:{XQMMC:'2',XNMC:'2026-2027'}}});eq(['qust','sdut'].includes(school)?r.courses===undefined:r.courses.length===2,true);
  r=await run({school,hidden:true,data:{xsxx:{XQMMC:'第一学期'}}});if(['fit','qust','sdut'].includes(school)){eq(r.courses===undefined,true);eq(r.requests.length,0);}else{eq(r.courses.length,2);eq(r.messages.some(m=>m.title==='当前课表学期名称'),true);}
  r=await run({school,noModule:true,data:{sjkList:[],rqazcList:[],queryModel:{},success:true,total:1}});eq(r.courses.length,2);
  r=await run({school,module:'N253508',url:(school==='sdut'?'https://jw.sdut.edu.cn':school==='qust'?'https://jwglxt1.qust.edu.cn':school==='fit'?'http://oaa.fitedu.net':school==='smu'?'http://jwxxt.fjsmu.edu.cn':'http://portal.fzfu.edu.cn')+'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508'});eq(['fit','sdut'].includes(school)?r.courses===undefined:r.courses.length===2,true);if(['fit','sdut'].includes(school))eq(r.requests.length,0);
  const rejected=[{id:'unknown'},{login:true},{missing:'xnm'},{missing:'xqm'},{yearValue:''},{termValue:''},{multiple:true},{disabled:true},{module:'N253508'},{hidden:true,label:null},{hidden:true,label:''},{selection:null},{selection:-1},{selection:1},{monday:null},{monday:'2026-09-08'},{monday:'2026-02-30'},{weeks:null},{weeks:'11'},{weeks:'41'},{changeField:'xnm'},{changeField:'xqm'},{changeModule:true},{navigate:true},
   {row:{kcmc:''}},{row:{xm:''}},{row:{cdmc:'未安排'}},{row:{xqj:8}},{row:{xqjmc:'星期一'}},{row:{jcs:''}},{row:{jcs:null,jc:'1-2'}},{row:{jcs:'3-1'}},{row:{jc:'1-2'}},{row:{zcd:'1-8周未知'}},{row:{dsz:1}},{row:{extra:[{}]}},{row:{xnm:'2025'}},{secondRow:{zcd:''}},
   ...[{kbList:[]},{sjkList:[{}]},{rqazcList:[{}]},{unknown:{rows:[{}]}},{total:2},{hasMore:true},{pages:2},{success:0},{code:403},{error:'failed'},{msg:'会话过期，请重新登录'},{xqm:'12'},{xsxx:{XQMMC:'2'}},{xsxx:{XNMC:'2025-2026'}}].map(data=>({data})),
   {http:true},{redirect:true},{encoding:'application/json; charset=GBK'},{replacement:true},{responseUrl:'https://wrong.invalid/jwglxt/kbcx/xskbcx_cxXsgrkb.html'},{json:'<html>登录</html>'},{json:'null'},{json:'[]'},{json:'bad json'},{identityChange:true},{change:'jxb_id'},{change:'opaqueScalar'},{secondFail:true}];
  for(const o of rejected){r=await run({school,...o});eq(r.courses===undefined,true);eq(r.messages.some(m=>['courses','config','times'].includes(m.action)),false);eq(r.messages.some(m=>m.action==='alert'),true);}
 }
 for(const url of ['https://portal.fzfu.edu.cn','http://jw.fzfu.edu.cn','https://jw.fzfu.edu.cn'])eq((await run({url:url+'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'})).courses.length,2);
 eq((await run({school:'smu',url:'https://jwxxt.fjsmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'})).courses.length,2);
 for(const [school,url]of [['fzfu','http://portal.fzfu.edu.cn/cas/login'],['fzfu','https://portal.fzfu.edu.cn:8443/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html'],['smu','http://portal.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html'],['fzfu','http://jwxxt.fjsmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html'],['smu','http://wrong.invalid/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html'],['fzfu','http://portal.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?jsdm=js'],['smu','http://jwxxt.fjsmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?jsdm=js']])eq((await run({school,url})).courses===undefined,true);
 for(const url of ['https://oaa.fitedu.net/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html','http://oaa.fitedu.net:8080/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html','http://portal.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html','http://oaa.fitedu.net/cas/login','http://oaa.fitedu.net/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?jsdm=js'])eq((await run({school:'fit',url})).courses===undefined,true);
 for(const school of ['fzfu','smu'])eq((await run({school,url:'http://oaa.fitedu.net/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html'})).courses===undefined,true);
 for(let n=1;n<=6;n++)eq((await run({school:'qust',url:`https://jwglxt${n}.qust.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151`})).courses.length,2);
 for(const url of ['http://jwglxt1.qust.edu.cn','https://jwglxt7.qust.edu.cn','https://jwglxt1.qust.edu.cn:8443','https://jwglxt1.qust.edu.cn.evil.invalid'])eq((await run({school:'qust',url:url+'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'})).courses===undefined,true);
 for(const o of [{url:'https://jwglxt1.qust.edu.cn/jwglxt/kbcx/xskbcx_cxXsKbcxIndex.html?gnmkdm=N2151'},{url:'https://jwglxt1.qust.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html',noModule:true},{termValue:'12'},{yearValue:'2025'},{row:{bz:{unsafe:true}}}])eq((await run({school:'qust',...o})).courses===undefined,true);
 const detail=await run({school:'qust',row:{bjmc:'合成班级',xf:'2',kxh:'01',sknr:'合成内容',bz:'合成备注'}});eq(JSON.parse(detail.courses[0].extraInfo).bz,'合成备注');eq(JSON.parse(detail.courses[0].extraInfo).bjmc,'合成班级');
 const aliases=await run({school:'qust',row:{kcmc:null,KCMC:'合成别名课程',xm:null,jsxm:'合成别名教师',xqmc:null,cdxqmc:'合成别名校区',cdmc:null,jxcdmc:'B202',xqj:null,XQJ:'3',jcs:null,JCOR:'3-4',zcd:null,weeks:'6周,9周',BJMC:'合成别名班级',BZ:'合成别名备注'}});eq(aliases.courses.map(c=>[c.name,c.teacher,c.position,c.day,c.startSection,c.endSection,c.weeks]),[['合成别名课程','合成别名教师','合成别名校区 B202',3,3,4,[6,9]]]);eq(JSON.parse(aliases.courses[0].extraInfo).bjmc,'合成别名班级');
 for(const row of [{KCMC:'冲突名称'},{cdxqmc:'冲突校区'},{JCS:'7-8'},{XM:'冲突教师'},{ZCD:'2周'},{BJMC:{unsafe:true}}]){const r=await run({school:'qust',row});eq(r.courses===undefined,true);eq(r.messages.some(m=>['courses','config','times'].includes(m.action)),false);}
 for(const url of ['http://jw.sdut.edu.cn','https://jw.sdut.edu.cn:8443','https://wrong.sdut.edu.cn','https://jwglxt1.qust.edu.cn'])eq((await run({school:'sdut',url:url+'/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'})).courses===undefined,true);
 console.log(`PASS: ${checks} FZFU/SMU/FIT/QUST/SDUT selected-term assertions; explicit school profiles, synthetic protocol only.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
