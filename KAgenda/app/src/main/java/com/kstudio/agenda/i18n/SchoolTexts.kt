package com.kstudio.agenda.i18n

/**
 * 学校专属的**界面文案**表（与抓取机制无关的差异）。
 *
 * 例如「账号」卡片要写出某校教务系统的正式名称（含域名），这类文案不适合放进通用文案，
 * 也不适合在 Zh/En/Fr 三份实现里各写一个 `schoolId == "xxx"` 分支（新增学校要改三处、
 * 容易漏）。统一收敛到本表：**新增学校如需特殊文案，只在这里加一行**。
 *
 * 注意：与「抓取流程」相关的学校差异不放这里，而在 `data/SchoolFlow*.kt`
 * 的学校流程插件里（那才是机制，本表只管显示）。
 */
internal object SchoolTexts {

    /**
     * 「账号」卡片副标题里对该校教务系统的称呼；返回 null 表示用通用文案
     * （「登录<校名>的教务系统」）。
     */
    fun accountSystemName(schoolId: String, lang: AppLang): String? = when (schoolId) {
        // 北航（默认学校）：写明本研教育管理系统的正式名称与域名，便于用户确认登录目标
        "buaa" -> when (lang) {
            AppLang.EN -> "BUAA's academic system (byxt.buaa.edu.cn)"
            AppLang.FR -> "le système académique de BUAA (byxt.buaa.edu.cn)"
            else -> "北航本研教育管理系统（byxt.buaa.edu.cn）"
        }

        else -> null
    }
}
