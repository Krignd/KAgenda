package com.kstudio.agenda.data

/**
 * 江苏大学的两条访问链路 —— 代码刻意完全隔离，互不引用。
 *
 * 用户在「设置 → 账号」里勾选「通过 WebVPN」：
 * - 勾选   → [UjsWebVpnFlow]（链路 A）
 * - 不勾选 → [UjsDefaultFlow]（链路 B）
 *
 * 【为什么拆成两个对象，而不是在某处 if 一下】
 * 用户的明确要求：两条链路从「登录窗口首屏地址」到「引擎导航到课表页」再到
 * 「404 回退候选」，必须从头到尾各写一份，任一方后续调整都不牵连另一方。
 * 因此每个对象都**自带**自己的入口地址、课表地址与回退序列；
 * 引擎（[WebScheduleEngine]）与登录窗口（WebLoginActivity）各自按勾选状态
 * 分派到对应的那一份，不存在「共用一段再分叉」的写法。
 *
 * 目前两条链路的地址取值相同（用户已确认），但它们是各自定义的字面量：
 * 以后任一方要改地址，只动自己对象里的常量即可。
 *
 *
 * 【共同背景】江大教务 = 正方 + webvpn.ujs.edu.cn 门户
 *
 * 三台主机在 WebVPN 下的加密路径（前 32 位十六进制是 wrdvpn 的固定密钥
 * `wrdvpnisthebest!`，后半段才是主机名密文；算法为 AES-128-OFB，
 * key = iv = `wrdvpnisthebest!`，密文长度等于主机名长度）：
 * - `jwxt`   = `fae0598869256243300d8db9d6562d` —— 课表数据所在（根级 /kbcx/…）
 *   → 解密核对：`jwxt.ujs.edu.cn` ✅
 * - `xuanke` = `e8e240922c352645741bc7a99c406d368d` —— 一卡通登录入口
 *   → 解密核对：`xuanke.ujs.edu.cn` ✅
 *   【勘误 2026-10-03】此前写成 `…9c406d36c6`，解密结果是 `xuanke.ujs.edu.c%`
 *   （末字节错一个字节，% 还会被 URL 编码成 %25）。因此历史上「xuanke 上所有
 *   jwxt 式路径实测均 404」的结论**无效** —— 当时访问的是一个不存在的主机。
 * - `pass`（统一身份认证 CAS）= `e0f6528f69256243300d8db9d6562d`，走 /https/ 代理
 *   → 解密核对：`pass.ujs.edu.cn` ✅（不是注释里旧写的 authserver）
 *
 * 门户登录环节有滑块验证码 + 短信二次认证，脚本无法代填，故两条链路都走
 * 「用户手动登录 + 引擎自动抓取」。区别只在于勾选与否决定用哪一份实现。
 */

// ===========================================================================
// 链路 A：通过 WebVPN（勾选框 = 勾选）
//
// 登录窗口首屏 → 认统一身份认证 CAS；引擎接手后 → 直接开 jwxt 的课表页。
// 本对象不引用链路 B 的任何符号。
// ===========================================================================

object UjsWebVpnFlow {

    /**
     * WebVPN 门户登录页 —— 登录窗口的**首屏**（第一步）。
     *
     * 【为什么首屏不用 CAS 地址】实测：门户未登录时直接打开 CAS 地址，门户会先把
     * CAS 页渲染出来（表单可见、甚至能输入），过一会儿才 302 到门户登录页。
     * 结果是界面提示「① → ② → ①」来回跳，用户还会先在一个根本提交不了的页面上
     * 白输入一遍账号密码。改成先落户页，等门户放行后再进 CAS，链条就单调了。
     */
    private const val PORTAL_LOGIN = "https://webvpn.ujs.edu.cn/login"

    /** WebVPN 代理下的统一身份认证登录页（第二步：选择/使用 CAS 统一身份认证）。 */
    private const val CAS =
        "https://webvpn.ujs.edu.cn/https/77726476706e69737468656265737421e0f6528f69256243300d8db9d6562d/cas/login?service=http%3A%2F%2Fjwxt.ujs.edu.cn%2Fsso%2Fjziotlogin"

    /** WebVPN 代理下的 jwxt 课表页（正方 V-9 标准结构，周次写在课程文本里）。 */
    private const val SCHEDULE =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421fae0598869256243300d8db9d6562d/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

    /** jwxt / xuanke 两台教务主机（用于生成回退候选与一卡通登录入口）。 */
    private const val JWXT =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421fae0598869256243300d8db9d6562d"
    private const val XUANKE =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421e8e240922c352645741bc7a99c406d368d"

    /**
     * 教务系统登录入口（教务处站点 jwc 上的落地页）。
     *
     * 段解密核对：`fae042d2323a7b1e7b0c9ce29b5b` → `jwc.ujs.edu.cn`（走 /https/ 代理）。
     * 用户 2026-10-03 实测提供：从这个页面可以点进教务系统登录。
     *
     * 【为什么不用 xuanke 根路径】直接访问 `http://xuanke.ujs.edu.cn/` 的代理地址时，
     * 门户会在约 150ms 内直接返回失败页（wengine-vpn/failed）—— 说明该主机不在门户
     * 允许列表里（或根本不存在）。因此真正可用的入口是教务处这个落地页。
     */
    private const val JWC_ENTRY =
        "https://webvpn.ujs.edu.cn/https/77726476706e69737468656265737421fae042d2323a7b1e7b0c9ce29b5b/qtxx/jwxt.htm"

    /** 登录窗口首屏地址（WebVPN 门户登录页 = 第一步）。 */
    fun entryUrl(): String = PORTAL_LOGIN

    /** 第二步：统一身份认证（CAS）登录页。 */
    fun casEntryUrl(): String = CAS

    /** 引擎接手后要打开的课表页地址。 */
    fun scheduleUrl(): String = SCHEDULE

    /**
     * 教务系统登录入口（`jwc.ujs.edu.cn/qtxx/jwxt.htm` 的 WebVPN 地址）。
     *
     * 教务系统这层没登录时，课表页会渲染出它自己的登录表单（且提示一卡通用户
     * 不要在此登录）。此时把用户带到这里，让他点页面上的入口进教务系统登录。
     */
    fun jwcEntryUrl(): String = JWC_ENTRY

    /** 判断某个地址是不是那个「教务系统登录入口」落地页。 */
    fun isJwcEntryUrl(url: String): Boolean = url.contains(JWC_ENTRY)

    /** 判断某个地址是不是 jwxt / xuanke 这两台教务主机上的（用于区分「已进教务系统」）。 */
    fun isCampusUrl(url: String): Boolean = url.contains(JWXT) || url.contains(XUANKE)

    /** 判断某个地址是不是 xuanke（一卡通入口）上的。 */
    fun isXuankeUrl(url: String): Boolean = url.contains(XUANKE)

    /**
     * 登录入口候选序列（页面 404 时逐个回退）。
     *
     * 注意：历史注释里「xuanke 上 jwxt 式路径实测全部 404」的结论已被推翻 ——
     * 当时用的是写错的 xuanke 段（c6，指向不存在的主机），404 与路径结构无关。
     * 现在段已修正为 8d，这几条候选是否可用需要真机重新验证，故仍保留整条候选链。
     */
    fun entryFallbacks(): List<String> = listOf(
        CAS,
        "$XUANKE/",
        "$XUANKE/default2.aspx",
        "$XUANKE/xtgl/index_initMenu.html?jsdm=xs",
        "$JWXT/xtgl/login_slogin.html",
    )

    /**
     * 课表页路径变体（页面 404 时逐个回退）。
     *
     * 正方 V-9 的标准部署存在 `/jwglxt` 上下文前缀这一变体，故对同一资源生成
     * 原样、加前缀、根路径三种候选，哪个能用由运行时探针（notFound 字段）现场判定。
     */
    fun scheduleFallbacks(): List<String> = listOf(
        SCHEDULE,
        "$JWXT/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
        "$JWXT/",
    )
}

// ===========================================================================
// 链路 B：默认方式（勾选框 = 未勾选）
//
// 与链路 A 结构完全对称，但**独立成文**：地址、回退序列都在这里各自定义，
// 不引用链路 A 的任何符号。目前地址取值与链路 A 相同（用户确认）。
// ===========================================================================

object UjsDefaultFlow {

    /** WebVPN 门户登录页（首屏 = 第一步）；原因见链路 A 同名常量。 */
    private const val PORTAL_LOGIN = "https://webvpn.ujs.edu.cn/login"

    /** 统一身份认证（CAS）登录页 = 第二步。 */
    private const val CAS =
        "https://webvpn.ujs.edu.cn/https/77726476706e69737468656265737421e0f6528f69256243300d8db9d6562d/cas/login?service=http%3A%2F%2Fjwxt.ujs.edu.cn%2Fsso%2Fjziotlogin"

    /** 引擎导航目标：课表页。 */
    private const val SCHEDULE =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421fae0598869256243300d8db9d6562d/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"

    private const val JWXT =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421fae0598869256243300d8db9d6562d"
    private const val XUANKE =
        "https://webvpn.ujs.edu.cn/http/77726476706e69737468656265737421e8e240922c352645741bc7a99c406d368d"

    /** 教务系统登录入口（教务处站点 jwc 上的落地页）；说明见链路 A 同名常量。 */
    private const val JWC_ENTRY =
        "https://webvpn.ujs.edu.cn/https/77726476706e69737468656265737421fae042d2323a7b1e7b0c9ce29b5b/qtxx/jwxt.htm"

    /** 登录窗口首屏地址（WebVPN 门户登录页 = 第一步）。 */
    fun entryUrl(): String = PORTAL_LOGIN

    /** 第二步：统一身份认证（CAS）登录页。 */
    fun casEntryUrl(): String = CAS

    /** 引擎接手后要打开的课表页地址。 */
    fun scheduleUrl(): String = SCHEDULE

    /** 教务系统登录入口（说明见链路 A 同名方法）。 */
    fun jwcEntryUrl(): String = JWC_ENTRY

    /** 是否那个「教务系统登录入口」落地页。 */
    fun isJwcEntryUrl(url: String): Boolean = url.contains(JWC_ENTRY)

    /** 是否 jwxt / xuanke 这两台教务主机上的地址。 */
    fun isCampusUrl(url: String): Boolean = url.contains(JWXT) || url.contains(XUANKE)

    /** 是否 xuanke（一卡通入口）上的地址。 */
    fun isXuankeUrl(url: String): Boolean = url.contains(XUANKE)

    /** 登录入口候选序列（页面 404 时逐个回退）。 */
    fun entryFallbacks(): List<String> = listOf(
        CAS,
        "$XUANKE/",
        "$XUANKE/default2.aspx",
        "$XUANKE/xtgl/index_initMenu.html?jsdm=xs",
        "$JWXT/xtgl/login_slogin.html",
    )

    /** 课表页路径变体（页面 404 时逐个回退）。 */
    fun scheduleFallbacks(): List<String> = listOf(
        SCHEDULE,
        "$JWXT/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default",
        "$JWXT/",
    )
}

/*
 * 说明：这里**故意不提供**「按开关返回同一个抽象接口」的统一层。
 * 调用方（引擎与登录窗口）在分派后直接写 `UjsWebVpnFlow.xxx()` 或
 * `UjsDefaultFlow.xxx()`，两条链路在调用处就是两处显式引用，
 * 一眼能看出走的是哪一份，也不会因为抽公共接口而重新耦合到一起。
 */
