'use strict';
// Main-owned synthetic fixtures. No school traffic, copied samples or upstream execution.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=__dirname+'/..',common=fs.readFileSync(root+'/app/src/main/assets/academic/school-common.js','utf8');
const source=fs.readFileSync(root+'/app/src/main/assets/shiguang/scripts/EXT/xyu-personal.js','utf8');
let checks=0;const eq=(a,b)=>{assert.deepEqual(JSON.parse(JSON.stringify(a)),b);checks++;};
const profiles=[{id:'ext_xyu_personal',origin:'https://zfjwxt.xyc.edu.cn',prefix:'/jwglxt/'}];
let active=profiles[0];
async function run(o={}){
 const location=new URL(o.url||(active.origin+active.prefix+'kbcx/personal-synthetic.html'));
 const messages=[],requests=[];let resolve,reads=0;const done=new Promise(r=>resolve=r);
 const row={kcmc:'合成高等数学',xm:'合成甲、合成乙',cdmc:'A101',xqmc:'合成校区',xqj:'2',jcor:'1-2,5',zcd:'1-8周(单),12周',kch_id:'SYN-C1',jxb_id:'SYN-T1',jxbmc:'合成教学班'};
 const api={showAlert:async(title,text)=>{messages.push({action:'alert',text});return true;},
 showSingleSelection:async(title,raw,initial)=>{messages.push({action:'selection',options:JSON.parse(raw),initial});return o.selection===undefined?0:o.selection;},
 showPrompt:async(title,tip,value,validator)=>{messages.push({action:'prompt',title,value,validator});if(o.navigate)location.href+='?changed';return title==='要导入的学年'?(o.year===undefined?'2026-2027':o.year):title==='开学第一周的周一'?(o.monday===undefined?'2026-09-07':o.monday):(o.weeks===undefined?'18':o.weeks);},
 savePresetTimeSlots:async value=>messages.push({action:'times',data:JSON.parse(value)}),saveCourseConfig:async value=>messages.push({action:'config',data:JSON.parse(value)}),saveImportedCourses:async value=>messages.push({action:'courses',data:JSON.parse(value)}),notifyTaskCompletion:()=>resolve()};
 class Parser{parseFromString(text){return {body:{textContent:text.replace(/<[^>]+>/g,'')},querySelector:()=>text.includes('type=password')?{}:null};}}
 const context=vm.createContext({window:{AndroidBridgePromise:api},location,document:{querySelector:()=>o.login?{}:null},URL,URLSearchParams,AbortController,DOMParser:Parser,setTimeout,clearTimeout,console,fetch:async(raw,init)=>{
  const url=new URL(raw);requests.push({url:url.href,method:init.method,body:init.body});assert.equal(url.origin,active.origin);assert.equal(init.credentials,'same-origin');assert.equal(init.redirect,'error');assert.equal(init.cache,'no-store');
  const isData=url.pathname===active.prefix+'kbcx/xskbcxMobile_cxXsKb.html';assert.equal(isData,true);assert.equal(init.method,'POST');assert.equal(url.searchParams.size,0);assert.equal(url.searchParams.has('su'),false);
  let text;
  if(isData){
   reads++;if(o.secondFail&&reads>1)throw Error('Synthetic failure');const fields=new URLSearchParams(init.body);assert.equal(fields.size,2);assert.equal(init.referrer,location.href);assert.equal(init.referrerPolicy,'same-origin');assert.equal(fields.get('xnm'),'2026');assert.equal(fields.get('xqm'),o.selection===2?'16':o.selection===1?'12':'3');assert.match(init.headers['Content-Type'],/^application\/x-www-form-urlencoded/);assert.equal(init.headers['X-Requested-With'],'XMLHttpRequest');
   const rows=[{...row,...o.row}];if(o.secondRow)rows.push({...row,...o.secondRow});
   if(o.change&&reads>1)rows[0][o.change]='CHANGED';
   const data={kbList:rows,xsxx:{XH:o.identityChange&&reads>1?'SYN-OTHER':'SYN-SELF'},...o.data};
   if(o.reorder&&reads>1)data.kbList=rows.reverse().map(r=>Object.fromEntries(Object.entries(r).reverse()));
   text=o.json===undefined?JSON.stringify(data):o.json;
  }
  return {ok:!o.http,redirected:!!o.redirect,url:o.responseUrl===undefined?url.href:o.responseUrl,headers:{get:()=>o.encoding||'application/json; charset=UTF-8'},text:async()=>o.replacement?text+'\ufffd':text};
 }});
 vm.runInContext(common.replace('__ADAPTER_ID__',JSON.stringify(o.id||active.id)),context);vm.runInContext(source,context);
 let timer;try{await Promise.race([done,new Promise((_,reject)=>timer=setTimeout(()=>reject(Error('adapter timeout')),2000))]);}finally{clearTimeout(timer);}
 return {context,messages,requests,courses:messages.find(x=>x.action==='courses')?.data,config:messages.find(x=>x.action==='config')?.data};
}
(async()=>{
 for(active of profiles){
 let r=await run();if(!r.courses)console.error(r.messages,r.requests);eq(r.courses.map(c=>[c.name,c.teacher,c.position,c.day,c.startSection,c.endSection,c.weeks]),[
 ['合成高等数学','合成甲、合成乙','合成校区 A101',2,1,2,[1,3,5,7,12]],['合成高等数学','合成甲、合成乙','合成校区 A101',2,5,5,[1,3,5,7,12]]]);
 eq(r.config,{semesterTotalWeeks:18,semesterStartDate:'2026-09-07'});eq(r.requests.length,2);eq(r.messages.filter(m=>m.action==='prompt').every(m=>m.value===''),true);eq(r.messages.find(m=>m.action==='selection').initial,-1);eq(r.messages.some(m=>m.action==='times'),false);
 eq(JSON.stringify(r.courses).includes('SYN-SELF'),false);eq(JSON.parse(r.courses[0].extraInfo).jxbmc,'合成教学班');
 r=await run({selection:1,data:{xsxx:{XH:'SYN-SELF',XNMC:'2026-2027',XQMMC:'第二学期'}},row:{xnm:'2026',xqm:'12'}});eq(r.courses.length,2);
 r=await run({selection:2,data:{xsxx:{XH:'SYN-SELF',XNMC:'2026-2027',XQMMC:'第三学期'}},row:{xnm:'2026',xqm:'16'}});eq(r.courses.length,2);eq(JSON.parse(r.courses[0].extraInfo).semester,'2026-2027学年第三学期');eq(r.messages.find(m=>m.action==='selection').options.length,3);
 r=await run({secondRow:{xm:'另一教师',cdmc:'B202',xqmc:'另一校区',jxb_id:'SYN-T2'}});eq(r.courses.length,4);eq(r.courses[2].teacher,'另一教师');eq(r.courses[2].position,'另一校区 B202');
 r=await run({secondRow:{jxb_id:'SYN-T2'},reorder:true});eq(r.courses.length,4);
 r=await run({secondRow:{}});eq(r.courses.length,4);eq(JSON.parse(r.courses[2].extraInfo).sourceRow,2);
 r=await run({row:{jcor:null,jc:'3-4节',zcd:'2-8周(双),10周',xqj:7}});eq(r.courses.map(c=>[c.day,c.startSection,c.endSection,c.weeks]),[[7,3,4,[2,4,6,8,10]]]);
 r=await run({data:{sjkList:[],rqazcList:[],queryModel:{},success:true,code:0,total:1},row:{nested:[]}});eq(r.courses.length,2);
 r=await run({row:{xqj:null,xqjmc:'星期日',jcor:null,jc:'1-2,5',xm:null,jsxm:'合成甲、合成乙',cdmc:null,jxdd:'A101',zcd:null,zcdmc:'1-8周(单),12周',do_jxb_id:'SYN-ALT'}});eq(r.courses[0].day,7);eq(r.courses[0].teacher,'合成甲、合成乙');eq(JSON.parse(r.courses[0].extraInfo).do_jxb_id,'SYN-ALT');
 r=await run({row:{xqjmc:'周二',jc:'1,2,5',jsxm:'合成甲、合成乙',jsxx:'合成甲、合成乙',jxdd:'A101',zcdmc:'1,3,5,7,12周'}});eq(r.courses.length,2);
 r=await run({row:{lh:'合成教学楼',kcxz:'合成必修',kclb:'合成专业课',khfsmc:'合成考试',jxbzc:'合成一班;合成二班'}});eq(JSON.parse(r.courses[0].extraInfo).lh,'合成教学楼');eq(JSON.parse(r.courses[0].extraInfo).jxbzc,'合成一班;合成二班');
 r=await run({row:{day:'7',xf:'3.5'}});eq(r.courses[0].day,2);eq(JSON.parse(r.courses[0].extraInfo).xf,'3.5');
 r=await run({row:{jcs:'1-2,5'}});eq(r.courses.length,2);
 const rejected=[{selection:2,data:{xsxx:{XH:'SYN-SELF',XQMMC:'第二学期'}}},{selection:2,data:{xqm:'3'}},{selection:2,row:{xqm:'12'}},{url:'http://jwgl.gnnu.cn/jwglxt/xtgl/index_initMenu.html'},{url:active.origin+'/jwglxt/xtgl/index_initMenu.html'},{url:active.origin+'/jwglxt/kbcxevil/x'},{url:active.origin+'/kbcxevil/x'},{url:active.origin+'/xtglevil/x'},{data:{xsxx:{}}},{data:{xsxx:{XH:null}}},{url:active.origin+(active.prefix==='/jwglxt/'?'/xtgl/':'/jwglxt/xtgl/')+'index_initMenu.html'},{row:{jsxm:'另一教师'}},{row:{jsxx:'未设置教师'}},{row:{jxdd:'B202'}},{row:{zcdmc:'2周'}},{row:{xqjmc:'星期三'}},{row:{xqj:null,xqjmc:'星期八'}},{data:{unparsed:'1-2周实验'}},{row:{do_jxb_id:{nested:1}}},{year:'1999-2000'},{year:'2101-2102'},{url:'http://jwgl.ndnu.edu.cn/jwglxt/'},{url:'https://jwgl.ndnu.edu.cn:8443/jwglxt/'},{url:'https://wrong.invalid/jwglxt/'},{url:'https://jwgl.ndnu.edu.cn/login.html'},{id:'wrong'},{login:true},{http:true},{redirect:true},{encoding:'text/html; charset=GBK'},{replacement:true},{responseUrl:'https://jwgl.ndnu.edu.cn/jwglxt/xtgl/login_slogin.html'},
 ...[null,'','2026','2026-2028','x','2026-2027bad'].map(year=>({year})),...[null,-1,3,'bad'].map(selection=>({selection})),...[null,'','2026-09-08','2026-02-30'].map(monday=>({monday})),...[null,'','11','41','18.5'].map(weeks=>({weeks})),
 ...['kcmc','xm','cdmc','xqj','zcd','jcor'].map(key=>({row:{[key]:''}})),{row:{xm:'未知'}},{row:{cdmc:'未安排'}},{row:{xqj:8}},{row:{xqjmc:'星期一'}},{row:{jcor:'1,3bad'}},{row:{jcor:'3-1'}},{row:{jc:'1-2'}},{row:{zcd:'1-8周未知'}},{row:{dsz:1}},{row:{xnm:'2025'}},{row:{xqm:'12'}},{row:{branch:[{period:1}]}},{row:{xqmc:{name:'unknown'}}},
 {secondRow:{zcd:''}},...[{kbList:[]},{items:[{}]},{sjkList:[{}]},{rqazcList:[{}]},{other:{rows:[{}]}},{queryModel:{total:2}},{total:2},{hasMore:true},{totalPages:2},{pageNumber:2},{success:0},{code:403},{error:'failed'},{errorCode:1},{msg:'登录已失效，请重新登录'},{message:'没有权限'},{xnm:'2025'},{xqm:'12'},{xsxx:{XH:{id:'bad'}}},{xsxx:{XNMC:'2025-2026'}},{xsxx:{XQMMC:'第二学期'}}].map(data=>({data})),
 ...['<html>登录</html>','null','[]','not json'].map(json=>({json})),...['kcmc','jxb_id','unmappedScalar'].map(change=>({change})),{row:{jcs:'3-4'}},{identityChange:true},{secondFail:true},{navigate:true},{responseUrl:''}];
 for(const o of rejected){r=await run(o);eq(r.courses===undefined,true);eq(r.messages.some(m=>['courses','config','times'].includes(m.action)),false);eq(r.messages.some(m=>m.action==='alert'),true);}
 }
 console.log(`PASS: ${checks} XYU adapter assertions (exact school origin and full-term mobile two-field POST); synthetic protocol only, no school/account requests.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
