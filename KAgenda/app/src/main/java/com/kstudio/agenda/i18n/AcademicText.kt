package com.kstudio.agenda.i18n

import com.kstudio.agenda.data.SchoolCatalog
import com.kstudio.agenda.model.School

/** Import-specific copy; source identities and original descriptions stay in their original language. */
object AcademicText {
    private fun localized(zh: String, en: String, fr: String): String = when (AppText.resolve(AppText.state.value)) {
        AppLang.EN -> en
        AppLang.FR -> fr
        else -> zh
    }

    val unknownSystem: String get() = localized("未确定", "Undetermined", "Non déterminé")
    val hideUndeterminedSchools: String get() = localized("隐藏未确定的学校", "Hide undetermined schools", "Masquer les établissements non déterminés")

    /** Same semantic category as the label; a known type need not have a dedicated importer. */
    fun isUndetermined(school: School): Boolean =
        !(school.supported && school.warehouseId.isBlank()) &&
            SchoolCatalog.knownSystemTypes(school).none { systemName(it) != null } &&
            !SchoolCatalog.hasAdditionalSource(school) && school.warehouseId.isBlank() &&
            SchoolCatalog.usesBrowserImport(school)

    fun systemName(type: String): String? = when (type) {
        "zf_new" -> localized("新正方", "New Zhengfang", "Nouveau Zhengfang")
        "zf", "zf_1" -> localized("旧正方", "Legacy Zhengfang", "Ancien Zhengfang")
        "zhengfang" -> localized("正方", "Zhengfang", "Zhengfang")
        "qz", "qz_with_node", "qz_br", "qz_crazy" -> localized("强智", "Qiangzhi", "Qiangzhi")
        "qz_old" -> localized("旧强智", "Legacy Qiangzhi", "Ancien Qiangzhi")
        "jinzhi" -> localized("金智", "Jinzhi", "Jinzhi")
        "urp" -> "URP"
        "urp_new" -> localized("新 URP", "New URP", "Nouveau URP")
        "cf" -> localized("青果", "Kingosoft", "Kingosoft")
        "chaoxing" -> localized("超星", "Chaoxing", "Chaoxing")
        "eams" -> "EAMS"
        else -> null
    }

    fun schoolSupportLabel(school: School, strings: AppStrings = AppText.current): String = when {
        school.supported && school.warehouseId.isBlank() -> strings.independentSupport
        else -> SchoolCatalog.knownSystemTypes(school).mapNotNull(::systemName).distinct().joinToString(" / ").ifBlank {
            when {
                SchoolCatalog.hasAdditionalSource(school) -> dedicated
                school.warehouseId.isNotBlank() -> strings.warehouseSupport
                SchoolCatalog.usesBrowserImport(school) -> unknownSystem
                else -> strings.unsupportedSchool
            }
        }
    }

    /** Presentation names only; original source metadata, identifiers and parser selection stay intact. */
    fun adapterName(adapter: SchoolCatalog.Adapter): String = when (adapter.parser.ifBlank { adapter.id }) {
        "zf_new" -> localized("新正方教务系统 · HTML", "New Zhengfang · HTML", "Nouveau Zhengfang · HTML")
        "zhengfang_01" -> localized("正方教务系统 · HTML", "Zhengfang · HTML", "Zhengfang · HTML")
        "zf" -> localized("正方教务系统 · 旧版", "Zhengfang · legacy", "Zhengfang · ancienne version")
        "zf_1" -> localized("正方教务系统 · 旧版变体", "Zhengfang · legacy variant", "Zhengfang · variante ancienne")
        "qz" -> localized("强智教务系统", "Qiangzhi academic system", "Système pédagogique Qiangzhi")
        "qz_with_node" -> localized("强智教务系统 · 明确节次", "Qiangzhi · explicit sections", "Qiangzhi · périodes explicites")
        "qz_br" -> localized("强智教务系统 · BR", "Qiangzhi · BR", "Qiangzhi · BR")
        "qz_crazy" -> localized("强智教务系统 · Crazy", "Qiangzhi · Crazy", "Qiangzhi · Crazy")
        "qz_old" -> localized("强智教务系统 · 旧版", "Qiangzhi · legacy", "Qiangzhi · ancienne version")
        "urp_01", "urp" -> localized("URP 教务系统", "URP academic system", "Système pédagogique URP")
        "urp_new" -> localized("URP 教务系统 · 新版", "URP · new version", "URP · nouvelle version")
        "qingguo_01", "cf" -> localized("青果教务系统", "Kingosoft academic system", "Système pédagogique Kingosoft")
        "chaoxing" -> localized("超星教务系统", "Chaoxing academic system", "Système pédagogique Chaoxing")
        else -> adapter.name
    }

    val dedicated: String get() = when (AppText.resolve(AppText.state.value)) {
        AppLang.EN -> "School adapter · unverified"
        AppLang.FR -> "Adaptateur d’établissement · non vérifié"
        else -> "本校适配 · 待验证"
    }
    data class Strings(val systemOptions: String, val historical: String, val address: String, val open: String,
        val invalidAddress: String, val unverified: String, val failed: String, val legacy: String,
        val importTitle: String, val source: String, val updated: String, val blockedFrames: String)
    val httpBlocked: String get() = when (AppText.resolve(AppText.state.value)) {
        AppLang.EN -> "This HTTP host is not in the bundled portal allowlist. Use the school's HTTPS address or request an adapter."
        AppLang.FR -> "Cet hôte HTTP n’est pas dans la liste des portails. Utilisez l’adresse HTTPS de l’établissement ou demandez un adaptateur."
        else -> "该 HTTP 主机尚未列入教务清单，请使用学校的 HTTPS 地址或提交适配请求。"
    }
    val current: Strings get() = when (AppText.resolve(AppText.state.value)) {
        AppLang.EN -> Strings("Options: Zhengfang / Qiangzhi / URP / Kingosoft / Chaoxing", "Historical adapter · unverified", "Academic portal URL", "Open",
            "Enter an http(s) URL without credentials.",
            "Choose Zhengfang, Qiangzhi, URP, Kingosoft or Chaoxing to match your school's portal. These options and historical adapters remain unverified for this school. Review all courses, weeks and times before saving.",
            "No complete timetable was parsed. Open the timetable page, check the semester and try another system type. Existing data has been kept.",
            "Legacy parser; a recent repository snapshot does not mean recent school validation.",
            "Academic portal import", "Adapter source", "Script updated", "Open the timetable frame directly if extraction fails.")
        AppLang.FR -> Strings("Options : Zhengfang / Qiangzhi / URP / Kingosoft / Chaoxing", "Ancien adaptateur · non vérifié", "URL du portail pédagogique", "Ouvrir",
            "Saisissez une URL http(s) sans identifiants.",
            "Choisissez Zhengfang, Qiangzhi, URP, Kingosoft ou Chaoxing selon le portail de votre établissement. Ces options et les anciens adaptateurs restent non vérifiés. Vérifiez les cours, les semaines et les horaires avant de sauvegarder.",
            "Aucun emploi du temps complet reconnu. Ouvrez la page des cours, vérifiez le semestre et essayez un autre système. Les données existantes sont conservées.",
            "Ancien parseur ; la date du dépôt ne prouve pas une validation récente.",
            "Import du portail pédagogique", "Source de l’adaptateur", "Script modifié", "En cas d’échec, ouvrez directement le cadre de l’emploi du temps.")
        else -> Strings("可选：正方 / 强智 / URP / 青果 / 超星", "历史适配 · 待验证", "教务系统网址", "打开",
            "请输入不含账号密码的 http(s) 网址。",
            "请按学校实际教务系统选择正方、强智、URP、青果或超星。这些选项及历史适配尚未经过本校账号验证，导入前请逐项确认课程、周次、学期和作息。",
            "未解析出完整课表。请进入课表页面，核对学期后尝试其他教务类型；原有课表已保留。",
            "旧版解析器；仓库快照日期不代表本校适配最近更新或已实测。",
            "教务系统导入", "适配代码来源", "脚本修改时间", "如课表位于嵌入页面且提取失败，请直接打开该课表页面。")
    }
}
