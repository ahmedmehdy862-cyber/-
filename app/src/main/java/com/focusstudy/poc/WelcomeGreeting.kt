package com.focusstudy.poc

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * A quiet hello the first time the app opens each day.
 *
 * It never interrupts: it slides in above the day card, says what is actually waiting, and leaves
 * on its own after a few seconds. Opening the app again the same day shows nothing.
 */
@Composable
internal fun WelcomeGreeting(name: String, openToday: Int, doneToday: Int) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val today = LocalDate.now().toEpochDay()
    var visible by remember { mutableStateOf(prefs.getLong(KEY_DAY, -1L) != today) }

    LaunchedEffect(Unit) {
        if (visible) prefs.edit().putLong(KEY_DAY, today).apply()
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(8_000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { -it / 4 },
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(FocusColors.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(mark(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        greet(name),
                        style = MaterialTheme.typography.titleSmall,
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(hint(openToday, doneToday), style = MaterialTheme.typography.bodySmall, color = FocusColors.TextSecondary)
                }
            }
        }
    }
}

private const val PREFS = "focus_welcome"
private const val KEY_DAY = "welcome_day"

private fun mark(): String = when (LocalTime.now().hour) {
    in 0..4 -> "🌙"
    in 5..11 -> "☀️"
    in 12..16 -> "🌤"
    else -> "🌙"
}

private fun greet(name: String): String {
    val who = name.trim().ifBlank { "يا بطل" }
    return when (LocalTime.now().hour) {
        in 0..4 -> "سهران معنا يا $who؟ خفّف الحمل وارتاح"
        in 5..11 -> "صباح الخير، $who ☀️"
        in 12..16 -> "نهارك هادئ ومنظّم، $who"
        else -> "مساء الخير، $who 🌙"
    }
}

private fun hint(openToday: Int, doneToday: Int): String = when {
    openToday > 0 -> "عندك $openToday مهمة اليوم. اختر واحدة صغيرة وابدأ — الباقي يمشي أسهل."
    doneToday > 0 -> "أنجزت $doneToday اليوم. لمسة أخيرة: أضف مهمة واحدة لبكرة وأنت مرتاح."
    else -> "لم تحدّد مهام اليوم بعد. مهمة واحدة الآن تكفي لتشعر أن اليوم تحت السيطرة."
}
