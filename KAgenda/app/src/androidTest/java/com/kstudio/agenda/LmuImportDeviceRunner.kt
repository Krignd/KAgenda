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
import com.kstudio.agenda.i18n.AcademicText
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Primary-authored synthetic fixtures; no upstream execution, school traffic or database writes. */
class LmuImportDeviceRunner:Instrumentation(){
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
 override fun onStart(){
  val result=Bundle();var web:WebView?=null
  try{
   check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk")){"Emulator only"}
   fun <T> main(block:()->T):T{var value:T?=null;runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST") return value as T}
   val view=main{WebView(targetContext).apply{settings.javaScriptEnabled=true}};web=view
   fun read(path:String)=targetContext.assets.open(path).bufferedReader().use{it.readText()}
   fun eval(js:String):Any{val latch=CountDownLatch(1);var answer="null";main{view.evaluateJavascript(js){answer=it;latch.countDown()}};check(latch.await(5,TimeUnit.SECONDS));return JSONTokener(answer).nextValue()}
   val setup="""
    window.__messages=[];window.__done=false;window.__reads=0;
    if(window.__fault==='missing-self')document.querySelector('#xhid').remove();
    if(window.__fault==='not-hidden')document.querySelector('#xhid').type='text';
    if(window.__fault==='disabled')document.querySelector('#xnxq1').disabled=true;
    window._kagendaWarehouse={dispatch:function(raw){var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
     if(m.id){var v=true;if(m.action==='selection')v=window.__fault==='cancel'?null:0;
      if(m.action==='prompt'){v=m.payload.title==='第一周周一'?(window.__fault==='bad-monday'?'2026-09-08':window.__fault==='cancel-date'?null:'2026-09-07'):(window.__fault==='short-weeks'?'8':window.__fault==='long-weeks'?'41':'18');
       if(window.__fault==='self-change')document.querySelector('#xhid').value='SYN-OTHER';
       if(window.__fault==='term-change')document.querySelector('#xnxq1').value='2026-2027-2';
       if(window.__fault==='label-change')document.querySelector('#xnxq1').selectedOptions[0].textContent='CHANGED';
      }window.__kagendaSgReply(m.id,true,v);}
    }};
    window.fetch=async function(raw,init){var u=new URL(raw),f=window.__fault;window.__reads++;
     if(u.origin!=='https://jw.lmu.cn'||u.pathname!=='/admin/pkgl/xskb/sdpkkbList'||u.searchParams.get('xhid')!=='SYN-SELF'||u.searchParams.get('xqdm')!=='SYN-CAMPUS'||u.searchParams.get('xnxq')!=='2026-2027-1'||Array.from(u.searchParams.keys()).length!==3||init.method!=='GET'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong LMU request');
     if(f==='second-failure'&&window.__reads>1)throw Error('Synthetic failure');
     var row={kcmc:'<b>合成中文课程</b>',tmc:'合成甲、合成乙',croommc:'A101',xqmc:'合成校区',type:window.__type==null?1:window.__type,djc:3,zc:'1-9(单)周',xingqi:2,jxbmc:'合成教学班'},rows=[row];
     if(window.__numeric){row.type='1';row.djc='3';row.xingqi='2';row.zc=[1,'3',5];}
     if(window.__campusMissing)delete row.xqmc;
     if(window.__variants)rows.push(Object.assign({},row,{tmc:'另一教师',jxbmc:'另一教学班'}));
     if(window.__duplicates)rows.push(Object.assign({},row));
     if(f==='active-content')row.kcmc='<img src="https://synthetic.invalid/no-request">';
     if(f==='name')row.kcmc='';if(f==='teacher')row.tmc='';if(f==='room')row.croommc='未安排';if(f==='day')row.xingqi=8;if(f==='section')row.djc='1-2';if(f==='type')row.type=6;if(f==='week')row.zc='9-1周';if(f==='nested')row.extra=[{}];if(f==='secret')row.token='SYN-NOT-A-KEY';if(f==='row-term')row.xnxq='OTHER';if(f==='mixed')rows.push(Object.assign({},row,{zc:''}));if(f==='raw-change'&&window.__reads>1)row.jxbmc='CHANGED';
     var text=f==='html'?'<html>登录</html>':f==='envelope'?JSON.stringify({rows:rows,hasMore:true}):f==='empty'?'[]':JSON.stringify(rows),buf=f==='encoding'?new Uint8Array([255]):new TextEncoder().encode(text);
     return{status:f==='http'?403:200,redirected:f==='redirect',url:f==='wrong-path'?'https://jw.lmu.cn/admin/login':u.href,headers:{get:function(){return f==='charset'?'application/json;charset=GBK':'application/json;charset=utf-8';}},arrayBuffer:async()=>buf.buffer};
    };
   """.trimIndent()
   var successes=0;var rejections=0
   fun fixture(flags:String="",reject:Boolean=false,url:String="https://jw.lmu.cn/admin/pkgl/xskb/queryKbForXsd",expected:Int=1):WarehousePayload.Batch?{
    val loaded=CountDownLatch(1)
    main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))};view.loadDataWithBaseURL(url,"<html><body><input type=hidden id=xhid value=SYN-SELF><input type=hidden id=xqdm value=SYN-CAMPUS><select id=xnxq1><option value=2026-2027-1 selected>2026-2027第一学期</option><option value=2026-2027-2>第二学期</option></select></body></html>","text/html","UTF-8",null)}
    check(loaded.await(10,TimeUnit.SECONDS));eval(flags+setup)
    val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-lmu"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_lmu_personal"))
    eval(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/lmu-personal.js"),read("shiguang/runner.js")))
    val deadline=SystemClock.elapsedRealtime()+10000
    while(eval("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"LMU timeout"};SystemClock.sleep(30)}
    val messages=JSONArray(eval("JSON.stringify(window.__messages)").toString());fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
    if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Partial staged: $messages"};rejections++;return null}
    check(data("courses").isNotBlank()){"LMU failed: $messages"};val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==expected&&batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(eval("window.__reads")==2);successes++;return batch
   }
   val regular=fixture()!!;check(regular.courses.single().let{it.title=="合成中文课程"&&it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.weeksSet()==setOf(1,3,5,7,9)&&it.extraInfo.contains("合成教学班")&&!it.extraInfo.contains("SYN-SELF")})
   for(type in 0..5){val b=fixture("window.__type=$type;")!!;check(b.courses.single().title=="合成中文课程")}
   fixture("window.__numeric=true;");fixture("window.__campusMissing=true;");fixture("window.__variants=true;",expected=2)
   val dup=fixture("window.__duplicates=true;")!!;check(dup.courses.single().extraInfo.let{it.contains("\"sourceRow\":1")&&it.contains("\"sourceRow\":2")})
   for(fault in listOf("missing-self","not-hidden","disabled","cancel","bad-monday","cancel-date","short-weeks","long-weeks","self-change","term-change","label-change","name","teacher","room","day","section","type","week","nested","secret","row-term","mixed","raw-change","html","envelope","empty","encoding","http","redirect","wrong-path","charset","second-failure","active-content")){
    fixture("window.__fault='$fault';",reject=true)
    if(fault in listOf("self-change","term-change","label-change"))check(eval("window.__reads")==1)
   }
   for(url in listOf("https://wrong.invalid/admin/pkgl/xskb/queryKbForXsd","http://jw.lmu.cn/admin/pkgl/xskb/queryKbForXsd","https://jw.lmu.cn/admin/pkgl/xskb/queryKbForXsd?xhid=OTHER","https://jw.lmu.cn/admin/"))fixture(reject=true,url=url)
   val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="黎明职业大学"};check(school.supported&&SchoolCatalog.adapters(school).any{it.id=="ext_lmu_personal"});check("chaoxing" in SchoolCatalog.knownSystemTypes(school));check(!AcademicText.isUndetermined(school))
   result.putString("stream","PASS: $successes LMU successes and $rejections whole-batch rejections; real Chromium, production bridge/native payload, category/class/campus preservation and catalog; no school requests or persistent writes.");finish(-1,result)
  }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
 }
}
