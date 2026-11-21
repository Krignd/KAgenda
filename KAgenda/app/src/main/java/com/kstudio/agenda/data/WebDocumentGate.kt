package com.kstudio.agenda.data

import org.json.JSONObject

/** 用文档标记区分导航前的旧 DOM；URL 改变或迟到的 onPageFinished 都不能替代文档提交。 */
internal object WebDocumentGate {
    fun markScript(marker: String) =
        "(function(){window.__kagendaPreviousDocument=${JSONObject.quote(marker)};return JSON.stringify(true);})();"

    fun readyScript(marker: String) =
        "(function(){return JSON.stringify(window.__kagendaPreviousDocument!==${JSONObject.quote(marker)} && document.readyState==='complete');})();"
}
