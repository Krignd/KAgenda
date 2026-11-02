package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kstudio.agenda.data.WarehousePayload
import com.kstudio.agenda.data.WarehouseScriptRunner
import com.kstudio.agenda.data.SchoolCatalog
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Chromium DOM + bundled runner/bridge + native payload, synthetic data only. */
class SingleSchoolDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var web: WebView? = null
        try {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            fun <T> main(block: () -> T): T { var value: T? = null; runOnMainSync { value = block() }; @Suppress("UNCHECKED_CAST") return value as T }
            val view = main { WebView(targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            } }; web = view
            fun read(path: String) = targetContext.assets.open(path).bufferedReader().use { it.readText() }
            fun evaluate(js: String): Any {
                val latch = CountDownLatch(1); var answer = "null"
                main { view.evaluateJavascript(js) { answer = it; latch.countDown() } }
                check(latch.await(5, TimeUnit.SECONDS))
                return JSONTokener(answer).nextValue()
            }
            var successfulFixtures = 0
            var rejectedFixtures = 0
            fun fixture(id: String, url: String, script: String, html: String, setup: String = "", expected: Int = 1, acceptAlerts: Boolean = true, promptAnswer: String? = "10", selectionAnswers: List<Int> = emptyList(), timeoutMillis: Long = 10_000, promptAnswers: List<String?>? = null, semesterWeeksAnswer: String? = "20"): WarehousePayload.Batch {
                val loaded = CountDownLatch(1)
                main {
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView?, u: String?) { loaded.countDown() }
                        override fun shouldInterceptRequest(v: WebView?, r: WebResourceRequest?): WebResourceResponse =
                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    view.loadDataWithBaseURL(url, "<html><body>$html</body></html>", "text/html", "UTF-8", null)
                }
                check(loaded.await(10, TimeUnit.SECONDS)) { "Fixture load failed: $id" }
                val mock = """
                    window.__messages=[];window.__done=false;var selections=${JSONArray(selectionAnswers)};var prompts=${if(promptAnswers==null) "null" else JSONArray(promptAnswers).toString()};
                    window._kagendaWarehouse={dispatch:function(raw){
                      var m=JSON.parse(raw);window.__messages.push(m);
                      if(m.action==='complete')window.__done=true;
                      if(m.id){var reply=m.action==='selection'?(selections.length?selections.shift():0):m.action==='prompt'?(m.payload.title==='学期教学周数'?${if (semesterWeeksAnswer == null) "null" else JSONObject.quote(semesterWeeksAnswer)}:(prompts?(prompts.length?prompts.shift():null):${if (promptAnswer == null) "null" else JSONObject.quote(promptAnswer)})):m.action==='alert'?$acceptAlerts:true;
                        window.__kagendaSgReply(m.id,true,reply);}
                    }};
                    window.fetch=async function(){throw Error('Unexpected network access in fixture');};
                    $setup
                """.trimIndent()
                evaluate(mock)
                val bridge = read("shiguang/bridge.js").replace("__TOKEN__", JSONObject.quote("fixture")) + "\n" +
                    read("academic/school-common.js").replace("__ADAPTER_ID__", JSONObject.quote(id))
                evaluate(WarehouseScriptRunner.wrap(bridge, read("shiguang/scripts/EXT/$script"), read("shiguang/runner.js")))
                val deadline = SystemClock.elapsedRealtime() + timeoutMillis
                while (evaluate("window.__done") != true) { check(SystemClock.elapsedRealtime() < deadline) { "Port timeout: $id" }; SystemClock.sleep(50) }
                val messages = JSONArray(evaluate("JSON.stringify(window.__messages)").toString())
                fun data(action: String): String = (0 until messages.length()).map { messages.getJSONObject(it) }
                    .lastOrNull { it.getString("action") == action }?.getJSONObject("payload")?.getString("data").orEmpty()
                val raw = data("courses")
                if (expected == 0) {
                    check(raw.isBlank() && data("config").isBlank() && data("times").isBlank()) { "$id staged data on rejection: $messages" }
                    check((0 until messages.length()).any { messages.getJSONObject(it).getString("action") == "alert" })
                    rejectedFixtures++
                    return WarehousePayload.Batch(emptyList(), emptyList(), null, 1)
                }
                check(raw.isNotBlank()) { "$id failed: $messages" }
                val batch = WarehousePayload.parse(raw, data("times"), data("config"))
                check(batch.courses.size == expected) { "$id: ${batch.courses.size} courses" }
                successfulFixtures++
                return batch
            }
            val ugUrl = "https://ehallapp.nju.edu.cn/jwapp/sys/wdkb/*default/index.do#/xskcb"
            val ugHtml = "<div id=dqxnxqkclb>2026-2027学年 第1学期</div><table><tr>" +
                listOf("课程代码","课程名称","教师","上课时间及地点").joinToString("") { "<th>$it</th>" } + "</tr><tr>" +
                listOf("C1","模拟算法","甲","周三 2-4节 1-5周(单) A201").joinToString("") { "<td>$it</td>" } +
                "</tr></table><nav>1-1 总记录数:1 总页数:1 跳转</nav>"
            val ug = fixture("ext_nju_ug", ugUrl, "nju.js",ugHtml)
            check(ug.courses.single().weeksSet() == setOf(1,3,5))
            check(ug.totalWeeks==10 && ug.firstMonday==null && ug.times.isEmpty())
            for(bad in listOf(ugHtml.replace("总页数:1","总页数:2"),ugHtml.replace("总记录数:1","总记录数:2"),
                ugHtml.replace("教师","未知字段"),ugHtml.replace("甲</td>","</td>"),ugHtml+"<input name=course value=selected>")){
                fixture("ext_nju_ug",ugUrl,"nju.js",bad,expected=0)
            }
            fixture("ext_nju_ug","https://ehallapp.nju.edu.cn/jwapp/sys/kcbcx/*default/index.do","nju.js",ugHtml,expected=0)
            fixture("ext_nju_xk", "https://xk.nju.edu.cn/", "nju.js", """
                <div class=currentTerm>2026秋</div><div class=course-head><div><div>课程名</div><div>教师</div><div>时间地点</div><div>状态</div></div></div>
                <div class=course-body><div><div>模拟选课</div><div>甲</div><div><div>周二 3节 2-8周(双) A202</div></div><div>已选</div></div></div>
            """)
            fixture("ext_dnui", "https://xk.neusoft.edu.cn/xsxk/web/curriculum", "dnui.js", "模拟东软课表", """
                window.scheduleList=[{KCM:'模拟东软',SKXQ:2,KSJC:3,JSJC:4,SKJS:'[001]甲',YPSJDD:'A',SKZCMC:'1-6周(双)',XNXQ:'2026-1'}];
            """)
            val cells = listOf("1","模拟调课","","","","","","甲","05-08 星期3:3-4节 A110","","","06周 星期3:3-4节 调至 09周 星期1:7-8节 B210")
            val fzu = fixture("ext_fzu", "https://jwcjwxt2.fzu.edu.cn:81/", "fzu.js", "<select id=ContentPlaceHolder1_DDL_xnxq><option value=2026-1 selected>2026秋</option></select><table id=ContentPlaceHolder1_DataList_xxk><tr>" + cells.joinToString("") { "<td>$it</td>" } + "</tr></table>", expected=2)
            check(fzu.courses[0].weeksSet() == setOf(5,7,8) && fzu.courses[1].weeksSet() == setOf(9))
            check(fzu.totalWeeks == 20)
            check(evaluate("window.__kagendaSgValidate('kagendaSemesterWeekCountValidator','20')") == false)
            check(evaluate("typeof window.__kagendaSgValidate('kagendaSemesterWeekCountValidator','8')") == "string")
            for (answer in listOf(null, "", "8", "0", "41", "20x", "20.5")) {
                fixture("ext_fzu", "https://jwcjwxt2.fzu.edu.cn:81/", "fzu.js",
                    "<select id=ContentPlaceHolder1_DDL_xnxq><option value=2026-1 selected>2026秋</option></select><table id=ContentPlaceHolder1_DataList_xxk><tr>" +
                        cells.joinToString("") { "<td>$it</td>" } + "</tr></table>", expected=0, semesterWeeksAnswer=answer)
            }
            for (missing in listOf("", "未安排", "暂无", "待定")) {
                val incomplete = cells.toMutableList().apply { this[1] = "模拟缺排课"; this[8] = missing; this[11] = "" }
                val rows = listOf(cells, incomplete).joinToString("") { row -> "<tr>" + row.joinToString("") { "<td>$it</td>" } + "</tr>" }
                fixture("ext_fzu", "https://jwcjwxt2.fzu.edu.cn:81/", "fzu.js",
                    "<select id=ContentPlaceHolder1_DDL_xnxq><option value=2026-1 selected>2026秋</option></select>" +
                        "<table id=ContentPlaceHolder1_DataList_xxk>$rows</table>", expected=0)
            }
            val form = "<select id=xnm><option value=2026 selected>2026</option></select><select id=xqm><option value=3 selected>秋</option></select>"
            for ((id, host) in listOf("ext_usst" to "jwgl.usst.edu.cn", "ext_zju" to "zdbk.zju.edu.cn", "ext_suse" to "jwgl.suse.edu.cn")) {
                val originalZf=fixture(id, "https://$host/jwglxt/kbcx/index", "zhengfang.js", form, """
                    window.fetch=async function(url, init){if(new URL(url).origin!==location.origin)throw Error('Wrong origin');
                      return {ok:true,text:async()=>JSON.stringify({kbList:[{kcb:'模拟课<br>其他',xm:'甲',cdmc:'A',xqmc:'军工路校区',xqj:2,jcs:'3-4',zcd:'第4周',djj:3,skcd:2,dsz:2}]})};};
                """)
                if(id=="ext_usst")check(originalZf.courses.single().room=="军工路校区 A")
            }
            val timed = fixture("ext_xjtlu", "https://timetableplus.xjtlu.edu.cn/", "xjtlu.js", """
                <table class=timetable><tr><td class=day-cell data-day=MON><div class=event><div class=event-name>MEC103-Lecture-D1</div>
                <div class=event-info>YAN YAN</div><div class=event-info>SIP-EB237</div><div class=event-info>Week: 1-6,8-</div><div class=event-info>09:00 - 10:50</div></div></td></tr></table>
            """)
            check(timed.times.single().first.toString() == "09:00" && timed.times.single().second.toString() == "10:50")
            check(timed.courses.single().weeksSet() == setOf(1,2,3,4,5,6,8,9,10))
            check(evaluate("window.__messages.filter(m=>m.action==='prompt' && m.payload.title==='学期教学周数').length") == 0)
            for (answer in listOf(null, "", "5", "41")) {
                fixture("ext_xjtlu", "https://timetableplus.xjtlu.edu.cn/", "xjtlu.js", """
                    <table class=timetable><tr><td class=day-cell data-day=MON><div class=event><div class=event-name>合成定时课</div>
                    <div class=event-info>甲</div><div class=event-info>A101</div><div class=event-info>Week: 1-6</div><div class=event-info>09:00 - 10:50</div></div></td></tr></table>
                """, expected=0, semesterWeeksAnswer=answer)
            }
            fixture("ext_scujcc", "https://jwweb.cdjcc.edu.cn/", "scujcc.js", """
                <table id=Table1><tr><td>模拟锦城<br>周二第1,2,5节{第2-8周|单周}<br>甲<br>A201</td></tr></table>
            """, expected=2)
            val zzuli = fixture("ext_zzuli", "https://jwgl.zzuli.edu.cn/student/xkjg.wdkb.jsp", "zzuli.js", "模拟列表", """
                window.fetch=async function(url,init){
                  if(new URL(url).origin!==location.origin)throw Error('Wrong origin');
                  if(url.includes('showYearTerm'))return {ok:true,text:async()=>JSON.stringify({xn:2026,xq_m:0,xnxqDesc:'模拟2026秋'})};
                  var body;
                  if(url.includes('SetMainInfo'))body='var _loginid="99999";';
                  else {
                    if(atob(new URL(url).searchParams.get('params'))!=='xn=2026&xq=0&xh=99999')throw Error('Wrong term');
                    body='<table><tr><td>上课班级代码</td></tr><tr>'+['1','[C01]模拟轻大','','','','[001]甲','','','','1-11周(单) 五[3-4] 三教楼106(172)；2-8周(双) 二[5] 教室 B'].map(s=>'<td>'+s+'</td>').join('')+'</tr></table>';
                  }
                  return {ok:true,headers:{get:()=> 'text/html; charset=UTF-8'},arrayBuffer:async()=>new TextEncoder().encode(body).buffer};
                };
            """, expected=2)
            check(zzuli.courses[0].weeksSet() == setOf(1,3,5,7,9,11) && zzuli.courses[1].room == "教室 B")
            // Zhejiang additions use the production runner and true Chromium
            // selectors. All fetch responses below are synthetic and local.
            for ((id, host, prefix) in listOf(
                Triple("ext_wzu_api", "jwxt.wzu.edu.cn", "/jwglxt"),
                Triple("ext_zjyc", "jwxt.zjyc.edu.cn", ""),
                Triple("ext_zjut_api", "www.gdjw.zjut.edu.cn", "/jwglxt"),
                Triple("ext_zwu_api", "jw.zwu.edu.cn", "/jwglxt")
            )) {
                val sample = fixture(id, "https://$host$prefix/kbcx/index", "zhejiang.js", form, """
                    window.fetch=async function(url,init){
                      if(new URL(url).origin!==location.origin || init.body.get('xnm')!=='2026' || init.body.get('xqm')!=='3')throw Error('Wrong school/term');
                      return {ok:true,text:async()=>JSON.stringify({kbList:[{kcmc:'模拟浙江课程',xm:'甲',cdmc:'A',xqj:7,jcor:'3-4,7',zcd:'2-8周(双),11周'}]})};
                    };
                """, expected=2)
                check(sample.courses.all { it.weeksSet() == setOf(2,4,6,8,11) })
            }
            fixture("ext_zjgsu_web", "http://jwxt.zjgsu.edu.cn/jwglxt/kbcx/index", "zhejiang.js", form, """
                window.__postCount=0;
                window.fetch=async function(url,init){
                  if(new URL(url).origin!==location.origin)throw Error('Wrong origin');
                  if(!init.method)return {ok:true,headers:{get:()=> 'text/html; charset=UTF-8'},arrayBuffer:async()=>new TextEncoder().encode('<select id=xnm></select><select id=xqm></select>').buffer};
                  if(init.body.get('gnmkdm')!=='N2151'||init.body.get('xqm')!=='3')throw Error('Wrong module/term');
                  return {ok:true,text:async()=>JSON.stringify(++window.__postCount===1?{code:910}:{kbList:[{kcmc:'模拟工商',xm:'乙',cdmc:'B',xqj:2,jc:'1-2',zcd:'1-8周(单)'}]})};
                };
            """)
            check(evaluate("window.__postCount") == 2)
            val dongfang = fixture("ext_zufedfc_html", "http://jwglxt.zufedfc.edu.cn/jwglxt/kbcx/index", "zhejiang.js", form + """
                <table id=kbgrid_table_0><tr><th colspan=8>2026-2027学年第1学期</th></tr><tr><td id=5-9 rowspan=2>
                  <div class=timetable_con><span class=title>★模拟东方</span>
                  <p><i title="节/周">&#xe000;</i>（9-10节）2-8周(双)</p>
                  <p><i title="教师">&#xe001;</i><span>甲</span></p>
                  <p><i title="上课地点">&#xe002;</i><span>B201</span></p></div>
                </td></tr></table>
            """)
            check(dongfang.courses.single().weeksSet() == setOf(2,4,6,8))
            check(dongfang.courses.single().room == "B201")
            for ((id, host, endpoint) in listOf(
                Triple("ext_cjlu_api", "jwxt.cjlu.edu.cn", "xskbcx_cxXsKb.html"),
                Triple("ext_shufezj_api", "jwxt.shufe-zj.edu.cn", "xskbcx_cxXsgrkb.html")
            )) {
                val strict = fixture(id, "https://$host/kbcx/index?su=fixture", "zhejiang.js", form, """
                    window.fetch=async function(url,init){
                      if(new URL(url).origin!==location.origin || !url.includes('$endpoint') || init.body.get('xqm')!=='3')throw Error('Wrong request');
                      return {ok:true,text:async()=>JSON.stringify({kbList:[{kcmc:'模拟新增路线',xm:'甲',cdmc:'A',xqj:4,jcs:'5',zcd:'第3周'}]})};
                    };
                """)
                check(strict.courses.single().weeksSet() == setOf(3))
            }
            for ((id, host, key) in listOf(
                Triple("ext_usts_api", "jwgl.usts.edu.cn", "sjkList"),
                Triple("ext_cumt_api", "jwxt.cumt.edu.cn", "kbList"),
                Triple("ext_cumtxh_api", "jwxt.cumtxh.cn", "kbList")
            )) {
                val jiangsu = fixture(id, "https://$host/jwglxt/kbcx/index", "jiangsu.js", form, """
                    window.fetch=async function(url,init){
                      if(new URL(url).origin!==location.origin || init.body.get('xnm')!=='2026' || init.body.get('xqm')!=='3')throw Error('Wrong school/term');
                      var data={}; data['$key']=[{kcmc:'模拟江苏课程',jsxm:'甲',xm:'乙',cdmc:'C102',xqj:2,jc:'1-2,5',qsjsz:'2-8周(双),11周',zcd:'2-8周(双),11周'}];
                      return {ok:true,text:async()=>JSON.stringify(data)};
                    };
                """, expected=2)
                check(jiangsu.courses.all { it.weeksSet() == setOf(2,4,6,8,11) && it.room == "C102" })
                fixture(id, "https://$host/jwglxt/kbcx/index", "jiangsu.js", form, """
                    window.fetch=async function(){var data={};
                      data['$key']=[{kcmc:'模拟完整课',jsxm:'甲',xm:'乙',cdmc:'C102',xqj:2,jc:'1-2',qsjsz:'1-8周',zcd:'1-8周'}];
                      if('$key'==='sjkList')data['$key'].push({kcmc:'模拟未排实践'});else data.sjkList=[{kcmc:'模拟未排实践'}];
                      return {ok:true,text:async()=>JSON.stringify(data)};};
                """, expected=0)
            }
            fixture("ext_wzu_api", "https://jwxt.wzu.edu.cn/jwglxt/kbcx/index", "zhejiang.js", form, """
                window.fetch=async()=>({ok:true,text:async()=>JSON.stringify({kbList:[{kcmc:'模拟完整课',xm:'甲',cdmc:'A101',xqj:1,jc:'1-2',zcd:'1-8周'}],sjkList:[{kcmc:'模拟未排实践'}]})});
            """, expected=0)
            val njucmHtml = """
                <p>学期起始日期：2026-09-07</p>
                <select id=allSemesters><option value=2026-2027-1 selected>模拟2026秋</option><option value=2026-2027-2>模拟2027春</option></select>
                <select id=weeks><option value=all selected>全部周次</option><option value=1>1</option><option value=2>2</option><option value=3>3</option></select>
                <input type=checkbox id=hasExperiment checked>包含实验
                <table class=courseTable><tbody></tbody></table>
            """
            val njucmSetup = """
                window.__auditTrace=[];
                window.__periods=[['08:00','08:45'],['08:50','09:35'],['09:50','10:35'],['10:40','11:25'],['13:30','14:15'],['14:20','15:05'],['15:20','16:05'],['16:10','16:55'],['18:00','18:45'],['18:50','19:35'],['19:50','20:35'],['20:40','21:25']];
                window.__renderSchedule=function(){
                  var week=document.querySelector('#weeks').value;
                  var a=week==='all'||week==='1'||week==='2', b=week==='all'||week==='3';
                  var rows=[];
                  for(var i=0;i<12;i++){
                    var times=window.__periods[i], start=(window.__wrongTimes&&week==='2'&&i===0)?'08:05':times[0];
                    var row='<tr><td class=dayPartUnit>第'+(i+1)+'节 '+start+'-'+times[1]+'</td>';
                    for(var day=1;day<=7;day++){
                      if(a&&i===1&&day===1)continue;
                      if(a&&i===0&&day===1){
                        var room=window.__badWeek===week?'A999':'A201';
                        row+='<td rowspan=2><span class=course-name>模拟中医课程</span><span>C001</span><br><span>（1-2周）（1-2节）仙林校区 '+room+'  甲</span><br><span>班级样本</span></td>';
                      }else if(b&&i===4&&day===5){
                        row+='<td><span class=course-name>模拟实验课程</span><span>C002</span><br><span>（第3周）（5节）B301  乙</span></td>';
                      }else row+='<td></td>';
                    }
                    rows.push(row+'</tr>');
                  }
                  document.querySelector('table.courseTable tbody').innerHTML=rows.join('');
                };
                document.querySelector('#weeks').addEventListener('change',function(){
                  window.__auditTrace.push(this.value);
                  if(window.__stale)return;
                  setTimeout(function(){
                    if(window.__changeTerm&&document.querySelector('#weeks').value==='2')document.querySelector('#allSemesters').value='2026-2027-2';
                    window.__renderSchedule();
                  },75);
                });
                window.__renderSchedule();
            """
            val njucm = fixture("ext_njucm_dom", "https://jwgl.njucm.edu.cn/student/for-std/course-table", "njucm.js", njucmHtml, njucmSetup, expected=2)
            check(njucm.firstMonday.toString() == "2026-09-07" && njucm.totalWeeks == 3)
            check(njucm.times.size == 12 && njucm.times.first().first.toString() == "08:00")
            check(njucm.courses[0].weeksSet() == setOf(1,2) && njucm.courses[0].room == "仙林校区 A201")
            check(njucm.courses[1].weeksSet() == setOf(3))
            check(evaluate("JSON.stringify(window.__auditTrace)") == "[\"1\",\"2\",\"3\",\"all\"]")
            for (failure in listOf("window.__badWeek='2';", "window.__wrongTimes=true;", "window.__changeTerm=true;", "window.__stale=true;")) {
                fixture("ext_njucm_dom", "https://jwgl.njucm.edu.cn/student/for-std/course-table", "njucm.js", njucmHtml, failure+njucmSetup, expected=0)
                check(evaluate("document.querySelector('#weeks').value") == "all")
            }
            fixture("ext_njucm_dom", "https://jwgl.njucm.edu.cn/student/for-std/course-table", "njucm.js", njucmHtml, njucmSetup+"document.querySelector('#hasExperiment').checked=false;", expected=0)
            for (accept in listOf(true, false)) {
                fixture("ext_njucm_dom", "https://jwgl.njucm.edu.cn/student/for-std/course-table", "njucm.js", njucmHtml+"<table id=lessons><tr><td class=courseInfo><p>模拟未排课程</p></td></tr></table>", njucmSetup, expected=0, acceptAlerts=accept)
            }
            val njtech=fixture("ext_njtech_api", "https://jwgl.njtech.edu.cn/kbcx/index", "jiangsu.js", form, """
                window.fetch=async function(url,init){
                  if(new URL(url).origin!==location.origin||new URL(url).pathname!=='/kbcx/xskbcx_cxXsgrkb.html'||init.body.get('xnm')!=='2026')throw Error('Wrong request');
                  var row={kcmc:'模拟南工课程',xm:'甲',cdmc:'A201',xqj:3,jcs:'1-2,5',zcd:'1-8周(双)'};
                  return {ok:true,text:async()=>JSON.stringify({kbList:[row],sjkList:[{...row,kcmc:'模拟已排实践',jcs:'7',zcd:'第4周'}],jxhjkcList:[]})};
                };
            """, expected=3)
            check(njtech.courses.take(2).all { it.weeksSet()==setOf(2,4,6,8) })
            for (body in listOf("{kbList:[{kcmc:'不完整课程',zcd:'1周'}]}", "{kbList:[{kcmc:'正常课程',xm:'甲',xqj:1,jcs:'1-2',zcd:'1周'}],jxhjkcList:{}}",
                "{kbList:[{kcmc:'完整课程',xm:'甲',cdmc:'A101',xqj:1,jcs:'1-2',zcd:'1-8周'}],jxhjkcList:[{kcmc:'未排实践'}]}")) {
                fixture("ext_njtech_api", "https://jwgl.njtech.edu.cn/kbcx/index", "jiangsu.js", form, "window.fetch=async()=>({ok:true,text:async()=>JSON.stringify($body)});", expected=0)
            }
            val nuistSetup = """
                var dateReads=0;window.__nuistTrace=[];
                window.fetch=async function(url,init){
                  var path=new URL(url).pathname, data;
                  if(new URL(url).origin!==location.origin)throw Error('Wrong origin');
                  if(path.endsWith('/dqxnxq.do'))data={datas:{dqxnxq:{rows:[{DM:'2026-2027-2',MC:'模拟当前学期'}]}}};
                  else if(path.endsWith('/cxjcs.do')){
                    if(init.body.get('XN')!=='2026-2027'||init.body.get('XQ')!=='2')throw Error('Wrong term context');
                    data={datas:{cxjcs:{rows:[{XQKSRQ:++dateReads>1&&window.__dateChanged?'2026-09-14':'2026-09-07 00:00:00'}]}}};
                  }else{
                    if(!path.endsWith('/cxxszhxqkb.do')||init.body.get('XNXQDM')!=='2026-2027-2')throw Error('Wrong schedule request');
                    var w=Number(init.body.get('SKZC'));window.__nuistTrace.push(w);
                    if(window.__weekFailure&&w===2)throw Error('Synthetic missing week');
                    data={datas:{cxxszhxqkb:{rows:w===3?[]:[{KCM:'模拟南信大课程',SKJS:'甲',JASMC:w===2?'B102':'A101',SKXQ:3,KSJC:'3',JSJC:'4'}],totalSize:w===3?0:1}}};
                  }
                  return {ok:true,text:async()=>JSON.stringify(data)};
                };
            """
            val nuist=fixture("ext_nuist_weekly", "https://jwxt.nuist.edu.cn/jwapp/sys/wdkb/*default/index.do", "nuist.js", "", nuistSetup, expected=2, promptAnswer="3")
            check(nuist.firstMonday.toString()=="2026-09-07" && nuist.totalWeeks==3 && nuist.times.isEmpty())
            check(nuist.courses[0].weeksSet()==setOf(1) && nuist.courses[1].room=="B102" && nuist.courses[1].weeksSet()==setOf(2))
            check(evaluate("JSON.stringify(window.__nuistTrace)")=="[1,2,3]")
            for (failure in listOf("window.__weekFailure=true;", "window.__dateChanged=true;")) {
                fixture("ext_nuist_weekly", "https://jwxt.nuist.edu.cn/jwapp/sys/wdkb/*default/index.do", "nuist.js", "", failure+nuistSetup, expected=0, promptAnswer="3")
            }
            fixture("ext_nuist_weekly", "https://jwxt.nuist.edu.cn/jwapp/sys/wdkb/*default/index.do", "nuist.js", "", nuistSetup, expected=0, promptAnswer=null)
            val seuSetup = """
                var courseReads=0,calendarReads=0;
                window.fetch=async function(url,init){
                  var u=new URL(url), data;
                  if(u.origin!==location.origin)throw Error('Wrong origin');
                  if(u.pathname.endsWith('/xnxqcx.do'))data={datas:{xnxqcx:{rows:[{DM:'2026-2027-2',MC:'模拟秋季学期'}]}}};
                  else if(u.pathname.endsWith('/cxjcs.do')){
                    if(init.body.get('XN')!=='2026-2027'||init.body.get('XQ')!=='2')throw Error('Wrong calendar');
                    data={datas:{cxjcs:{rows:[{ZZC:window.__noWeeks?undefined:18,XQKSRQ:++calendarReads>1&&window.__dateChanged?'2026-09-28':'2026-09-21 00:00:00'}]}}};
                  }else{
                    if(!u.pathname.endsWith('/xskcb.do')||u.searchParams.get('XNXQDM')!=='2026-2027-2'||u.searchParams.has('SKZC'))throw Error('Partial term request');
                    courseReads++;
                    var row={KCM:'模拟东大实验',SKJS:'甲',JASMC:'A101',SKXQ:7,KSJC:'2',JSJC:'5',SKZC:'0000001111',JXBID:'C1',KBID:'B1',YPSJDD:'7-10周 星期日 2-5节,7-10周 星期日 7-10节'};
                    var rows=[row,{...row,KSJC:'7',JSJC:'10',JASMC:window.__roomChanged&&courseReads>1?'C103':'B102',KBID:'B2'}];
                    if(courseReads>1)rows.reverse();
                    data={datas:{xskcb:{rows:rows,totalSize:2}}};
                  }
                  return {ok:true,text:async()=>JSON.stringify(data)};
                };
            """
            val seuNew=fixture("ext_seu_wdkb", "https://ehall.seu.edu.cn/jwapp/sys/wdkb/*default/index.do", "seu.js", "", seuSetup, expected=2)
            check(seuNew.firstMonday.toString()=="2026-09-21" && seuNew.totalWeeks==18 && seuNew.times.isEmpty())
            check(seuNew.courses.all { it.weeksSet()==setOf(7,8,9,10) })
            check(seuNew.courses[0].startPeriod==2 && seuNew.courses[0].endPeriod==5 && seuNew.courses[1].startPeriod==7)
            for (failure in listOf("window.__roomChanged=true;", "window.__dateChanged=true;", "window.__noWeeks=true;")) {
                fixture("ext_seu_wdkb", "https://ehall.seu.edu.cn/jwapp/sys/wdkb/*default/index.do", "seu.js", "", failure+seuSetup, expected=0)
            }
            val nuaaSetup = """
                var formReads=0, tableReads=0;window.__nuaaExecuted=false;window.__nuaaRequests=[];
                function tableBody(){
                  var room=(window.__nuaaChanged && tableReads>1)?'C':'A';
                  var args=['','','','模拟南航 &lt;','','',room,'010101','','','','','',''];
                  var code='window.__nuaaExecuted=true;var unitCount=13;var table0={};var teachers=[{name:"甲"}];var activity=new TaskActivity('+args.map(JSON.stringify).join(',')+');';
                  code+='index=0*unitCount+'+(window.__nuaaLunch?4:0)+';index=0*unitCount+2;index=1*unitCount+6;';
                  var head=window.__nuaaOptional?'<th>课程代码</th><th>课程名称</th>':'<th>课程代码</th><th>课程名称</th><th>教师</th>';
                  var row='<td'+(window.__nuaaSpan?' colspan="2"':'')+'>C1</td><td>模拟南航 &amp;lt;</td>'+(window.__nuaaOptional?'':'<td>甲</td>');
                  var extra=window.__nuaaMissing?'<tr><td>C2</td><td>未知缺项</td><td>乙</td></tr>':'';
                  return '<html><body><script>'+code+'</'+'script><table><tr>'+head+'</tr><tr>'+row+'</tr>'+extra+'</table></body></html>';
                }
                window.fetch=async function(raw,init){
                  var url=new URL(raw);window.__nuaaRequests.push(url.pathname);
                  if(url.origin!==location.origin || init.credentials!=='same-origin' || init.redirect!=='error')throw Error('Wrong scope');
                  var text;
                  if(url.pathname==='/eams/courseTableForStd.action'){
                    formReads++;text='<script>addInput(form,"ids","123456");</'+'script><select id="semesterBar42Semester"></select>';
                  }else if(url.pathname==='/eams/dataQuery.action'){
                    if(init.body.get('tagId')!=='semesterBar42Semester')throw Error('Wrong calendar');
                    text='[{id:77,schoolYear:"2026-2027",name:"1"}]';
                  }else if(url.pathname==='/eams/courseTableForStd!courseTable.action'){
                    if(init.body.get('semester.id')!=='77' || init.body.get('ids')!=='123456' || init.body.get('startWeek')!=='' || init.body.get('setting.kind')!=='std')throw Error('Wrong semester scope');
                    tableReads++;text=tableBody();
                  }else throw Error('Unexpected path');
                  return {ok:true,url:url.href,headers:{get:(name)=>name.toLowerCase()==='content-type'?'text/html; charset=UTF-8':null},arrayBuffer:async()=>new TextEncoder().encode(text).buffer};
                };
            """
            val nuaaUrl="https://aao-eas.nuaa.edu.cn/eams/courseTableForStd.action"
            val nuaa=fixture("ext_nuaa_eams",nuaaUrl,"nuaa.js","",nuaaSetup,expected=3,promptAnswer="18")
            check(nuaa.totalWeeks==18 && nuaa.firstMonday==null && nuaa.times.isEmpty())
            check(nuaa.courses.all { it.title=="模拟南航 &lt;" && it.weeksSet()==setOf(1,3,5) })
            check(nuaa.courses.map { it.startPeriod }==listOf(1,3,5))
            check(evaluate("window.__nuaaExecuted")==false && evaluate("window.__nuaaRequests.length")==6)
            fixture("ext_nuaa_eams",nuaaUrl,"nuaa.js","","window.__nuaaOptional=true;"+nuaaSetup,expected=3,promptAnswer="18")
            for(failure in listOf("window.__nuaaChanged=true;","window.__nuaaMissing=true;","window.__nuaaSpan=true;","window.__nuaaLunch=true;")){
                fixture("ext_nuaa_eams",nuaaUrl,"nuaa.js","",failure+nuaaSetup,expected=0,promptAnswer="18")
            }
            fixture("ext_nuaa_eams",nuaaUrl,"nuaa.js","",nuaaSetup,expected=0,promptAnswer="4")
            fixture("ext_nuaa_eams",nuaaUrl,"nuaa.js","",nuaaSetup,expected=0,promptAnswer=null)
            val njustSetup = """
                var reads=0;window.__njustRequests=[];window.__njustExecuted=false;
                function njustPage(){
                  var name='模拟南理 &amp;lt;', room=(window.__njustChanged&&reads>2)?'C':'R1';
                  function block(r){return '<div class="kbcontent">'+name+'<br><font title="老师">甲</font><br><font title="周次(节次)">1-5周(单) [1,3节]</font><br><font title="教室">'+r+'</font></div>';}
                  var grid='<table id="kbtable"><tr><th>节次</th>'+['一','二','三','四','五','六','日'].map(x=>'<th>星期'+x+'</th>').join('')+'</tr><tr><td>1,3节</td><td>'+block(room)+'</td><td></td><td>'+block('R2')+'</td><td></td><td></td><td></td><td></td></tr></table>';
                  var meeting1=window.__njustContradiction?'R2':room, meeting2=window.__njustContradiction?'R1':'R2';
                  var detail='<table id="dataList"><tr>'+['序号','课程编号','课序号','课程名称','任课教师','上课时间','学分','上课地点','课程属性'].map(x=>'<th>'+x+'</th>').join('')+'</tr><tr>'+['1','C1','01',name,'甲','星期一(1,3节) '+meeting1+'<br>星期三(1,3节) '+meeting2,'2',room+',R2','必修'].map(x=>'<td>'+x+'</td>').join('')+'</tr></table>';
                  if(window.__njustMissing)detail=detail.replace('星期三(1,3节) '+meeting2,'星期五(1,3节) R2');
                  return '<select id="xnxq01id"><option value="2026-2027-1" selected>2026-2027学年第1学期</option></select><select id="zc"><option value="" selected>全部周次</option></select>'+grid+(window.__njustDuplicate?grid:'')+detail+'<script>window.__njustExecuted=true;</scr'+'ipt>';
                }
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__njustRequests.push(u.href);reads++;
                  if(u.origin!=='https://bkjw.njust.edu.cn'||u.pathname!=='/njlgdx/xskb/xskb_list.do'||init.credentials!=='same-origin'||init.redirect!=='error')throw Error('Wrong origin/protocol');
                  if(init.method==='POST'&&(u.searchParams.get('Ves632DSdyV')!=='NEW_XSD_PYGL'||init.body.get('xnxq01id')!=='2026-2027-1'||init.body.get('zc')!==''))throw Error('Wrong semester request');
                  var body=njustPage();return {ok:true,status:200,redirected:false,url:u.href,headers:{get:()=> 'text/html; charset=UTF-8'},arrayBuffer:async()=>new TextEncoder().encode(body).buffer};
                };
            """.trimIndent()
            val njustUrl="https://bkjw.njust.edu.cn/njlgdx/xskb/xskb_list.do"
            val njust=fixture("ext_njust_personal",njustUrl,"njust.js","",njustSetup,expected=4,promptAnswer="18")
            check(njust.totalWeeks==18 && njust.firstMonday==null && njust.times.isEmpty())
            check(njust.courses.all { it.title=="模拟南理 &lt;" && it.weeksSet()==setOf(1,3,5) })
            check(njust.courses.map { it.startPeriod }==listOf(1,3,1,3))
            check(evaluate("window.__njustExecuted")==false && evaluate("window.__njustRequests.length")==4)
            for(failure in listOf("window.__njustChanged=true;","window.__njustMissing=true;","window.__njustDuplicate=true;","window.__njustContradiction=true;")){
                fixture("ext_njust_personal",njustUrl,"njust.js","",failure+njustSetup,expected=0,promptAnswer="18")
            }
            fixture("ext_njust_personal",njustUrl,"njust.js","",njustSetup,expected=0,promptAnswer="4")
            fixture("ext_njust_personal",njustUrl,"njust.js","",njustSetup,expected=0,promptAnswer=null)
            fixture("ext_njust_personal","https://bkjw.njust.edu.cn/other","njust.js","",njustSetup,expected=0,promptAnswer="18")
            check(evaluate("window.__njustRequests.length")==0)
            // Self-authored synthetic pages; never use an upstream student's timetable fixture.
            val sudaSetup = """
                var reads=0,token='initial';window.__sudaRequests=[];window.__sudaExecuted=false;
                function sudaPage(year='2026-2027',semester='2'){
                  var otherYear=year==='2026-2027'?'2025-2026':'2026-2027',otherTerm=semester==='2'?'1':'2';
                  function option(value,selected){return '<option value="'+value+'"'+(selected?' selected':'')+'>'+value+'</option>';}
                  var fields='<input type="hidden" name="__VIEWSTATE" value="'+token+'"><input type="hidden" name="__EVENTVALIDATION" value="validation-'+token+'">';
                  var selects='<select id="xnd" name="xnd">'+option(year,true)+option(otherYear,false)+'</select><select id="xqd" name="xqd">'+option(semester,true)+option(otherTerm,false)+'</select>';
                  var anchor='<a onclick="go(\'/js_jxrl_szdx.aspx?xkkh=('+year+'-'+semester+')-SYNTH-01\')">模拟苏大 &amp;lt;<br>{第1-5周|2节/周}<br>合成甲<br>合成A</a>';
                  var table='<table id="Table1"><tr><td colspan="2">时间</td>'+['一','二','三','四','五','六','日'].map(x=>'<td>星期'+x+'</td>').join('')+'</tr><tr><td>上午</td><td>第1-2节</td><td>'+anchor+'</td>'+'<td></td>'.repeat(6)+'</tr></table>';
                  var time=window.__sudaMissing?'':'周二第3,5节',room=window.__sudaChanged&&reads>1?'变化实验室':'合成实验室';
                  var practice='<table id="DataGrid1"><tr class="datelisthead">'+['课程名称','教师','学分','起止周','上课时间','上课地点'].map(x=>'<td>'+x+'</td>').join('')+'</tr><tr>'+['合成实践','合成乙','1','第2-8周|双周',time,room].map(x=>'<td>'+x+'</td>').join('')+'</tr></table>';
                  var unscheduled='<table id="DBGridYxkc"><tr class="datelisthead"><td>学年</td></tr>'+(window.__sudaUnscheduled?'<tr><td>未排课</td></tr>':'')+'</table><table id="Datagrid2"><tr class="datelisthead"><td>学年</td></tr></table>';
                  return '<html><body><form method="post">'+fields+selects+table+practice+unscheduled+(window.__sudaDuplicate?'<input type="hidden" name="__VIEWSTATE" value="duplicate">':'')+'</form><script>window.__sudaExecuted=true;</scr'+'ipt></body></html>';
                }
                window.fetch=async function(raw,init){
                  var u=new URL(raw);reads++;window.__sudaRequests.push({method:init.method,url:u.href,time:Date.now()});
                  if(u.origin!=='http://xk.suda.edu.cn'||u.pathname!=='/xskbcx.aspx'||u.searchParams.get('xh')!=='SYNTH001'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong scope');
                  var year='2026-2027',semester='2';
                  if(init.method==='POST'){
                    if(init.body.get('__VIEWSTATE')!==token||init.body.get('__EVENTVALIDATION')!=='validation-'+token||!['xnd','xqd'].includes(init.body.get('__EVENTTARGET')))throw Error('Stale WebForms state');
                    if(!window.__sudaIgnored){year=init.body.get('xnd');semester=init.body.get('xqd');}
                  }
                  token='token-'+reads;
                  var body=window.__sudaLogin&&reads>1?'<input type="password">':sudaPage(year,semester);
                  return {ok:true,status:200,redirected:false,url:window.__sudaIdentity?u.href.replace('SYNTH001','OTHER'):u.href,headers:{get:()=> 'text/html; charset=UTF-8'},arrayBuffer:async()=>new TextEncoder().encode(body).buffer};
                };
            """.trimIndent()
            // Visible document starts with the initial state, independently of detached server responses.
            val sudaHtml = """
                <form method="post"><input type="hidden" name="__VIEWSTATE" value="initial"><input type="hidden" name="__EVENTVALIDATION" value="validation-initial">
                <select id="xnd" name="xnd"><option value="2026-2027" selected>2026-2027</option><option value="2025-2026">2025-2026</option></select>
                <select id="xqd" name="xqd"><option value="2" selected>2</option><option value="1">1</option></select></form>
            """.trimIndent()
            val sudaUrl="http://xk.suda.edu.cn/xskbcx.aspx?xh=SYNTH001&xm=Synth&gnmkdm=N121603"
            val suda=fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,sudaSetup,expected=3,promptAnswer="18")
            check(suda.totalWeeks==18 && suda.firstMonday==null && suda.times.isEmpty())
            check(suda.courses[0].title=="模拟苏大 &lt;" && suda.courses[0].weeksSet()==setOf(1,2,3,4,5))
            check(suda.courses[0].startPeriod==1 && suda.courses[0].endPeriod==2)
            check(suda.courses.drop(1).all {it.title=="合成实践" && it.weeksSet()==setOf(2,4,6,8)})
            check(suda.courses.drop(1).map {it.startPeriod}==listOf(3,5))
            check(evaluate("window.__sudaExecuted")==false && evaluate("window.__sudaRequests.length")==2)
            check(evaluate("window.__sudaRequests[1].time-window.__sudaRequests[0].time>=3000")==true)
            val sudaOther=fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,sudaSetup,expected=3,promptAnswer="18",selectionAnswers=listOf(1,1),timeoutMillis=35_000)
            check(sudaOther.courses.size==3 && evaluate("window.__sudaRequests.length")==6 && evaluate("window.__sudaExecuted")==false)
            for(failure in listOf("window.__sudaChanged=true;","window.__sudaMissing=true;","window.__sudaUnscheduled=true;","window.__sudaIdentity=true;","window.__sudaLogin=true;")){
                fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,failure+sudaSetup,expected=0,promptAnswer="18")
            }
            for(failure in listOf("window.__sudaDuplicate=true;","window.__sudaIgnored=true;")){
                fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,failure+sudaSetup,expected=0,promptAnswer="18",selectionAnswers=listOf(1,1))
            }
            fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,sudaSetup,expected=0,promptAnswer="4")
            fixture("ext_suda_personal",sudaUrl,"suda-personal.js",sudaHtml,sudaSetup,expected=0,promptAnswer=null)
            fixture("ext_suda_personal","http://xk.suda.edu.cn/other","suda-personal.js",sudaHtml,sudaSetup,expected=0,promptAnswer="18")
            check(evaluate("window.__sudaRequests.length")==0)
            // Own synthetic JUST page, including group-header rowspans and explicit clock labels.
            val justForm="<form id=ajaxForm><select id=xnm name=xnm><option value=2025 selected>2025-2026</option></select><select id=xqm name=xqm><option value=3 selected>1</option></select></form>"
            fun justBlock(name: String, pattern: String, code: String) =
                "<div class=timetable_con><div class=title><font color=blue>$name</font></div><p>$pattern</p><p>合成实验室</p><p>合成甲</p><p>(2025-2026-1)-$code-01</p></div>"
            val justRows=(1..11).joinToString("") { period ->
                "<tr>"+(when(period){1->"<td rowspan=4>上午</td>";5->"<td rowspan=4>下午</td>";9->"<td rowspan=3>晚上</td>";else->""})+
                    "<td>"+(if(period==1)"第1节(08:00-08:45)" else period.toString())+"</td>"+(1..7).joinToString("") { day ->
                    "<td id=$day-$period>"+(if(day==1&&period==1)justBlock("模拟江科 &amp;lt;","(1,3节)2-6周(双)","SYNTH01") else if(day==7&&period==9)justBlock("合成夜课","(9-10节)1-5周(单)","SYNTH02") else "")+"</td>"}+"</tr>"
            }
            val justHtml=justForm+"<table id=kbgrid_table_0><tr><td colspan=9><h6>2025-2026学年第1学期</h6></td></tr><tr>"+
                listOf("时间段","节次","星期一","星期二","星期三","星期四","星期五","星期六","星期日").joinToString(""){"<td>$it</td>"}+
                "</tr>"+justRows+"<tr><td colspan=9></td></tr></table>"
            val justUrl="https://client.v.just.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
            val just=fixture("ext_just_personal",justUrl,"just-personal.js",justHtml,expected=3,promptAnswer="18")
            check(just.totalWeeks==18 && just.firstMonday==null && just.times.isEmpty())
            check(just.courses[0].title=="模拟江科 &lt;" && just.courses[0].weeksSet()==setOf(2,4,6))
            check(just.courses.map {it.startPeriod}==listOf(1,3,9) && just.courses[2].endPeriod==10)
            fixture("ext_just_personal",justUrl.replace("client.v.just.edu.cn","jwxt.just.edu.cn"),"just-personal.js",justHtml,expected=3,promptAnswer="18")
            for(bad in listOf(justHtml.replace("color=blue","color=green"),justHtml.replace("2-6周(双)",""),
                justHtml.replace("2025-2026学年第1学期","2025-2026学年第2学期"),justHtml.replace("合成实验室</p>","</p>"),
                justHtml.replace("<td colspan=9></td></tr></table>","<td colspan=9>未排课实践</td></tr></table>"),
                justHtml+"<input name=week value=3>")){
                fixture("ext_just_personal",justUrl,"just-personal.js",bad,expected=0,promptAnswer="18")
            }
            fixture("ext_just_personal",justUrl,"just-personal.js",justHtml,expected=0,promptAnswer="4")
            fixture("ext_just_personal",justUrl,"just-personal.js",justHtml,expected=0,promptAnswer=null)
            fixture("ext_just_personal","https://client.v.just.edu.cn/other","just-personal.js",justHtml,expected=0,promptAnswer="18")
            fixture("ext_just_personal",justUrl,"just-personal.js",justHtml,"""
                var previousDispatch=window._kagendaWarehouse.dispatch;
                window._kagendaWarehouse.dispatch=function(raw){if(JSON.parse(raw).action==='prompt')document.querySelector('.timetable_con p').textContent='(1,3节)1-8周';previousDispatch(raw);};
            """,expected=0,promptAnswer="18")
            val wxxyUrl="https://jwgl.cwxu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
            val wxxySetup="""
                var wxReads=0;window.__wxRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);wxReads++;window.__wxRequests.push(u.href);
                  if(u.origin!=='https://jwgl.cwxu.edu.cn'||u.pathname!=='/jwglxt/kbcx/xskbcx_cxXsKb.html'||u.searchParams.get('gnmkdm')!=='N2151'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong scope');
                  if(init.body.get('xnm')!=='2026'||init.body.get('xqm')!=='3'||Array.from(init.body.keys()).length!==2)throw Error('Wrong term request');
                  var item={jxb_id:'SYNTH-01',kcmc:'合成锡院',xm:'合成甲',cdmc:window.__wxNoRoom?'':window.__wxChanged&&wxReads>1?'变化室':'合成室',xqj:'2',jcs:'3,5',oldzc:window.__wxMask||'5'};
                  var data={kbList:[item],sjkList:window.__wxExtra?[{}]:[],xsxx:{syntheticId:window.__wxIdentity&&wxReads>1?'OTHER':'SYNTH'}};
                  return {ok:!window.__wxLogin,status:window.__wxLogin?901:200,redirected:false,url:u.href,text:async()=>window.__wxHtml?'<html>登录</html>':JSON.stringify(data)};
                };
            """.trimIndent()
            val wxxy=fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form,wxxySetup,expected=2,promptAnswer="18")
            check(wxxy.totalWeeks==18 && wxxy.firstMonday==null && wxxy.times.isEmpty())
            check(wxxy.courses.all {it.title=="合成锡院" && it.weeksSet()==setOf(1,3)})
            check(wxxy.courses.map {it.startPeriod}==listOf(3,5) && evaluate("window.__wxRequests.length")==2)
            val wxForty=fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form,"window.__wxMask='549755813889';"+wxxySetup,expected=2,promptAnswer="40")
            check(wxForty.courses.all {it.weeksSet()==setOf(1,40)})
            for(failure in listOf("window.__wxNoRoom=true;","window.__wxMask='0';","window.__wxExtra=true;","window.__wxLogin=true;","window.__wxHtml=true;","window.__wxChanged=true;","window.__wxIdentity=true;")){
                fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form,failure+wxxySetup,expected=0,promptAnswer="18")
            }
            fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form,wxxySetup,expected=0,promptAnswer="2")
            fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form,wxxySetup,expected=0,promptAnswer=null)
            fixture("ext_wxxy_api",wxxyUrl,"wxxy.js",form+"<input name=week value=3>",wxxySetup,expected=0,promptAnswer="18")
            fixture("ext_wxxy_api",wxxyUrl.replace("jwgl.cwxu.edu.cn","other.invalid"),"wxxy.js",form,wxxySetup,expected=0,promptAnswer="18")
            val tongjiUrl="https://1.tongji.edu.cn/student/index"
            val tongjiSetup="""
                var tjReads=0;window.__tjRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__tjRequests.push(u.href);
                  if(u.origin!=='https://1.tongji.edu.cn'||init.credentials!=='same-origin'||init.redirect!=='error'||!u.searchParams.has('_t'))throw Error('Wrong Tongji scope');
                  var root={code:0,success:true};
                  if(u.pathname.endsWith('/list'))root.data=[{id:'SYNTH-TERM',fullName:'合成学期'}];
                  else if(u.pathname.endsWith('/detail')){
                    if(u.searchParams.get('id')!=='SYNTH-TERM')throw Error('Wrong calendar');
                    root.data={id:'SYNTH-TERM',beginDay:Date.UTC(2026,8,6,16),teachingWeekStart:1,
                      noWeekendWorkTimes:[{classNode:1,beginTime:'08:00:00',endTime:'08:45:00'},{classNode:2,beginTime:'08:55:00',endTime:'09:40:00'}]};
                    if(!window.__tjPrompt)root.data.teachingWeekEnd=18;
                    if(window.__tjBadDate)root.data.beginDay+=86400000;
                    if(window.__tjBadSlot)root.data.noWeekendWorkTimes[1].classNode=4;
                  }else if(u.pathname.endsWith('/findStudentTimetab')){
                    if(u.searchParams.get('calendarId')!=='SYNTH-TERM')throw Error('Wrong course term');
                    tjReads++;
                    root.data=[{courseName:window.__tjChanged&&tjReads>1?'变化课程':'合成同济',teachingClassId:'SYNTH-CLASS',
                      timeTableList:[{teacherName:'合成甲',roomIdI18n:window.__tjNoRoom?'':'合成室',dayOfWeek:2,timeStart:1,timeEnd:2,weeks:window.__tjBadWeek?[0]:[1,3,5]}]}];
                    if(window.__tjUnplanned)root.data.push({courseName:'未排合成',teachingClassId:'SYNTH-EMPTY',timeTableList:[]});
                    if(window.__tjPaged)root.hasMore=true;
                  }else throw Error('Unknown Tongji path');
                  return {ok:!window.__tjLogin,text:async()=>window.__tjHtml?'<html>登录</html>':JSON.stringify(root)};
                };
            """.trimIndent()
            val tj=fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","",tongjiSetup)
            check(tj.totalWeeks==18 && tj.firstMonday.toString()=="2026-09-07" && tj.times.size==2)
            check(tj.courses.single().title=="合成同济" && tj.courses.single().weeksSet()==setOf(1,3,5))
            check(evaluate("window.__tjRequests.length")==6)
            fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.__tjPrompt=true;"+tongjiSetup,promptAnswer="18")
            fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.__tjUnplanned=true;"+tongjiSetup,expected=0)
            for(flag in listOf("__tjNoRoom","__tjBadWeek","__tjPaged","__tjLogin","__tjHtml","__tjChanged","__tjBadDate","__tjBadSlot")){
                fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.$flag=true;"+tongjiSetup,expected=0)
            }
            fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.__tjUnplanned=true;"+tongjiSetup,expected=0,acceptAlerts=false)
            fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.__tjPrompt=true;"+tongjiSetup,expected=0,promptAnswer=null)
            fixture("ext_tongji_personal",tongjiUrl.replace("1.tongji.edu.cn","wrong.invalid"),"tongji-personal.js","",tongjiSetup,expected=0)
            fixture("ext_tongji_personal",tongjiUrl,"tongji-personal.js","","window.__tjUnplanned=true;"+tongjiSetup+"""
                var tjDispatch=window._kagendaWarehouse.dispatch;
                window._kagendaWarehouse.dispatch=function(raw){var m=JSON.parse(raw);if(m.action==='alert')window.__tjChanged=true;tjDispatch(raw);};
            """,expected=0)
            val shuUrl="https://jwxt.shu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
            val shuForm="""<select id=xnm><option value=2026 selected>2026-2027学年</option></select><select id=xqm><option value=32 selected>夏季学期</option></select>"""
            val shuSetup="""
                var shuReads=0;window.__shuRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);shuReads++;window.__shuRequests.push(u.href);
                  if(u.origin!=='https://jwxt.shu.edu.cn'||u.pathname!=='/jwglxt/kbcx/xskbcx_cxXsgrkb.html'||u.searchParams.get('gnmkdm')!=='N2151'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong SHU scope');
                  if(init.body.get('xnm')!=='2026'||init.body.get('xqm')!=='32'||init.body.get('kzlx')!=='ck'||Array.from(init.body.keys()).length!==6)throw Error('Wrong SHU term');
                  var item={jxb_id:'SYNTH-01',kcmc:'合成上大',xm:'合成甲',cdmc:window.__shuNoRoom?'':window.__shuChanged&&shuReads>1?'变化室':'合成室',xqumc:'宝山校区',xqj:'2',jcs:'3,5',zcd:window.__shuBadWeek?'0周':'第1-5周(单)'};
                  var data={kbList:[item],sjkList:window.__shuExtra?[{}]:[],xsxx:{syntheticId:window.__shuIdentity&&shuReads>1?'OTHER':'SYNTH'}};
                  if(window.__shuPaged)data.hasMore=true;
                  if(window.__shuConflict)data.kbList.push(Object.assign({},item,{xm:'矛盾教师'}));
                  return {ok:!window.__shuLogin,status:window.__shuLogin?901:200,redirected:false,url:u.href,text:async()=>window.__shuHtml?'<html>登录</html>':JSON.stringify(data)};
                };
            """.trimIndent()
            val shu=fixture("ext_shu_personal",shuUrl,"shu-personal.js",shuForm,shuSetup,expected=2,promptAnswer="18")
            check(shu.totalWeeks==18 && shu.firstMonday==null && shu.times.isEmpty())
            check(shu.courses.all { it.title=="合成上大" && it.weeksSet()==setOf(1,3,5) })
            check(shu.courses.map {it.startPeriod}==listOf(3,5) && evaluate("window.__shuRequests.length")==2)
            for(flag in listOf("__shuNoRoom","__shuBadWeek","__shuExtra","__shuPaged","__shuLogin","__shuHtml","__shuChanged","__shuIdentity","__shuConflict")){
                fixture("ext_shu_personal",shuUrl,"shu-personal.js",shuForm,"window.$flag=true;"+shuSetup,expected=0,promptAnswer="18")
            }
            fixture("ext_shu_personal",shuUrl,"shu-personal.js",shuForm,shuSetup,expected=0,promptAnswer=null)
            fixture("ext_shu_personal",shuUrl,"shu-personal.js",shuForm,shuSetup,expected=0,promptAnswer="2")
            fixture("ext_shu_personal",shuUrl,"shu-personal.js",shuForm,shuSetup+"""
                var shuDispatch=window._kagendaWarehouse.dispatch;
                window._kagendaWarehouse.dispatch=function(raw){var m=JSON.parse(raw);if(m.action==='prompt')document.querySelector('#xqm').options[0].value='3';shuDispatch(raw);};
            """,expected=0,promptAnswer="18")
            val sjtuUrl="https://i.sjtu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html"
            val sjtuForm="""<select id=xnm><option value=2026 selected>2026-2027学年</option></select><select id=xqm><option value=3 selected>第1学期</option></select>"""
            val sjtuSetup="""
                var sjtuReads=0;
                window.fetch=async function(raw,init){
                  var u=new URL(raw);sjtuReads++;
                  if(u.origin!=='https://i.sjtu.edu.cn'||u.pathname!=='/kbcx/xskbcx_cxXsKb.html'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong SJTU scope');
                  if(init.body.get('xnm')!=='2026'||init.body.get('xqm')!=='3'||Array.from(init.body.keys()).length!==2)throw Error('Wrong SJTU term');
                  var item={kch_id:'SYNTH-COURSE',jxbmc:'SYNTH-CLASS',kcmc:'合成交大',xm:'合成甲',cdmc:window.__sjtuMissing?'':window.__sjtuChanged&&sjtuReads>1?'变化室':'合成室',xqmc:'闵行校区',xqj:'2',jcs:'3,5',zcd:'第1-5周(单)',xkbz:'合成备注'};
                  var data={kbList:[item],sjkList:window.__sjtuExtra?[{}]:[],xsxx:{syntheticId:window.__sjtuIdentity&&sjtuReads>1?'OTHER':'SYNTH'}};
                  if(window.__sjtuPaged)data.hasMore=true;
                  return {ok:!window.__sjtuLogin,status:window.__sjtuLogin?901:200,redirected:false,url:u.href,text:async()=>window.__sjtuHtml?'<html>登录</html>':JSON.stringify(data)};
                };
            """.trimIndent()
            val sjtu=fixture("ext_sjtu_ug",sjtuUrl,"sjtu-undergraduate.js",sjtuForm,sjtuSetup,expected=2,promptAnswer="18")
            check(sjtu.courses.all {it.title=="合成交大" && it.room=="闵行校区 合成室" && it.extraInfo.contains("SYNTH-CLASS") && it.extraInfo.contains("合成备注")})
            check(sjtu.totalWeeks==18 && sjtu.firstMonday==null && sjtu.times.isEmpty())
            for(flag in listOf("__sjtuMissing","__sjtuChanged","__sjtuExtra","__sjtuIdentity","__sjtuPaged","__sjtuLogin","__sjtuHtml")){
                fixture("ext_sjtu_ug",sjtuUrl,"sjtu-undergraduate.js",sjtuForm,"window.$flag=true;"+sjtuSetup,expected=0,promptAnswer="18")
            }
            fixture("ext_sjtu_ug",sjtuUrl,"sjtu-undergraduate.js",sjtuForm,sjtuSetup,expected=0,promptAnswer=null)
            val ecnuUrl="https://byyt.ecnu.edu.cn/student/for-std/course-table"
            val ecnuCard="""<div class="tdHtml" lessonid="SYNTH-L"><div class="course-name">合成华师</div><div>教学班代码：SYNTH-C1</div><div>(1-5周) (1-2节) 闵行 A101 合成甲</div><div class="course-name">合成续课</div><div>教学班代码：SYNTH-C2</div><div>(6-8周) (1-2节) 普陀 A102 合成乙</div></div>"""
            val ecnuRows=(1..4).joinToString("") {period ->
                val start=8*60+(period-1)*55;val end=start+45
                "<tr class='$period'><td class='dayPartUnit'>${start/60}:${(start%60).toString().padStart(2,'0')} ~ ${end/60}:${(end%60).toString().padStart(2,'0')}</td>"+
                    (1..7).joinToString(""){day -> "<td class='td-content $day'>${if(period==1 && day==1)ecnuCard else ""}</td>"}+"</tr>"
            }
            val ecnuHtml="""<select id="allSemesters"><option value="SYNTH-TERM" selected>2026-2027学年第一学期</option></select><div id="startDate">2026-09-07</div><table class="courseTable"><tbody><tr><th>节次</th><th>星期一</th></tr>$ecnuRows</tbody></table>"""
            val ecnu=fixture("ext_ecnu_dom",ecnuUrl,"ecnu-personal.js",ecnuHtml,expected=2,promptAnswer="18")
            check(ecnu.firstMonday.toString()=="2026-09-07" && ecnu.times.size==4 && ecnu.totalWeeks==18)
            check(ecnu.courses.map {it.room}==listOf("闵行 A101","普陀 A102"))
            check(ecnu.courses.all {it.extraInfo.contains("教学班代码") && it.extraInfo.contains("2026-2027")})
            for(bad in listOf(ecnuHtml.replace("2026-09-07","2026-09-08"),ecnuHtml.replace("SYNTH-C2","SYNTH-C1"),ecnuHtml.replace("(1-2节)","(1-8节)"),ecnuHtml+"<input type=checkbox>",ecnuHtml+"<div class=pagination>下一页</div>",ecnuHtml.replace("lessonid=","unknown="))){
                fixture("ext_ecnu_dom",ecnuUrl,"ecnu-personal.js",bad,expected=0,promptAnswer="18")
            }
            fixture("ext_ecnu_dom",ecnuUrl,"ecnu-personal.js",ecnuHtml,expected=0,promptAnswer=null)
            fixture("ext_ecnu_dom",ecnuUrl,"ecnu-personal.js",ecnuHtml,expected=0,promptAnswer="4")
            fixture("ext_ecnu_dom",ecnuUrl,"ecnu-personal.js",ecnuHtml,"""
                var ecDispatch=window._kagendaWarehouse.dispatch;
                window._kagendaWarehouse.dispatch=function(raw){var m=JSON.parse(raw);if(m.action==='prompt')document.querySelector('#startDate').textContent='2026-09-14';ecDispatch(raw);};
            """,expected=0,promptAnswer="18")
            // Own synthetic HFUT fixture; no upstream samples, credentials or network.
            val hfutData = context.assets.open("hfut-synthetic.json").bufferedReader().use { it.readText() }
            val hfutHtml = "<script>var studentId = 91;window.hfutPage={bizTypeId:23,semesterId:101};</script>"
            val hfutUrl = "http://jxglstu.hfut.edu.cn/eams5-student/for-std/course-table/info/91"
            val hfutSetup = """
                window.__hfutData=$hfutData;window.__hfutRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__hfutRequests.push({url:raw,method:init.method});
                  if(u.origin!==location.origin||init.credentials!=='same-origin'||init.cache!=='no-store'||init.redirect!=='error')throw Error('Wrong scope');
                  var d=JSON.parse(JSON.stringify(window.__hfutData));
                  if(window.__hfutFault==='teacher')d.datumData.result.scheduleList[0].personName='';
                  if(window.__hfutFault==='room')d.datumData.result.scheduleList[0].room=null;
                  if(window.__hfutFault==='periods')d.datumData.result.scheduleList[0].periods=1;
                  if(window.__hfutFault==='branch')d.datumData.result.otherCourses=[{}];
                  if(window.__hfutFault==='change'&&window.__hfutRequests.length>3)d.datumData.result.scheduleList[0].personName='变化甲';
                  var response;
                  if(u.pathname.endsWith('/get-data')){
                    if(init.method!=='GET'||u.searchParams.get('dataId')!=='91'||u.searchParams.get('semesterId')!=='101'||u.searchParams.get('bizTypeId')!=='23')throw Error('Wrong term query');
                    response=d.termData;
                  }else{
                    if(init.method!=='POST')throw Error('Wrong method');var b=JSON.parse(init.body);
                    if(u.pathname.endsWith('/datum')){if(b.studentId!==91||b.weekIndex!==''||JSON.stringify(b.lessonIds)!=='[11,12]')throw Error('Wrong datum body');response=d.datumData;}
                    else if(u.pathname.endsWith('/timetable-layout')){if(b.timeTableLayoutId!==7)throw Error('Wrong layout');response=d.layoutData;}
                    else throw Error('Unexpected endpoint');
                  }
                  return {ok:true,status:200,redirected:false,url:u.href,text:async()=>JSON.stringify(response)};
                };
            """.trimIndent()
            val hfut = fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,hfutSetup,expected=2,promptAnswers=listOf("2026-08-31","18"))
            check(hfut.firstMonday.toString()=="2026-08-31" && hfut.totalWeeks==18 && hfut.times.size==3)
            check(hfut.courses.map { it.teacher }.toSet()==setOf("合成甲","合成乙"))
            check(hfut.courses.first { it.title=="合成课程11" }.weeksSet()==setOf(1,3))
            check(hfut.courses.first { it.title=="合成课程11" }.extraInfo.contains("CLASS-11"))
            check(evaluate("window.__hfutRequests.length")==6)
            for (fault in listOf("teacher","room","periods","branch","change")) {
                fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,"window.__hfutFault='$fault';"+hfutSetup,expected=0,promptAnswers=listOf("2026-08-31","18"))
            }
            fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,hfutSetup,expected=0,promptAnswers=listOf(null))
            fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,hfutSetup,expected=0,promptAnswers=listOf("2026-08-31",null))
            fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,hfutSetup,expected=0,promptAnswers=listOf("2026-09-01","18"))
            fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml+"<input name=week value=3>",hfutSetup,expected=0,promptAnswers=listOf("2026-08-31","18"))
            fixture("ext_hfut_personal",hfutUrl,"hfut-personal.js",hfutHtml,hfutSetup+"""
                var hfutDispatch=window._kagendaWarehouse.dispatch;
                window._kagendaWarehouse.dispatch=function(raw){if(JSON.parse(raw).action==='prompt')document.querySelector('script').textContent='var studentId = 92;var info={bizTypeId:23,semesterId:101};';hfutDispatch(raw);};
            """,expected=0,promptAnswers=listOf("2026-08-31","18"))
            // Original Wuhu mobile protocol, own synthetic responses only.
            val wuhuData=context.assets.open("wuhu-synthetic.json").bufferedReader().use { it.readText() }
            val wuhuUrl="https://ehall.uwh.edu.cn/jwmobile/"
            val wuhuSetup="""
                window.__wuhuData=$wuhuData;window.__wuhuRequests=[];window.__wuhuReads=0;
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__wuhuRequests.push(u.pathname);
                  if(u.origin!==location.origin||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong Wuhu scope');
                  var d=JSON.parse(JSON.stringify(window.__wuhuData)),r;
                  if(window.__wuhuFault==='teacher')d.details[0].response.data.teacherInfo[1].xm='';
                  if(window.__wuhuFault==='room')d.weeks[0].response.data.theorySchedule[0].jasmc='';
                  if(window.__wuhuFault==='clock')d.slotsResponse.datas.jc.rows[0].KSSJ='25:00';
                  if(window.__wuhuFault==='branch')d.weeks[1].response.data.practiceSchedule=[{kcm:'合成实践'}];
                  if(window.__wuhuFault==='total')d.slotsResponse.datas.jc.totalSize=99;
                  if(u.pathname.endsWith('/school/time')){if(init.method!=='GET')throw Error('Wrong calendar method');window.__wuhuReads++;r=d.schoolTime;}
                  else if(u.pathname.endsWith('/jc.do')){if(init.method!=='POST'||init.body)throw Error('Wrong slot method');r=d.slotsResponse;}
                  else if(u.pathname.endsWith('/querySchedule')){
                    var b=JSON.parse(init.body);if(b.xnxqdm!==d.schoolTime.xnxqdm)throw Error('Wrong semester');
                    r=d.weeks.find(x=>x.week===b.skzc).response;
                    if(window.__wuhuFault==='weekfail'&&b.skzc===2)r.code=1;
                  }else if(u.pathname.endsWith('/queryScheduleDetail')){
                    var b=JSON.parse(init.body);if(b.wid!=='')throw Error('Wrong detail body');
                    r=d.details.find(x=>JSON.stringify(x.identity)===JSON.stringify([b.jxbid,b.kbid,b.jxblx])).response;
                    if(window.__wuhuFault==='change'&&window.__wuhuReads>1)r.data.teacherInfo[0].xm='变化甲';
                  }else throw Error('Unexpected Wuhu path');
                  return {ok:true,status:200,redirected:false,url:u.href,text:async()=>JSON.stringify(r)};
                };
            """.trimIndent()
            val wuhu=fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","",wuhuSetup,expected=3,promptAnswer="2026-08-31")
            check(wuhu.firstMonday.toString()=="2026-08-31" && wuhu.totalWeeks==3 && wuhu.times.size==4)
            check(wuhu.courses.count {it.teacher=="合成甲、合成乙"}==2)
            check(wuhu.courses.single {it.room=="合成楼合成A101"}.weeksSet()==setOf(1,3))
            check(wuhu.courses.all {it.extraInfo.contains("2026-2027-1")})
            check(evaluate("window.__wuhuRequests.length")==14)
            for(fault in listOf("teacher","room","clock","branch","change","total","weekfail")){
                fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","","window.__wuhuFault='$fault';"+wuhuSetup,expected=0,promptAnswer="2026-08-31")
            }
            fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","",wuhuSetup,expected=0,promptAnswer=null)
            fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","",wuhuSetup,expected=0,promptAnswer="2026-09-01")
            fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","<input name=week value=1>",wuhuSetup,expected=0,promptAnswer="2026-08-31")
            fixture("ext_wuhu_personal",wuhuUrl,"wuhu-personal.js","<input type=password>",wuhuSetup,expected=0,promptAnswer="2026-08-31")
            // Jiangxia: production asset, browser DOMParser, request scope, native
            // validators and payload. Everything below is own synthetic data.
            val jiangxiaSchool=SchoolCatalog.load(targetContext, emptyList()).single { it.name=="福建江夏学院" }
            check(jiangxiaSchool.supported && "zf_new" in SchoolCatalog.knownSystemTypes(jiangxiaSchool))
            check(SchoolCatalog.adapters(jiangxiaSchool).any {it.id=="ext_fjjxu_personal" && it.licenseAsset=="fjjxu-personal-bundle-MIT.txt"})
            val jiangxiaUrl="https://jwxt.fjjxu.edu.cn/jwglxt/xtgl/index_initMenu.html"
            val jiangxiaSetup="""
                window.__jxCalendar=0;window.__jxCourses=0;window.__jxRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__jxRequests.push(u.pathname+u.search);
                  if(u.origin!=='https://jwxt.fjjxu.edu.cn'||init.method!=='GET'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store')throw Error('Wrong Jiangxia request');
                  var fault=window.__jxFault,text;
                  if(u.pathname==='/jwglxt/xtgl/index_cxAreaFive.html'){
                    window.__jxCalendar++;
                    if(u.searchParams.get('gnmkdm')!=='index'||u.searchParams.get('localeKey')!=='zh_CN')throw Error('Wrong calendar request');
                    var year=fault==='term-change'&&window.__jxCalendar>1?'2027-2028':'2026-2027';
                    var start=window.__jxNonMonday?'2026-09-08':'2026-09-07';
                    text=fault==='calendar-login'?'<input type=password>':fault==='no-calendar'?'校历未加载':'<p>'+year+'学年'+(fault==='unsupported-term'?'4':window.__jxTerm||'1')+'学期('+start+'至2027-01-17)</p>';
                  }else if(u.pathname==='/jwglxt/kbcx/xskbcx_cxXsKb.html'){
                    window.__jxCourses++;
                    var termCode=window.__jxTerm==='3'?'16':window.__jxTerm==='2'?'12':'3';
                    if(u.searchParams.get('xnm')!=='2026'||u.searchParams.get('xqm')!==termCode||u.searchParams.get('gnmkdm')!=='N2151')throw Error('Wrong Jiangxia semester');
                    if(fault==='second-failure'&&window.__jxCourses>1)throw Error('Synthetic second request failure');
                    var row={kcmc:'合成江夏算法',xm:'合成甲、合成乙',cdmc:'A101',xqmc:'合成校区',xqjmc:'星期二',xqj:'2',jcs:'1-2,5',zcd:'1-8周(单),12周',kch_id:'C1',jxb_id:'J1',xnm:'2026',xqm:'3'};
                    row.jxbmc='合成教学班';row.jxbzc='合成班级组成';row.xqh_id='CAMPUS-2';row.xkbz='合成备注';
                    row.xqm=termCode;
                    if(window.__jxJcOnly){delete row.jcs;delete row.xqjmc;row.jc='1-2,5节';}
                    var d={kbList:[row],xsxx:{xh:fault==='identity-change'&&window.__jxCourses>1?'SYNTHETIC-002':'SYNTHETIC-001',XH:'SYNTHETIC-UPPER-ID',XM:'合成学生',XNMC:'2026-2027',XQMMC:window.__jxTerm||'1'}};
                    if(fault==='missing-teacher')row.xm='';
                    if(fault==='pending-room')row.cdmc='待定';
                    if(fault==='weekday')row.xqj=3;
                    if(fault==='week')row.zcd='0周';
                    if(fault==='section')row.jcs='3-1';
                    if(fault==='section-conflict')row.jc='1-2';
                    if(fault==='parity')row.dsz='1';
                    if(fault==='other-term')row.xnm='2025';
                    if(fault==='total')d.total=2;
                    if(fault==='pages')d.totalPages=2;
                    if(fault==='unknown-array')d.otherCourses=[{kcmc:'合成其他课'}];
                    if(fault==='unknown-object')d.other={rows:[{}]};
                    if(fault==='practice')d.sjkList=[{kcmc:'合成实践'}];
                    if(fault==='practice-second')d.jxhjkcList=[{kcmc:'合成其他实践'}];
                    if(fault==='mixed')d.kbList.push(Object.assign({},row,{kcmc:'合成缺排课',zcd:''}));
                    if(fault==='room-change'&&window.__jxCourses>1)row.cdmc='变化教室';
                    if(fault==='upper-identity-change'&&window.__jxCourses>1)d.xsxx.XH='CHANGED-ID';
                    if(fault==='upper-term-change'&&window.__jxCourses>1)d.xsxx.XQMMC='2';
                    if(fault==='class-change'&&window.__jxCourses>1)row.jxbmc='变化教学班';
                    if(fault==='raw-change'&&window.__jxCourses>1)row.unknownScalar='变化元数据';
                    if(fault==='nested-arrangement')row.arrangements=[{period:3}];
                    if(fault==='numeric-weekday'){delete row.xqjmc;row.xqj=8;}
                    if(fault==='jc-invalid'){delete row.jcs;row.jc='3-1';}
                    if(fault==='self-year')d.xsxx.XNMC='2025-2026';
                    if(fault==='self-term')d.xsxx.XQMMC='第二学期';
                    if(fault==='success-zero')d.success=0;
                    if(fault==='error-with-old-rows')d.error='服务器错误';
                    if(fault==='expired-with-old-rows')d.msg='会话已过期，请重新登录';
                    if(window.__jxVariants)d.kbList.push(Object.assign({},row,{xm:'另一教师',cdmc:'B201',jxb_id:'J2'}));
                    text=fault==='course-login'?'<html>登录</html>':JSON.stringify(d);
                  }else throw Error('Unexpected Jiangxia path');
                  if(fault==='encoding')text+='\ufffd';
                  return {ok:fault!=='http-failure',redirected:fault==='redirect',url:fault==='wrong-response-path'?u.origin+'/jwglxt/xtgl/login_slogin.html':u.href,
                    headers:{get:function(){return 'text/html; charset=UTF-8';}},text:async()=>text};
                };
            """.trimIndent()
            val jiangxia=fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","",jiangxiaSetup,expected=2,promptAnswer="18")
            check(jiangxia.firstMonday.toString()=="2026-09-07" && jiangxia.totalWeeks==18 && jiangxia.times.isEmpty())
            check(jiangxia.courses.map {it.startPeriod}==listOf(1,5))
            check(jiangxia.courses.all {it.weeksSet()==setOf(1,3,5,7,12) && it.teacher=="合成甲、合成乙" && it.room=="合成校区 A101" && it.extraInfo.contains("J1")})
            check(jiangxia.courses.all {it.extraInfo.contains("合成教学班") && it.extraInfo.contains("合成班级组成") && it.extraInfo.contains("CAMPUS-2") && it.extraInfo.contains("合成备注") && !it.extraInfo.contains("SYNTHETIC-UPPER-ID")})
            check(evaluate("window.__jxRequests.length")==4)
            check(evaluate("window.__kagendaSgValidate('kagendaFjjxuWeeksValidator','18')")==false)
            check(evaluate("typeof window.__kagendaSgValidate('kagendaFjjxuWeeksValidator','11')")=="string")
            fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxJcOnly=true;"+jiangxiaSetup,expected=2,promptAnswer="18")
            for(term in listOf("2","3")){
                val extraTerm=fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxTerm='$term';"+jiangxiaSetup,expected=2,promptAnswer="18")
                check(extraTerm.courses.all{it.extraInfo.contains("第"+term+"学期")})
            }
            val jiangxiaVariants=fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxVariants=true;"+jiangxiaSetup,expected=4,promptAnswer="18")
            check(jiangxiaVariants.courses.count {it.teacher=="另一教师" && it.room=="合成校区 B201" && it.extraInfo.contains("J2")}==2)
            fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxNonMonday=true;"+jiangxiaSetup,expected=2,promptAnswers=listOf("18","2026-09-07"))
            check(evaluate("window.__kagendaSgValidate('kagendaFjjxuMondayValidator','2026-09-07')")==false)
            check(evaluate("typeof window.__kagendaSgValidate('kagendaFjjxuMondayValidator','2026-09-08')")=="string")
            for(fault in listOf("calendar-login","no-calendar","course-login","missing-teacher","pending-room","weekday","week","section","section-conflict","parity","other-term","total","pages","unknown-array","unknown-object","practice","practice-second","mixed","room-change","term-change","identity-change","second-failure","encoding","http-failure","redirect","upper-identity-change","upper-term-change","class-change","raw-change","nested-arrangement","success-zero","error-with-old-rows","expired-with-old-rows","wrong-response-path","unsupported-term","numeric-weekday","jc-invalid","self-year","self-term")){
                fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxFault='$fault';"+jiangxiaSetup,expected=0,promptAnswer="18")
            }
            for(answer in listOf(null,"","11","41","18.5"))fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","",jiangxiaSetup,expected=0,promptAnswer=answer)
            for(answer in listOf(null,"2026-09-08","2026-02-30"))fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","","window.__jxNonMonday=true;"+jiangxiaSetup,expected=0,promptAnswers=listOf("18",answer))
            for(url in listOf("http://jwxt.fjjxu.edu.cn/","https://wrong.invalid/","https://jwxt.fjjxu.edu.cn:8443/"))fixture("ext_fjjxu_personal",url,"fjjxu-personal.js","",jiangxiaSetup,expected=0,promptAnswer="18")
            fixture("ext_fjjxu_personal",jiangxiaUrl,"fjjxu-personal.js","<input type=password>",jiangxiaSetup,expected=0,promptAnswer="18")
            // QNU: own synthetic full tables + production asset/bridge. The
            // source's personal button supplies the request URL; class table
            // navigation is never substituted for it.
            val qnuUrl="https://jw.qztc.edu.cn/academic/student/currcourse/currcourse.jsdo?year=2026&term=3"
            val qnuForm="""
              <select name=year><option value=2026 selected>2026</option></select>
              <select name=term><option value=3 selected>秋</option></select>
              <input type=button value="个人课表" onclick="window.open('/academic/manager/coursearrange/showTimetable.do?owned=synthetic&year=2026&term=3')">
              <input type=button value="班级课表">
            """.trimIndent()
            val qnuData=context.assets.open("qnu-synthetic.json").bufferedReader().use { it.readText() }
            val qnuSetup="""
                window.__qnuData=$qnuData;window.__qnuReads=0;window.__qnuRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__qnuRequests.push(u.href);window.__qnuReads++;
                  if(u.origin!==location.origin||u.pathname!=='/academic/manager/coursearrange/showTimetable.do'||init.method!=='GET'||init.cache!=='no-store'||init.credentials!=='same-origin'||init.redirect!=='error')throw Error('Wrong QNU request');
                  if(window.__qnuFault==='second-failure'&&window.__qnuReads>1)throw Error('Synthetic second request failure');
                  var text=window.__qnuTable||window.__qnuData.regular;
                  if(window.__qnuFault==='change'&&window.__qnuReads>1)text=text.replaceAll('A101','变化教室');
                  var buffer=window.__qnuFault==='gbk'?Uint8Array.from(atob(window.__qnuData.regularGbkBase64),c=>c.charCodeAt(0)).buffer:new TextEncoder().encode(text).buffer;
                  return {ok:window.__qnuFault!=='http-failure',redirected:window.__qnuFault==='redirect',url:u.href,
                    headers:{get:()=>window.__qnuFault==='gbk'?'text/html; charset=gb18030':'text/html; charset=UTF-8'},arrayBuffer:async()=>buffer};
                };
            """.trimIndent()
            val qnu=fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,qnuSetup,expected=2,promptAnswers=listOf("2026-09-07","18"))
            check(qnu.times.size==11 && qnu.times[4].first.toString()=="14:00" && qnu.courses.single {it.title=="合成文学"}.startPeriod==5)
            check(qnu.firstMonday.toString()=="2026-09-07" && qnu.totalWeeks==18 && qnu.courses.single {it.title=="合成算法"}.weeksSet()==setOf(1,3,5,7,10))
            check(evaluate("window.__qnuRequests.length")==2)
            check(evaluate("window.__kagendaSgValidate('kagendaQnuMondayValidator','2026-09-07')")==false)
            check(evaluate("window.__kagendaSgValidate('kagendaQnuWeeksValidator','18')")==false)
            val qnuObj=JSONObject(qnuData)
            val positive=qnuObj.getJSONArray("positive")
            for(n in 1 until positive.length()){
                val batch=fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,qnuSetup+"window.__qnuTable="+JSONObject.quote(positive.getString(n))+";",expected=if(n in listOf(2,3,4))3 else 2,promptAnswers=listOf("2026-09-07","18"))
                when(n){
                    1->check(batch.courses.single {it.title=="合成算法"}.weeksSet()==setOf(3,5,7,10))
                    2->check(batch.courses.single {it.room=="新教室"}.let {it.weeksSet()==setOf(3)&&it.teacher=="代课甲"&&it.dayOfWeek==3})
                    3->check(batch.courses.single {it.teacher=="代课乙"}.weeksSet()==setOf(1))
                    4->check(batch.courses.single {it.title=="合成新补课"}.weeksSet()==setOf(9))
                }
            }
            fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,"window.__qnuFault='gbk';"+qnuSetup,expected=2,promptAnswers=listOf("2026-09-07","18"))
            val negative=qnuObj.getJSONArray("negative")
            for(n in 0 until negative.length())fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,qnuSetup+"window.__qnuTable="+JSONObject.quote(negative.getJSONObject(n).getString("html"))+";",expected=0,promptAnswers=listOf("2026-09-07","18"))
            for(fault in listOf("second-failure","change","http-failure","redirect"))fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,"window.__qnuFault='$fault';"+qnuSetup,expected=0,promptAnswers=listOf("2026-09-07","18"))
            for(answer in listOf(null,"2026-09-08","2026-02-30"))fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,qnuSetup,expected=0,promptAnswers=listOf(answer,"18"))
            for(answer in listOf(null,"","8","41","18.5"))fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm,qnuSetup,expected=0,promptAnswers=listOf("2026-09-07",answer))
            fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm+"<input type=password>",qnuSetup,expected=0,promptAnswers=listOf("2026-09-07","18"))
            fixture("ext_qnu_personal",qnuUrl,"qnu-personal.js",qnuForm.replace("value=\"个人课表\"","value=\"班级课表\""),qnuSetup,expected=0,promptAnswers=listOf("2026-09-07","18"))
            val qnuSchool=SchoolCatalog.load(targetContext,emptyList()).single {it.name=="泉州师范学院"}
            check(qnuSchool.supported && SchoolCatalog.adapters(qnuSchool).any {it.id=="ext_qnu_personal"})
            // HQU original implementation: synthetic complete JSON responses,
            // explicit native selection, independent semester/calendar input.
            val hquSetup="""
                window.__hquTerms=0;window.__hquCourses=0;window.__hquRequests=[];
                window.fetch=async function(raw,init){
                  var u=new URL(raw);window.__hquRequests.push(u.href);
                  if(u.origin!==location.origin||init.method!=='POST'||init.credentials!=='same-origin'||init.cache!=='no-store'||init.redirect!=='error'||init.headers['X-Requested-With']!=='XMLHttpRequest')throw Error('Wrong HQU request');
                  if(u.searchParams.get('sf_request_type')!==(location.port==='9443'?'ajax':null))throw Error('Wrong aTrust request type');
                  var action,rows,fault=window.__hquFault;
                  if(u.pathname==='/jwapp/sys/wdkb/modules/xskcb/cxxljc.do'){
                    action='cxxljc';window.__hquTerms++;if(init.body.toString()!=='')throw Error('Wrong calendar form');
                    rows=[{XN:'2026-2027',XQ:'1',XQKSRQ:'2026-09-07 00:00:00'},{XN:'2025-2026',XQ:'2',XQKSRQ:'2026-03-02'}];
                    if(fault==='term-change'&&window.__hquTerms>1)rows[0].XQKSRQ='2026-09-14';
                    if(fault==='term-year')rows[0].XN='2026-2028';
                    if(fault==='term-date')rows[0].XQKSRQ='2026-02-30';
                  }else if(u.pathname==='/jwapp/sys/wdkb/modules/xskcb/cxxszhxqkb.do'){
                    action='cxxszhxqkb';window.__hquCourses++;
                    var code=window.__hquExpectedTerm||'2026-2027-1';
                    if(Array.from(init.body.keys()).length!==3||['XNXQDM','XNXQDM2','XNXQDM3'].some(k=>init.body.get(k)!==code))throw Error('Wrong HQU semester');
                    if(fault==='second-failure'&&window.__hquCourses>1)throw Error('Synthetic second course request failure');
                    var row={KCM:'合成华侨算法',SKJS:'合成甲、合成乙',JASMC:'合成校区 A101',SKXQ:'2',KSJC:'1',JSJC:'2',SKZC:'10101010',KBLB:'1',ZCMC:'1-8周(单)',KCH:'C1',JXBID:'T1',XF:'3',XNXQDM:code};
                    if(fault==='special')row.KBLB='2';
                    if(fault==='teacher')row.SKJS='';
                    if(fault==='room')row.JASMC='';
                    if(fault==='week')row.SKZC='10x01010';
                    if(fault==='empty-week')row.SKZC='00000000';
                    if(fault==='large-week')row.SKZC='0'.repeat(40)+'1';
                    if(fault==='week-description')row.ZCMC='2-8周(双)';
                    if(fault==='day')row.SKXQ=0;
                    if(fault==='period')row.JSJC=0;
                    if(fault==='other-term')row.XNXQDM='different';
                    if(fault==='pending-change')row.YPSJDD='第3周停课';
                    if(fault==='nested')row.teacherDetails=[{name:'未核验教师'}];
                    if(fault==='class-change'&&window.__hquCourses>1)row.JXBID='T2';
                    if(fault==='room-change'&&window.__hquCourses>1)row.JASMC='变化教室';
                    if(window.__hquTrailingZero)row.SKZC+='0'.repeat(45);
                    rows=[row];
                    if(window.__hquDoubleClass)rows.push(Object.assign({},row,{JXBID:'T2'}));
                    if(window.__hquVariants)rows.push(Object.assign({},row,{JXBID:'T2',SKJS:'另一教师',JASMC:'B202'}));
                    if(fault==='mixed-special')rows.push(Object.assign({},row,{JXBID:'T2',KBLB:'2'}));
                    if(fault==='mixed-missing')rows.push(Object.assign({},row,{JXBID:'T2',SKJS:''}));
                  }else throw Error('Unexpected HQU path');
                  var module={rows:rows,totalSize:rows.length};
                  if(fault==='term-total'&&action==='cxxljc')delete module.totalSize;
                  if(action==='cxxszhxqkb'){
                    if(fault==='course-total')delete module.totalSize;
                    if(fault==='missing-page')module.totalSize=rows.length+1;
                    if(fault==='next-page')module.hasNext=true;
                    if(fault==='pages')module.totalPages=2;
                    if(fault==='unknown-module')module.extraRows=[{}];
                  }
                  var data={code:'0',datas:{}};data.datas[action]=module;
                  if(fault==='branch'&&action==='cxxszhxqkb')data.datas.practice=[{}];
                  if(fault==='root-branch')data.extraArrangements=[{}];
                  if(fault==='business-failure')data.code='1';
                  return {ok:fault!=='http-failure',redirected:fault==='redirect',url:u.href,text:async()=>fault==='login-response'?'<html>登录</html>':JSON.stringify(data)};
                };
            """.trimIndent()
            val hquUrl="https://jwapp.hqu.edu.cn/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh"
            val hquProxy="https://jwapp-hqu-edu-cn-s.atrust.hqu.edu.cn:9443/jwapp/sys/wdkb/*default/index.do?EMAP_LANG=zh"
            for((id,url)in listOf("ext_hqu_personal" to hquUrl,"ext_hqu_atrust_personal" to hquProxy)){
                val hqu=fixture(id,url,"hqu-personal.js","",hquSetup,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
                check(hqu.times.isEmpty()&&hqu.firstMonday.toString()=="2026-09-07"&&hqu.totalWeeks==18)
                check(hqu.courses.single().let {it.teacher=="合成甲、合成乙"&&it.room=="合成校区 A101"&&it.weeksSet()==setOf(1,3,5,7)&&it.extraInfo.contains("T1")})
                check(evaluate("window.__hquRequests.length")==4)
                check(evaluate("window.__kagendaSgValidate('kagendaHquMondayValidator','2026-09-07')")==false)
                check(evaluate("window.__kagendaSgValidate('kagendaHquWeeksValidator','18')")==false)
            }
            val hquOlder=fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","","window.__hquExpectedTerm='2025-2026-2';"+hquSetup,promptAnswers=listOf("2026-03-02","18"),selectionAnswers=listOf(1))
            check(hquOlder.firstMonday.toString()=="2026-03-02"&&hquOlder.courses.single().extraInfo.contains("2025-2026"))
            val hquClasses=fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","","window.__hquDoubleClass=true;"+hquSetup,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            check(hquClasses.courses.single().extraInfo.let {it.contains("T1")&&it.contains("T2")})
            val hquVariants=fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","","window.__hquVariants=true;"+hquSetup,expected=2,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            check(hquVariants.courses.map {it.teacher}.toSet()==setOf("合成甲、合成乙","另一教师"))
            fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","","window.__hquTrailingZero=true;"+hquSetup,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            for(fault in listOf("term-total","term-year","term-date","course-total","missing-page","next-page","pages","unknown-module","branch","root-branch","business-failure","special","teacher","room","week","empty-week","large-week","week-description","day","period","other-term","pending-change","nested","class-change","room-change","mixed-special","mixed-missing","term-change","second-failure","http-failure","redirect","login-response")){
                fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","","window.__hquFault='$fault';"+hquSetup,expected=0,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            }
            for(choice in listOf(-1,5))fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","",hquSetup,expected=0,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(choice))
            for(answer in listOf(null,"2026-09-08","2026-02-30"))fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","",hquSetup,expected=0,promptAnswers=listOf(answer,"18"),selectionAnswers=listOf(0))
            for(answer in listOf(null,"","6","41","18.5"))fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","",hquSetup,expected=0,promptAnswers=listOf("2026-09-07",answer),selectionAnswers=listOf(0))
            for(url in listOf("http://jwapp.hqu.edu.cn/jwapp/","https://wrong.invalid/jwapp/","https://jwapp.hqu.edu.cn:9443/jwapp/","https://jwapp.hqu.edu.cn/login/"))fixture("ext_hqu_personal",url,"hqu-personal.js","",hquSetup,expected=0,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            fixture("ext_hqu_personal",hquUrl,"hqu-personal.js","<input type=password>",hquSetup,expected=0,promptAnswers=listOf("2026-09-07","18"),selectionAnswers=listOf(0))
            val hquSchool=SchoolCatalog.load(targetContext,emptyList()).single {it.name=="华侨大学"}
            check(SchoolCatalog.adapters(hquSchool).first().id=="ext_hqu_personal")
            check(SchoolCatalog.adapters(hquSchool).any {it.id=="ext_hqu_atrust_personal"})
            result.putString("stream", "PASS: $successfulFixtures school entries and $rejectedFixtures rejection fixtures, real Chromium DOM, production JS runner/bridge and Kotlin payload; no school network requests, no personal data, no persistent writes.")
            finish(-1, result)
        } catch(e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString().take(4500)}"); finish(0, result)
        } finally { runOnMainSync { web?.destroy() } }
    }
}
