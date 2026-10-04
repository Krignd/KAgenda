package com.kstudio.agenda.ui

import android.app.Activity
import android.os.SystemClock
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.data.UjsDefaultFlow
import com.kstudio.agenda.data.UjsWebVpnFlow
import com.kstudio.agenda.model.School
import com.kstudio.agenda.util.AppLog
import kotlinx.coroutines.delay

/*
 * ===========================================================================
 * 江苏大学 · 登录窗口的两条链路
 *
 * 勾选「通过 WebVPN」（链路 A）与未勾选（链路 B）各有一份**完整的状态机**，
 * 地址分别取自 [UjsWebVpnFlow] / [UjsDefaultFlow]（见 data/UjsFlow.kt）。
 *
 * 两份实现刻意不共用任何流程代码：探测、阶段提示、404 回退、跳转节流各写一份。
 *
 * 【流程分三个阶段，界面顶部会同步显示当前阶段并写入日志】
 *   阶段① 登录 WebVPN 门户（拖滑块 + 短信验证码）
 *   阶段② 通过 WebVPN 登录教务系统（统一身份认证输一卡通号密码）
 *   阶段③ 已进入教务系统/课表页 → 提示点击「完成」结束登录
 *
 * 【为什么不能在「proxied 且没有登录表单」时判定已登录】
 * 统一身份认证页（…/https/<pass>/cas/login）本身也是经过门户代理的地址，
 * 满足 proxied，且它的登录表单在 iframe 里 —— 只看顶层文档会得到 login=false，
 * 于是刚打开首屏就被误报「登录成功」。因此这里额外要求：
 * 地址必须落在 jwxt / xuanke 这两台**教务主机**上（见 isCampusUrl）。
 *
 * 【教务系统未登录怎么办】
 * 课表页在教务会话缺失时会渲染它自己的登录表单（并提示一卡通用户不要在此登录）。
 * 检测到这种表单（非统一身份认证页）时，自动带用户去教务系统登录入口，
 * 让他在那里点「登录」完成一卡通登录，再回到课表页点「完成」。
 * ===========================================================================
 */

/** 阶段序号（只用于排序与抑制重复日志，数值本身无外部含义） */
private const val UJS_STAGE_WAIT = 0

/** ① 在 WebVPN 门户页点击「CAS 统一身份认证登录」 */
private const val UJS_STAGE_PORTAL = 1

/** ② 在统一身份认证页输入一卡通账号密码 + 滑块 + 短信验证 */
private const val UJS_STAGE_SSO = 2

/** ② 教务系统未登录，需要在入口页点击「CAS 统一身份认证登录」 */
private const val UJS_STAGE_LOGIN_ENTRY = 3

/** ③ 凭据已提交，等待系统处理 */
private const val UJS_STAGE_PROCESSING = 4

/** ④ 课表页就绪，点「完成」 */
private const val UJS_STAGE_READY = 5

/** 异常：页面 404 */
private const val UJS_STAGE_FALLBACK = 6

/** 异常：门户代理失败（目标主机不可达） */
private const val UJS_STAGE_BLOCKED = 7

/**
 * 阶段序号 → 界面提示文案（两条链路共用同一套文案，但各自推进）。
 *
 * 用户在界面上看到的是四步：
 *   ① 点击「CAS 统一身份认证登录」
 *   ② 输入一卡通账号与密码，并通过滑块验证与短信验证（若开启二次验证）
 *   ③ 等待系统处理，即将完成登录…
 *   ④ 已进入课表页，请点击「完成」
 * 第 3 个常量（LOGIN_ENTRY）仍属第②步的另一种屏幕，避免用户看到的编号来回变。
 */
private fun ujsStageHint(stage: Int): String = when (stage) {
    UJS_STAGE_PORTAL -> "① 点击「CAS 统一身份认证登录」"
    UJS_STAGE_SSO -> "② 输入一卡通账号与密码，并通过滑块验证与短信验证（若开启二次验证）"
    UJS_STAGE_LOGIN_ENTRY -> "② 请点击页面上的「CAS 统一身份认证登录」进入教务系统"
    UJS_STAGE_PROCESSING -> "③ 等待系统处理，即将完成登录…"
    UJS_STAGE_READY -> "④ 已进入课表页，请点击右上角「完成」结束登录"
    UJS_STAGE_FALLBACK -> "页面 404，正在尝试备用入口…"
    UJS_STAGE_BLOCKED -> "门户无法访问上一目标，正在切换登录入口…"
    else -> "正在打开 WebVPN 门户…"
}

/**
 * 江苏大学在登录窗口的流程。
 *
 * 勾选「通过 WebVPN」与否在 [run] 里读一次设置并分派到两条**完全隔离**的链路，
 * 分派之后各自走完全程、互不调用。
 */
internal class UjsLoginFlow(private val school: School) : SchoolLoginFlow {

    /**
     * 当前生效的链路（true = 通过 WebVPN）。默认值与设置的默认值一致，
     * 供 [run] 之前就被调用的 [isLoggedIn] / [jumpButton] 使用。
     */
    private var useWebVpn: Boolean = true

    override fun title(homeReached: Boolean): String =
        if (homeReached) "已登录，点击“完成”即可同步" else "请按下方提示完成登录"

    /**
     * 「已登录」判定：**课表视图已渲染**，或**已站在课表页且没有登录表单**。
     *
     * 不能只看「没有登录表单」：统一认证页的表单在 iframe 里，只看顶层文档会得到
     * login=false，刚打开首屏就会被误报「已登录」。
     */
    override fun isLoggedIn(probe: LoginProbe?, homeReached: Boolean): Boolean {
        if (homeReached) return true
        val p = probe ?: return false
        val onSchedule = p.url.startsWith(scheduleUrlInUse().substringBefore('?'))
        return p.ready || p.grid || p.app || (onSchedule && !p.login)
    }

    override fun jumpButton(loggedIn: Boolean): LoginJumpButton {
        val target = if (loggedIn) scheduleUrlInUse() else entryUrlInUse()
        return LoginJumpButton(label = if (loggedIn) "去课表页" else "回入口页", target = target)
    }

    override suspend fun run(host: LoginWindowHost) {
        useWebVpn = SettingsStore.read(host.view.context).ujsWebVpn
        AppLog.i("WebLogin", "手动登录分派：链路 = " + if (useWebVpn) "A·通过 WebVPN" else "B·默认方式")
        if (useWebVpn) runWebVpnLogin(host) else runDefaultLogin(host)
    }

    private fun entryUrlInUse(): String =
        if (useWebVpn) UjsWebVpnFlow.entryUrl() else UjsDefaultFlow.entryUrl()

    private fun scheduleUrlInUse(): String =
        if (useWebVpn) UjsWebVpnFlow.scheduleUrl() else UjsDefaultFlow.scheduleUrl()

    // ------------------------------------------------------------ 链路 A

    /**
     * 链路 A：勾选「通过 WebVPN」时的登录窗口状态机。
     *
     * 与链路 B 结构对称但**独立成文**：地址、回退候选都来自 [UjsWebVpnFlow]，
     * 不调用链路 B 的任何函数。
     */
    private suspend fun runWebVpnLogin(host: LoginWindowHost) {
        var firstTick = true
        var lastProbeSign = ""
        var lastStageShown = ""
        var lastNormalStage = 0
        var showingError = false
        var lastNavTarget = ""
        var lastNavAt = 0L
        var scheduleNavCount = 0
        var entryNavCount = 0
        var casNavCount = 0
        var casFallbackCount = 0
        var lastNotFoundNav = ""

        fun navigate(target: String, reason: String) {
            val now = SystemClock.uptimeMillis()
            if (target == lastNavTarget && now - lastNavAt < 10_000L) return
            lastNavTarget = target
            lastNavAt = now
            AppLog.i("WebLogin", "链路A 自动跳转（$reason）: ${target.take(90)}")
            host.view.loadUrl(target)
        }

        /**
         * 阶段推进。
         *
         * - 正常阶段**只前进不回头**：登录过程中页面会短暂经过门户域名下的中转页，
         *   不加限制就会出现「② 之后又闪回 ①」的回跳提示（用户明确反馈过）。
         * - 异常阶段（404 / 门户失败页）可以覆盖显示，但不会改变已到达的正常阶段；
         *   恢复正常后提示会自动切回当前阶段。
         * - 文案没变化就不重复写日志与刷新界面（原来每 2 秒刷一行）。
         */
        fun stage(s: Int) {
            val hint = ujsStageHint(s)
            val isError = s == UJS_STAGE_FALLBACK || s == UJS_STAGE_BLOCKED
            if (isError) {
                showingError = true
            } else if (s <= lastNormalStage) {
                // 不回头；若当前正显示异常提示，则恢复为已到达的阶段
                if (!showingError) return
                showingError = false
            } else {
                lastNormalStage = s
                showingError = false
            }
            if (hint == lastStageShown) return
            lastStageShown = hint
            AppLog.i("WebLogin", "链路A 阶段提示：$hint")
            host.setStatus(hint)
        }

        while (true) {
            delay(if (firstTick) 1_000 else 2_000)
            firstTick = false
            val act = host.view.context as? Activity
            if (act == null || act.isFinishing || act.isDestroyed) break
            val probe = probe(host.view, school) ?: continue
            host.onProbe(probe)
            // 探针原始输出只在内容变化时记日志
            val sign = "${probe.url}|${probe.login}|${probe.grid}|${probe.app}|${probe.ready}|${probe.notFound}"
            if (sign != lastProbeSign) {
                lastProbeSign = sign
                AppLog.d("WebLogin", "链路A 状态: $probe")
            }

            val url = probe.url
            val onCas = isCasPage(url)
            val onCampus = UjsWebVpnFlow.isCampusUrl(url)
            val onJwcEntry = UjsWebVpnFlow.isJwcEntryUrl(url)
            val onSchedulePage = url.startsWith(UjsWebVpnFlow.scheduleUrl().substringBefore('?'))
            // 就绪 = 课表表格已渲染出课程块；或已站在课表页且没有登录表单
            // （后者覆盖「本学期没有课」导致一个课程块都没有的情况）
            val ready = probe.ready || (onSchedulePage && !probe.login)
            // 门户站点内（未走 /http|/https 代理）：/login 是登录页，其余页面说明门户已放行。
            // 排除门户的代理失败页（/wengine-vpn/failed）—— 它也在门户域名下，但那是「不可达」，
            // 交给下面的失败分支处理，否则会被误当成「门户已放行」。
            val onPortalLogin = !probe.proxied && url.contains("/login")
            val onPortalSite = !probe.proxied && url.contains("webvpn.ujs.edu.cn") &&
                !url.contains("wengine-vpn") && !probe.netError

            when {
                // 就绪：登录流程可以结束了
                ready -> {
                    stage(UJS_STAGE_READY)
                    host.markLoggedIn()
                }

                // 统一身份认证（CAS）页：第二步
                onCas -> stage(UJS_STAGE_SSO)

                // 第一步：WebVPN 门户登录页
                onPortalLogin -> stage(UJS_STAGE_PORTAL)

                // 门户站点内的其它页面 = 门户已放行 → 打开统一身份认证（避免停在门户首页）
                onPortalSite -> {
                    stage(UJS_STAGE_PORTAL)
                    if (casNavCount < 2) {
                        casNavCount++
                        navigate(UjsWebVpnFlow.casEntryUrl(), "门户已放行，打开统一身份认证")
                    }
                }

                // 已站在「教务系统登录入口」落地页上：页面交给用户，等他点进去登录
                onJwcEntry -> stage(UJS_STAGE_LOGIN_ENTRY)

                // 页面 404 → 按本链路自己的入口候选序列逐个回退
                probe.notFound -> {
                    stage(UJS_STAGE_FALLBACK)
                    val candidates = UjsWebVpnFlow.entryFallbacks()
                        .filter { it.substringBefore('?') != url.substringBefore('?') }
                    val nxt = candidates.firstOrNull { it != lastNotFoundNav } ?: candidates.firstOrNull()
                    if (nxt != null) {
                        lastNotFoundNav = nxt
                        AppLog.i("WebLogin", "链路A 404，尝试备用入口（共 ${candidates.size} 个）: ${nxt.take(90)}")
                        host.view.loadUrl(nxt)
                    }
                }

                // 门户代理失败页（目标主机不可达）→ 回退到统一身份认证入口
                !onCampus && (probe.netError || url.contains("wengine-vpn")) -> {
                    stage(UJS_STAGE_BLOCKED)
                    if (casFallbackCount < 2) {
                        casFallbackCount++
                        navigate(UjsWebVpnFlow.casEntryUrl(), "门户无法访问当前目标，回退统一身份认证入口")
                    }
                }

                // 已落到教务主机（jwxt / xuanke）
                onCampus -> {
                    if (probe.login) {
                        // 教务系统这层的登录表单：带用户去教务处那个登录入口
                        stage(UJS_STAGE_LOGIN_ENTRY)
                        when {
                            entryNavCount < 1 -> {
                                entryNavCount++
                                navigate(UjsWebVpnFlow.jwcEntryUrl(), "教务系统未登录，打开教务系统登录入口")
                            }
                            casFallbackCount < 2 -> {
                                casFallbackCount++
                                navigate(UjsWebVpnFlow.casEntryUrl(), "登录入口不可达，回退统一身份认证入口")
                            }
                        }
                    } else {
                        // 教务会话已建立：打开课表页（就绪后由上面的 ready 分支置为可完成）
                        stage(UJS_STAGE_PROCESSING)
                        if (!onSchedulePage && scheduleNavCount < 2) {
                            scheduleNavCount++
                            navigate(UjsWebVpnFlow.scheduleUrl(), "已进入教务系统，打开课表页")
                        }
                    }
                }

                else -> stage(UJS_STAGE_WAIT)
            }
        }
    }

    // ------------------------------------------------------------ 链路 B

    /**
     * 链路 B：未勾选「通过 WebVPN」时的登录窗口状态机。
     *
     * 结构与链路 A 完全对称，但**独立成文**：地址与回退候选都来自 [UjsDefaultFlow]，
     * 不调用链路 A 的任何函数。目前两者地址相同（用户确认），后续各自可独立调整。
     */
    private suspend fun runDefaultLogin(host: LoginWindowHost) {
        var firstTick = true
        var lastProbeSign = ""
        var lastStageShown = ""
        var lastNormalStage = 0
        var showingError = false
        var lastNavTarget = ""
        var lastNavAt = 0L
        var scheduleNavCount = 0
        var entryNavCount = 0
        var casNavCount = 0
        var casFallbackCount = 0
        var lastNotFoundNav = ""

        fun navigate(target: String, reason: String) {
            val now = SystemClock.uptimeMillis()
            if (target == lastNavTarget && now - lastNavAt < 10_000L) return
            lastNavTarget = target
            lastNavAt = now
            AppLog.i("WebLogin", "链路B 自动跳转（$reason）: ${target.take(90)}")
            host.view.loadUrl(target)
        }

        /** 阶段推进（只前进不回头 + 不重复刷屏）；规则同链路 A。 */
        fun stage(s: Int) {
            val hint = ujsStageHint(s)
            val isError = s == UJS_STAGE_FALLBACK || s == UJS_STAGE_BLOCKED
            if (isError) {
                showingError = true
            } else if (s <= lastNormalStage) {
                if (!showingError) return
                showingError = false
            } else {
                lastNormalStage = s
                showingError = false
            }
            if (hint == lastStageShown) return
            lastStageShown = hint
            AppLog.i("WebLogin", "链路B 阶段提示：$hint")
            host.setStatus(hint)
        }

        while (true) {
            delay(if (firstTick) 1_000 else 2_000)
            firstTick = false
            val act = host.view.context as? Activity
            if (act == null || act.isFinishing || act.isDestroyed) break
            val probe = probe(host.view, school) ?: continue
            host.onProbe(probe)
            val sign = "${probe.url}|${probe.login}|${probe.grid}|${probe.app}|${probe.ready}|${probe.notFound}"
            if (sign != lastProbeSign) {
                lastProbeSign = sign
                AppLog.d("WebLogin", "链路B 状态: $probe")
            }

            val url = probe.url
            val onCas = isCasPage(url)
            val onCampus = UjsDefaultFlow.isCampusUrl(url)
            val onJwcEntry = UjsDefaultFlow.isJwcEntryUrl(url)
            val onSchedulePage = url.startsWith(UjsDefaultFlow.scheduleUrl().substringBefore('?'))
            val ready = probe.ready || (onSchedulePage && !probe.login)
            val onPortalLogin = !probe.proxied && url.contains("/login")
            val onPortalSite = !probe.proxied && url.contains("webvpn.ujs.edu.cn") &&
                !url.contains("wengine-vpn") && !probe.netError

            when {
                ready -> {
                    stage(UJS_STAGE_READY)
                    host.markLoggedIn()
                }

                onCas -> stage(UJS_STAGE_SSO)

                onPortalLogin -> stage(UJS_STAGE_PORTAL)

                onPortalSite -> {
                    stage(UJS_STAGE_PORTAL)
                    if (casNavCount < 2) {
                        casNavCount++
                        navigate(UjsDefaultFlow.casEntryUrl(), "门户已放行，打开统一身份认证")
                    }
                }

                // 已站在「教务系统登录入口」落地页上：页面交给用户，等他点进去登录
                onJwcEntry -> stage(UJS_STAGE_LOGIN_ENTRY)

                probe.notFound -> {
                    stage(UJS_STAGE_FALLBACK)
                    val candidates = UjsDefaultFlow.entryFallbacks()
                        .filter { it.substringBefore('?') != url.substringBefore('?') }
                    val nxt = candidates.firstOrNull { it != lastNotFoundNav } ?: candidates.firstOrNull()
                    if (nxt != null) {
                        lastNotFoundNav = nxt
                        AppLog.i("WebLogin", "链路B 404，尝试备用入口（共 ${candidates.size} 个）: ${nxt.take(90)}")
                        host.view.loadUrl(nxt)
                    }
                }

                // 门户代理失败页（目标主机不可达）→ 回退到统一身份认证入口
                !onCampus && (probe.netError || url.contains("wengine-vpn")) -> {
                    stage(UJS_STAGE_BLOCKED)
                    if (casFallbackCount < 2) {
                        casFallbackCount++
                        navigate(UjsDefaultFlow.casEntryUrl(), "门户无法访问当前目标，回退统一身份认证入口")
                    }
                }

                onCampus -> {
                    if (probe.login) {
                        stage(UJS_STAGE_LOGIN_ENTRY)
                        when {
                            entryNavCount < 1 -> {
                                entryNavCount++
                                navigate(UjsDefaultFlow.jwcEntryUrl(), "教务系统未登录，打开教务系统登录入口")
                            }
                            casFallbackCount < 2 -> {
                                casFallbackCount++
                                navigate(UjsDefaultFlow.casEntryUrl(), "登录入口不可达，回退统一身份认证入口")
                            }
                        }
                    } else {
                        stage(UJS_STAGE_PROCESSING)
                        if (!onSchedulePage && scheduleNavCount < 2) {
                            scheduleNavCount++
                            navigate(UjsDefaultFlow.scheduleUrl(), "已进入教务系统，打开课表页")
                        }
                    }
                }

                else -> stage(UJS_STAGE_WAIT)
            }
        }
    }
}

/**
 * 是否处于统一身份认证（CAS）页面。
 *
 * 统一身份认证挂在 `pass.<school>`（WebVPN 下走 /https/<pass 段>/cas/…），地址里必含 `/cas/`；
 * 这里同时兼容 authserver 形态。
 *
 * 用途：CAS 页虽然也满足「已过门户」，但它仍是登录页 ——
 * 判断「教务系统是否已登录」时必须把它排除掉。
 */
private fun isCasPage(url: String): Boolean =
    url.contains("/cas/", ignoreCase = true) || url.contains("authserver", ignoreCase = true)
