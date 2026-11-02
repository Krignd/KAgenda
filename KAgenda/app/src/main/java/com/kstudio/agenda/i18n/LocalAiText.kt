package com.kstudio.agenda.i18n

/** Shared by settings, the download worker and all assistant entry points. */
object LocalAiText {
    val originalText get() = text("原通知文本（可选）", "Original notice (optional)", "Notification originale (facultatif)")
    val keepOriginal get() = text("保存原通知文本", "Keep original notice", "Conserver la notification originale")
    val fallback get() = text("本地模型未完成，已提供离线规则预览，请核对", "Local model unavailable; review the offline rule preview", "Modèle local indisponible ; vérifiez l’aperçu hors ligne")
    fun text(zh: String, en: String, fr: String): String = when (AppText.resolve(AppText.state.value)) {
        AppLang.ZH -> zh
        AppLang.FR -> fr
        else -> en
    }
    val title get() = text("本地 AI 模型", "Local AI models", "Modèles IA locaux")
    val intro get() = text(
        "本地模式下，简单指令优先用规则处理；复杂表达使用已启用的本地模型。安装包不含模型，需自行下载。云端模式使用网络服务。所有结果都需确认后才会保存。",
        "Local mode uses rules for simple commands and the enabled local model for complex text. Models are optional downloads. Cloud mode uses an online service. Confirm results before saving.",
        "Le mode local utilise les règles pour les commandes simples et le modèle activé pour les textes complexes. Les modèles sont à télécharger. Le cloud utilise un service en ligne. Confirmez avant d’enregistrer.")
    val local get() = text("本地模式（不上传文本）", "Local mode (text stays on device)", "Mode local (texte sur l’appareil)")
    val cloud get() = text("云端模式（DeepSeek）", "Cloud mode (DeepSeek)", "Mode cloud (DeepSeek)")
    val cloudNote get() = text("仅选择云端模式时，AI 识别才会发送文本与必要日程字段。学校适配代码生成仍使用云端。", "AI parsing sends text and relevant event fields only in cloud mode. School adapter generation still uses the cloud.", "L’analyse IA envoie le texte et les champs utiles uniquement en mode cloud. La génération d’adaptateurs reste dans le cloud.")
    val light get() = text("轻量版 · 运行负担较小，复杂表达请仔细核对；建议 6GB 物理内存", "Light · lower resource use; check complex text carefully. 6GB physical RAM recommended", "Léger · moins de ressources ; vérifiez les textes complexes. 6 Go de RAM physique conseillés")
    val standard get() = text("标准版 · 模型更大，实际耗时取决于手机；建议 8GB 物理内存", "Standard · larger model; speed depends on the phone. 8GB physical RAM recommended", "Standard · modèle plus volumineux ; vitesse selon le téléphone. 8 Go de RAM physique conseillés")
    val download get() = text("下载", "Download", "Télécharger")
    val resume get() = text("继续下载", "Resume", "Reprendre")
    val cancel get() = text("暂停下载", "Pause download", "Suspendre le téléchargement")
    val delete get() = text("删除模型", "Delete model", "Supprimer le modèle")
    val use get() = text("启用此模型", "Enable this model", "Activer ce modèle")
    val selected get() = text("已启用", "Enabled", "Activé")
    val ready get() = text("已下载并校验，尚未启用", "Downloaded and verified; not enabled", "Téléchargé et vérifié, non activé")
    val notDownloaded get() = text("未下载", "Not downloaded", "Non téléchargé")
    val waiting get() = text("等待网络或系统调度…", "Waiting for network or system scheduling…", "En attente du réseau ou du système…")
    val verifying get() = text("正在校验完整性…", "Verifying integrity…", "Vérification de l’intégrité…")
    val downloading get() = text("下载中", "Downloading", "Téléchargement")
    val partial get() = text("已有部分文件，可继续下载或清除", "Partial files kept; resume or remove them", "Fichiers partiels conservés ; reprenez ou effacez-les")
    val downloadSize get() = text("下载大小", "Download size", "Taille du téléchargement")
    val removePartial get() = text("清除下载文件", "Remove download files", "Effacer les fichiers téléchargés")
    val downloadHint get() = text("只需启用一个模型。未启用已下载的模型不会用于识别；暂停下载会保留文件，可继续下载或清除。", "Enable only one model at a time. Downloaded models are used only when enabled. Pausing keeps files so you can resume or remove them.", "Un seul modèle actif à la fois. Seul le modèle activé est utilisé. La pause conserve les fichiers pour reprendre ou les effacer.")
    val rulesMode get() = text("当前：仅规则识别（尚无可用模型）", "Current: rules only (no model available)", "Actuellement : règles seules (aucun modèle disponible)")
    val rulesHint get() = text("快速规则识别始终离线，适合格式明确的简单指令；智能识别按上方模式处理。", "Quick rules always work offline for simple, explicit commands. Smart parse follows the mode above.", "Les règles rapides fonctionnent hors ligne pour les commandes simples et explicites. L’analyse intelligente suit le mode ci-dessus.")
    val noModelHint get() = text("复杂文字需要模型。可在设置 → 功能&权限 → AI 识别下载并启用本地模型，或选择云端模式。", "Complex text needs a model. In Settings → Features & permissions → AI, download and enable a local model or select cloud mode.", "Les textes complexes nécessitent un modèle. Dans Réglages → Fonctions et autorisations → IA, activez un modèle local ou choisissez le cloud.")
    val wifiOnly get() = text("仅通过不限流量的网络下载（通常为 Wi-Fi）", "Use an unmetered network only (usually Wi-Fi)", "Réseau non facturé uniquement (généralement Wi-Fi)")
    val source get() = text("来源：Hugging Face / Unsloth，Qwen3，Apache-2.0。网络不可达时可稍后重试。", "Source: Hugging Face / Unsloth, Qwen3, Apache-2.0. Retry later if unreachable.", "Source : Hugging Face / Unsloth, Qwen3, Apache-2.0. Réessayez si le site est inaccessible.")
    val unsupported get() = text("本地模型需要 64 位设备与 64 位应用进程；仍可使用规则或云端识别。", "Local models require a 64-bit device and app process. Rules and cloud parsing remain available.", "Les modèles locaux nécessitent un appareil et un processus 64 bits. Les règles et le cloud restent disponibles.")
    val needModel get() = text("规则无法处理这条指令。可在设置 → 功能&权限 → AI 识别下载本地模型，或选择云端模式", "Download a local model in Settings → Features & permissions → AI, or select cloud mode", "Téléchargez un modèle dans Réglages → Fonctions et autorisations → IA, ou choisissez le cloud")
    val rulesOnly get() = text("仅使用规则识别（当前没有可用的本地模型）；请核对结果，确认后才会保存", "Rules only (no local model available). Review and confirm to save.", "Règles seules (aucun modèle local disponible). Vérifiez et confirmez pour enregistrer.")
    val invalidReply get() = text("模型未返回完整有效的操作，请缩短输入后重试；未写入日程", "The model returned invalid or incomplete operations. Shorten the input and retry; no events were saved.", "Opérations invalides ou incomplètes. Raccourcissez le texte ; aucun événement enregistré.")
    fun error(code: String) = when (code) {
        "space" -> text("空间不足，请清理手机存储后重试", "Not enough storage. Free up space and retry.", "Stockage insuffisant. Libérez de l’espace.")
        "checksum" -> text("模型校验失败，请重新下载", "Verification failed. Download again.", "Vérification échouée. Téléchargez à nouveau.")
        "input_too_long" -> text("输入或相关日程过长，请分批识别", "Input or event context is too long. Process smaller batches.", "Texte ou contexte trop long. Procédez par lots.")
        "cancelled_or_timeout" -> text("本地识别超过 90 秒或已取消，请缩短输入", "Local parsing timed out after 90 seconds or was cancelled. Shorten the input.", "Analyse annulée ou délai de 90 secondes dépassé. Raccourcissez le texte.")
        "model_load", "context_load", "native_failure" -> text("模型加载失败，可能是可用内存不足；请关闭其他应用或换用轻量版", "Model loading failed. Free up memory or use the light model.", "Chargement impossible. Libérez de la mémoire ou utilisez le modèle léger.")
        "network" -> text("下载中断或网络不可达，可继续下载", "Download interrupted or network unavailable. You can resume.", "Téléchargement interrompu ou réseau inaccessible. Vous pouvez reprendre.")
        else -> invalidReply
    }
}
