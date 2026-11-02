package com.kstudio.agenda.i18n

import java.text.Normalizer
import java.util.Locale

data class HelpQuestion(val id: String, val title: String, val answer: String)

/** Search both the question and its answer; accept case and accent variations. */
fun searchHelpQuestions(questions: List<HelpQuestion>, query: String): List<HelpQuestion> {
    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    val words = normalize(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (words.isEmpty()) questions else questions.filter { question ->
        val text = normalize("${question.title} ${question.answer}")
        words.all { it in text }
    }
}

internal object HelpContent {
    val zh = listOf(
        HelpQuestion("lock_screen", "锁屏上看不到通知？",
            "部分系统（如 MIUI/HyperOS、EMUI）会默认隐藏静默通知。请先在「设置 → 功能&权限 → 常驻通知」开启常驻通知和锁屏显示，再在系统通知设置中为 K日程开启「锁屏通知」。若仍不显示，请检查系统是否隐藏了锁屏通知内容。"),
        HelpQuestion("reminders", "收不到提醒或提醒延迟怎么办？",
            "在「设置 → 功能&权限 → 权限设置」检查通知、精确闹钟和电池优化权限。确认课程或日程已开启提醒，日程填写了开始时间；若系统限制后台运行，请允许 K日程在后台运行。"),
        HelpQuestion("sync", "课表同步失败怎么办？",
            "先在「设置 → 学校&账号」确认学校和登录信息。江苏大学等需要手动认证的学校，请通过「网页登录」完成认证；第三步若出现「点此直接登录」或「CAS 统一身份认证登录」，请点击该入口，进入课表页后再点右上角「完成」返回同步。若网页可正常访问但仍同步失败，可在「开发者工具」查看并分享运行日志。"),
        HelpQuestion("course_edits", "如何修改课程或恢复课表？",
            "点开课程详情，选择「修改课程」，再选择仅修改这一次或全部同一课程。修改保存在本机。可在「设置 → 课程修改」还原所选课程，或清除全部修改后重新同步教务课表。"),
        HelpQuestion("backup", "如何备份并在新设备恢复？",
            "在「设置 → 数据&图片 → 数据备份与导入」导出备份，并将文件保存在可访问的位置。新设备安装后导入该文件即可恢复数据；登录密码和 API Key 需重新填写。"),
        HelpQuestion("documents", "如何导入文档中的课程和日程？",
            "在「设置 → 数据&图片 → 文档导入」选择 Word、Excel、PPT、PDF 或文本文件，读取后核对识别结果并选择导入。也可从文件管理器使用 K日程打开文档。扫描图片类 PDF 可能无法提取文字。"),
        HelpQuestion("ai", "如何使用 AI 助手？",
            "点击顶栏的 AI 助手入口，描述要添加或修改的日程。在「设置 → 功能&权限 → AI 识别」可选下载轻量版或标准版本地模型，下载并启用后可处理复杂文字；「快速规则识别」无需模型，适合明确的简单指令。未下载模型时只显示规则识别入口。也可主动选择云端模式。身份预设有助于识别通知中与你相关的安排，确认识别结果后再应用。"),
    )

    val en = listOf(
        HelpQuestion("lock_screen", "Notification missing on the lock screen?",
            "Some systems, including MIUI/HyperOS and EMUI, hide silent notifications by default. Enable persistent notifications and lock screen display in Settings → Features & permissions → Persistent notification, then allow lock screen notifications for K日程 in system notification settings. If they are still missing, check whether the system hides notification content on the lock screen."),
        HelpQuestion("reminders", "Why are reminders missing or delayed?",
            "Check notification, exact alarm and battery optimization permissions in Settings → Features & permissions → Permissions. Enable reminders for the course or event and give the event a start time. Allow K日程 to run in the background if your system restricts it."),
        HelpQuestion("sync", "What should I do if timetable sync fails?",
            "Check your school and login details in Settings → School & account. For schools requiring manual authentication, such as Jiangsu University, use Web login. In step 3, tap “点此直接登录” (direct login) or “CAS 统一身份认证登录” (CAS login) if shown. Once the timetable appears, tap Done at the top right to return and sync. If the website works but syncing still fails, view and share the runtime log from Developer tools."),
        HelpQuestion("course_edits", "How do I edit courses or restore the timetable?",
            "Open a course, choose Edit course, then select this occurrence or all occurrences of the course. Changes are saved locally. In Settings → Course edits, restore selected courses or clear all edits and sync the school timetable again."),
        HelpQuestion("backup", "How do I back up and restore on a new device?",
            "Export a backup from Settings → Data & images → Backup & restore and keep the file somewhere accessible. Import it after installing on your new device to restore your data. Enter login passwords and API keys again."),
        HelpQuestion("documents", "How do I import courses and events from a document?",
            "Choose a Word, Excel, PPT, PDF or text file in Settings → Data & images → Document import. Review the detected items before importing. You can also open documents with K日程 from your file manager. Scanned PDFs may not contain extractable text."),
        HelpQuestion("ai", "How do I use the AI assistant?",
            "Open the AI assistant from the top bar and describe the events to add or edit. In Settings → Features & permissions → AI, optionally download a light or standard model for offline use, then enable it. Quick rules need no model and handle explicit simple commands; only rules are shown until a model is available. You can also choose cloud mode. Your identity profile helps identify relevant arrangements in notices. Review the results before applying them."),
    )

    val fr = listOf(
        HelpQuestion("lock_screen", "Notification absente de l’écran verrouillé ?",
            "Certains systèmes, comme MIUI/HyperOS et EMUI, masquent les notifications silencieuses par défaut. Activez la notification permanente et son affichage sur l’écran verrouillé dans Réglages → Fonctions et autorisations → Notification permanente, puis autorisez les notifications sur écran verrouillé pour K日程 dans les réglages système. Vérifiez aussi si le système masque leur contenu."),
        HelpQuestion("reminders", "Pourquoi les rappels sont-ils absents ou en retard ?",
            "Vérifiez les autorisations de notification, d’alarme exacte et d’optimisation de la batterie dans Réglages → Fonctions et autorisations → Autorisations. Activez les rappels du cours ou de l’événement et indiquez une heure de début. Si le système limite l’activité en arrière-plan, autorisez K日程 à y fonctionner."),
        HelpQuestion("sync", "Que faire si la synchronisation des cours échoue ?",
            "Vérifiez l’établissement et les identifiants dans Réglages → École et compte. Pour les établissements exigeant une authentification manuelle, comme l’université du Jiangsu, utilisez la Connexion web. À l’étape 3, touchez « 点此直接登录 » (connexion directe) ou « CAS 统一身份认证登录 » (connexion CAS) si ce lien apparaît. Une fois l’emploi du temps affiché, touchez Terminé en haut à droite pour revenir synchroniser. Si le site fonctionne mais que la synchronisation échoue, consultez et partagez le journal depuis Outils développeur."),
        HelpQuestion("course_edits", "Comment modifier les cours ou restaurer l’emploi du temps ?",
            "Ouvrez un cours, choisissez Modifier le cours, puis cette occurrence ou toutes les occurrences du même cours. Les modifications sont locales. Dans Réglages → Modifications de cours, restaurez les cours sélectionnés ou effacez toutes les modifications avant de resynchroniser."),
        HelpQuestion("backup", "Comment sauvegarder et restaurer sur un nouvel appareil ?",
            "Exportez une sauvegarde depuis Réglages → Données et images → Sauvegarde et restauration et conservez le fichier dans un emplacement accessible. Importez-le après l’installation sur le nouvel appareil. Saisissez à nouveau les mots de passe et les clés API."),
        HelpQuestion("documents", "Comment importer des cours et événements depuis un document ?",
            "Choisissez un fichier Word, Excel, PPT, PDF ou texte dans Réglages → Données et images → Import de documents. Vérifiez les éléments reconnus avant de les importer. Vous pouvez aussi ouvrir le document avec K日程 depuis le gestionnaire de fichiers. Les PDF numérisés peuvent ne pas contenir de texte extractible."),
        HelpQuestion("ai", "Comment utiliser l’assistant IA ?",
            "Ouvrez l’assistant IA dans la barre supérieure et décrivez les événements à ajouter ou modifier. Dans Réglages → Fonctions et autorisations → IA, téléchargez un modèle léger ou standard pour une utilisation hors ligne, puis activez-le. Les règles rapides traitent les commandes simples sans modèle ; seules les règles sont affichées sans modèle disponible. Le mode cloud reste optionnel. Le profil d’identité aide à repérer les informations qui vous concernent. Vérifiez les résultats avant de les appliquer."),
    )
}
