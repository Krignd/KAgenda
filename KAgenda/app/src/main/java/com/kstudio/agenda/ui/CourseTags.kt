package com.kstudio.agenda.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.CourseTagDefinition
import com.kstudio.agenda.data.LessonRef
import com.kstudio.agenda.data.PlannerStore
import com.kstudio.agenda.data.removeCourseTag
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.model.Course
import com.kstudio.agenda.ui.components.AppOutlinedTextField
import com.kstudio.agenda.ui.theme.FrostedAlertDialog
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun CourseTagActions(course: Course, date: LocalDate) {
    val vm = LocalPlanner.current
    val lesson = LessonRef.of(LocalPlannerSchool.current, course, date, LocalPlannerSemester.current)
    val data by PlannerStore.data.collectAsState()
    val selected = data.courseTags[lesson.id].orEmpty()
    var managing by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.courseTagDefinitions.forEach { tag ->
                FilterChip(selected = tag.id in selected, onClick = {
                    vm.editPlanner { state ->
                        val current = state.courseTags[lesson.id].orEmpty()
                        val tags = if (tag.id in current) current - tag.id else current + tag.id
                        state.copy(courseTags = if (tags.isEmpty()) state.courseTags - lesson.id else state.courseTags + (lesson.id to tags))
                    }
                }, label = { Text(lessonTagLabel(tag)) })
            }
        }
        TextButton(onClick = { managing = true }) {
            Text(plannerText("管理标签", "Manage tags", "Gérer les étiquettes"))
        }
    }
    if (managing) CourseTagManager(vm) { managing = false }
}

@Composable
private fun CourseTagManager(vm: AppViewModel, onDismiss: () -> Unit) {
    val data by PlannerStore.data.collectAsState()
    val t = LocalStrings.current
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    FrostedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plannerText("课程标签", "Course tags", "Étiquettes de cours")) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                data.courseTagDefinitions.forEach { tag ->
                    val label = lessonTagLabel(tag)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        IconButton(onClick = { editing = tag.id; name = label }) {
                            Icon(Icons.Default.Edit, plannerText("修改标签", "Rename tag", "Renommer l’étiquette"))
                        }
                        IconButton(onClick = { deleting = tag.id }) {
                            Icon(Icons.Default.Delete, plannerText("删除标签", "Delete tag", "Supprimer l’étiquette"))
                        }
                    }
                }
                TextButton(onClick = { editing = ""; name = "" }) {
                    Text(plannerText("添加标签", "Add tag", "Ajouter une étiquette"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.confirm) } },
    )
    editing?.let { id ->
        val label = name.trim()
        val duplicate = data.courseTagDefinitions.any { it.id != id && lessonTagLabel(it).equals(label, ignoreCase = true) }
        FrostedAlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (id.isEmpty()) plannerText("添加标签", "Add tag", "Ajouter une étiquette")
                else plannerText("修改标签", "Rename tag", "Renommer l’étiquette")) },
            text = {
                AppOutlinedTextField(name, { name = it.take(30) }, singleLine = true, isError = duplicate,
                    label = { Text(plannerText("标签名称", "Tag name", "Nom de l’étiquette")) })
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank() && !duplicate, onClick = {
                    vm.editPlanner { state ->
                        val definitions = if (id.isEmpty()) state.courseTagDefinitions + CourseTagDefinition(UUID.randomUUID().toString(), label)
                        else state.courseTagDefinitions.map { tag -> if (tag.id == id) tag.copy(label = label) else tag }
                        state.copy(courseTagDefinitions = definitions)
                    }
                    editing = null
                }) { Text(t.save) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(t.cancel) } },
        )
    }
    deleting?.let { id ->
        FrostedAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(plannerText("删除课程标签？", "Delete course tag?", "Supprimer l’étiquette ?")) },
            text = { Text(plannerText("这个标签也会从已标记的课程上移除。", "The tag will also be removed from all classes using it.",
                "L’étiquette sera aussi retirée des cours concernés.")) },
            confirmButton = {
                TextButton(onClick = { vm.editPlanner { it.removeCourseTag(id) }; deleting = null }) { Text(t.delete) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t.cancel) } },
        )
    }
}
