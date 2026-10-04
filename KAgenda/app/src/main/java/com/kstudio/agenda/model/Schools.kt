package com.kstudio.agenda.model

import android.content.Context
import com.kstudio.agenda.data.JsScripts
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
    /**
     * 是否必须由用户手动完成登录（可选，默认 false）。
     *
     * 置 true 的学校（如江苏大学：WebVPN 门户有滑块验证码 + 短信二次认证），
     * 引擎不会尝试自动填表提交账密，而是引导用户打开可见网页窗口自行登录；
     * 登录完成后引擎只负责检测状态并抓取课表。
     */
    val manualLogin: Boolean = false,
    /**
     * 课表页地址（可选，配合 [manualLogin] 使用）。
     *
     * 手动登录的学校往往「登录入口」与「课表页」不在同一个地址上：必须以教务系统
     * 入口页为首屏才能走到登录表单，课表数据却挂在另一个深层地址。此字段记录后者，
     * 用户登录完成后由引擎导航过去再提取。
     */
    val scheduleUrl: String? = null,
    /** 是否用户添加的自定义学校 */
    val custom: Boolean = false,
) {
    /** 教务系统主机名（用于 CAS 回跳判定） */
    val host: String get() = homeUrl.substringAfter("://").substringBefore("/")
}

object Schools {
    const val DEFAULT_ID = "buaa"

    /**
     * 江苏大学的两条访问链路（三台主机的 WebVPN 加密路径、登录入口候选、
     * 课表页路径变体）已全部迁出到 data/UjsFlow.kt，分别由
     * [com.kstudio.agenda.data.UjsWebVpnFlow]（勾选「通过 WebVPN」）与
     * [com.kstudio.agenda.data.UjsDefaultFlow]（未勾选）两支独立持有。
     * 该校的其它差异（抓取流程 / 作息预设 / 补充课程 / 设置开关）见
     * `data/SchoolFlowUjs.kt` 的学校流程插件。本文件只保留纯数据。
     */

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
        /**
         * 江苏大学（正方教务 V-9.0 + WebVPN 门户）。
         *
         * 与其他学校的关键差异：WebVPN 门户在登录环节启用了**滑块验证码 + 短信二次认证**，
         * 无法用脚本自动提交账密（服务端在校验凭据前就要求滑块）。
         * 因此本校走「**手动登录 + 自动抓取**」模式：
         *   用户在可见的网页窗口里自行完成登录（拖滑块 / 输验证码），
         *   引擎只负责检测「已进入课表页」→ 提取整学期课表。
         *
         * 【三台主机的角色分工（v5/v6 实测结论）】
         * - `jwxt`（正方 V-9）：课表数据所在，但其登录页提示「一卡通用户不要在此登录」；
         * - `xuanke`（正方老版）：一卡通登录入口，但路径结构未明（v5 实测 jwxt 式路径
         *   连同根路径在内全部 404，不能押注）；
         * - `authserver`（统一身份认证 CAS）：标准一卡通登录页，v3 真机抓包证实其
         *   `cas/login?service=http://jwxt.ujs.edu.cn/sso/jziotlogin` 地址有效，
         * 登录成功后 ticket 回跳 jwxt 建立会话 —— v6 起以它为首选入口。
         * 若 CAS 不可用，两条链路各自的入口候选序列会继续尝试 xuanke 等。
         *
         * 课表页为正方标准结构，周次直接写在课程文本里（如「周数：4-11周」），
         * 一次提取即可覆盖整学期，无需按周重放。
         *
         * 【两条链路】勾选「通过 WebVPN」与否对应 data/UjsFlow.kt 里两份
         * 完全隔离的实现；此处登记的地址取自 WebVPN 那一支（两条链路目前同址），
         * 仅供未走分支的通用代码兜底。
         *
         * 【差异归属】探针脚本 / 抓取流程 / 作息预设 / 补充课程 / 设置开关
         * 全部在 data/SchoolFlowUjs.kt 的学校流程插件里，本处只提供数据。
         */
        School(
            id = "ujs",
            name = "江苏大学",
            ssoUrl = "https://webvpn.ujs.edu.cn/login",
            // 首屏：统一身份认证 CAS 登录页（一卡通账号的标准入口，
            // 登录后由 CAS ticket 回跳 jwxt 建立教务会话）。
            // 404 时由对应链路自己的入口候选序列逐个回退。
            homeUrl = com.kstudio.agenda.data.UjsWebVpnFlow.entryUrl(),
            serviceApi = com.kstudio.agenda.data.UjsWebVpnFlow.entryUrl(),
            // 课表页：**jwxt** 上的正方 V-9 课表（存档「个人课表.html」证实数据在此）。
            // 登录成功后导航到这里提取整学期课表。
            scheduleUrl = com.kstudio.agenda.data.UjsWebVpnFlow.scheduleUrl(),
            supported = true,
            // 探针按学校数据走（引擎只看 probeJs，不做学校 id 判断）
            probeJs = JsScripts.UJS_DETECT,
            loginJs = null,
            waitSelector = null,
            extractJs = JsScripts.UJS_EXTRACT,
            manualLogin = true,
            custom = false,
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
                    if (!s.scheduleUrl.isNullOrBlank()) put("scheduleUrl", s.scheduleUrl)
                    if (s.manualLogin) put("manualLogin", true)
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
        val schedule = o.optString("scheduleUrl").trim().takeIf { it.isNotBlank() }
        val manual = o.optBoolean("manualLogin", false)
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
            scheduleUrl = schedule,
            // 有独立课表页 = 登录与课表分离，必须由用户手动登录
            manualLogin = manual || schedule != null,
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
