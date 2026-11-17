package com.focusstudy.poc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Long-term goals: bigger than a task, measured by a number the person chooses
 * (كتب، ساعات، دروس، مكالمات…) so the same screen serves study, work, health and hobbies.
 */
@Composable
internal fun GoalsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { GoalRepository.get(context) }
    val scope = rememberCoroutineScope()

    var goals by remember { mutableStateOf<List<Goal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var version by remember { mutableStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("10") }
    var unit by rememberSaveable { mutableStateOf("دروس") }
    var category by rememberSaveable { mutableStateOf("") }
    var dueDays by rememberSaveable { mutableStateOf("30") }
    var confirmDelete by remember { mutableStateOf<Goal?>(null) }

    LaunchedEffect(version) {
        loading = true
        goals = runCatching { repository.goals() }.getOrDefault(emptyList())
        loading = false
    }
    fun refresh() { version++ }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.width(8.dp))
            Text("أهدافي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("هدف جديد", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(60) },
                            label = { Text("ماذا تريد أن تحقق؟") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = target,
                                onValueChange = { target = it.filter(Char::isDigit).take(5) },
                                label = { Text("الهدف") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it.take(14) },
                                label = { Text("الوحدة") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = { category = it.take(20) },
                                label = { Text("المجال (اختياري)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            OutlinedTextField(
                                value = dueDays,
                                onValueChange = { dueDays = it.filter(Char::isDigit).take(3) },
                                label = { Text("خلال أيام") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        Button(
                            onClick = {
                                val days = dueDays.toIntOrNull()
                                if (title.isNotBlank() && (target.toIntOrNull() ?: 0) > 0) {
                                    scope.launch {
                                        repository.add(
                                            title = title,
                                            target = target.toInt(),
                                            unit = unit.ifBlank { "خطوة" },
                                            dueAt = days?.let {
                                                LocalDate.now().plusDays(it.toLong())
                                                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            },
                                            category = category
                                        )
                                        title = ""; category = ""; refresh()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("أضف الهدف") }
                    }
                }
            }

            if (!loading && goals.isEmpty()) {
                item {
                    Text(
                        "لا أهداف بعد. الهدف يختلف عن المهمة: له مقياس ومهلة، وتتقدّم فيه خطوة كل مرة.",
                        color = FocusColors.TextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }

            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    onStep = { delta -> scope.launch { repository.addProgress(goal.id, delta); refresh() } },
                    onComplete = {
                        scope.launch {
                            repository.setStatus(goal.id, if (goal.status == "done") "active" else "done")
                            refresh()
                        }
                    },
                    onDelete = { confirmDelete = goal }
                )
            }
        }
    }

    confirmDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = FocusColors.SurfaceHigh,
            title = { Text("حذف الهدف؟") },
            text = { Text("«${goal.title}» وتقدّمه (${goal.current} من ${goal.target}) سيُحذف.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { repository.delete(goal.id); confirmDelete = null; refresh() } }) {
                    Text("حذف", color = FocusColors.onTint(FocusColors.Rose))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun GoalCard(goal: Goal, onStep: (Int) -> Unit, onComplete: () -> Unit, onDelete: () -> Unit) {
    val done = goal.status == "done"
    Card(
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        goal.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOfNotNull(
                            goal.category.takeIf { it.isNotBlank() },
                            "${goal.current} من ${goal.target} ${goal.unit}",
                            goal.dueAt?.let { "حتى " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ar-EG"))) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusColors.TextSecondary
                    )
                }
                if (done) Text("✓ تم", color = FocusColors.onTint(FocusColors.Green), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)).background(FocusColors.SurfaceHigh)) {
                Box(
                    Modifier
                        .fillMaxWidth(goal.progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(if (done) FocusColors.Green else FocusColors.Blue)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onStep(1) }) { Text("+1", color = FocusColors.onTint(FocusColors.Blue)) }
                TextButton(onClick = { onStep(-1) }) { Text("‑1", color = FocusColors.TextSecondary) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onComplete) {
                    Text(if (done) "إعادة فتح" else "أنجزت", color = FocusColors.onTint(FocusColors.Green))
                }
                TextButton(onClick = onDelete) { Text("حذف", color = FocusColors.onTint(FocusColors.Rose)) }
            }
        }
    }
}
