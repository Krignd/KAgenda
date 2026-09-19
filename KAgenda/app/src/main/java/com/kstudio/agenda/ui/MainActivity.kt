package com.kstudio.agenda.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.overlay.FloatingBallService
import com.kstudio.agenda.ui.theme.KAgendaTheme
import kotlinx.coroutines.launch

/**
 * 由通知 / 小组件点击带入的一次性启动请求：
 * - quickAdd：打开 AI 快速添加对话
 * - focusEpochDay/focusTitle：定位到指定日期（周/日/月视图）并闪烁对应课程/日期
 */
data class LaunchRequest(
    val quickAdd: Boolean,
    val focusEpochDay: Long?,
    val focusTitle: String,
    val fromWidget: Boolean = false,
    val id: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {

    private val launchRequest = mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 语言状态由 AppText.state 驱动：切换语言即时重组全部界面（无需重建 Activity）
            val lang by AppText.state.collectAsState()
            CompositionLocalProvider(LocalStrings provides AppText.stringsFor(lang)) {
                KAgendaTheme {
                    val vm: AppViewModel = viewModel()
                    MainScreen(
                        vm = vm,
                        launch = launchRequest.value,
                        onLaunchHandled = { launchRequest.value = null },
                    )
                }
            }
        }
        handleIntent(intent)
        // 悬浮球：设置已开启且获得「显示在其他应用上层」权限时，确保前台服务在运行
        lifecycleScope.launch {
            val s = runCatching { SettingsStore.read(this@MainActivity) }.getOrNull()
            if (s?.floatingBall == true && Settings.canDrawOverlays(this@MainActivity)) {
                FloatingBallService.start(this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val quickAdd = intent.getBooleanExtra(EXTRA_QUICK_ADD, false)
        val focusDay = if (intent.hasExtra(EXTRA_FOCUS_EPOCH_DAY)) {
            intent.getLongExtra(EXTRA_FOCUS_EPOCH_DAY, -1L).takeIf { it >= 0 }
        } else null
        if (!quickAdd && focusDay == null) return
        launchRequest.value = LaunchRequest(
            quickAdd = quickAdd,
            focusEpochDay = focusDay,
            focusTitle = intent.getStringExtra(EXTRA_FOCUS_TITLE).orEmpty(),
            fromWidget = intent.getBooleanExtra(EXTRA_FOCUS_FROM_WIDGET, false),
        )
        // 消费后清除 extras：避免旋转/恢复时重复触发快速添加或闪烁
        intent.removeExtra(EXTRA_QUICK_ADD)
        intent.removeExtra(EXTRA_FOCUS_EPOCH_DAY)
        intent.removeExtra(EXTRA_FOCUS_TITLE)
        intent.removeExtra(EXTRA_FOCUS_FROM_WIDGET)
    }

    companion object {
        const val EXTRA_QUICK_ADD = "com.kstudio.agenda.extra.QUICK_ADD"
        const val EXTRA_FOCUS_EPOCH_DAY = "com.kstudio.agenda.extra.FOCUS_EPOCH_DAY"
        const val EXTRA_FOCUS_TITLE = "com.kstudio.agenda.extra.FOCUS_TITLE"
        const val EXTRA_FOCUS_FROM_WIDGET = "com.kstudio.agenda.extra.FOCUS_FROM_WIDGET"
    }
}
