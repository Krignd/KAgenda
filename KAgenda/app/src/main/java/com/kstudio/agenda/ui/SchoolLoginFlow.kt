package com.kstudio.agenda.ui

import com.kstudio.agenda.model.School

/**
 * 登录窗口的「学校流程」插件 —— 学校差异在**登录界面**这一侧的归属地。
 *
 * 与引擎侧的 [com.kstudio.agenda.data.SchoolFlow] 一一对应：那个负责「怎么抓课表」，
 * 这个负责「登录窗口里怎么引导用户 + 怎么判定已登录」。通用登录界面只面向本接口，
 * 因此新增学校不会改动 WebLoginActivity 的通用流程，也就不会影响其他学校。
 */
internal interface SchoolLoginFlow {

    /**
     * 运行登录窗口的自动流程（周期探测 / 自动跳转 / 顶部阶段提示），
     * 一直阻塞到页面关闭。返回即表示流程结束。
     *
     * 不接管自动填表/自动跳转的学校不会注册本流程（返回 null），走通用流程。
     */
    suspend fun run(host: LoginWindowHost)

    /**
     * 「已登录」判定。
     *
     * 通用规则是「课表已渲染」；需要经由门户/统一认证多跳的学校往往要更严格的条件
     * （否则会在用户还没输密码时就误报已登录），因此交给学校自己实现。
     */
    fun isLoggedIn(probe: LoginProbe?, homeReached: Boolean): Boolean

    /** 顶栏快捷跳转按钮；返回 null 表示用通用的「去登录」按钮 */
    fun jumpButton(loggedIn: Boolean): LoginJumpButton?

    /** 顶栏标题 */
    fun title(homeReached: Boolean): String
}

/** 登录窗口向学校流程暴露的最小能力集（改状态、刷提示，不直接操作界面结构） */
internal interface LoginWindowHost {
    /** 登录窗口的 WebView（与通用流程共用同一个） */
    val view: android.webkit.WebView

    /** 刷新界面下方的「探针原始输出」调试行 */
    fun onProbe(probe: LoginProbe)

    /** 刷新界面顶部的阶段提示 */
    fun setStatus(text: String)

    /** 标记「已登录」（界面据此提示可以点「完成」） */
    fun markLoggedIn()
}

/** 登录窗口顶栏的快捷跳转按钮 */
internal data class LoginJumpButton(val label: String, val target: String)

/**
 * 登录窗口学校流程注册表 —— 新增学校只在这里加一行。
 *
 * 键必须与 `Schools.ALL` 的 `id` 一致；未登记的学校返回 null（走通用登录流程）。
 */
internal object SchoolLoginFlows {

    /** schoolId → 流程工厂（每次建新实例：流程内部持有「本次会话」的链路选择等状态） */
    private val registry: Map<String, (School) -> SchoolLoginFlow> = mapOf(
        // 江苏大学：门户 + 统一认证多跳，需要分阶段引导（见 SchoolLoginFlowUjs.kt）
        com.kstudio.agenda.data.UjsFlowPlugin.SCHOOL_ID to { school -> UjsLoginFlow(school) },
    )

    fun of(school: School): SchoolLoginFlow? = registry[school.id]?.invoke(school)
}
