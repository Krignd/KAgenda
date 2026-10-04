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
import com.kstudio.agenda.ui.theme.GlassBackdrop
import com.kstudio.agenda.ui.theme.KAgendaTheme
import com.kstudio.agenda.util.AppPresence
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
    /** 由其他应用「用 K日程 打开 / 分享到」传入的文档（进入文档导入页） */
    val docUri: android.net.Uri? = null,
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
            val vm: AppViewModel = viewModel()
            // 界面风格（默认 / 液态玻璃）由设置驱动，切换后立即生效
            val settings by vm.settings.collectAsState()
            val glass = settings.glassUi
            CompositionLocalProvider(LocalStrings provides AppText.stringsFor(lang)) {
                KAgendaTheme(glass = glass) {
                    // 液态玻璃：先铺渐变背景（含两团柔光），内容叠在其上形成毛玻璃观感
                    if (glass) GlassBackdrop {
                        MainScreen(
                            vm = vm,
                            launch = launchRequest.value,
                            onLaunchHandled = { launchRequest.value = null },
                        )
                    } else {
                        MainScreen(
                            vm = vm,
                            launch = launchRequest.value,
                            onLaunchHandled = { launchRequest.value = null },
                        )
                    }
                }
            }
        }
        handleIntent(intent)
        // 悬浮球：设置已开启且获得「显示在其他应用上层」权限时，确保前台服务在运行；
        // 未开启或权限缺失时确保没有残留（“没有悬浮球权限就不显示悬浮球”）
        lifecycleScope.launch {
            val s = runCatching { SettingsStore.read(this@MainActivity) }.getOrNull()
            if (s?.floatingBall == true && Settings.canDrawOverlays(this@MainActivity)) {
                FloatingBallService.start(this@MainActivity)
            } else {
                FloatingBallService.stop(this@MainActivity)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 悬浮球据此判断：点按时应用是否在前台（前台 → 打开应用内 AI 助手界面）
        AppPresence.visible = true
        // 应用回到前台时收起悬浮球面板：同一时刻只保留一个 AI 助手界面
        if (AiSurface.state.value == AiSurface.Kind.Overlay) AiSurface.close(AiSurface.Kind.Overlay)
    }

    override fun onPause() {
        AppPresence.visible = false
        super.onPause()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        // AI 识别完成的通知点进来：直接打开 AI 助手界面（结果已在 ViewModel 里）
        // 字符串字面量与 Notifier.EXTRA_OPEN_AI_RESULT 保持一致
        val openAiResult = intent.getBooleanExtra("kagenda_open_ai_result", false)
        val quickAdd = intent.getBooleanExtra(EXTRA_QUICK_ADD, false) || openAiResult
        val focusDay = if (intent.hasExtra(EXTRA_FOCUS_EPOCH_DAY)) {
            intent.getLongExtra(EXTRA_FOCUS_EPOCH_DAY, -1L).takeIf { it >= 0 }
        } else null
        // 其他应用「用 K日程 打开」/「分享到 K日程」带进来的文档
        val docUri = docUriFrom(intent)
        if (!quickAdd && focusDay == null && docUri == null) return
        launchRequest.value = LaunchRequest(
            quickAdd = quickAdd,
            focusEpochDay = focusDay,
            focusTitle = intent.getStringExtra(EXTRA_FOCUS_TITLE).orEmpty(),
            fromWidget = intent.getBooleanExtra(EXTRA_FOCUS_FROM_WIDGET, false),
            docUri = docUri,
        )
        // 消费后清除 extras：避免旋转/恢复时重复触发快速添加或闪烁
        intent.removeExtra(EXTRA_QUICK_ADD)
        intent.removeExtra("kagenda_open_ai_result")
        intent.removeExtra(EXTRA_FOCUS_EPOCH_DAY)
        intent.removeExtra(EXTRA_FOCUS_TITLE)
        intent.removeExtra(EXTRA_FOCUS_FROM_WIDGET)
    }

    /**
     * 取出其他应用传入的文档 Uri：
     * - ACTION_VIEW / ACTION_EDIT：intent.data
     * - ACTION_SEND / ACTION_SEND_MULTIPLE：EXTRA_STREAM（Android 10+ 也可能放在 clipData）
     */
    private fun docUriFrom(intent: Intent): android.net.Uri? {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let { return it }
            }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)?.let { return it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM))
                    ?.firstOrNull()?.let { return it }
            }
        }
        // 兜底：从 ClipData 取（部分应用只给 ClipData）
        val clip = intent.clipData ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).uri
    }

    companion object {
        const val EXTRA_QUICK_ADD = "com.kstudio.agenda.extra.QUICK_ADD"
        const val EXTRA_FOCUS_EPOCH_DAY = "com.kstudio.agenda.extra.FOCUS_EPOCH_DAY"
        const val EXTRA_FOCUS_TITLE = "com.kstudio.agenda.extra.FOCUS_TITLE"
        const val EXTRA_FOCUS_FROM_WIDGET = "com.kstudio.agenda.extra.FOCUS_FROM_WIDGET"
    }
}
