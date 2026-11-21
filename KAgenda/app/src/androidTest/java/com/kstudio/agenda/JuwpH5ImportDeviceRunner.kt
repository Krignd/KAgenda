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
class JuwpH5ImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result=Bundle();var web:WebView?=null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk")){"Emulator only"}
            fun <T> main(block:()->T):T {var value:T?=null;runOnMainSync{value=block()};@Suppress("UNCHECKED_CAST") return value as T}
            val view=main{WebView(targetContext).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true}};web=view
            fun read(path:String)=targetContext.assets.open(path).bufferedReader().use{it.readText()}
            fun evaluate(js:String):Any {val latch=CountDownLatch(1);var answer="null";main{view.evaluateJavascript(js){answer=it;latch.countDown()}};check(latch.await(5,TimeUnit.SECONDS));return JSONTokener(answer).nextValue()}
            val origin="http://jiaowuh5.juwp.edu.cn/"
            val setup="""
                window.__messages=[];window.__done=false;window.__counts={};
                var f=window.__fault||'',token='SYNTHETIC-JUWP-TOKEN',info={userNo:'SYN-USER',name:'合成学生',token:token,userType:'2'};
                sessionStorage.clear();sessionStorage.setItem('Token',token);sessionStorage.setItem('userInfo',JSON.stringify(info));sessionStorage.setItem('schoolCode',f==='school'?'OTHER':'11319');sessionStorage.setItem('Identity',f==='role'?'1':'2');
                window._kagendaWarehouse={dispatch:function(raw){var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;if(m.id){var value=true;if(m.action==='selection')value=0;if(m.action==='prompt')value=m.payload.title==='第一周周一'?'2026-09-07':'18';window.__kagendaSgReply(m.id,true,value);}}};
                window.fetch=async function(raw,init){var u=new URL(raw),p=u.pathname;window.__counts[p]=(window.__counts[p]||0)+1;
                  if(u.origin!=='http://jiaowuh5.juwp.edu.cn'||init.method!=='POST'||init.headers.token!==token||init.credentials!=='same-origin'||init.redirect!=='error'||init.body!==undefined)throw Error('Wrong request');
                  var row={courseName:'合成理论',teacherName:'合成教师甲、合成教师乙',location:'合成校区A101',ktmc:'合成教学班',kch:'SYN-KCH',jx0408id:'SYN-JX',classTime:'10102',weekNoteDetail:'101,102',classWeek:'1-5周(单)',classWeekDetails:'1,3,5',startTime:'08:15',endTIme:'09:35'},d;
                  if(p==='/njwhd/student/my')d={code:1,data:[{studentID:f==='identity'?'OTHER':'SYN-USER',name:f==='name'?'其他姓名':'合成学生',schoolName:f==='old-name'?'南昌工程学院':'江西水利电力大学',academy:'合成学院',className:'合成行政班'}]};
                  else if(p==='/njwhd/getXnxqList')d=[{xnxq01id:'SYN-TERM',xqmc:'合成学期',isdqxq:'1'}];
                  else if(p==='/njwhd/Get_sjkbms')d={code:1,data:[{kbjcmsid:'SYN-MODE',kbjcmsmc:'合成模式',mrms:'1'}]};
                  else if(p==='/njwhd/student/curriculum'){
                    if(u.searchParams.size!==3||u.searchParams.get('week')!=='all'||u.searchParams.get('xnxq01id')!=='SYN-TERM'||u.searchParams.get('kbjcmsid')!=='SYN-MODE')throw Error('Wrong full-term query');
                    d={code:1,data:[{week:f==='single-week'?'1':'all',date:[],item:[row]}],jcdatalist:[],nkbList:[]};
                    if(f==='teacher')row.teacherName='';if(f==='room')row.location='';if(f==='clock')row.endTIme='07:00';if(f==='half-clock')delete row.endTIme;
                    if(f==='week-conflict')row.classWeekDetails='2,4';if(f==='sections')row.classTime='10002';if(f==='note-conflict')row.weekNoteDetail='20102';
                    if(f==='nkb')d.nkbList=[{kcmc:'合成未排课'}];if(f==='unknown')d.unparsed='未核验实践';if(f==='pages')d.pages=2;
                    if(f==='empty')d.data[0].item=[];if(f==='expiry'){d.code=0;d.Msg='登录已过期';}
                    if(f==='second-failure'&&window.__counts[p]>1)throw Error('Synthetic failure');
                    if(f==='raw-change'&&window.__counts[p]>1)row.location='合成校区B202';if(f==='token-echo')row.teacherName=token;
                  }else throw Error('Forbidden endpoint');
                  var bytes=new TextEncoder().encode(f==='html'?'<html>login</html>':JSON.stringify(d));
                  return {ok:f!=='http',redirected:f==='redirect',url:u.href,headers:{get:function(k){return k.toLowerCase()==='content-type'?'application/json; charset=UTF-8':null;}},body:{getReader:function(){var sent=false;return {read:async function(){if(sent)return {done:true};sent=true;return {done:false,value:bytes};},cancel:async function(){},releaseLock:function(){}};}}};
                };
            """.trimIndent()
            var successes=0;var rejections=0
            fun fixture(fault:String="",reject:Boolean=false) {
                val loaded=CountDownLatch(1)
                main{view.webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,url:String?){loaded.countDown()};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?):WebResourceResponse?{if(r?.url?.scheme=="data")return null;throw IllegalStateException("Unexpected network resource")}};view.loadDataWithBaseURL(origin,"<html><head><link rel=icon href='data:,'></head><body id=boot>Synthetic</body></html>","text/html","UTF-8",null)}
                check(loaded.await(10,TimeUnit.SECONDS));evaluate("window.__fault="+JSONObject.quote(fault)+";"+setup)
                val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-juwp"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_juwp_h5_personal"))
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/juwp-h5-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000
                while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"JUWP fixture timeout"};SystemClock.sleep(30)}
                val messages=JSONArray(evaluate("JSON.stringify(window.__messages)").toString())
                fun data(action:String)=(0 until messages.length()).map{messages.getJSONObject(it)}.lastOrNull{it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
                if(reject){check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank());check((0 until messages.length()).any{messages.getJSONObject(it).getString("action")=="alert"});rejections++;return}
                check(data("courses").isNotBlank()){"JUWP import failed: $messages"};check(!data("courses").contains("SYN-USER")&&!data("courses").contains("SYNTHETIC-JUWP-TOKEN"))
                val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));val course=batch.courses.single();check(course.hasCustomTime&&course.timeRange=="08:15-09:35"&&course.startPeriod==1&&course.endPeriod==2&&course.weeksSet()==setOf(1,3,5));check(batch.totalWeeks==18&&batch.firstMonday.toString()=="2026-09-07"&&batch.times.isEmpty());check(evaluate("Object.values(window.__counts).reduce((a,b)=>a+b,0)")==8);successes++
            }
            fixture();fixture("old-name")
            for(fault in listOf("school","role","identity","name","single-week","teacher","room","clock","half-clock","week-conflict","sections","note-conflict","nkb","unknown","pages","empty","expiry","second-failure","raw-change","token-echo","html","http","redirect"))fixture(fault,true)
            val school=SchoolCatalog.load(targetContext,emptyList()).single{it.name=="江西水利电力大学"};check(school.supported&&SchoolCatalog.adapters(school).any{it.id=="ext_juwp_h5_personal"})
            result.putString("stream","PASS: $successes JUWP H5 success and $rejections whole-batch rejection fixtures; real WebView bridge and exact-time native payload, synthetic responses only.");finish(-1,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString().take(5000)}");finish(0,result)}finally{runOnMainSync{web?.destroy()}}
    }
}
