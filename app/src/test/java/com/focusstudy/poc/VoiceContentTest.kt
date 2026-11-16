package com.focusstudy.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the brand promises of §3: no scene ever leaks a placeholder, says a banned word,
 * or loses its mark. Every tone/persona combination is rendered, not just the default.
 */
class VoiceContentTest {

    private val vars = mapOf("name" to "أحمد", "count" to "3", "task" to "مراجعة الفيزياء", "done" to "2")
    private val tones = VoiceTone.entries
    private val personas = Persona.entries

    @Test fun everyCombinationRendersWithoutPlaceholder() {
        for (scene in VoiceScene.entries) for (persona in personas) for (tone in tones) {
            val text = Voice.render(scene, persona, tone, vars)
            assertFalse("$scene/$persona/$tone left a placeholder: $text", Voice.hasUnresolvedPlaceholder(text))
            assertTrue("$scene/$persona/$tone is empty", text.isNotBlank())
        }
    }

    @Test fun everyCombinationAvoidsBannedWords() {
        for (scene in VoiceScene.entries) for (persona in personas) for (tone in tones) {
            val text = Voice.render(scene, persona, tone, vars)
            assertFalse("$scene/$persona/$tone says something banned: $text", Voice.isForbidden(text))
        }
    }

    @Test fun bannedListIsNotEmptyAndDocumented() {
        assertTrue(Voice.bannedWords.size >= 8)
        assertTrue(Voice.bannedWords.contains("هتلاقي"))
        assertTrue(Voice.bannedWords.contains("فشلت"))
    }

    @Test fun notificationsWordIsNotFlaggedAsBlame() {
        // «الإشعارات» contains «عار»; a substring check used to mark it forbidden.
        assertFalse(Voice.isForbidden("الإشعارات الذكية مفعّلة الآن"))
        assertTrue(Voice.isForbidden("إنت عار على نفسك"))
    }

    @Test fun bannedWordsMatchWholeWordsOnly() {
        assertFalse(Voice.isForbidden("كسولة الحركة ليست كلمة هنا"))
        assertTrue(Voice.isForbidden("ده كسول"))
    }

    @Test fun multiWordBanIsDetectedAsPhrase() {
        assertTrue(Voice.isForbidden("إنت متأخر دائمًا"))
        assertFalse(Voice.isForbidden("إنت متأخر اليوم فقط"))
    }

    @Test fun personaMarkAppearsInTheGreeting() {
        for (persona in personas) {
            val variants = (0..6).map {
                Voice.render(VoiceScene.MORNING, persona, VoiceTone.SEMI_FORMAL, vars, variantSeed = it)
            }
            assertTrue("no greeting carries the mark for $persona", variants.any { it.contains(persona.mark) })
        }
    }

    @Test fun reverentScenesNeverAskForAGrin() {
        for (tone in tones) {
            val text = Voice.render(VoiceScene.HARD_DAY, Persona.EXPLORER, tone, vars)
            assertEquals(VoiceTone.FORMAL_REVERENT, Voice.resolveTone(VoiceScene.HARD_DAY, tone))
            assertFalse(text.contains("🩴"))
        }
    }

    @Test fun mostPlayfulEmojiStaysOutOfOrdinaryScenes() {
        for (scene in VoiceScene.entries) {
            val text = Voice.render(scene, Persona.ARTIST, VoiceTone.BOLD_PLAYFUL, vars)
            if (scene != VoiceScene.STREAK && scene != VoiceScene.DEFERRED && scene != VoiceScene.SESSION_DONE) {
                assertFalse("$scene used the opt-in emoji", text.contains("🩴"))
            }
        }
    }

    @Test fun renderIsDeterministicForASeed() {
        val first = Voice.render(VoiceScene.MORNING, Persona.SCIENTIST, VoiceTone.LIGHT_PLAYFUL, vars, variantSeed = 3)
        val second = Voice.render(VoiceScene.MORNING, Persona.SCIENTIST, VoiceTone.LIGHT_PLAYFUL, vars, variantSeed = 3)
        assertEquals(first, second)
    }

    @Test fun eachSceneProducesAtLeastTwoVariants() {
        for (scene in VoiceScene.entries) {
            val variants = (0..6).map {
                Voice.render(scene, Persona.EXPLORER, VoiceTone.SEMI_FORMAL, vars, variantSeed = it)
            }.toSet()
            assertTrue("$scene has only one phrasing", variants.size >= 2)
        }
    }
}
