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

/** Production WebView bridge/runner and native payload; entirely self-written synthetic records. */
class XmuImportDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var web: WebView? = null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            fun <T> main(block: () -> T): T { var value: T? = null; runOnMainSync { value = block() }; @Suppress("UNCHECKED_CAST") return value as T }
            val view = main { WebView(targetContext).apply { settings.javaScriptEnabled = true } }; web = view
            fun read(path: String) = targetContext.assets.open(path).bufferedReader().use { it.readText() }
            fun evaluate(js: String): Any {
                val latch = CountDownLatch(1); var answer = "null"
                main { view.evaluateJavascript(js) { answer = it; latch.countDown() } }
                check(latch.await(5, TimeUnit.SECONDS))
                return JSONTokener(answer).nextValue()
            }
            val setup = """
                window.__messages=[];window.__done=false;window.__requests=[];window.__reads={};
                window._kagendaWarehouse={dispatch:function(raw){
                    var m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;
                    if(m.id){var reply=m.action==='selection'?(window.__older?1:0):m.action==='prompt'?(window.__cancel?null:(window.__older?'2026-03-02':'2026-09-07')):true;
                        window.__kagendaSgReply(m.id,true,reply);}
                }};
                window.fetch=async function(raw,init){
                    var u=new URL(raw),key=u.pathname.match(/([^/]+)[.]do$/)[1];
                    var names={initXsxx:'identity',kfdxnxqcx:'terms',getZcxx:'weeks',queryXsskjc:'clocks',queryXspkjg:'courses',rqapjghbcx:'dates',queryXswsckbkc:'unarranged'};
                    var action=names[key],n=window.__reads[action]=(window.__reads[action]||0)+1,f=window.__fault||'';
                    if(!action||u.origin!=='https://jw.xmu.edu.cn'||init.credentials!=='same-origin'||init.cache!=='no-store'||init.redirect!=='error'||init.method!==(action==='identity'?'GET':'POST'))throw Error('Wrong synthetic request');
                    var fields=action==='identity'?u.searchParams:new URLSearchParams(init.body),term=window.__older?'2025-2026-2':'2026-2027-1';
                    if(['weeks','clocks','courses','dates','unarranged'].includes(action)&&fields.get('XNXQDM')!==term)throw Error('Wrong selected term');
                    if(['courses','dates'].includes(action)&&fields.get('ZC')!=='')throw Error('Wrong full-term mode');
                    if(['identity','courses','unarranged'].includes(action)&&fields.get('XH')!=='')throw Error('Not self-only');
                    if(action==='clocks'&&fields.get('XH')!=='SYN-STUDENT')throw Error('Wrong own clock identity');
                    window.__requests.push({action:action,fields:Object.fromEntries(fields)});
                    if(f==='second-failure'&&action==='unarranged'&&n===2)throw Error('Synthetic second read failure');
                    var row={WID:'SYN-R1',BJDM:'SYN-C1',KCDM:'SYN-K1',KCMC:'合成算法',JSXM:'合成甲、合成乙',JASMC:'A101',XQDM_DISPLAY:'合成翔安校区',XQ:'2',JCFADM:'SYN-PLAN-A',KSSJ:'08:00',JSSJ:'09:40',ZCMC:'1-8周(单),10周',SJLY:'0',KSJCDM:'opaque-1',JSJCDM:'opaque-2'};
                    var courses=[row],clocks=[{JCFADM:'SYN-PLAN-A',DM:'opaque-A1',MC:'第1节',KSSJ:'0800',JSSJ:'08:45'},{JCFADM:'SYN-PLAN-A',DM:'opaque-A2',MC:'第二节',KSSJ:'08:55:00',JSSJ:'09:40'}];
                    if(window.__doubleClass)courses.push(Object.assign({},row,{WID:'SYN-R2',BJDM:'SYN-C2'}));
                    if(window.__variants)courses.push(Object.assign({},row,{WID:'SYN-R2',BJDM:'SYN-C2',JSXM:'合成乙',JASMC:'B202'}));
                    if(window.__secondPlan||f==='different-plans'){
                        courses.push(Object.assign({},row,{WID:'SYN-R2',BJDM:'SYN-C2',JCFADM:'SYN-PLAN-B'}));
                        clocks=clocks.concat(clocks.map(function(x){return Object.assign({},x,{JCFADM:'SYN-PLAN-B',DM:x.DM.replace('A','B')},f==='different-plans'?{KSSJ:x.MC==='第1节'?'08:10':'09:05',JSSJ:x.MC==='第1节'?'08:55':'09:50'}:{});}));
                    }
                    var terms=[{XNXQDM:'2026-2027-1',XNXQDM_DISPLAY:'2026-2027学年第一学期',DM:'opaque-dictionary-id'},{XNXQDM:'2025-2026-2',MC:'2025-2026学年第二学期'}];
                    var data={identity:{success:true,data:[{XH:'SYN-STUDENT',XM:'合成学生'}]},terms:{datas:{kfdxnxqcx:{rows:terms,totalSize:2}}},
                        weeks:{success:true,zcList:Array.from({length:18},function(_,i){return {ZC:String(i+1)};}),currentZc:'4'},clocks:{success:true,data:clocks},
                        courses:{success:true,pkjgList:courses,ttbList:[]},dates:{datas:{rqapjghbcx:{rows:[],totalSize:0}}},unarranged:{success:true,data:[]}};
                    if(f==='term-total')delete data.terms.datas.kfdxnxqcx.totalSize;
                    if(f==='term-page')data.terms.datas.kfdxnxqcx.totalSize=3;
                    if(f==='duplicate-term')terms[1].XNXQDM=terms[0].XNXQDM;
                    if(f==='ttb')data.courses.ttbList.push({TKLXDM:'02'});
                    if(f==='date')data.dates.datas.rqapjghbcx={rows:[{RQ:'2026-10-01'}],totalSize:1};
                    if(f==='date-total')delete data.dates.datas.rqapjghbcx.totalSize;
                    if(f==='unarranged')data.unarranged.data.push({KCMC:'合成未排课'});
                    if(f==='permission'){delete data.unarranged.success;data.unarranged.msg='没有权限';}
                    if(f==='mixed-bad')courses.push(Object.assign({},row,{WID:'SYN-R2',JSXM:''}));
                    if(f==='teacher')row.JSXM='';if(f==='room')row.JASMC='';if(f==='wide-week')row.ZCMC='1-19周';
                    if(f==='period-code')clocks[0].MC='opaque-period-code';if(f==='missing-clock')clocks.splice(0,1);
                    if(f==='unknown-branch')data.courses.practice=[{}];if(f==='row-branch')row.extraArrangements=[{}];
                    if(f==='next-page')data.courses.hasMore=true;if(f==='course-total')data.courses.totalCount=2;
                    if(f==='empty-courses')data.courses.pkjgList=[];if(f==='missing-week')data.weeks.zcList.splice(3,1);
                    if(f==='identity-change'&&n===2)data.identity.data[0].XH='SYN-OTHER';
                    if(f==='class-change'&&n===2)row.BJDM='SYN-CHANGED';
                    if(f==='clock-change'&&n===2)clocks[0].KSSJ='08:01';
                    if(f==='calendar-change'&&n===2)data.weeks.zcList.pop();
                    if(window.__reorder&&n===2){terms.reverse();clocks.reverse();courses.reverse();data.weeks.zcList.reverse();}
                    if(f==='current-week-tick'&&n===2)data.weeks.currentZc='5';
                    if(window.__zeroPages)Object.assign(data.dates.datas.rqapjghbcx,{pages:0,pageNumber:0,pageSize:0});
                    if(window.__smallPageSize)Object.assign(data.courses,{pageSize:1,totalSize:courses.length,pages:1});
                    return {ok:f!=='http',redirected:f==='redirect',url:u.href,headers:{get:function(){return 'application/json; charset=UTF-8';}},
                        text:async function(){return f==='html'?'<html>登录</html>':JSON.stringify(data[action]);}};
                };
            """.trimIndent()
            var successes=0;var rejections=0
            val url="https://jw.xmu.edu.cn/gsapp/sys/wdkbapp/*default/index.do?EMAP_LANG=zh"
            fun fixture(flags: String="",reject: Boolean=false,html: String="",baseUrl: String=url,expectedCourses: Int=1): WarehousePayload.Batch? {
                val loaded=CountDownLatch(1)
                main {
                    view.webViewClient=object:WebViewClient(){
                        override fun onPageFinished(v:WebView?,u:String?){loaded.countDown()}
                        override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?)=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                    }
                    view.loadDataWithBaseURL(baseUrl,"<html><body>$html</body></html>","text/html","UTF-8",null)
                }
                check(loaded.await(10,TimeUnit.SECONDS))
                evaluate(flags+setup)
                val bridge=read("shiguang/bridge.js").replace("__TOKEN__",JSONObject.quote("synthetic-xmu"))+"\n"+read("academic/school-common.js").replace("__ADAPTER_ID__",JSONObject.quote("ext_xmu_personal"))
                evaluate(WarehouseScriptRunner.wrap(bridge,read("shiguang/scripts/EXT/xmu-personal.js"),read("shiguang/runner.js")))
                val deadline=SystemClock.elapsedRealtime()+10000
                while(evaluate("window.__done")!=true){check(SystemClock.elapsedRealtime()<deadline){"XMU fixture timed out"};SystemClock.sleep(30)}
                val messages=JSONArray(evaluate("JSON.stringify(window.__messages)").toString())
                fun data(action:String)=(0 until messages.length()).map {messages.getJSONObject(it)}.lastOrNull {it.getString("action")==action}?.getJSONObject("payload")?.getString("data").orEmpty()
                if(reject){
                    check(data("courses").isBlank()&&data("config").isBlank()&&data("times").isBlank()){"Staged data on failed import: $messages"}
                    check((0 until messages.length()).any {messages.getJSONObject(it).getString("action")=="alert"});rejections++;return null
                }
                check(data("courses").isNotBlank()){"XMU failed: $messages"}
                val batch=WarehousePayload.parse(data("courses"),data("times"),data("config"));check(batch.courses.size==expectedCourses)
                check(batch.totalWeeks==18&&batch.times.size==2&&batch.times[0].first.toString()=="08:00")
                check(evaluate("window.__requests.length")==14);successes++;return batch
            }
            val regular=fixture()!!
            check(regular.firstMonday.toString()=="2026-09-07")
            check(regular.courses.single().let {it.title=="合成算法"&&it.teacher=="合成甲、合成乙"&&it.room=="合成翔安校区 A101"&&it.startPeriod==1&&it.endPeriod==2&&it.weeksSet()==setOf(1,3,5,7,10)&&it.extraInfo.contains("SYN-C1")})
            check(evaluate("window.__kagendaSgValidate('kagendaXmuMondayValidator','2026-09-07')")==false)
            val classes=fixture("window.__doubleClass=true;")!!
            check(classes.courses.single().extraInfo.let {it.contains("SYN-C1")&&it.contains("SYN-C2")&&it.contains("SYN-R2")})
            val variants=fixture("window.__variants=true;",expectedCourses=2)!!
            check(variants.courses.map {it.teacher}.toSet()==setOf("合成甲、合成乙","合成乙"))
            check(fixture("window.__older=true;")!!.firstMonday.toString()=="2026-03-02")
            check(fixture("window.__secondPlan=true;")!!.courses.single().extraInfo.contains("SYN-PLAN-B"))
            fixture("window.__doubleClass=true;window.__reorder=true;")
            fixture("window.__fault='current-week-tick';")
            fixture("window.__zeroPages=true;")
            fixture("window.__doubleClass=true;window.__smallPageSize=true;")
            for(fault in listOf("term-total","term-page","duplicate-term","ttb","date","date-total","unarranged","permission","mixed-bad","teacher","room","wide-week","period-code","missing-clock","unknown-branch","row-branch","next-page","course-total","empty-courses","missing-week","identity-change","class-change","clock-change","calendar-change","second-failure","http","redirect","html","different-plans"))fixture("window.__fault='$fault';",reject=true)
            fixture("window.__cancel=true;",reject=true)
            fixture(reject=true,html="<input type=password>")
            fixture(reject=true,baseUrl="https://other.invalid/gsapp/sys/wdkbapp/")
            fixture(reject=true,baseUrl=url+"&reqXH=OTHER")
            val school=SchoolCatalog.load(targetContext,emptyList()).single {it.name=="厦门大学"}
            check(school.supported&&SchoolCatalog.hasAdditionalSource(school)&&!AcademicText.isUndetermined(school))
            check(SchoolCatalog.adapters(school).first().let {it.id=="ext_xmu_personal"&&it.licenseAsset=="xmu-personal-original-MIT.txt"})
            result.putString("stream","PASS: XMU $successes success and $rejections rejection fixtures; production WebView/bridge/runner and Kotlin payload/catalog. Synthetic only; no school network, personal data or persistent writes.")
            finish(-1,result)
        }catch(error:Throwable){result.putString("stream","FAIL: ${error.stackTraceToString().take(4500)}");finish(0,result)}
        finally{runOnMainSync {web?.destroy()}}
    }
}
