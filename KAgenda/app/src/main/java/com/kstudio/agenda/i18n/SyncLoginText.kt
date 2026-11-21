package com.kstudio.agenda.i18n

object SyncLoginText {
    val incomplete: String get() = when (AppText.resolve(AppText.state.value)) {
        AppLang.ZH -> "认证跳转未完成，或教务系统连接异常，请重试同步；若仍失败，请查看日志"
        AppLang.FR -> "Redirection d’authentification inachevée ou connexion indisponible. Réessayez la synchronisation ; si l’échec persiste, consultez le journal."
        else -> "Authentication redirect did not finish or the portal connection failed. Retry sync; check the logs if it keeps failing."
    }
}
