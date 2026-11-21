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

/** Same own synthetic fixtures as desktop Chromium, production bridge/native parser; no school traffic. */
class SdustImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle();var web:WebView?=null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk")){"Emulator only"}
            fun <T> main(block:()->T):T {var value:T?=null;runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST") return value as T}
            val view=main{WebView(targetContext).apply{settings.javaScriptEnabled=true}};web=view
            fun read(path:String)=targetContext.assets.open(path).bufferedReader().use{it.readText()}
            fun evaluate(js:String):Any {val latch=CountDownLatch(1);var answer="null";main{view.evaluateJavascript(js){answer=it;latch.countDown()}};check(latch.await(5,TimeUnit.SECONDS));return JSONTokener(answer).nextValue()}
            val fixture=context.assets.open("sdust-qz-fixture.js").bufferedReader().use{it.readText()}
            val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-sdust"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_sdust_qz_personal"))
            var successes=0;var rejections=0
            fun load(html:String) {val latch=CountDownLatch(1);main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){latch.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))};view.loadDataWithBaseURL("https://jwglxt.sdust.edu.cn/jsxsd/xskb/xskb_list.do",html,"text/html","UTF-8",null)};check(latch.await(10,TimeUnit.SECONDS))}
            load("<html><body>Synthetic</body></html>");evaluate(fixture)
            val cases=JSONArray(evaluate("JSON.stringify([...kagendaSdustFixture.accepted,...kagendaSdustFixture.rejected])").toString())
            for(i in 0 until cases.length()) {
                val fault=cases.getString(i);evaluate(fixture);val html=evaluate("kagendaSdustFixture.page("+JSONObject.quote(fault)+",0,true)").toString();load(html);evaluate(fixture+"\nkagendaSdustFixture.install("+JSONObject.quote(fault)+");")
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/sdust-qz-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000;while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"SDUST timeout: $fault"};SystemClock.sleep(30)}
                val answer=JSONObject(evaluate("JSON.stringify((function(){try{return kagendaSdustFixture.check("+JSONObject.quote(fault)+");}catch(e){return {fixtureError:String(e.message||e)};}})())").toString());check(!answer.has("fixtureError")){"$fault: $answer"}
                if(answer.getBoolean("rejected")){rejections++;continue}
                val batch=WarehousePayload.parse(answer.getString("courses"),answer.getString("times"),answer.getString("config"));check(batch.courses.size==if(fault in listOf("two-teachers","reorder"))4 else 2);check(batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(batch.courses.all{it.weeksSet()==setOf(1,3,5,7,12)&&it.dayOfWeek==2&&it.room.contains("教学楼")&&it.extraInfo.contains("合成教学班")});successes++
            }
            val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="山东科技大学"};check(school.supported&&"qz" in SchoolCatalog.knownSystemTypes(school));check(SchoolCatalog.adapters(school).first().id=="ext_sdust_qz_personal")
            result.putString("stream","PASS: $successes SDUST success and $rejections whole-batch rejection fixtures; real WebView DOM, production bridge and native payload; synthetic data only.");finish(-1,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
    }
}
