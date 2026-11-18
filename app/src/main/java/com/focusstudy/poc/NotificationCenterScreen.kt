package com.focusstudy.poc

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One place for everything that can interrupt the person.
 *
 * Each kind of notification has a switch, the system permission has a status, and one button sends a
 * real notification so «الإشعارات لا تصل» becomes something the phone answers, not a guess.
 */
@Composable
internal fun NotificationCenterScreen(
    preferences: FocusPreferences,
    onBack: () -> Unit,
    onSave: (FocusPreferences) -> Unit,
    onRequestNotifications: () -> Unit
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsGranted(context)) }
    var sent by remember { mutableStateOf(false) }
    // The permission dialog is async: re-check when the activity resumes instead of immediately
    // after the request click, which used to leave the card stuck on "denied".
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshPermission = rememberUpdatedState { allowed = notificationsGranted(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.width(8.dp))
            Text("الإشعارات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            StatusCard(allowed, onAllow = onRequestNotifications)
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("ماذا يصلك؟", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    PreferenceSwitchRow(
                        "تذكير المهام",
                        "عدد المهام المتأخرة وأقرب خطوة، ويتوقف أثناء جلسة التركيز.",
                        preferences.smartRemindersEnabled
                    ) { onSave(preferences.copy(smartRemindersEnabled = it)) }
                    PreferenceSwitchRow(
                        "رسالة الصباح",
                        "نية اليوم وما ينتظرك، في الساعة التي تختارها.",
                        preferences.morningMessageEnabled
                    ) { onSave(preferences.copy(morningMessageEnabled = it)) }
                    PreferenceSwitchRow(
                        "رسالة المساء",
                        "مراجعة قصيرة لما أنجزته ودعوة لتجهيز الغد.",
                        preferences.eveningMessageEnabled
                    ) { onSave(preferences.copy(eveningMessageEnabled = it)) }
                    PreferenceSwitchRow(
                        "احترام ساعات الهدوء",
                        "لا شيء يصل في أوقات الراحة الليلية.",
                        preferences.quietHoursEnabled
                    ) { onSave(preferences.copy(quietHoursEnabled = it)) }
                    PreferenceSwitchRow(
                        "رسائل الفريق",
                        "إعلانات وتحديثات الخدمة بدون تحديث التطبيق.",
                        preferences.generalNotificationsEnabled
                    ) { onSave(preferences.copy(generalNotificationsEnabled = it)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            TestCard(sent, allowed) {
                StudyNotificationScheduler.notifyTest(context)
                sent = true
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "إن أرسلت إشعارًا ولم يظهر: الإذن ممنوع، أو الهاتف في وضع عدم الإزعاج، أو أُوقفت إشعارات التطبيق من إعدادات النظام.",
                style = MaterialTheme.typography.bodySmall,
                color = FocusColors.TextMuted
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(allowed: Boolean, onAllow: () -> Unit) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (allowed) "الإشعارات مسموحة ✓" else "الإشعارات ممنوعة الآن",
                style = MaterialTheme.typography.titleSmall,
                color = if (allowed) FocusColors.Blue else FocusColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (allowed) "سيصلك تذكير المهام ورسائل الفريق في شريط الإشعارات."
                else "بدون الإذن لن يظهر أي تذكير. اسمح به من هنا أو من إعدادات النظام.",
                style = MaterialTheme.typography.bodySmall,
                color = FocusColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!allowed) Button(onClick = onAllow) { Text("اسمح بالإشعارات") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("إعدادات النظام") }
            }
        }
    }
}

@Composable
private fun TestCard(sent: Boolean, allowed: Boolean, onSend: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("جرّبها الآن", style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "اضغط الزر ثم انزل بشريط الإشعارات: يجب أن تجد «الإشعارات تعمل ✓»، والضغط عليها يفتح مهامي.",
                style = MaterialTheme.typography.bodySmall,
                color = FocusColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSend) { Text("أرسل إشعارًا تجريبيًا") }
                Spacer(Modifier.width(10.dp))
                if (sent) Text(
                    if (allowed) "أُرسل — افحص الشريط" else "الإذن ممنوع",
                    style = MaterialTheme.typography.labelSmall,
                    color = FocusColors.TextMuted
                )
            }
        }
    }
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
