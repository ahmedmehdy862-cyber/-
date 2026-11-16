package com.focusstudy.poc

import android.content.Context

/** Voice & tone system: one place for every sentence the app says, with a register per situation. */
enum class VoiceTone(val key: String, val label: String, val weight: Int) {
    FORMAL_REVERENT("formal", "موقّر", 0),
    SEMI_FORMAL("semi", "سيمي فورمال", 1),
    LIGHT_PLAYFUL("light", "مرح خفيف", 2),
    BOLD_PLAYFUL("bold", "مرح جريء", 3);

    companion object {
        fun from(key: String?): VoiceTone = entries.firstOrNull { it.key == key } ?: SEMI_FORMAL
    }
}

internal enum class VoiceScene(val key: String) {
    MORNING("morning"),
    EVENING("evening"),
    TASK_DUE("task_due"),
    TASK_OVERDUE("overdue"),
    EXAM_SOON("exam_soon"),
    DONE_TODAY("done_today"),
    HARD_DAY("hard_day"),
    STREAK("streak"),
    DEFERRED("deferred"),
    SESSION_DONE("session_done"),
    EMPTY_TASKS("empty_tasks")
}

internal enum class Persona(val index: Int, val label: String, val mark: String) {
    EXPLORER(0, "المستكشف", "🧭"),
    SCIENTIST(1, "العالِم", "🔭"),
    HERO(2, "البطل", "⚡"),
    ARTIST(3, "الفنان", "🎨");

    companion object {
        fun from(index: Int): Persona = entries.firstOrNull { it.index == index } ?: EXPLORER
    }
}

enum class TonePreference(val key: String, val label: String, val ceiling: VoiceTone) {
    FORMAL("formal", "رسمي", VoiceTone.SEMI_FORMAL),
    BALANCED("balanced", "متوازن", VoiceTone.LIGHT_PLAYFUL),
    PLAYFUL("playful", "مرح", VoiceTone.BOLD_PLAYFUL);

    companion object {
        fun from(key: String?): TonePreference = entries.firstOrNull { it.key == key } ?: BALANCED
    }
}

/** Everything that can make a situation serious; the most reverent factor wins. */
internal data class ToneState(
    val userTone: TonePreference = TonePreference.BALANCED,
    val hardDay: Boolean = false,
    val tired: Boolean = false,
    val examWithinDay: Boolean = false,
    val religiousScene: Boolean = false,
    val duringPrayer: Boolean = false
)

internal object ToneResolver {
    fun resolve(state: ToneState): VoiceTone {
        if (state.religiousScene || state.duringPrayer || state.hardDay || state.tired || state.examWithinDay) {
            return VoiceTone.FORMAL_REVERENT
        }
        return state.userTone.ceiling
    }
}

internal object Voice {
    /** Never repeat a sentence used in this many previous calls. */
    const val RECENT_WINDOW = 14

    private val recent = ArrayDeque<String>()

    private val forbidden = listOf("هتلاقي", "اختار", "يا فاشل", "كسول", "بتاعة", "عبيط", "الشبشب")

    fun say(
        scene: VoiceScene,
        persona: Persona = Persona.EXPLORER,
        tone: VoiceTone = VoiceTone.SEMI_FORMAL,
        vars: Map<String, String> = emptyMap()
    ): String {
        val text = render(scene, persona, tone, vars, recent.toList())
        recent.addLast(text)
        while (recent.size > RECENT_WINDOW) recent.removeFirst()
        return text
    }

    /** Deterministic renderer used by the app and by tests. */
    fun render(
        scene: VoiceScene,
        persona: Persona,
        tone: VoiceTone,
        vars: Map<String, String>,
        avoidRecent: List<String> = emptyList(),
        variantSeed: Int = 0
    ): String {
        val effective = resolveTone(scene, tone)
        val options = candidates(scene, persona, effective).map { fill(it, vars) }
            .ifEmpty { candidates(scene, persona, VoiceTone.FORMAL_REVERENT).map { fill(it, vars) } }
            .ifEmpty { listOf(fill(defaultLine(scene), vars)) }
        val fresh = options.filter { it !in avoidRecent }
        val pool = fresh.ifEmpty { options }
        return pool[Math.floorMod(variantSeed, pool.size)]
    }

    fun isForbidden(text: String): Boolean = forbidden.any { text.contains(it) }

    fun hasUnresolvedPlaceholder(text: String): Boolean = PLACEHOLDER.containsMatchIn(text)

    fun resolveTone(scene: VoiceScene, requested: VoiceTone): VoiceTone =
        if (scene == VoiceScene.HARD_DAY) VoiceTone.FORMAL_REVERENT else requested

    private fun candidates(scene: VoiceScene, persona: Persona, tone: VoiceTone): List<String> {
        val table = when (scene) {
            VoiceScene.MORNING -> listOf(
                "صباح الخير {name} {mark} الخريطة جاهزة وأمامنا {count} محطات، أولها خفيفة، وعد.",
                "صباح الخير {name} {mark} فرضية اليوم: إنهاء المهمة الصعبة أولًا يخفف بقية اليوم.",
                "صباح الخير {name} {mark} التحدي الأول يرتدي حذاءه وينتظرك.",
                "صباح الخير {name} {mark} اليوم لوحة بيضاء، ولن نحاسبك على الخربشات الأولى.",
                "صباح الخير {name}. عندك {count} مهام اليوم، وأهمها {task}."
            )
            VoiceScene.EVENING -> listOf(
                "مساء الخير {name}. أنهيت {done} من {count} اليوم — وهذا يكفي.",
                "مساء الخير {name}. نراجع بلا لوم: ما لم يتم اليوم نجده غدًا.",
                "مساء الخير {name}. أغلقنا الصفحة، ونبدأ الغد بنفس هادئ."
            )
            VoiceScene.TASK_DUE -> listOf(
                "موعد {task} الآن.",
                "حان وقت {task} ({minutes} دقيقة). هل نبدأ؟",
                "{task} تنتظرك الآن ({minutes} دقيقة)."
            )
            VoiceScene.TASK_OVERDUE -> listOf(
                "{task} تأخرت عن موعدها. نعيد جدولتها؟",
                "{task} خارج الجدول الآن. خطوة صغيرة تكفي.",
                "بقيت {task} من دون موعد. نقسّمها إلى خطوات؟"
            )
            VoiceScene.EXAM_SOON -> listOf(
                "تبقّى على امتحان {subject} {days}. أضفنا لك جلسات مراجعة قصيرة.",
                "امتحان {subject} بعد {days}. مراجعة واحدة اليوم تكفي.",
                "بقي {days} على {subject} — نبدأ بمراجعة قصيرة؟"
            )
            VoiceScene.DONE_TODAY -> listOf(
                "{done} مهام أُنجزت اليوم! القائمة تكاد تشكرك بنفسها.",
                "أنجزت {done} مهام اليوم. {mark} سجّلها لك.",
                "تمّت {done} من مهام اليوم — تقدم حقيقي لا شعار."
            )
            VoiceScene.HARD_DAY -> listOf(
                "يبدو أن يومك ثقيل. خففنا الخطة إلى الأساسيات فقط.",
                "لن نطالبك بأكثر من الأساسيات اليوم. هذا قرار حكيم.",
                "يوم صعب يمر، والخطة الخفيفة تحفظ لك استمرارك."
            )
            VoiceScene.STREAK -> listOf(
                "{count} أيام متتالية. استمر على هذا الإيقاع.",
                "سلسلة {count} أيام قائمة. خطوة أخرى اليوم تُثبّتها.",
                "التزام {count} أيام — لا شيء يعادله في الاستمرار."
            )
            VoiceScene.DEFERRED -> listOf(
                "هذه المهمة أُجّلت {count} مرات. نقسّمها إلى خطوات أصغر؟",
                "تأجيل جديد لـ{task}. خطوة أصغر قد تنهيها فعلًا.",
                "أجّلنا {task} مرة أخرى. نبدأ منها 10 دقائق فقط؟"
            )
            VoiceScene.SESSION_DONE -> listOf(
                "انتهت جلسة {minutes} دقيقة. كيف كان تركيزك؟",
                "{minutes} دقيقة تركيز انتهت. سجّل تقييمك بنقرة.",
                "جلسة {minutes} دقيقة كاملة. خُد نفَسًا."
            )
            VoiceScene.EMPTY_TASKS -> listOf(
                "لا شيء على القائمة اليوم. استمتع بالهدوء، أو أضف مهمة قبل أن يملّ الجدول.",
                "القائمة فارغة اليوم. هذا أيضًا إنجاز.",
                "لا مهام اليوم. نضيف واحدة صغيرة أم نترك اليوم هادئًا؟"
            )
        }
        val personaLines = if (scene == VoiceScene.MORNING) listOf(morningFor(persona)) else emptyList()
        val all = (personaLines + table).distinct()
        val perTone = if (tone == VoiceTone.FORMAL_REVERENT) {
            all.filter { line -> JOKE_MARKERS.none { line.contains(it) } }.ifEmpty { all }
        } else all
        return perTone
    }

    private fun morningFor(persona: Persona): String = when (persona) {
        Persona.EXPLORER -> "صباح الخير {name} {mark} الخريطة جاهزة، وأمامنا {count} محطات أولها خفيفة."
        Persona.SCIENTIST -> "صباح الخير {name} {mark} فرضية اليوم: إنهاء المهمة الصعبة أولًا يخفف بقية اليوم."
        Persona.HERO -> "صباح الخير {name} {mark} التحدي الأول يرتدي حذاءه وينتظرك."
        Persona.ARTIST -> "صباح الخير {name} {mark} اليوم لوحة بيضاء، ولن نحاسبك على الخربشات الأولى."
    }

    private val JOKE_MARKERS = listOf("تكاد تشكرك", "يملّ الجدول", "الشبشب")

    private fun defaultLine(scene: VoiceScene): String = when (scene) {
        VoiceScene.TASK_DUE -> "موعد {task} الآن."
        VoiceScene.EXAM_SOON -> "امتحان {subject} بعد {days}."
        VoiceScene.EMPTY_TASKS -> "لا مهام اليوم."
        VoiceScene.HARD_DAY -> "خففنا الخطة اليوم."
        else -> "{task}"
    }

    private fun fill(template: String, vars: Map<String, String>): String {
        var text = template
        for ((key, value) in vars) text = text.replace("{$key}", value)
        text = text.replace("{name}", "صديقي")
            .replace("{mark}", "")
            .replace("{task}", "مهمتك")
            .replace("{subject}", "المادة")
            .replace("{count}", "0")
            .replace("{done}", "0")
            .replace("{days}", "يوم")
            .replace("{minutes}", "25")
        text = PLACEHOLDER.replace(text, "")
        return text.replace(Regex("\\s{2,}"), " ").trim()
    }

    private val PLACEHOLDER = Regex("\\{[a-zA-Z_]+\\}")
}

/** Small façade the UI uses so the tone setting and the persona are read once. */
internal object AppVoice {
    fun line(context: Context, scene: VoiceScene, vars: Map<String, String> = emptyMap()): String {
        val prefs = FocusPreferences.load(context)
        return Voice.say(
            scene = scene,
            persona = Persona.from(prefs.companionStyle),
            tone = ToneResolver.resolve(ToneState(userTone = prefs.tonePreference)),
            vars = vars
        )
    }
}
