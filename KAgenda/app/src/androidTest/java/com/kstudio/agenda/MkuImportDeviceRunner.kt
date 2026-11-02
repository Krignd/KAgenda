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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

/** Own synthetic semantic table and response fixtures; no real courses, accounts or school traffic. */
class MkuImportDeviceRunner:Instrumentation(){
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
    if(window.__fault==='missing-select')document.querySelector('#xnxq01id').remove();
    if(window.__fault==='disabled')document.querySelector('#xnxq01id').disabled=true;
    window._kagendaWarehouse={dispatch:function(raw){var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
     if(m.id){var v=true;if(m.action==='selection')v=window.__fault==='cancel-selection'?null:0;
      if(m.action==='prompt'){v=m.payload.title==='校历第一周周一'?(window.__fault==='bad-monday'?'2026-09-08':window.__fault==='cancel-date'?null:'2026-09-07'):(window.__fault==='short-weeks'?'6':window.__fault==='long-weeks'?'41':'18');
       if(window.__fault==='term-change')document.querySelector('#xnxq01id').value='2026-2027-2';
       if(window.__fault==='label-change')document.querySelector('#xnxq01id').selectedOptions[0].textContent='CHANGED';
      }window.__kagendaSgReply(m.id,true,v);}
    }};
    window.__html=function(){
     var f=window.__fault,term=window.__opaque?'TERM-OPAQUE':'2026-2027-1',label=window.__opaque?'2026-2027第三学期':'2026-2027第一学期';
     if(f==='response-term')term='OTHER';if(f==='response-label')label='OTHER';
     var select='<select id=xnxq01id><option selected value="'+term+'">'+label+'</option></select>';
     var time=f==='week'?'8-1周[1节]':f==='section'?'1-8周[6-1节]':f==='out-of-row'?'1-8周[9节]':'1-8周(单)[1,3,5-6节]';
     var teacher=f==='teacher'?'未知教师':'合成甲、合成乙',room=f==='room'?'未安排':'A101';
     var info='老师:'+teacher+';时间:'+time+';地点:'+room+(window.__noCampus?'':';校区:合成校区')+';教学班:合成教学班';
     if(f==='unknown-label')info+=';未知:安排';if(f==='missing-room')info=info.replace(/;地点:[^;]+/,'');
     function li(extra){return '<li '+(f==='raw-change'&&window.__reads>1?'data-class=SYN-B':'data-class=SYN-A')+'><span class=qz-hasCourse-title>'+(f==='name'?'':'合成中文课程')+'</span><span class=qz-hasCourse-abbrinfo>'+extra+'</span>'+(f==='li-extra'?'未核验内容':'')+'</li>';}
     var entry=li(info);if(window.__variants)entry+=li(info.replace('合成甲、合成乙','另一教师').replace('合成教学班','另一教学班'));if(window.__duplicates)entry+=li(info);
     if(f==='mixed')entry+='<li>未核验非空条目</li>';if(f==='active-content')entry+='<img src="https://synthetic.invalid/no-request">';
     var days=window.__flip?['二','一','三','四','五','六','日']:['一','二','三','四','五','六','日'];if(f==='duplicate-day')days[1]='一';
     var header='<tr><th>节次</th>'+days.map(function(d){return '<th>星期'+d+'</th>';}).join('')+'</tr>';
     var r1='<tr><td><span class=index-title>'+(f==='row-label'?'未知节次':window.__rowspan?'1-2':'1-6')+'</span></td>';
     for(var day=1;day<=7;day++)r1+='<td name=kbDataTd '+(day===2&&window.__rowspan?'rowspan=2':'')+'>'+(day===2?(f==='cell-extra'?'未核验内容':'')+'<ul>'+entry+'</ul>':'')+'</td>';r1+='</tr>';
     var r2='<tr><td><span class=index-title>'+(window.__rowspan?'3-6':'7-8')+'</span></td>';for(day=1;day<=7;day++)if(!(window.__rowspan&&day===2))r2+='<td name=kbDataTd></td>';r2+='</tr>';
     var table='<table>'+header+r1+r2+'</table>';if(f==='grid')table=table.replace('<td name=kbDataTd></td>','');
     if(f==='no-cells')table=table.replace(/name=kbDataTd/g,'');
     var extra=f==='extra-table'?'<table><tr><td>实践待排安排</td></tr></table>':window.__logo?'<img src="https://synthetic.invalid/inert-logo">':'';
     var html='<html><head><meta charset="UTF-8"></head><body>'+select+table+extra+'</body></html>';
     if(f==='incomplete')html=html.replace('</html>','');if(f==='login')html='<html><body><input type=password></body></html>';return html;
    };
    window.fetch=async function(raw,init){var u=new URL(raw),f=window.__fault;window.__reads++;
     if(u.origin!=='https://jwgl.mku.edu.cn'||u.pathname!=='/jsxsd/xskb/xskb_list.do'||u.searchParams.get('viweType')!=='0'||u.searchParams.get('zc')!==''||u.searchParams.get('cj0701id')!==''||u.searchParams.get('xnxq01id')!==(window.__opaque?'TERM-OPAQUE':'2026-2027-1')||Array.from(u.searchParams.keys()).length!==4||init.method!=='GET'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store'||init.referrer!==location.href||init.referrerPolicy!=='same-origin'||!init.signal)throw Error('Wrong MKU request');
     if(f==='second-failure'&&window.__reads>1)throw Error('Synthetic failure');
     var html=window.__html(),buf=f==='encoding'?new Uint8Array([255]):new TextEncoder().encode(html);
     return{ok:f!=='http',status:f==='http'?403:200,redirected:f==='redirect',url:f==='wrong-path'?'https://jwgl.mku.edu.cn/jsxsd/':u.href,headers:{get:function(){return f==='charset'?'text/html;charset=GBK':'text/html;charset=UTF-8';}},arrayBuffer:async()=>buf.buffer};
    };
   """.trimIndent()
   var successes=0;var rejections=0
   fun fixture(flags:String="",reject:Boolean=false,url:String="https://jwgl.mku.edu.cn/jsxsd/xskb/xskb_list.do",variants:Boolean=false,duplicates:Boolean=false,opaque:Boolean=false):WarehousePayload.Batch?{
    val counter=AtomicInteger();val requests=ConcurrentLinkedQueue<String>();val loaded=CountDownLatch(1);val term=if(opaque)"TERM-OPAQUE" else "2026-2027-1";val label=if(opaque)"2026-2027第三学期" else "2026-2027第一学期"
    main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?):WebResourceResponse?{if(r?.url?.scheme=="data")return null;counter.incrementAndGet();requests.add("${r?.url?.scheme}://${r?.url?.host}${r?.url?.path}".take(200));return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))}};view.loadDataWithBaseURL(url,"<html><head><link rel=icon href='data:,'></head><body><select id=xnxq01id><option selected value='$term'>$label</option><option value=2026-2027-2>第二学期</option></select></body></html>","text/html","UTF-8",null)}
    check(loaded.await(10,TimeUnit.SECONDS));check(eval("!!document.querySelector('#xnxq01id')")==true){"Synthetic bootstrap HTML did not load"};eval("window.__variants=$variants;window.__duplicates=$duplicates;window.__opaque=$opaque;"+flags+setup)
    val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-mku"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_mku_personal"))
    eval(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/mku-personal.js"),read("shiguang/runner.js")))
    val deadline=SystemClock.elapsedRealtime()+10000
    while(eval("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"MKU timeout"};SystemClock.sleep(30)}
    val messages=JSONArray(eval("JSON.stringify(window.__messages)").toString());fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
    check(counter.get()==0){"Resource request escaped inert parser: $requests"}
    if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Partial staged: $messages"};rejections++;return null}
    check(data("courses").isNotBlank()){"MKU failed: $messages"};val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==if(variants)6 else 3);check(batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(eval("window.__reads")==2);successes++;return batch
   }
   val first=fixture()!!;check(first.courses.map{it.startPeriod}==listOf(1,3,5));check(first.courses.all{it.title=="合成中文课程"&&it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.dayOfWeek==2&&it.weeksSet()==setOf(1,3,5,7)&&it.extraInfo.contains("合成教学班")})
   fixture(variants=true);val dup=fixture(duplicates=true)!!;check(dup.courses.all{it.extraInfo.contains("\"sourceRow\":1")&&it.extraInfo.contains("\"sourceRow\":2")})
   fixture(opaque=true);fixture("window.__rowspan=true;");val flip=fixture("window.__flip=true;")!!;check(flip.courses.all{it.dayOfWeek==1});fixture("window.__logo=true;");val noCampus=fixture("window.__noCampus=true;")!!;check(noCampus.courses.all{it.room=="A101"})
   for(fault in listOf("missing-select","disabled","cancel-selection","bad-monday","cancel-date","short-weeks","long-weeks","term-change","label-change","response-term","response-label","teacher","room","missing-room","unknown-label","name","week","section","out-of-row","row-label","duplicate-day","mixed","li-extra","cell-extra","active-content","extra-table","grid","no-cells","incomplete","login","encoding","charset","http","redirect","wrong-path","raw-change","second-failure")){
    fixture("window.__fault='$fault';",reject=true)
    if(fault in listOf("term-change","label-change"))check(eval("window.__reads")==1)
   }
   for(url in listOf("https://wrong.invalid/jsxsd/xskb/xskb_list.do","http://jwgl.mku.edu.cn/jsxsd/xskb/xskb_list.do","https://jwgl.mku.edu.cn/jsxsd/","https://jwgl.mku.edu.cn/jsxsd/xskb/xskb_list.do?zc=1","https://jwgl.mku.edu.cn/jsxsd/xskb/xskb_list.do?cj0701id=OTHER"))fixture(reject=true,url=url)
   val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="闽南科技学院"};check(school.supported&&"qz" in SchoolCatalog.knownSystemTypes(school));check(!AcademicText.isUndetermined(school));check(SchoolCatalog.adapters(school).first().id=="ext_mku_personal");check(SchoolCatalog.adapters(school).any{it.id=="MKU_01"})
   result.putString("stream","PASS: $successes MKU successes and $rejections whole-batch rejections; real Chromium semantic grid, inert resource-free parsing, actual selected term, disjoint periods, production bridge/native payload and catalog; no school requests or persistent writes.");finish(-1,result)
  }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
 }
}
