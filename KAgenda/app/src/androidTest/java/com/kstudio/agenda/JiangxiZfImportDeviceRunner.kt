package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.SchoolCatalog
import com.kstudio.agenda.data.WarehousePayload
import com.kstudio.agenda.data.WarehouseScriptRunner
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Original synthetic data + production WebView/bridge/native payload. Emulator only. */
class JiangxiZfImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle();var web:WebView?=null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk")){"Emulator only"}
            fun <T> main(block:()->T):T {var value:T?=null;runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST") return value as T}
            val view=main{WebView(targetContext).apply{settings.javaScriptEnabled=true}};web=view
            fun read(path:String)=targetContext.assets.open(path).bufferedReader().use{it.readText()}
            fun evaluate(js:String):Any {val latch=CountDownLatch(1);var answer="null";main{view.evaluateJavascript(js){answer=it;latch.countDown()}};check(latch.await(5,TimeUnit.SECONDS));return JSONTokener(answer).nextValue()}
            var totalSuccesses=0;var totalRejections=0
            for(profile in listOf(arrayOf("ext_jju_personal","https://zhjw1.jju.edu.cn","/jwglxt/","九江学院"),arrayOf("ext_nvu_personal","https://jwxt.nvu.edu.cn","/","南昌职业大学"))) {
            val id=profile[0];val origin=profile[1];val prefix=profile[2];val schoolName=profile[3]
            val url=origin+prefix+"xtgl/index_initMenu.html"
            val setup="""
                window.__messages=[];window.__done=false;window.__reads=0;window.__requests=[];
                window._kagendaWarehouse={dispatch:function(raw){
                  var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
                  if(m.id){var value=true;if(m.action==='selection')value=window.__fault==='cancel-selection'?null:window.__spring?1:0;
                    if(m.action==='prompt')value=m.payload.title==='要导入的学年'?(window.__fault==='cancel-year'?null:window.__fault==='bad-year'?'2026-2028':'2026-2027'):
                      m.payload.title==='开学第一周的周一'?(window.__fault==='cancel-monday'?null:window.__fault==='bad-monday'?'2026-09-08':'2026-09-07'):
                      window.__fault==='cancel-weeks'?null:window.__fault==='short-weeks'?'11':'18';
                    window.__kagendaSgReply(m.id,true,value);}
                }};
                window.fetch=async function(raw,init){
                  var u=new URL(raw),f=window.__fault,isData=u.pathname===${JSONObject.quote(prefix+"kbcx/xskbcx_cxXsgrkb.html")};window.__requests.push(u.href);
                  if(u.origin!==${JSONObject.quote(origin)}||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store'||init.method!==(isData?'POST':'GET')||u.searchParams.get('gnmkdm')!=='N2151')throw Error('Wrong NDNU request');
                  var text;
                  if(isData){
                    window.__reads++;var fields=new URLSearchParams(init.body);
                    if(fields.get('xnm')!=='2026'||fields.get('xqm')!==(window.__spring?'12':'3')||Array.from(fields.keys()).length!==2||new URL(init.referrer).pathname!==${JSONObject.quote(prefix+"kbcx/xskbcx_cxXskbcxIndex.html")}||init.referrerPolicy!=='same-origin')throw Error('Wrong explicit term/referrer');
                    if(f==='second-failure'&&window.__reads>1)throw Error('Synthetic second request failure');
                    var row={kcmc:'合成江西高数',xm:'合成甲、合成乙',cdmc:'A101',xqmc:'合成校区',xqj:'2',jcs:'1-2,5',zcd:'1-8周(单),12周',jxb_id:'SYN-T1',jxbmc:'合成教学班'};
                    var d={kbList:[row],xsxx:{XH:'SYN-SELF',XNMC:'2026-2027',XQMMC:window.__spring?'2':'1'}};
                    if(window.__jcOnly){delete row.jcs;row.jc='3-4节';row.zcd='2-8周(双),10周';row.xqj=7;}
                    if(window.__duplicates)d.kbList.push(Object.assign({},row));
                    if(window.__variants)d.kbList.push(Object.assign({},row,{xm:'另一教师',cdmc:'B202',xqmc:'另一校区',jxb_id:'SYN-T2'}));
                    if(window.__emptyBranches){d.sjkList=[];d.rqazcList=[];d.queryModel={};d.total=d.kbList.length;d.success=true;}
                    if(window.__aliases){delete row.xm;row.jsxm='合成甲、合成乙';delete row.cdmc;row.jxdd='A101';delete row.xqj;row.xqjmc='星期二';delete row.zcd;row.zcdmc='1-8周(单),12周';row.do_jxb_id='SYN-ALT';}
                    if(f==='teacher-conflict')row.jsxm='另一教师';if(f==='room-conflict')row.jxdd='B202';if(f==='week-conflict')row.zcdmc='2周';if(f==='scalar-branch')d.unparsed='1-2周实验';
                    if(f==='teacher')row.xm='';if(f==='pending-room')row.cdmc='未安排';if(f==='weekday')row.xqj='8';if(f==='weekday-conflict')row.xqjmc='星期一';
                    if(f==='section')row.jcs='3-1';if(f==='section-conflict')row.jc='1-2';if(f==='week')row.zcd='1-8周未知';if(f==='parity')row.dsz=1;
                    if(f==='mixed')d.kbList.push(Object.assign({},row,{zcd:''}));if(f==='nested')row.extraArrangements=[{period:3}];
                    if(f==='practice')d.sjkList=[{kcmc:'合成实践'}];if(f==='adjustment')d.rqazcList=[{}];if(f==='unknown-root')d.extra={rows:[{}]};
                    if(f==='total')d.total=2;if(f==='pages')d.pages=2;if(f==='has-more')d.hasMore=true;if(f==='empty')d.kbList=[];
                    if(f==='success-zero')d.success=0;if(f==='error')d.error='failed';if(f==='expiry')d.msg='会话已过期，请重新登录';if(f==='code')d.code=403;
                    if(f==='root-term')d.xqm='16';if(f==='row-term')row.xnm='2025';if(f==='self-year')d.xsxx.XNMC='2025-2026';if(f==='self-term')d.xsxx.XQMMC='2';
                    if(f==='identity-change'&&window.__reads>1)d.xsxx.XH='SYN-OTHER';if(f==='class-change'&&window.__reads>1)row.jxb_id='CHANGED';if(f==='raw-change'&&window.__reads>1)row.opaqueScalar='CHANGED';
                    if(window.__reorder&&window.__reads>1)d.kbList=d.kbList.reverse().map(function(r){return Object.fromEntries(Object.entries(r).reverse());});
                    text=f==='html'?'<html>登录</html>':f==='json'?'not json':JSON.stringify(d);
                  }else{
                    if(u.pathname!==${JSONObject.quote(prefix+"kbcx/xskbcx_cxXskbcxIndex.html")}||u.searchParams.get('layout')!=='default')throw Error('Wrong index path');
                    return {ok:f!=='index-http',redirected:f==='index-redirect',url:f==='index-path'?u.origin+'/wrong':u.href,text:async()=>f==='index-login'?'<input type=password>':'<html>synthetic personal schedule</html>'};
                  }
                  return {ok:f!=='http',redirected:f==='redirect',url:f==='wrong-path'?u.origin+'/jwglxt/xtgl/login_slogin.html':u.href,headers:{get:function(){return f==='charset'?'text/html; charset=GBK':'text/html; charset=UTF-8';}},text:async()=>f==='encoding'?text+'\ufffd':text};
                };
            """.trimIndent()
            var successes=0;var rejections=0;val network=java.util.concurrent.atomic.AtomicInteger()
            fun fixture(flags:String="",reject:Boolean=false,html:String="",baseUrl:String=url,expected:Int=2):WarehousePayload.Batch? {
                val loaded=CountDownLatch(1)
                main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?):WebResourceResponse?{if(r?.url?.scheme=="data")return null;network.incrementAndGet();return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))}};view.loadDataWithBaseURL(baseUrl,"<html><head><link rel=icon href='data:,'></head><body><span id=boot>Synthetic</span>$html</body></html>","text/html","UTF-8",null)}
                check(loaded.await(10,TimeUnit.SECONDS));check(evaluate("!!document.querySelector('#boot')")==true);evaluate(flags+setup)
                val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-ndnu"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote(id))
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/jiangxi-zf-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000
                while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"NDNU fixture timeout"};SystemClock.sleep(30)}
                val messages=JSONArray(evaluate("JSON.stringify(window.__messages)").toString())
                fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
                check(network.get()==0){"Unexpected resource request"}
                if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Partial data staged: $messages"};check((0 until messages.length()).any{messages.getJSONObject(it).getString("action")=="alert"});rejections++;return null}
                check(data("courses").isNotBlank()){"NDNU import failed: $messages"};val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==expected&&batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(evaluate("window.__requests.length")==4);successes++;return batch
            }
            val regular=fixture()!!
            check(regular.courses.map{it.startPeriod}==listOf(1,5))
            check(regular.courses.all{it.title=="合成江西高数"&&it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.weeksSet()==setOf(1,3,5,7,12)&&it.extraInfo.contains("SYN-T1")&&!it.extraInfo.contains("SYN-SELF")})
            check(evaluate("window.__kagendaSgValidate('kagendaJiangxiZfYearValidator','2026-2027')")==false)
            check(evaluate("typeof window.__kagendaSgValidate('kagendaJiangxiZfYearValidator','2026-2028')")=="string")
            check(evaluate("window.__kagendaSgValidate('kagendaJiangxiZfMondayValidator','2026-09-07')")==false)
            check(evaluate("typeof window.__kagendaSgValidate('kagendaJiangxiZfWeeksValidator','11')")=="string")
            fixture("window.__aliases=true;")
            fixture("window.__spring=true;")
            fixture("window.__emptyBranches=true;")
            val jc=fixture("window.__jcOnly=true;",expected=1)!!;check(jc.courses.single().let{it.dayOfWeek==7&&it.startPeriod==3&&it.endPeriod==4&&it.weeksSet()==setOf(2,4,6,8,10)})
            val variants=fixture("window.__variants=true;",expected=4)!!;check(variants.courses.count{it.teacher=="另一教师"&&it.room=="另一校区 B202"}==2)
            fixture("window.__variants=true;window.__reorder=true;",expected=4)
            val duplicates=fixture("window.__duplicates=true;",expected=2)!!;check(duplicates.courses.all{it.extraInfo.contains("\"sourceRow\":1")&&it.extraInfo.contains("\"sourceRow\":2")})
            for(fault in listOf("index-http","index-redirect","index-path","index-login","teacher-conflict","room-conflict","week-conflict","scalar-branch","cancel-year","bad-year","cancel-selection","cancel-monday","bad-monday","cancel-weeks","short-weeks","teacher","pending-room","weekday","weekday-conflict","section","section-conflict","week","parity","mixed","nested","practice","adjustment","unknown-root","total","pages","has-more","empty","success-zero","error","expiry","code","root-term","row-term","self-year","self-term","identity-change","class-change","raw-change","second-failure","html","json","http","redirect","wrong-path","charset","encoding"))fixture("window.__fault='$fault';",reject=true)
            for(badUrl in listOf("http://jwgl.ndnu.edu.cn/jwglxt/","https://wrong.invalid/jwglxt/","https://jwgl.ndnu.edu.cn:8443/jwglxt/","https://jwgl.ndnu.edu.cn/login.html"))fixture(reject=true,baseUrl=badUrl)
            fixture(reject=true,html="<input type=password>")
            val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name==schoolName}
            check(school.supported&&"zhengfang" in SchoolCatalog.knownSystemTypes(school))
            val adapters=SchoolCatalog.adapters(school);check(adapters.first().id==id&&adapters.first().licenseAsset=="jiangxi-zf-personal-original-MIT.txt")
            totalSuccesses+=successes;totalRejections+=rejections
            }
            val vendorSchool=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="江西水利电力大学"}
            check("qz" in SchoolCatalog.knownSystemTypes(vendorSchool))
            result.putString("stream","PASS: $totalSuccesses JJU/NVU success and $totalRejections whole-batch rejection fixtures; real Chromium DOM/production bridge/native payload, no school requests or user data.");finish(-1,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
    }
}
