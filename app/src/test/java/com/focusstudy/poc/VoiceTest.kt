package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    private val vars = mapOf(
        "name" to "أحمد",
        "task" to "مراجعة الفيزياء",
        "subject" to "الكيمياء",
        "count" to "3",
        "done" to "2",
        "days" to "3",
        "minutes" to "25",
        "mark" to "🧭"
    )

    @Test
    fun `no scene keeps a placeholder`() {
        for (scene in VoiceScene.entries) {
            for (persona in Persona.entries) {
                for (tone in VoiceTone.entries) {
                    for (seed in 0..2) {
                        val text = Voice.render(scene, persona, tone, vars, variantSeed = seed)
                        assertFalse("$scene/$persona/$tone -> $text", Voice.hasUnresolvedPlaceholder(text))
                        assertTrue("$scene/$persona/$tone empty", text.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun `placeholder survives without a variable map`() {
        val text = Voice.render(VoiceScene.TASK_DUE, Persona.EXPLORER, VoiceTone.SEMI_FORMAL, emptyMap())
        assertFalse(Voice.hasUnresolvedPlaceholder(text))
    }

    @Test
    fun `formal register carries no joke or forbidden wording`() {
        for (scene in VoiceScene.entries) {
            val text = Voice.render(scene, Persona.HERO, VoiceTone.FORMAL_REVERENT, vars)
            assertFalse(text, text.contains("تكاد تشكرك"))
            assertFalse(text, text.contains("يملّ الجدول"))
            assertFalse(Voice.isForbidden(text))
        }
    }

    @Test
    fun `notification scenes stay within 140 characters`() {
        for (scene in listOf(VoiceScene.TASK_DUE, VoiceScene.TASK_OVERDUE, VoiceScene.EXAM_SOON)) {
            for (persona in Persona.entries) {
                val text = Voice.render(scene, persona, VoiceTone.SEMI_FORMAL, vars)
                assertTrue("$text is ${text.length}", text.length <= 140)
            }
        }
    }

    @Test
    fun `consecutive greetings never repeat`() {
        val first = Voice.say(VoiceScene.MORNING, Persona.EXPLORER, VoiceTone.LIGHT_PLAYFUL, vars)
        val second = Voice.say(VoiceScene.MORNING, Persona.EXPLORER, VoiceTone.LIGHT_PLAYFUL, vars)
        assertNotEquals(first, second)
    }

    @Test
    fun `recent window avoids the last fourteen sentences`() {
        val personaLines = (0..2).map { Voice.render(VoiceScene.TASK_DUE, Persona.EXPLORER, VoiceTone.SEMI_FORMAL, vars, variantSeed = it) }
        val avoid = personaLines.dropLast(1)
        val fresh = Voice.render(VoiceScene.TASK_DUE, Persona.EXPLORER, VoiceTone.SEMI_FORMAL, vars, avoidRecent = avoid)
        assertTrue("expected a non-recent line, got $fresh", fresh !in avoid)
    }

    @Test
    fun `each persona has its own morning voice`() {
        val lines = Persona.entries.map { Voice.render(VoiceScene.MORNING, it, VoiceTone.LIGHT_PLAYFUL, vars) }
        assertEquals(lines.size, lines.distinct().size)
        assertTrue(lines.all { it.contains("أحمد") })
    }

    @Test
    fun `persona changes the greeting content`() {
        assertTrue(Voice.render(VoiceScene.MORNING, Persona.EXPLORER, VoiceTone.LIGHT_PLAYFUL, vars).contains("الخريطة"))
        assertTrue(Voice.render(VoiceScene.MORNING, Persona.ARTIST, VoiceTone.LIGHT_PLAYFUL, vars).contains("لوحة"))
    }

    @Test
    fun `a hard day downgrades any user tone to the reverent register`() {
        val resolved = ToneResolver.resolve(
            ToneState(userTone = TonePreference.PLAYFUL, hardDay = true)
        )
        assertEquals(VoiceTone.FORMAL_REVERENT, resolved)
    }

    @Test
    fun `tired and exam-day states are reverent`() {
        assertEquals(
            VoiceTone.FORMAL_REVERENT,
            ToneResolver.resolve(ToneState(userTone = TonePreference.PLAYFUL, tired = true))
        )
        assertEquals(
            VoiceTone.FORMAL_REVERENT,
            ToneResolver.resolve(ToneState(userTone = TonePreference.PLAYFUL, examWithinDay = true))
        )
        assertEquals(
            VoiceTone.FORMAL_REVERENT,
            ToneResolver.resolve(ToneState(userTone = TonePreference.PLAYFUL, religiousScene = true))
        )
    }

    @Test
    fun `the user setting caps playfulness`() {
        assertEquals(VoiceTone.SEMI_FORMAL, ToneResolver.resolve(ToneState(userTone = TonePreference.FORMAL)))
        assertEquals(VoiceTone.LIGHT_PLAYFUL, ToneResolver.resolve(ToneState(userTone = TonePreference.BALANCED)))
        assertEquals(VoiceTone.BOLD_PLAYFUL, ToneResolver.resolve(ToneState(userTone = TonePreference.PLAYFUL)))
    }

    @Test
    fun `bold playful is opt in and detected by the forbidden list`() {
        assertTrue(Voice.isForbidden("الشبشب الافتراضي خرج من الدرج"))
        assertFalse(Voice.isForbidden("حان الآن موعد صلاة العصر."))
    }
}
