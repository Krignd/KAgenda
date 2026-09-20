package com.kstudio.agenda.overlay

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kstudio.agenda.R
import com.kstudio.agenda.data.AgendaStore
import com.kstudio.agenda.data.AiClient
import com.kstudio.agenda.data.AiSkills
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.notif.StatusNotification
import com.kstudio.agenda.ui.AiSurface
import com.kstudio.agenda.ui.MainActivity
import com.kstudio.agenda.ui.aiOpBadgeLabel
import com.kstudio.agenda.ui.aiOpSummary
import com.kstudio.agenda.data.AiAssistant
import com.kstudio.agenda.util.AppPresence
import com.kstudio.agenda.widget.NextClassWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/**
 * 系统级 AI 悬浮球（TYPE_APPLICATION_OVERLAY，可显示在其他应用之上）：
 * - 悬浮球：拖动移动；松手后自动吸附到最近的屏幕侧边；点按打开快速输入面板；长按打开 App；
 * - 输入面板：直接在当前应用上方输入文字 → DeepSeek 多条目识别 → 全部添加到本地日程/计划，
 *   全程无需离开当前应用（亦可一键转去 App 内界面）；
 * - 前台服务常驻，随「设置 → 悬浮球」开关启停；需「显示在其他应用上层」权限。
 */
class FloatingBallService : Service() {

    companion object {
        const val ACTION_STOP = "com.kstudio.agenda.overlay.action.STOP"
        private const val CHANNEL_ID = "floating_ball"
        private const val NOTIF_ID = 925220
        private const val PREFS = "floating_ball_pos"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_PANEL_X = "panel_x"
        private const val KEY_PANEL_Y = "panel_y"
        /** 闲置（未触摸/未拖动）时的透明度：数值越小越通透，避免遮挡其他应用内容 */
        private const val REST_ALPHA = 0.55f

        /** 悬浮窗权限轮询间隔：权限被系统/用户收回后立即隐藏悬浮球 */
        private const val PERMISSION_POLL_MS = 3_000L

        /** 开启悬浮球（未授予悬浮窗权限时不动作） */
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingBallService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingBallService::class.java)) }
        }

        fun isRunning(context: Context): Boolean = running

        @Volatile
        private var running = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    private var ball: View? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null

    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var parsedOps: List<AiSkills.AiOp> = emptyList()
    private var busy = false

    /** 面板中的输入框与反馈文本（收起时用于保存本次输入） */
    private var panelInput: EditText? = null
    private var panelMsgText: String = ""

    /** 上次未提交的输入（收起面板后保留，重新打开可直接继续编辑/添加） */
    private var draftText: String = ""
    private var draftOps: List<AiSkills.AiOp> = emptyList()
    private var draftMsg: String = ""

    /** 在飞识别请求：收起面板时取消，避免下次打开面板时被 busy 拦住长时间无响应 */
    private var requestJob: Job? = null

    /** 悬浮窗权限守护 / AI 界面互斥监听 */
    private var permissionJob: Job? = null
    private var surfaceJob: Job? = null

    private val ballSizePx: Int get() = dp(54)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        running = true
        // 权限在运行中被收回（系统重置 / 卸载重装 / 用户手动关闭）时立即隐藏悬浮球，
        // 避免“没有权限却还显示着悬浮球”的假象
        permissionJob = scope.launch {
            while (true) {
                delay(PERMISSION_POLL_MS)
                if (!Settings.canDrawOverlays(this@FloatingBallService)) {
                    hidePanel(keepDraft = true)
                    stopSelf()
                    break
                }
            }
        }
        // AI 助手界面互斥：应用内对话框打开时收起悬浮球面板（同一时刻只保留后者）
        surfaceJob = scope.launch {
            AiSurface.state.collect { kind ->
                if (kind != AiSurface.Kind.Overlay && panel != null) hidePanel(keepDraft = true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 未授予「显示在其他应用上层」权限时：不显示悬浮球
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundNow()
        // 设置里已关闭时：不创建悬浮球并退出（含系统重启服务后的兜底）
        scope.launch {
            val enabled = runCatching { SettingsStore.read(this@FloatingBallService).floatingBall }
                .getOrDefault(false)
            if (!enabled) {
                hidePanel(keepDraft = true)
                stopSelf()
            } else if (ball == null) {
                runCatching { addBall() }.onFailure { stopSelf() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        snapAnimator?.cancel()
        permissionJob?.cancel()
        surfaceJob?.cancel()
        hidePanel(keepDraft = true)
        AiSurface.close(AiSurface.Kind.Overlay)
        ball?.let { runCatching { windowManager.removeView(it) } }
        ball = null
        scope.cancel()
        super.onDestroy()
    }

    /** 屏幕旋转/尺寸变化：把悬浮球重新夹回屏内（否则横竖屏切换后可能停在屏外不可触达） */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hidePanel(keepDraft = true)
        val v = ball ?: return
        val lp = ballParams ?: return
        val m = resources.displayMetrics
        lp.x = lp.x.coerceIn(0, (m.widthPixels - ballSizePx).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(dp(24), (m.heightPixels - ballSizePx - dp(40)).coerceAtLeast(dp(24)))
        runCatching { windowManager.updateViewLayout(v, lp) }
    }

    // ------------------------------------------------------------ 前台通知

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        val t = AppText.current
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, t.overlayNotifTitle, NotificationManager.IMPORTANCE_LOW).apply {
                description = t.overlayNotifText
                setShowBadge(false)
            }
        )
        val contentPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingBallService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.overlayNotifTitle)
            .setContentText(t.overlayNotifText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .addAction(0, t.overlayNotifStop, stopPi)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    // ------------------------------------------------------------ 悬浮球

    private fun addBall() {
        val frame = FrameLayout(this)
        val icon = ImageView(this)
        // 图标 = K日程应用图标（圆角蓝渐变底 + 日历）+ 右下角 DeepSeek 小角标（角标绘制在图标之上）
        icon.setImageResource(R.drawable.ic_overlay_ball_icon)
        frame.addView(
            icon,
            FrameLayout.LayoutParams(ballSizePx, ballSizePx, Gravity.CENTER),
        )
        frame.elevation = dp(6).toFloat()
        frame.alpha = REST_ALPHA

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedX = prefs.getInt(KEY_X, screenW - ballSizePx)
        val savedY = prefs.getInt(KEY_Y, (screenH * 0.42f).toInt())
        val lp = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, (screenW - ballSizePx).coerceAtLeast(0))
            y = savedY.coerceIn(dp(24), (screenH - ballSizePx - dp(40)).coerceAtLeast(dp(24)))
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var downTime = 0L
        frame.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    downTime = e.eventTime
                    frame.alpha = 1f
                    snapAnimator?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        // 开始拖动悬浮球时收起面板（输入内容会保留），避免面板停留在旧位置造成困惑
                        hidePanel(keepDraft = true)
                    }
                    if (dragging) {
                        lp.x = (startX + dx).toInt()
                            .coerceIn(0, (screenW - ballSizePx).coerceAtLeast(0))
                        lp.y = (startY + dy).toInt()
                            .coerceIn(dp(24), (screenH - ballSizePx - dp(40)).coerceAtLeast(dp(24)))
                        runCatching { windowManager.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge()
                    } else if (e.eventTime - downTime > 600L) {
                        hidePanel(keepDraft = true)
                        openApp()
                    } else if (panel != null) {
                        // 面板已打开 → 收起
                        hidePanel(keepDraft = true)
                    } else if (AppPresence.visible) {
                        // 应用就在前台：改为打开应用内的 AI 助手界面（不同时显示两个界面）
                        openApp()
                    } else {
                        showPanel()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapToEdge()
                    true
                }
                else -> false
            }
        }

        ball = frame
        ballParams = lp
        windowManager.addView(frame, lp)
    }

    /** 松手（或启动恢复）时：把悬浮球吸附回最近的屏幕侧边 */
    private fun snapToEdge() {
        val lp = ballParams ?: return
        val view = ball ?: return
        val screenW = resources.displayMetrics.widthPixels
        val center = lp.x + ballSizePx / 2
        val targetX = if (center < screenW / 2) 0 else (screenW - ballSizePx).coerceAtLeast(0)
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(lp.x, targetX).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.x = it.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, lp) }
            }
            start()
        }
        view.alpha = REST_ALPHA
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_X, targetX)
            .putInt(KEY_Y, lp.y)
            .apply()
    }

    // ------------------------------------------------------------ 快速输入面板

    private fun showPanel() {
        if (panel != null) return
        val t = AppText.current
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val textColor = if (night) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val subColor = if (night) 0xFF9AA0A6.toInt() else 0xFF5F6368.toInt()
        val accent = if (night) 0xFF9ECAFE.toInt() else 0xFF1D4ED8.toInt()

        val v = LayoutInflater.from(this).inflate(R.layout.overlay_quick_add, null)
        val title = v.findViewById<TextView>(R.id.overlay_title)
        val close = v.findViewById<TextView>(R.id.overlay_close)
        val input = v.findViewById<EditText>(R.id.overlay_input)
        val open = v.findViewById<TextView>(R.id.overlay_open)
        val parseBtn = v.findViewById<Button>(R.id.overlay_parse)
        val addBtn = v.findViewById<Button>(R.id.overlay_add)
        val msg = v.findViewById<TextView>(R.id.overlay_msg)

        title.text = t.qaTitle
        title.setTextColor(textColor)
        close.setTextColor(subColor)
        input.hint = t.qaHint
        input.setTextColor(textColor)
        input.setHintTextColor(subColor)
        open.text = t.qaOpenInApp
        open.setTextColor(accent)
        parseBtn.text = t.qaParse
        addBtn.text = t.qaConfirm
        addBtn.isEnabled = false
        msg.setTextColor(subColor)
        msg.visibility = View.GONE
        parsedOps = emptyList()
        panelInput = input
        panelMsgText = ""

        fun setMsg(text: String) {
            panelMsgText = text
            msg.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            msg.text = text
        }

        // 恢复上次未提交的输入：误关面板后不必重新输入（再点一下悬浮球即可继续）
        val savedText = draftText
        val savedOps = draftOps
        val savedMsg = draftMsg
        if (savedText.isNotBlank()) {
            input.setText(savedText)
            input.setSelection(savedText.length)
            parsedOps = savedOps
            addBtn.isEnabled = parsedOps.isNotEmpty()
            if (savedMsg.isNotBlank()) setMsg(savedMsg)
        }

        // 修改输入内容后，之前的识别结果作废（防止“改了文字却把旧结果加进去”的误操作）
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                // 草稿跟随输入框：面板收起后仍保留本次输入
                draftText = s?.toString().orEmpty()
                draftOps = emptyList()
                draftMsg = ""
                if (parsedOps.isNotEmpty()) {
                    parsedOps = emptyList()
                    addBtn.isEnabled = false
                    setMsg(t.qaReParse)
                }
            }
        })

        close.setOnClickListener { hidePanel(keepDraft = true) }
        open.setOnClickListener {
            openApp()
            hidePanel(keepDraft = true)
        }
        parseBtn.setOnClickListener {
            val text = input.text?.toString().orEmpty()
            if (text.isBlank() || busy) return@setOnClickListener
            busy = true
            parseBtn.isEnabled = false
            addBtn.isEnabled = false
            setMsg(t.aiRunning)
            requestJob = scope.launch {
                try {
                    val key = SettingsStore.effectiveAiKey(this@FloatingBallService)
                    if (key.isNullOrBlank()) {
                        setMsg(t.aiNeedKey)
                        return@launch
                    }
                    val model = SettingsStore.effectiveAiModel(this@FloatingBallService)
                    val reply = AiClient.chat(
                        apiKey = key,
                        model = model,
                        systemPrompt = AiSkills.assistantSystemPrompt,
                        userPrompt = AiSkills.assistantUserPrompt(
                            text,
                            LocalDate.now(),
                            AiAssistant.contextLines(AgendaStore.events.value),
                        ),
                    )
                    // 等待期间用户改了文字：本次结果作废（防止与当前内容不符的操作入库）
                    if (text != input.text?.toString().orEmpty()) {
                        setMsg(t.qaStaleResult)
                        return@launch
                    }
                    val list = AiSkills.parseOpsReply(reply)
                    if (list.isEmpty()) {
                        parsedOps = emptyList()
                        setMsg(t.qaNothing)
                    } else {
                        parsedOps = list
                        // 反馈中标注每条是「日程」还是「计划」，避免用户分不清去向
                        val existing = AgendaStore.events.value
                        setMsg(
                            list.joinToString("\n") { op ->
                                "[" + aiOpBadgeLabel(op, t) + "] " + aiOpSummary(op, t, existing)
                            }
                        )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    setMsg((e.message ?: t.parseFail).take(80))
                } finally {
                    busy = false
                    parseBtn.isEnabled = true
                    addBtn.isEnabled = parsedOps.isNotEmpty()
                }
            }
        }
        addBtn.setOnClickListener {
            val list = parsedOps
            if (list.isEmpty() || busy) return@setOnClickListener
            busy = true
            addBtn.isEnabled = false
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) {
                        val result = AiAssistant.apply(this@FloatingBallService, list)
                        runCatching { StatusNotification.refresh(this@FloatingBallService) }
                        runCatching { NextClassWidgetUpdater.updateAndSchedule(this@FloatingBallService) }
                        result
                    }
                    Toast.makeText(
                        this@FloatingBallService,
                        t.qaOpsDone(r.added, r.updated, r.deleted, r.unmatched),
                        Toast.LENGTH_SHORT,
                    ).show()
                    parsedOps = emptyList()
                    // 已提交：清空草稿，下次打开面板是全新的输入
                    hidePanel(keepDraft = false)
                } finally {
                    busy = false
                }
            }
        }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val width = min(screenW - dp(28), dp(360)).coerceAtLeast(dp(240))
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不用 WATCH_OUTSIDE_TOUCH：点按输入法键盘也会触发 ACTION_OUTSIDE，
            // 会导致用户打字时面板被误关；面板改由 ✕ / 悬浮球 / “在应用中打开” 收起
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_PANEL_X, (screenW - width) / 2)
                .coerceIn(dp(4), (screenW - width - dp(4)).coerceAtLeast(dp(4)))
            y = prefs.getInt(KEY_PANEL_Y, dp(72))
                .coerceIn(0, (screenH - dp(120)).coerceAtLeast(0))
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        // 面板可拖动：按住空白处/标题区可移动整个面板（输入框与按钮仍正常响应）
        attachPanelDrag(v, lp)

        panel = v
        panelParams = lp
        runCatching { windowManager.addView(v, lp) }
        // 与「应用内 AI 助手对话框」互斥：面板成为当前界面，对方收到后自动收起
        AiSurface.openOverlay()
        input.requestFocus()
        input.post {
            val imm = getSystemService(InputMethodManager::class.java)
            runCatching { imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    /** 面板拖动：把面板拖到不遮挡内容的位置（位置会记住，下次打开沿用） */
    private fun attachPanelDrag(root: View, lp: WindowManager.LayoutParams) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        (abs(e.rawX - downRawX) > slop || abs(e.rawY - downRawY) > slop)
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val m = resources.displayMetrics
                        lp.x = (startX + (e.rawX - downRawX)).toInt()
                            .coerceIn(dp(4), (m.widthPixels - lp.width - dp(4)).coerceAtLeast(dp(4)))
                        lp.y = (startY + (e.rawY - downRawY)).toInt()
                            .coerceIn(0, (m.heightPixels - dp(120)).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(root, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putInt(KEY_PANEL_X, lp.x)
                            .putInt(KEY_PANEL_Y, lp.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 收起面板。
     * @param keepDraft true=保留本次输入（下次打开面板继续编辑）；false=已提交，清空草稿
     */
    private fun hidePanel(keepDraft: Boolean) {
        val v = panel ?: return
        if (keepDraft) {
            panelInput?.text?.toString()?.let { draftText = it }
            draftOps = parsedOps
            draftMsg = panelMsgText
        } else {
            draftText = ""
            draftOps = emptyList()
            draftMsg = ""
        }
        panel = null
        panelParams = null
        panelInput = null
        panelMsgText = ""
        parsedOps = emptyList()
        // 收起面板时取消在飞识别请求：否则重开面板后 busy 期间点“识别”会毫无反应
        requestJob?.cancel()
        requestJob = null
        busy = false
        runCatching { windowManager.removeView(v) }
        AiSurface.close(AiSurface.Kind.Overlay)
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                }
            )
        }
    }
}
