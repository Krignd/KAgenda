package com.kstudio.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kstudio.agenda.data.BillRule
import com.kstudio.agenda.data.PlannerStore
import com.kstudio.agenda.i18n.LocalStrings
import com.kstudio.agenda.ui.components.AppButton
import com.kstudio.agenda.ui.components.AppOutlinedButton
import com.kstudio.agenda.ui.components.AppOutlinedTextField
import com.kstudio.agenda.ui.components.TagChip
import com.kstudio.agenda.ui.theme.FrostedAlertDialog
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.UUID

private fun billCategories() = linkedMapOf(
    "subscription" to plannerText("订阅", "Subscription", "Abonnement"),
    "telecom" to plannerText("通信", "Telecom", "Télécommunications"),
    "utilities" to plannerText("生活缴费", "Utilities", "Services publics"),
    "rent" to plannerText("房租", "Rent", "Loyer"),
    "credit" to plannerText("信用卡", "Credit card", "Carte de crédit"),
    "insurance" to plannerText("保险", "Insurance", "Assurance"),
    "other" to plannerText("其他", "Other", "Autre"),
)

private fun BillRule.categoryKey() = customCategory?.takeIf { it.isNotBlank() }?.let { "custom/$it" } ?: (category ?: "other")

private fun BillRule.categoryLabel(categories: Map<String, String>) =
    customCategory?.takeIf { it.isNotBlank() } ?: categories[category] ?: categories.getValue("other")

@Composable
internal fun BillsScreen(vm: AppViewModel) {
    val data by PlannerStore.data.collectAsState()
    val today by produceState(LocalDate.now()) {
        while (true) { value = LocalDate.now(); delay(30_000) }
    }
    val categories = billCategories()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    val filters = linkedMapOf("" to plannerText("全部分类", "All categories", "Toutes les catégories"))
    data.bills.forEach { filters[it.categoryKey()] = it.categoryLabel(categories) }
    val activeFilter = categoryFilter.takeIf { it in filters }.orEmpty()
    val bills = data.bills.filter { activeFilter.isEmpty() || it.categoryKey() == activeFilter }.sortedBy { it.next(today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlannerMenu(filters.getValue(activeFilter), filters) { categoryFilter = it }
                AppButton(onClick = { editing = "" }) { Text(plannerText("添加账单日", "Add bill date", "Ajouter une facture")) }
            }
            if (bills.isEmpty()) Text(plannerText("暂无账单", "No bills", "Aucune facture"),
                Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(bills, key = { it.id }) { bill ->
            PlannerCard(onClick = { editing = bill.id }) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BillIcon(iconFor(bill.icon, bill.category), 42.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bill.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TagChip(bill.categoryLabel(categories), MaterialTheme.colorScheme.primary)
                        Text(billRepeat(bill.repeat, bill.interval ?: 1, bill.days), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(bill.next(today).toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    editing?.let { id ->
        val existing = data.bills.firstOrNull { it.id == id }
        BillEditor(existing, onDismiss = { editing = null }, onSave = { bill ->
            vm.editPlanner { it.copy(bills = it.bills.filterNot { old -> old.id == bill.id } + bill) }
            editing = null
        }, onDelete = {
            vm.editPlanner { it.copy(bills = it.bills.filterNot { bill -> bill.id == id }) }
            editing = null
        })
    }
}

private fun billRepeat(key: String, count: Int, days: Int) = when (key) {
    "year" -> if (count == 1) plannerText("每年", "Yearly", "Chaque année")
        else plannerText("每 $count 年", "Every $count years", "Tous les $count ans")
    "month" -> if (count == 1) plannerText("每月", "Monthly", "Chaque mois")
        else plannerText("每 $count 个月", "Every $count months", "Tous les $count mois")
    "week" -> plannerText("每周", "Weekly", "Chaque semaine")
    else -> plannerText("每 $days 天", "Every $days days", "Tous les $days jours")
}

@Composable
private fun BillEditor(bill: BillRule?, onDismiss: () -> Unit, onSave: (BillRule) -> Unit, onDelete: () -> Unit) {
    val t = LocalStrings.current
    var title by rememberSaveable { mutableStateOf(bill?.title.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(bill?.first ?: LocalDate.now().toString()) }
    var repeat by rememberSaveable { mutableStateOf(bill?.repeat ?: "month") }
    var days by rememberSaveable { mutableStateOf((bill?.days ?: 30).toString()) }
    var months by rememberSaveable { mutableStateOf((bill?.takeIf { it.repeat == "month" }?.interval ?: 1).toString()) }
    var years by rememberSaveable { mutableStateOf((bill?.takeIf { it.repeat == "year" }?.interval ?: 1).toString()) }
    var category by rememberSaveable {
        mutableStateOf(if (bill?.customCategory.isNullOrBlank()) bill?.category ?: "other" else "custom")
    }
    var custom by rememberSaveable { mutableStateOf(bill?.customCategory.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(bill?.icon) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickingIcon by rememberSaveable { mutableStateOf(false) }
    val dayInterval = days.toIntOrNull()?.takeIf { it in 1..3650 }
    val calendarInterval = when (repeat) {
        "year" -> years.toIntOrNull()?.takeIf { it in 1..120 }
        "month" -> months.toIntOrNull()?.takeIf { it in 1..120 }
        else -> null
    }
    val repeats = linkedMapOf(
        "week" to plannerText("每周", "Weekly", "Chaque semaine"),
        "month" to plannerText("每 N 个月", "Every N months", "Tous les N mois"),
        "year" to plannerText("每 N 年", "Every N years", "Tous les N ans"),
        "days" to plannerText("每 N 天", "Every N days", "Tous les N jours"),
    )
    val categories = billCategories().apply { put("custom", plannerText("自定义分类", "Custom category", "Catégorie personnalisée")) }
    FrostedAlertDialog(onDismissRequest = onDismiss, title = { Text(plannerText("账单日", "Bill dates", "Factures")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedTextField(title, { title = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(plannerText("账单名称", "Bill name", "Nom")) }, singleLine = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plannerText("首次账单日", "First bill date", "Première facture"), Modifier.weight(1f))
                AppOutlinedButton(onClick = { picking = true }) { Text(date) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plannerText("重复周期", "Repeat", "Répéter"), Modifier.weight(1f))
                PlannerMenu(repeats.getValue(repeat), repeats) { repeat = it }
            }
            when (repeat) {
                "month" -> BillNumberField(months, { months = it }, 3, plannerText("间隔月数", "Months between bills", "Intervalle en mois"))
                "year" -> BillNumberField(years, { years = it }, 3, plannerText("间隔年数", "Years between bills", "Intervalle en ans"))
                "days" -> BillNumberField(days, { days = it }, 4, plannerText("间隔天数", "Days between bills", "Intervalle en jours"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plannerText("分类", "Category", "Catégorie"), Modifier.weight(1f))
                PlannerMenu(categories[category] ?: categories.getValue("other"), categories) { category = it }
            }
            if (category == "custom") AppOutlinedTextField(custom, { custom = it.take(30) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text(plannerText("分类名称", "Category name", "Nom de la catégorie")) })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BillIcon(iconFor(icon, category), 32.dp)
                TextButton(onClick = { pickingIcon = true }) {
                    Text(plannerText("选择图标", "Choose icon", "Choisir une icône"))
                }
            }
        }
    }, scrollText = false, confirmButton = {
        val validInterval = repeat == "week" || if (repeat == "days") dayInterval != null else calendarInterval != null
        TextButton(enabled = title.isNotBlank() && validInterval && (category != "custom" || custom.isNotBlank()), onClick = {
            onSave(BillRule(bill?.id ?: UUID.randomUUID().toString(), title.trim(), date, repeat, dayInterval ?: 30,
                category = category.takeUnless { it == "custom" }, customCategory = custom.trim().takeIf { category == "custom" },
                icon = icon, interval = calendarInterval))
        }) { Text(t.save) }
    }, dismissButton = {
        if (bill != null) TextButton(onClick = onDelete) { Text(t.delete) }
        TextButton(onClick = onDismiss) { Text(t.cancel) }
    })
    if (picking) PlannerDatePicker(LocalDate.parse(date), { picking = false }) { date = it.toString(); picking = false }
    if (pickingIcon) BillIconPicker(category, onDismiss = { pickingIcon = false }) { icon = it; pickingIcon = false }
}

@Composable
private fun BillNumberField(value: String, change: (String) -> Unit, length: Int, label: String) {
    AppOutlinedTextField(value, { next -> change(next.filter(Char::isDigit).take(length)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(label) })
}

private data class BillIconSpec(val key: String, val label: String, val vector: ImageVector? = null, val mark: String = "", val color: Color? = null)

private fun billIcons(): List<BillIconSpec> {
    val categories = billCategories()
    return listOf(
        BillIconSpec("subscription", categories.getValue("subscription"), Icons.Default.Subscriptions),
        BillIconSpec("telecom", categories.getValue("telecom"), Icons.Default.PhoneAndroid),
        BillIconSpec("utilities", categories.getValue("utilities"), Icons.Default.Bolt),
        BillIconSpec("rent", categories.getValue("rent"), Icons.Default.Home),
        BillIconSpec("credit", categories.getValue("credit"), Icons.Default.CreditCard),
        BillIconSpec("insurance", categories.getValue("insurance"), Icons.Default.Shield),
        BillIconSpec("other", categories.getValue("other"), Icons.AutoMirrored.Filled.ReceiptLong),
        BillIconSpec("netflix", "Netflix", mark = "N", color = Color(0xFFB00020)),
        BillIconSpec("youtube", "YouTube Premium", Icons.Default.PlayArrow, color = Color(0xFFB3261E)),
        BillIconSpec("spotify", "Spotify", Icons.Default.MusicNote, color = Color(0xFF1E7847)),
        BillIconSpec("apple", "Apple Music", Icons.Default.Headphones, color = Color(0xFFB22858)),
        BillIconSpec("icloud", "iCloud", Icons.Default.Cloud, color = Color(0xFF2468A3)),
        BillIconSpec("bilibili", plannerText("哔哩哔哩大会员", "Bilibili Premium", "Bilibili Premium"), mark = "B", color = Color(0xFFB94379)),
        BillIconSpec("qqmusic", plannerText("QQ 音乐", "QQ Music", "QQ Music"), mark = "QQ", color = Color(0xFF287047)),
        BillIconSpec("netease", plannerText("网易云音乐", "NetEase Cloud Music", "NetEase Cloud Music"), mark = "云", color = Color(0xFFB3261E)),
        BillIconSpec("tencent", plannerText("腾讯视频", "Tencent Video", "Tencent Video"), mark = "腾讯", color = Color(0xFF2670A1)),
        BillIconSpec("iqiyi", plannerText("爱奇艺", "iQIYI", "iQIYI"), mark = "爱", color = Color(0xFF287047)),
    )
}

private fun iconFor(key: String?, category: String?): BillIconSpec {
    val icons = billIcons()
    return icons.firstOrNull { it.key == key } ?: icons.firstOrNull { it.key == category } ?: icons.first { it.key == "other" }
}

@Composable
private fun BillIcon(spec: BillIconSpec, size: androidx.compose.ui.unit.Dp) {
    val color = spec.color ?: MaterialTheme.colorScheme.primaryContainer
    val foreground = if (spec.color == null) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
        if (spec.vector != null) Icon(spec.vector, contentDescription = null, Modifier.size(size * 0.58f), tint = foreground)
        else Text(spec.mark, color = foreground, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BillIconPicker(category: String, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val t = LocalStrings.current
    FrostedAlertDialog(onDismissRequest = onDismiss, title = { Text(plannerText("选择图标", "Choose icon", "Choisir une icône")) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                BillIconOption(iconFor(null, category), plannerText("按分类显示", "Use category icon", "Utiliser l’icône de catégorie")) { onSelect(null) }
                billIcons().forEach { spec -> BillIconOption(spec, spec.label) { onSelect(spec.key) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } })
}

@Composable
private fun BillIconOption(spec: BillIconSpec, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BillIcon(spec, 32.dp)
            Text(label)
        }
    }
}
