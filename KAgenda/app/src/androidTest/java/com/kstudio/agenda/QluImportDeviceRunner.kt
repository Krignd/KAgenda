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

/** Own synthetic DOM/responses only, real WebView/bridge/native payload. No school traffic. */
class QluImportDeviceRunner : Instrumentation() {
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
                window.__messages=[];window.__done=false;window.__termReads=0;window.__courseReads=0;window.__requests=[];
                window._kagendaWarehouse={dispatch:function(raw){var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
                  if(m.id){var value=true;if(m.action==='selection')value=window.__fault==='cancel'?null:0;
                    if(m.action==='prompt'){value=/^(开学|Monday|Lundi)/.test(m.payload.title)?(window.__fault==='bad-monday'?'2026-09-08':'2026-09-07'):(window.__fault==='short-weeks'?'11':'18');}
                    window.__kagendaSgReply(m.id,true,value);}}};
                window.fetch=async function(raw,init){
                  var u=new URL(raw),f=window.__fault,fields=new URLSearchParams(init.body),text;window.__requests.push(u.href);
                  if(u.origin!=='https://jw.qlu.edu.cn'||init.method!=='POST'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store'||init.referrer!==location.href||init.referrerPolicy!=='same-origin')throw Error('Wrong QLU request');
                  if(u.pathname==='/jwglxt/xtgl/index_cxAreaSix.html'){
                    window.__termReads++;if(u.search!=='?localeKey=zh_CN&gnmkdm=index'||init.body!=='')throw Error('Wrong semester request');
                    text='<html><body><script>var bait="2025-2026学年2学期(2026-03-01至2026-07-25)";<\/script><div>2026-2027学年'+(f==='second'?'2':'1')+'学期(2026-09-01至2027-01-25)</div></body></html>';
                    if(f==='term-change'&&window.__termReads>1)text=text.replace('2026-09-01','2026-09-02');if(f==='term-missing')text='<html>Unknown semester</html>';if(f==='term-login')text='<input type=password>';if(f==='term-invalid')text=text.replace('2026-09-01','2026-02-30');
                  }else if(u.pathname==='/jwglxt/kbcx/xskbcx_cxXsKb.html'){
                    window.__courseReads++;if(u.search!=='?gnmkdm=N2151'||fields.size!==2||fields.get('xnm')!=='2026'||fields.get('xqm')!==(f==='second'?'12':'3'))throw Error('Wrong two-field QLU contract');
                    if(f==='second-failure'&&window.__courseReads>1)throw Error('Synthetic second failure');
                    var row={kcmc:'合成齐鲁课程',xm:'合成甲、合成乙',jc:'1-2,5',zcd:'1-8周(单),12周',xqj:2,xqmc:'合成校区',cdmc:'A101',jxbmc:'合成教学班',xf:'2',khfsmc:'合成考核'},d={kbList:[row],xsxx:{XH:'SYN-SELF'}};
                    if(f==='teacher')row.xm='';if(f==='teacher-conflict')row.jsxm='冲突教师';if(f==='room')row.cdmc='未安排';if(f==='weekday')row.xqj=8;if(f==='week')row.zcd='1-8周未知';if(f==='section')row.jc='08:00-09:30';if(f==='section-conflict')row.jcs='7-8';if(f==='jcs-only'){delete row.jc;row.jcs='1-2';}
                    if(f==='practice')d.sjkList=[{}];if(f==='unknown')d.unparsed='未知实践';if(f==='nested')row.extra=[{}];if(f==='pages')d.pages=2;if(f==='total')d.total=2;if(f==='empty')d.kbList=[];if(f==='expiry')d.msg='会话已过期，请重新登录';if(f==='row-term')row.xnm='2025';
                    if(f==='identity-change'&&window.__courseReads>1)d.xsxx.XH='SYN-OTHER';if(f==='raw-change'&&window.__courseReads>1)row.jxbmc='CHANGED';
                    text=f==='html'?'<html>login</html>':JSON.stringify(d);
                  }else throw Error('Forbidden endpoint');
                  return {ok:f!=='http',redirected:f==='redirect',url:u.href,headers:{get:()=>f==='encoding'?'text/html; charset=GBK':'text/html; charset=UTF-8'},text:async()=>text};
                };
            """.trimIndent()
            var successes=0;var rejections=0
            fun fixture(fault:String="",reject:Boolean=false,language:String="zh") {
                val loaded=CountDownLatch(1)
                main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))};view.loadDataWithBaseURL("https://jw.qlu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs","<html><body><input id=jsdm type=hidden value=xs></body></html>","text/html","UTF-8",null)}
                check(loaded.await(10,TimeUnit.SECONDS));evaluate("window.__fault="+JSONObject.quote(fault)+";window.kagendaUiLanguage="+JSONObject.quote(language)+";"+setup)
                val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-qlu"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_qlu_personal"))
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/qlu-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000;while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"QLU fixture timeout"};SystemClock.sleep(30)}
                val messages=JSONArray(evaluate("JSON.stringify(window.__messages)").toString());fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
                if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Partial staging: $messages"};check((0 until messages.length()).any{messages.getJSONObject(it).getString("action")=="alert"});rejections++;return}
                check(data("courses").isNotBlank()){"QLU import failed: $messages"};val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==2&&batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(batch.courses.map{it.startPeriod}==listOf(1,5));check(batch.courses.all{it.weeksSet()==setOf(1,3,5,7,12)&&it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.extraInfo.contains("合成教学班")&&it.extraInfo.contains("合成考核")&&!it.extraInfo.contains("SYN-SELF")});check(evaluate("window.__requests.length")==4);successes++
            }
            fixture();fixture("second");fixture(language="en");fixture(language="fr")
            for(fault in listOf("cancel","bad-monday","short-weeks","term-change","term-missing","term-login","term-invalid","second-failure","teacher","teacher-conflict","room","weekday","week","section","section-conflict","jcs-only","practice","unknown","nested","pages","total","empty","expiry","row-term","identity-change","raw-change","html","http","redirect","encoding"))fixture(fault,true)
            val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="齐鲁工业大学"};check(school.supported&&"zhengfang" in SchoolCatalog.knownSystemTypes(school));check(SchoolCatalog.adapters(school).first().id=="ext_qlu_personal")
            result.putString("stream","PASS: $successes QLU success and $rejections whole-batch rejection fixtures; real DOMParser strips script bait, production bridge/native payload, synthetic data only.");finish(-1,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
    }
}
