package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.security.NetworkSecurityPolicy
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.SchoolCatalog
import com.kstudio.agenda.data.WarehousePayload
import com.kstudio.agenda.data.WarehouseScriptRunner
import com.kstudio.agenda.i18n.AcademicText
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Own synthetic DOM/data only. No upstream execution or school traffic. */
class SelectedZfImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle();var web:WebView?=null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk")){"Emulator only"}
            fun <T> main(block:()->T):T {var value:T?=null;runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST") return value as T}
            val view=main{WebView(targetContext).apply{settings.javaScriptEnabled=true}};web=view
            fun read(path:String)=targetContext.assets.open(path).bufferedReader().use{it.readText()}
            fun evaluate(js:String):Any {val latch=CountDownLatch(1);var answer="null";main{view.evaluateJavascript(js){answer=it;latch.countDown()}};check(latch.await(5,TimeUnit.SECONDS));return JSONTokener(answer).nextValue()}
            val setup="""
                window.__messages=[];window.__done=false;window.__reads=0;window.__requests=[];
                if(window.__fault==='missing-year')document.querySelector('#xnm')?.remove();
                if(window.__fault==='module-conflict')document.querySelector('#gnmkdm').value='N253508';
                window._kagendaWarehouse={dispatch:function(raw){
                  var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
                  if(m.id){var value=true;if(m.action==='selection')value=window.__fault==='cancel-selection'?null:0;
                    if(m.action==='prompt')value=m.payload.title==='当前课表学期名称'?(window.__fault==='cancel-label'?null:'2026-2027学年第一学期'):
                      m.payload.title==='开学第一周的周一'?(window.__fault==='bad-monday'?'2026-09-08':'2026-09-07'):
                      window.__fault==='short-weeks'?'11':window.__fault==='cancel-weeks'?null:'18';
                    if(m.action==='prompt'&&m.payload.title==='按校历填写学期教学周数'){
                      if(window.__fault==='selection-change')document.querySelector('#xqm').value='12';
                      if(window.__fault==='module-change')document.querySelector('#gnmkdm').value='N253508';
                    }
                    window.__kagendaSgReply(m.id,true,value);}
                }};
                window.fetch=async function(raw,init){
                  var u=new URL(raw),f=window.__fault,fields=new URLSearchParams(init.body),isQuery=window.__school==='smu'||window.__school==='fit';window.__requests.push(u.href);window.__reads++;
                  if(u.origin!==location.origin||u.pathname!=='/jwglxt/kbcx/xskbcx_cxXsgrkb.html'||init.method!=='POST'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store'||init.referrer!==location.href||init.referrerPolicy!=='same-origin')throw Error('Wrong selected-school request');
                  var year=(document.querySelector('#xnm')||document.querySelector('#xnm_hide')).value,term=(document.querySelector('#xqm')||document.querySelector('#xqm_hide')).value,module=document.querySelector('#gnmkdm').value;
                  if(fields.get('xnm')!==year||fields.get('xqm')!==term||fields.get('kzlx')!=='ck'||fields.get('xsdm')!==''||fields.has('su')||Array.from(fields.keys()).length!==5)throw Error('Wrong actualselected fields');
                  if(isQuery){if(u.searchParams.get('gnmkdm')!==module||fields.has('gnmkdm')||fields.get('kclbdm')!=='')throw Error('Wrong SMU/FIT contract');}
                  else if(u.search||fields.get('gnmkdm')!==module||fields.has('kclbdm'))throw Error('Wrong FZFU contract');
                  if(f==='second-failure'&&window.__reads>1)throw Error('Synthetic second failure');
                  var row={kcmc:'合成选定学期高数',xm:'合成甲、合成乙',cdmc:'A101',xqmc:'合成校区',xqj:2,jcs:'1-2,5',zcd:'1-8周(单),12周',jxb_id:'SYN-T1',jxbmc:'合成教学班'};
                  var d={kbList:[row],xsxx:{XH:'SYN-SELF',XNMC:'2026-2027',XQMMC:window.__third?'3':'1'}};
                  if(window.__variants)d.kbList.push(Object.assign({},row,{xm:'另一教师',cdmc:'B202',xqmc:'另一校区',jxb_id:'SYN-T2'}));
                  if(window.__duplicates)d.kbList.push(Object.assign({},row));
                  if(window.__emptyBranches){d.sjkList=[];d.rqazcList=[];d.queryModel={};d.total=d.kbList.length;}
                  if(f==='teacher')row.xm='';if(f==='room')row.cdmc='未安排';if(f==='weekday')row.xqj=8;if(f==='weekday-conflict')row.xqjmc='星期一';
                  if(f==='section')row.jcs='3-1';if(f==='jc-only'){delete row.jcs;row.jc='1-2';}if(f==='section-conflict')row.jc='1-2';if(f==='week')row.zcd='1-8周未知';
                  if(f==='nested')row.arrangements=[{period:3}];if(f==='practice')d.sjkList=[{}];if(f==='adjustment')d.rqazcList=[{}];if(f==='unknown-root')d.extra={rows:[{}]};
                  if(f==='mixed')d.kbList.push(Object.assign({},row,{zcd:''}));if(f==='total')d.total=2;if(f==='pages')d.pages=2;if(f==='has-more')d.hasMore=true;if(f==='empty')d.kbList=[];
                  if(f==='success-zero')d.success=0;if(f==='code')d.code=403;if(f==='error')d.error='failed';if(f==='expiry')d.msg='会话过期，请重新登录';
                  if(f==='self-year')d.xsxx.XNMC='2025-2026';if(f==='self-term')d.xsxx.XQMMC='第二学期';if(f==='root-term')d.xqm='OTHER';if(f==='row-term')row.xnm='2025';
                  if(f==='identity-change'&&window.__reads>1)d.xsxx.XH='SYN-OTHER';if(f==='class-change'&&window.__reads>1)row.jxb_id='CHANGED';if(f==='raw-change'&&window.__reads>1)row.opaqueScalar='CHANGED';
                  if(window.__reorder&&window.__reads>1)d.kbList=d.kbList.reverse().map(function(r){return Object.fromEntries(Object.entries(r).reverse());});
                  var text=f==='html'?'<html>登录</html>':f==='json'?'not json':JSON.stringify(d);
                  return {ok:f!=='http',redirected:f==='redirect',url:f==='wrong-path'?u.origin+'/jwglxt/xtgl/login_slogin.html':u.href,headers:{get:function(){return f==='charset'?'application/json; charset=GBK':'application/json; charset=UTF-8';}},text:async()=>f==='encoding'?text+'\ufffd':text};
                };
            """.trimIndent()
            var successes=0;var rejections=0
            fun fixture(school:String,flags:String="",reject:Boolean=false,hidden:Boolean=false,third:Boolean=false,opaque:Boolean=false,baseUrl:String?=null,expected:Int=2,password:Boolean=false):WarehousePayload.Batch? {
                val origin=if(school=="fit")"http://oaa.fitedu.net" else if(school=="smu")"http://jwxxt.fjsmu.edu.cn" else "http://portal.fzfu.edu.cn"
                val url=baseUrl?:"$origin/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"
                val y=if(opaque)"Y-OPAQUE" else "2026";val q=if(opaque)"Q-OPAQUE" else if(third)"16" else "3";val qLabel=if(third)"第三学期" else "第一学期"
                val html=if(hidden)"<input id=xnm_hide type=hidden value='$y'><input id=xqm_hide type=hidden value='$q'>" else "<select id=xnm><option value=2025>2025-2026学年</option><option value='$y' selected>2026-2027学年</option></select><select id=xqm><option value=12>第二学期</option><option value='$q' selected>$qLabel</option></select>"
                val loaded=CountDownLatch(1)
                main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))};view.loadDataWithBaseURL(url,"<html><body>$html<input id=gnmkdm type=hidden value=N2151>${if(password)"<input type=password>" else ""}</body></html>","text/html","UTF-8",null)}
                check(loaded.await(10,TimeUnit.SECONDS));evaluate("window.__school='$school';window.__third=$third;"+flags+setup)
                val id=if(school=="fit")"ext_fit_personal" else if(school=="smu")"ext_smu_personal" else "ext_fzfu_personal"
                val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-selected-zf"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote(id))
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/selected-zf-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000
                while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"Selected-ZF timeout"};SystemClock.sleep(30)}
                val messages=JSONArray(evaluate("JSON.stringify(window.__messages)").toString());fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
                if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Partial data staged: $messages"};check((0 until messages.length()).any{messages.getJSONObject(it).getString("action")=="alert"});rejections++;return null}
                check(data("courses").isNotBlank()){"Selected-ZF failed: $messages"};val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==expected&&batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(evaluate("window.__requests.length")==2);successes++;return batch
            }
            for(school in listOf("fzfu","smu","fit")){
                val regular=fixture(school)!!;check(regular.courses.map{it.startPeriod}==listOf(1,5));check(regular.courses.all{it.title=="合成选定学期高数"&&it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.weeksSet()==setOf(1,3,5,7,12)&&it.extraInfo.contains("SYN-T1")&&!it.extraInfo.contains("SYN-SELF")})
                check(evaluate("window.__kagendaSgValidate('kagendaSelectedZfMondayValidator','2026-09-07')")==false)
                fixture(school,hidden=true,reject=school=="fit");fixture(school,third=true);fixture(school,opaque=true);fixture(school,"window.__emptyBranches=true;")
                val variants=fixture(school,"window.__variants=true;window.__reorder=true;",expected=4)!!;check(variants.courses.count{it.teacher=="另一教师"&&it.room=="另一校区 B202"}==2)
                val duplicates=fixture(school,"window.__duplicates=true;")!!;check(duplicates.courses.all{it.extraInfo.contains("\"sourceRow\":1")&&it.extraInfo.contains("\"sourceRow\":2")})
                for(fault in listOf("missing-year","module-conflict","cancel-selection","bad-monday","short-weeks","cancel-weeks","selection-change","module-change","teacher","room","weekday","weekday-conflict","section","jc-only","section-conflict","week","nested","practice","adjustment","unknown-root","mixed","total","pages","has-more","empty","success-zero","code","error","expiry","self-year","self-term","root-term","row-term","identity-change","class-change","raw-change","second-failure","html","json","http","redirect","wrong-path","charset","encoding")){
                    fixture(school,"window.__fault='$fault';",reject=true)
                    if(fault in listOf("selection-change","module-change"))check(evaluate("window.__requests.length")==1)
                }
                fixture(school,"window.__fault='cancel-label';",reject=true,hidden=true)
                fixture(school,reject=true,password=true)
                val base=if(school=="fit")"http://oaa.fitedu.net" else if(school=="smu")"http://jwxxt.fjsmu.edu.cn" else "http://portal.fzfu.edu.cn"
                fixture(school,reject=true,baseUrl="$base/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?jsdm=js")
                fixture(school,reject=true,baseUrl="$base/cas/login")
                fixture(school,reject=true,baseUrl="http://wrong.invalid/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            }
            fixture("fzfu",baseUrl="https://portal.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("fzfu",baseUrl="http://jw.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("fzfu",baseUrl="https://jw.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("smu",baseUrl="https://jwxxt.fjsmu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("fit",reject=true,baseUrl="https://oaa.fitedu.net/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("fit",reject=true,baseUrl="http://portal.fzfu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            fixture("fzfu",reject=true,baseUrl="http://oaa.fitedu.net/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html")
            val schools=SchoolCatalog.load(targetContext,emptyList())
            for((name,id)in listOf("福州外语外贸学院" to "ext_fzfu_personal","三明学院" to "ext_smu_personal")){
                val school=schools.single{it.name==name};check(school.supported&&"zf_new" in SchoolCatalog.knownSystemTypes(school));check(!AcademicText.isUndetermined(school));check(SchoolCatalog.adapters(school).first().let{it.id==id&&it.licenseAsset=="selected-zf-personal-original-MIT.txt"})
            }
            val fit=schools.single{it.name=="福州理工学院"};check(fit.supported&&"zhengfang" in SchoolCatalog.knownSystemTypes(fit));check(!AcademicText.isUndetermined(fit));check(SchoolCatalog.adapters(fit).first().id=="ext_fit_personal");check(SchoolCatalog.adapters(fit).any{it.id=="FIT"})
            val policy=NetworkSecurityPolicy.getInstance();for(host in listOf("portal.fzfu.edu.cn","jw.fzfu.edu.cn","jwxxt.fjsmu.edu.cn","oaa.fitedu.net"))check(policy.isCleartextTrafficPermitted(host));check(!policy.isCleartextTrafficPermitted("unreviewed.invalid"))
            result.putString("stream","PASS: $successes selected-school successes and $rejections whole-batch rejections; real Chromium selected/hidden DOM, exact per-school requests, production bridge/native payload, catalogNewZF and exact-host network policy; no school requests/user data.");finish(-1,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
    }
}
