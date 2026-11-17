package com.focusstudy.poc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one place to add something.
 *
 * It floats above the bottom bar in the corner the bar leaves free, so it never covers a card
 * action or a bar item, and it offers the three things this app organises: a task, a skill or a
 * goal. The list opens upwards and closes as soon as one is picked.
 */
@Composable
internal fun QuickAddFab(
    modifier: Modifier = Modifier,
    onAddTask: () -> Unit,
    onAddSkill: () -> Unit,
    onAddGoal: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 45f else 0f, tween(180), label = "fab")

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(visible = open, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = FocusColors.Surface,
                shadowElevation = 8.dp,
                tonalElevation = 0.dp
            ) {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
                    QuickAddRow("✓", "مهمة", "شيء تنجزه اليوم أو لاحقًا") {
                        open = false
                        onAddTask()
                    }
                    QuickAddRow("✦", "مهارة", "تتبّع تدريبك ومستواك فيها") {
                        open = false
                        onAddSkill()
                    }
                    QuickAddRow("◎", "هدف", "مقياس ومهلة ومهام مرتبطة") {
                        open = false
                        onAddGoal()
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = FocusColors.BlueDeep,
            shadowElevation = 12.dp,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).clickable { open = !open }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "+",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.rotate(turn)
                )
            }
        }
    }
}

@Composable
private fun QuickAddRow(mark: String, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Text(mark, color = FocusColors.onTint(FocusColors.Blue), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = FocusColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = FocusColors.TextMuted)
        }
    }
}
