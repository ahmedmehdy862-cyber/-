package com.focusstudy.poc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A group of destinations, organised by what the person is trying to do. */
internal data class SectionGroup(val title: String, val ids: List<String>)

internal val SectionGroups = listOf(
    SectionGroup("التخطيط", listOf("today", "tasks", "calendar", "notes", "planner", "task_types")),
    SectionGroup("التطوّر", listOf("goals", "skills", "subjects", "analytics")),
    SectionGroup("الفريق والنظام", listOf("notifications", "messages", "updates", "prayer_times", "settings", "support"))
)

/**
 * The floating dock that replaced the full-width navigation bar.
 *
 * It keeps the person's own sections in one rounded surface over the background, marks the open one
 * with a soft pill, and ends with a fixed «الأقسام» door so nothing is more than two taps away.
 */
@Composable
internal fun FocusDock(
    tabs: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    onOpenSections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = FocusColors.Surface,
        shadowElevation = 16.dp,
        tonalElevation = 0.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FocusColors.SurfaceHigh)
                    .clickable { onOpenSections() }
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    DockItem(
                        mark = BottomTab.markOf(tab),
                        label = BottomTab.labelOf(tab),
                        selected = tab == current,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(tab) }
                    )
                }
                Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(26.dp).background(FocusColors.SurfaceHigh))
                DockItem(
                    mark = "▦",
                    label = "الأقسام",
                    selected = false,
                    modifier = Modifier.width(58.dp),
                    onClick = onOpenSections
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    mark: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pill by animateColorAsState(if (selected) FocusColors.SurfaceHigh else Color.Transparent, tween(220), label = "dock-pill")
    val tint by animateColorAsState(if (selected) FocusColors.Blue else FocusColors.TextMuted, tween(220), label = "dock-tint")
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(pill)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(mark, fontSize = 16.sp, color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(
            label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Every destination in one sheet, each row with a pin.
 *
 * Pinning writes the same preference the bar editor used, so the dock never has two sources of
 * truth; the five-slot cap is explained instead of silently refusing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionsSheet(
    pinned: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val current = remember(pinned) { BottomTab.normalize(pinned) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FocusColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("كل الأقسام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FocusColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("${current.size}/${BottomTab.MAX} في الشريط", style = MaterialTheme.typography.labelMedium, color = FocusColors.TextMuted)
            }
            Text(
                "اضغط أي قسم للانتقال إليه، واضغط «تثبيت» ليظهر في الشريط.",
                style = MaterialTheme.typography.bodySmall,
                color = FocusColors.TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("ابحث عن قسم") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            SectionGroups.forEach { group ->
                val rows = group.ids.mapNotNull { BottomTab.from(it) }
                    .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                if (rows.isNotEmpty()) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = FocusColors.TextMuted,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )
                    rows.forEach { tab ->
                        SectionRow(
                            tab = tab,
                            pinned = tab.id in current,
                            canPin = BottomTab.canAdd(current) || tab.id in current,
                            onOpen = { onSelect(tab.id) },
                            onToggle = { onTogglePin(tab.id) }
                        )
                    }
                }
            }
            if (!BottomTab.canAdd(current)) {
                Text(
                    "الشريط ممتلئ بخمسة أقسام — انزع التثبيت عن واحد لإضافة غيره.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusColors.TextMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionRow(
    tab: BottomTab,
    pinned: Boolean,
    canPin: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(34.dp).height(34.dp).clip(RoundedCornerShape(12.dp)).background(FocusColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) { Text(tab.mark, color = FocusColors.Blue) }
        Spacer(Modifier.width(12.dp))
        Text(tab.label, style = MaterialTheme.typography.bodyLarge, color = FocusColors.TextPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = onToggle, enabled = canPin) {
            Text(
                when {
                    pinned -> "مثبّت ✓"
                    canPin -> "تثبيت"
                    else -> "ممتلئ"
                },
                color = if (pinned) FocusColors.Blue else FocusColors.TextMuted
            )
        }
    }
}
