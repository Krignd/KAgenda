package com.kstudio.agenda.util

/**
 * 应用是否处于前台（由 MainActivity 的 onResume/onPause 维护）。
 * 悬浮球据此决定点按行为：应用在前台 → 打开应用内的 AI 助手界面；否则 → 打开悬浮输入面板。
 */
object AppPresence {

    @Volatile
    var visible: Boolean = false
}
