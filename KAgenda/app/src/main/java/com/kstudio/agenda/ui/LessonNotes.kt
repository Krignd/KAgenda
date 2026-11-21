@file:Suppress("DEPRECATION")

package com.kstudio.agenda.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import com.kstudio.agenda.ui.theme.FrostedAlertDialog as AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kstudio.agenda.data.*
import com.kstudio.agenda.data.engines.EngineFeatures
import com.kstudio.agenda.data.engines.EngineId
import com.kstudio.agenda.data.engines.OcrEngineContract
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.ui.engines.EngineComponentRow
import com.kstudio.agenda.ui.components.AppOutlinedTextField
import com.kstudio.agenda.ui.components.AppOutlinedButton as OutlinedButton
import com.kstudio.agenda.ui.theme.LocalGlassEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Composable
internal fun LessonNoteEditor(lesson: LessonRef, note: LessonNote?, autoCamera: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm = LocalPlanner.current
    val t = LocalStrings.current
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { note?.id ?: UUID.randomUUID().toString() }
    // A fresh capture is a draft until its note has been durably saved.
    val draft = context.cacheDir.resolve("lesson-drafts").resolve("$id.jpg")
    var pending by rememberSaveable { mutableStateOf(false) }
    val photo = if (pending) draft else note?.photo?.let { PlannerStore.photo(context, it) }
    var text by rememberSaveable { mutableStateOf(note?.text.orEmpty()) }
    var tags by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = note?.tags?.joinToString("，").orEmpty()
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var date by rememberSaveable { mutableStateOf(note?.deadline?.let { LocalDateTime.parse(it).toLocalDate().toString() }) }
    var time by rememberSaveable { mutableStateOf(note?.deadline?.let { LocalDateTime.parse(it).toLocalTime().toString() } ?: "23:59") }
    var countdown by rememberSaveable { mutableStateOf(note?.countdown ?: false) }
    var keepPhoto by rememberSaveable { mutableStateOf(note?.photo != null) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var didAuto by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) camera = true else error = plannerText("需要相机权限", "Camera permission is required", "L’accès à la caméra est nécessaire")
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
        else permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(autoCamera) {
        if (autoCamera && !didAuto) {
            didAuto = true
            launchCamera()
        }
    }
    val chosenTags = tags.text.split(',', '，', ';', '；').map(String::trim).filter(String::isNotBlank).distinct().take(12)
    fun close() {
        draft.delete()
        onDismiss()
    }
    AlertDialog(onDismissRequest = { if (!busy) close() }, title = {
        Text(plannerText("作业 / 信息", "Homework / notes", "Devoirs / notes"))
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lesson.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = ::launchCamera, enabled = !busy) {
                    Icon(Icons.Default.PhotoCamera, plannerText("拍照", "Take photo", "Prendre une photo"))
                }
            }
            AppOutlinedTextField(text, { text = it.take(24_000) }, Modifier.fillMaxWidth(), minLines = 3,
                label = { Text(plannerText("内容", "Notes", "Contenu")) })
            if (photo?.isFile == true) {
                PhotoPreview(photo, pending)
                if (EngineFeatures.installed(context, EngineId.OCR)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", photo)
                                val engine = EngineFeatures.instance(context, EngineId.OCR, OcrEngineContract::class.java)
                                val result = engine.extract(context, uri, plainText = true)
                                text = listOf(text, result).filter { it.isNotBlank() }.joinToString("\n").take(24_000)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = plannerText("识别失败，可保留原图", "Recognition failed. You can keep the photo.", "Échec de la reconnaissance. La photo peut être conservée.")
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(plannerText("识别到文本框", "Recognize text", "Reconnaître le texte")) }
                } else EngineComponentRow(EngineId.OCR)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keepPhoto, onCheckedChange = { keepPhoto = it })
                    Text(plannerText("保存原图", "Keep original photo", "Conserver la photo originale"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(plannerText("作业", "Homework", "Devoir"), plannerText("要求", "Requirement", "Consigne")).forEach { label ->
                    FilterChip(selected = label in chosenTags, onClick = {
                        val updated = (if (label in chosenTags) chosenTags - label else chosenTags + label).joinToString("，")
                        if (updated.length <= 240) tags = TextFieldValue(updated, TextRange(updated.length))
                    }, label = { Text(label) })
                }
            }
            OutlinedTextField(tags, { if (it.text.length <= 240) tags = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(plannerText("标签（逗号分隔）", "Tags (comma-separated)", "Étiquettes (séparées par virgules)")) },
                shape = if (LocalGlassEnabled.current) RoundedCornerShape(14.dp) else OutlinedTextFieldDefaults.shape)
            OutlinedButton(onClick = { picking = true }) { Text(date ?: plannerText("添加截止日期", "Add a deadline", "Ajouter une échéance")) }
            if (date != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickingTime = true }) { Text(time) }
                    TextButton(onClick = { date = null; countdown = false }) { Text(plannerText("清除截止时间", "Clear deadline", "Effacer l’échéance")) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(countdown, onCheckedChange = { countdown = it })
                    Text(plannerText("加入倒计时", "Add to countdowns", "Ajouter aux comptes à rebours"))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(enabled = !busy && (text.isNotBlank() || (keepPhoto && photo?.isFile == true)), onClick = {
            val stored = LessonNote(id, lesson, text.trim(), chosenTags, if (keepPhoto && photo?.isFile == true) {
                if (pending) UUID.randomUUID().toString() else note?.photo
            } else null, date?.let { LocalDate.parse(it).atTime(LocalTime.parse(time)).toString() }, countdown && date != null)
            busy = true
            vm.saveLessonNote(stored, if (pending) draft else null, note?.photo) { success ->
                busy = false
                if (success) close() else error = t.msgSaveFailed
            }
        }) { Text(t.save) }
    }, dismissButton = {
        Row {
            if (note != null) TextButton(enabled = !busy, onClick = {
                busy = true
                vm.deleteLessonNote(note) { success ->
                    busy = false
                    if (success) close() else error = t.msgSaveFailed
                }
            }) { Text(t.delete) }
            TextButton(enabled = !busy, onClick = ::close) { Text(t.cancel) }
        }
    })
    if (picking) PlannerDatePicker(date?.let(LocalDate::parse) ?: LocalDate.now(), { picking = false }) {
        date = it.toString()
        picking = false
    }
    if (pickingTime) TimeWheelDialog(time, allowFuzzy = false, onDismiss = { pickingTime = false }) {
        time = it
        pickingTime = false
    }
    if (camera) LessonCamera(onDismiss = { camera = false }, onJpeg = { jpeg ->
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    draft.parentFile?.mkdirs()
                    draft.writeBytes(jpeg)
                }
                pending = true
                keepPhoto = true
            } catch (_: java.io.IOException) {
                error = t.msgSaveFailed
            } finally {
                busy = false
            }
        }
    })
}

@Composable
private fun PhotoPreview(file: File, fresh: Boolean) {
    var bitmap by remember(file, fresh) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file, fresh, file.lastModified()) {
        bitmap = withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                runCatching {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(file)) { decoder, info, _ ->
                        val sample = (maxOf(info.size.width, info.size.height) / 900).coerceAtLeast(1)
                        decoder.setTargetSampleSize(sample)
                    }
                }.getOrNull()
            } else {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), plannerText("作业照片", "Homework photo", "Photo du devoir"),
        Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit) }
}

@Composable
private fun LessonCamera(onDismiss: () -> Unit, onJpeg: (ByteArray) -> Unit) {
    var preview by remember { mutableStateOf<LessonCameraPreview?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val t = LocalStrings.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) { Text(t.cancel) }
                    Button(enabled = ready && !failed, onClick = {
                        ready = false
                        preview?.capture { bytes ->
                            if (bytes == null) failed = true else {
                                onJpeg(bytes)
                                onDismiss()
                            }
                        }
                    }) { Text(plannerText("拍照", "Take photo", "Prendre une photo")) }
                }
                if (failed) {
                    Text(plannerText("无法打开相机", "Camera unavailable", "Caméra indisponible"))
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { context -> LessonCameraPreview(context) { ok -> ready = ok; failed = !ok }.also { preview = it } },
                            modifier = Modifier.matchParentSize(),
                            onRelease = { it.closeCamera() },
                        )
                    }
                }
            }
        }
    }
}

private class LessonCameraPreview(context: Context, private val status: (Boolean) -> Unit) :
    SurfaceView(context), SurfaceHolder.Callback {
    private var camera: Camera? = null

    init { holder.addCallback(this) }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        closeCamera()
        try {
            val info = Camera.CameraInfo()
            val count = Camera.getNumberOfCameras()
            val id = (0 until count).firstOrNull {
                Camera.getCameraInfo(it, info)
                info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
            } ?: 0
            Camera.getCameraInfo(id, info)
            val active = Camera.open(id).also { camera = it }
            val angle = (display?.rotation ?: 0) * 90
            val front = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            val preview = if (front) (720 - info.orientation - angle) % 360 else (info.orientation + 360 - angle) % 360
            active.setDisplayOrientation(preview)
            val params = active.parameters
            val jpegRotation = (info.orientation + if (front) -angle + 360 else angle) % 360
            params.setRotation(jpegRotation)
            if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in params.supportedFocusModes.orEmpty()) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            active.parameters = params
            active.setPreviewDisplay(holder)
            active.startPreview()
            status(true)
        } catch (_: Exception) {
            closeCamera()
            status(false)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = closeCamera()

    fun capture(result: (ByteArray?) -> Unit) {
        try {
            val active = camera ?: return result(null)
            active.takePicture(null, null) { bytes, _ -> if (camera === active) result(bytes) }
        } catch (_: Exception) {
            result(null)
        }
    }

    fun closeCamera() {
        val active = camera ?: return
        camera = null
        runCatching { active.stopPreview() }
        active.release()
    }
}

@Composable
internal fun LessonDetailsActions(course: Course, date: LocalDate) {
    val vm = LocalPlanner.current
    val settings by vm.settings.collectAsState()
    val planner by PlannerStore.data.collectAsState()
    val lesson = LessonRef.of(settings.schoolId, course, date, LocalPlannerSemester.current)
    val openNote = LocalOpenLessonNote.current
    val counterId = "lesson/${lesson.id}"
    val inCountdown = planner.links.any { it.id == counterId }
    OutlinedButton(onClick = { openNote(lesson, null) }, modifier = Modifier.fillMaxWidth()) {
        Text(plannerText("添加作业 / 信息", "Add homework / notes", "Ajouter un devoir / une note"))
    }
    OutlinedButton(onClick = {
        vm.editPlanner { state ->
            val links = state.links.filterNot { it.id == counterId }
            state.copy(links = if (inCountdown) links else links + CountdownLink(counterId, course.title, date.atTime(course.startTime).toString(), lesson = lesson))
        }
    }, modifier = Modifier.fillMaxWidth()) {
        Text(if (inCountdown) plannerText("移出倒计时", "Remove countdown", "Retirer le compte à rebours")
        else plannerText("加入倒计时", "Add to countdowns", "Ajouter aux comptes à rebours"))
    }
    planner.notes.filter { it.lesson.id == lesson.id }.forEach { note ->
        TextButton(onClick = { openNote(lesson, note.id) }, modifier = Modifier.fillMaxWidth()) {
            val summary = note.text.lineSequence().firstOrNull().orEmpty().take(70)
                .ifBlank { plannerText("作业照片", "Homework photo", "Photo du devoir") }
            Text(listOf(summary, note.tags.joinToString(" · "), note.deadline?.replace('T', ' ').orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · "))
        }
    }
}
