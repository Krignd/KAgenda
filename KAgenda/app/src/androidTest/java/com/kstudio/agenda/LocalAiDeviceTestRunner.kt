package com.kstudio.agenda

import android.app.Instrumentation
import android.os.Bundle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kstudio.agenda.data.AiInference
import com.kstudio.agenda.data.LocalAiModels
import com.kstudio.agenda.data.LocalAiPrompt
import com.kstudio.agenda.data.ModelDownloadWorker
import com.kstudio.agenda.data.SettingsStore
import com.kstudio.agenda.i18n.AppLang
import com.kstudio.agenda.i18n.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/** Explicit emulator smoke test; fixture is external and never packaged in either APK. */
class LocalAiDeviceTestRunner : Instrumentation() {
    private var bugfix = false
    private var network = false
    private var appearance = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); bugfix = arguments?.getString("bugfix") == "true"; network = arguments?.getString("network") == "true"; appearance = arguments?.getString("appearance") == "true"; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            if (bugfix) {
                check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
                val report = runBlocking {
                    val simpleStart = android.os.SystemClock.elapsedRealtime()
                    val simple = AiInference.recognize(targetContext, "明天下午3点开会\n后天下午4点交报告", emptyList())
                    check(simple.ops.size == 2) { "Expected two simple events" }
                    val simpleMs = android.os.SystemClock.elapsedRealtime() - simpleStart
                    val modelStart = android.os.SystemClock.elapsedRealtime()
                    val complex = AiInference.recognize(targetContext, "明天下午3点到5点在实验楼开组会", emptyList())
                    check(complex.ops.isNotEmpty()) { "Expected a model or rule preview" }
                    "simple_batch_ms=$simpleMs; model_or_fallback_ms=${android.os.SystemClock.elapsedRealtime()-modelStart}; preview_ops=${complex.ops.size}; note=${complex.note}"
                }
                result.putString("stream", report)
                finish(-1, result)
                return
            }
            if (appearance) {
                result.putString("stream", AppearanceDeviceChecks.run(this))
                finish(-1, result)
                return
            }
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")) { "Emulator only" }
            AppText.state.value = AppLang.ZH
            runBlocking {
                val context = targetContext
                val model = LocalAiModels.all.first()
                SettingsStore.setAiProvider(context, "local")
                SettingsStore.setLocalAiModel(context, model.id)
                // Uninstalled state must use rules without making a cloud request.
                LocalAiModels.receipt(context, model).delete()
                LocalAiModels.file(context, model).delete()
                val rules = AiInference.recognize(context, "明天下午3点开会", emptyList())
                check(rules.ops.isNotEmpty() && rules.note.isNotBlank())
                val mutation = runCatching { AiInference.recognize(context, "删除明天的体检", emptyList()) }
                check(mutation.isFailure)
                val fixture = File(context.getExternalFilesDir(null), "local-ai-test.gguf")
                check(fixture.length() == model.bytes) { "Missing external fixture" }
                fixture.copyTo(LocalAiModels.partial(context, model), overwrite = true)
                if (network) java.io.RandomAccessFile(LocalAiModels.partial(context, model), "rw").use { it.setLength(1024 * 1024) }
                // Resume a fully received file: the real worker hashes and promotes it atomically.
                val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf("model" to model.id)).build()
                val manager = WorkManager.getInstance(context)
                manager.enqueue(request).result.get()
                withTimeout(120_000) {
                    while (true) {
                        val info = manager.getWorkInfoById(request.id).get()
                        if (info?.state?.isFinished == true) {
                            check(info.state == WorkInfo.State.SUCCEEDED) { "Worker failed: ${info.outputData}" }
                            break
                        }
                        delay(500)
                    }
                }
                check(LocalAiModels.installed(context, model))
                val query = "请帮我添加日程：明天上午九点在图书馆开读书会，十点结束。"
                val reply = AiInference.local(context, model, LocalAiPrompt.systemFor(query),
                    LocalAiPrompt.user(query, java.time.LocalDate.now(), emptyList(), ""), true)
                val ops = runCatching { LocalAiPrompt.parseOps(reply, emptyList()) }.getOrElse {
                    throw AssertionError("Synthetic fixture output: $reply", it)
                }
                check(ops.size == 1) { "Expected one model operation" }
                val op = ops.single() as com.kstudio.agenda.data.AiSkills.AiOp.Add
                check(op.item.date == java.time.LocalDate.now().plusDays(1)) { "Wrong date" }
                check(op.item.startTime == "09:00" && op.item.endTime == "10:00") { "Wrong time" }
                check(op.item.location.contains("图书馆")) { "Wrong location" }
                val existing = com.kstudio.agenda.data.AiAssistant.buildEvent(op.item.copy(title = "体检", date = java.time.LocalDate.now(), startTime = "10:00"))
                val changed = AiInference.recognize(context, "把体检改到明天下午三点", listOf(existing)).ops.single()
                check(changed is com.kstudio.agenda.data.AiSkills.AiOp.Update) { "Expected update" }
                check(changed.matchId == existing.id && changed.set.date == java.time.LocalDate.now().plusDays(1) && changed.set.startTime == "15:00") { "Wrong update target/date/time: $changed" }
                result.putString("stream", "PASS: rules without weights, mutation guard, resumable worker SHA-256, real local Qwen3 add + update; network=$network\n")
            }
            finish(-1, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(2000)}")
            finish(0, result)
        }
    }
}
