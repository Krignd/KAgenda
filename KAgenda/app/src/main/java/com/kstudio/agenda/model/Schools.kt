package com.kstudio.agenda.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 学校配置：
 * - supported=true 表示可自动登录并抓取课表（其余学校可切换，但同步会提示）。
 * - homeUrl 为教务系统首页；ssoUrl 为统一身份认证登录页；serviceApi 为 CAS service 参数用的接口地址。
 */
data class School(
    val id: String,
    val name: String,
    val ssoUrl: String,
    val homeUrl: String,
    val serviceApi: String,
    val supported: Boolean,
    /** 自定义适配器提供的提取 JS（非空时引擎走适配器路径） */
    val extractJs: String? = null,
    /** 登录页自动登录脚本（可选）：用 window.__kagendaUser / __kagendaPass 读取账号密码并提交 */
    val loginJs: String? = null,
    /** 页面状态探针脚本（可选）：返回 {ready,login,captcha,error,netError} 的 JSON */
    val probeJs: String? = null,
    /** 提取前等待出现的关键元素选择器（可选，适配异步渲染的页面） */
    val waitSelector: String? = null,
    /** 是否用户添加的自定义学校 */
    val custom: Boolean = false,
) {
    /** 教务系统主机名（用于 CAS 回跳判定） */
    val host: String get() = homeUrl.substringAfter("://").substringBefore("/")
}

object Schools {
    const val DEFAULT_ID = "buaa"

    /** 学校列表（北航在前，其余按序展示） */
    val ALL: List<School> = listOf(
        School(
            id = "buaa",
            name = "北京航空航天大学",
            ssoUrl = "https://sso.buaa.edu.cn/login",
            homeUrl = "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/home/index.html#/",
            serviceApi = "https://byxt.buaa.edu.cn/jwapp/sys/homeapp/api/home/currentUser.do",
            supported = true,
        ),
        School(
            id = "thu",
            name = "清华大学",
            ssoUrl = "https://login.tsinghua.edu.cn/login",
            homeUrl = "https://zhjw.cic.tsinghua.edu.cn",
            serviceApi = "https://zhjw.cic.tsinghua.edu.cn",
            supported = false,
        ),
        School(
            id = "pku",
            name = "北京大学",
            ssoUrl = "https://iaaa.pku.edu.cn/iaaa/oauth.jsp",
            homeUrl = "https://dean.pku.edu.cn",
            serviceApi = "https://dean.pku.edu.cn",
            supported = false,
        ),
        School(
            id = "zju",
            name = "浙江大学",
            ssoUrl = "https://zjuam.zju.edu.cn/cas/login",
            homeUrl = "https://zdbk.zju.edu.cn",
            serviceApi = "https://zdbk.zju.edu.cn",
            supported = false,
        ),
        School(
            id = "fudan",
            name = "复旦大学",
            ssoUrl = "https://uis.fudan.edu.cn/authserver/login",
            homeUrl = "https://ehall.fudan.edu.cn",
            serviceApi = "https://ehall.fudan.edu.cn",
            supported = false,
        ),
        School(
            id = "sjtu",
            name = "上海交通大学",
            ssoUrl = "https://jaccount.sjtu.edu.cn/jaccount/",
            homeUrl = "https://i.sjtu.edu.cn",
            serviceApi = "https://i.sjtu.edu.cn",
            supported = false,
        ),
    )

    fun of(id: String?): School = all().firstOrNull { it.id == id } ?: all().first()

    /** 内置 + 用户自定义（后者在前？否——保持内置在前，自定义追加到末尾） */
    fun all(): List<School> = ALL + customList

    // ------------------------------------------------------------ 自定义学校（适配器）

    private const val CUSTOM_FILE = "custom_schools.json"

    @Volatile
    private var customList: List<School> = emptyList()

    /** 应用启动时调用：加载用户已保存的适配器 */
    fun loadCustom(context: Context) {
        runCatching {
            val f = File(context.filesDir, CUSTOM_FILE)
            if (!f.exists()) {
                customList = emptyList()
                return@runCatching
            }
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            val list = mutableListOf<School>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                runCatching { parseAdapter(o.toString(), null, null) }.getOrNull()?.let { list.add(it) }
            }
            customList = list
        }
    }

    /** 新增/更新自定义学校；成功返回 School，失败抛异常（message 供 UI 展示） */
    fun addOrUpdate(context: Context, code: String, nameFallback: String, siteFallback: String): School {
        val school = parseAdapter(code, nameFallback, siteFallback)
        val stored = customList.filterNot { it.id == school.id } + school
        val arr = JSONArray()
        stored.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("format", "KagendaSchoolAdapter/2")
                    put("id", s.id)
                    put("name", s.name)
                    put("ssoUrl", s.ssoUrl)
                    put("homeUrl", s.homeUrl)
                    put("serviceApi", s.serviceApi)
                    if (!s.extractJs.isNullOrBlank()) put("extractJs", s.extractJs)
                    if (!s.loginJs.isNullOrBlank()) put("loginJs", s.loginJs)
                    if (!s.probeJs.isNullOrBlank()) put("probeJs", s.probeJs)
                    if (!s.waitSelector.isNullOrBlank()) put("waitSelector", s.waitSelector)
                }
            )
        }
        File(context.filesDir, CUSTOM_FILE).writeText(arr.toString(), Charsets.UTF_8)
        customList = stored
        return school
    }

    /** 校验并解析适配代码（nameFallback/siteFallback 用于补全必填项）；兼容 v1/v2 */
    private fun parseAdapter(code: String, nameFallback: String?, siteFallback: String?): School {
        val o = JSONObject(code)
        val name = o.optString("name").trim().ifBlank { nameFallback?.trim().orEmpty() }
        require(name.isNotBlank()) { "缺少学校名称" }
        val home = o.optString("homeUrl").trim().ifBlank { siteFallback?.trim().orEmpty() }
        require(home.startsWith("http")) { "homeUrl 必须为 http(s) 地址" }
        val sso = o.optString("ssoUrl").trim().ifBlank { home }
        val service = o.optString("serviceApi").trim().ifBlank { home }
        val extract = o.optString("extractJs").trim().takeIf { it.isNotBlank() }
        val login = o.optString("loginJs").trim().takeIf { it.isNotBlank() }
        val probe = o.optString("probeJs").trim().takeIf { it.isNotBlank() }
        val wait = o.optString("waitSelector").trim().takeIf { it.isNotBlank() }
        val id = o.optString("id").trim().takeIf { it.isNotBlank() }
            ?: ("custom_" + Integer.toHexString(name.hashCode()))
        return School(
            id = id,
            name = name,
            ssoUrl = sso,
            homeUrl = home,
            serviceApi = service,
            supported = extract != null,
            extractJs = extract,
            loginJs = login,
            probeJs = probe,
            waitSelector = wait,
            custom = true,
        )
    }

    /** 适配代码格式模板（供用户复制修改；v2 支持登录脚本/页面探针/等待选择器） */
    fun template(): String =
        """{
  "format": "KagendaSchoolAdapter/2",
  "id": "my_school",
  "name": "我的大学",
  "homeUrl": "https://jw.example.edu.cn/schedule",
  "ssoUrl": "https://sso.example.edu.cn/login",
  "serviceApi": "https://jw.example.edu.cn/api",
  "probeJs": "return JSON.stringify({ready: !!document.querySelector('#schedule-table'), login: !!document.querySelector('input[type=password]'), captcha: false, error: '', netError: false})",
  "loginJs": "var u=document.querySelector('#username'); var p=document.querySelector('#password'); if(u&&p){u.value=window.__kagendaUser; p.value=window.__kagendaPass; document.querySelector('form').submit(); return 'ok';} return 'no-form'",
  "waitSelector": "#schedule-table",
  "extractJs": "return JSON.stringify([{title:'高等数学',day:1,start:1,end:2,room:'C101',teacher:'张三',weeks:'2-16'}])",
  "note": "请按自己学校的教务系统修改脚本；extractJs 必须 return 课程 JSON 字符串"
}"""
}
