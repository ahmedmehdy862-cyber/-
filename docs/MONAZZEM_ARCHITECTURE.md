# MONAZZEM — المعمارية (Phase 0)

## 1. الشكل العام

تطبيق Android أحادي النشاط (single-activity) مبني على Kotlin + Jetpack Compose.

```
MainActivity (ComponentActivity)
└── FocusTheme
    └── AppRoot                      ← يوجّه الشاشات بحالة نصية: screen
        ├── "onboarding"  FirstLaunchSetupScreen
        ├── "session"     SessionScreen            (مقفلة، لا رجوع)
        ├── "setup"       SessionSetupScreen
        ├── "picker"      BlockedAppsScreen
        ├── "today"/"tasks" TasksScreen
        ├── "calendar"    StudyCalendarScreen
        ├── "subjects"    SubjectsScreen
        ├── "more"        MoreSectionsScreen
        ├── "planner"     StudyPlannerScreen
        ├── "analytics"   StudyAnalyticsScreen
        ├── "settings"    PersonalizationScreen
        ├── "support"     SupportScreen
        ├── "updates"     AppUpdatesScreen
        ├── "prayer_times" PrayerTimesScreen
        └── "permanent_apps" PermanentAppsScreen
```

- التوجيه حالة واحدة: `screen = when { session != null -> "session"; setupTaskId != null -> "setup";
  pickerTaskId != null -> "picker"; !onboardingCompleted -> "onboarding"; else -> rootTab }`.
- `previousRootTab` يحفظ وجهة الرجوع للشاشات الثانوية (تُفتح بـ `openSecondary`).
- **قاعدة جديدة (هذه الجولة):** `BackHandler(enabled = screen !in setOf("today","tasks","session"))`
  يعيد: `pickerTaskId → null`، `setupTaskId → null`، `permanent_apps → settings`، وإلا `previousRootTab`.

## 2. تدفّق الجلسة

```
Task card "ابدأ جلسة"
  → SessionSetupScreen (المدة، نطاق الحجب، التطبيقات، بطاقة الأذونات)
  → onStart(taskId,title,minutes,packages)
      → SessionEngine.updateTaskBlocked(taskId, packages)
      → VpnService.prepare(context)
          إذن مطلوب → vpnPermission.launch(intent) → PendingStart → SessionEngine.start(...)
          إذن ممنوح → SessionEngine.start(...)
  → BlockVpnService (VpnService) + AppBlockAccessibilityService (BlockGate)
  → SessionAlarm يضمن الإنهاء في الموعد
  → انتهاء: رفع الحجب، تسجيل الجلسة في جدول sessions
```

حالة المشاركة: `SessionEngine.active`, `.remainingSeconds`, `.vpnLost`, `.tasksVersion`
(تُجمَع بـ `collectAsState`؛ `tasksVersion` يُستخدم كإشارة تحديث للقوائم).

## 3. البيانات

`StudyDb : SQLiteOpenHelper` — `DB_VERSION = 6`

| الجدول | الدور |
|--------|-------|
| `tasks` | المهام: العنوان، المدة المخططة، التصنيف، الأولوية، الاستحقاق، المادة، التكرار، الحزم المحجوبة |
| `sessions` | جلسات التركيز المنتهية (لحساب الإحصاءات) |
| `subtasks` | مهام فرعية لمهمة |
| `subjects` | المواد الدراسية |
| `events` | أحداث التقويم |
| `semesters` | الفصول الدراسية |
| `exams` | الامتحانات |

قواعد التغيير: ترحيل **إضافي فقط** (`ALTER TABLE ADD COLUMN` أو جدول جديد) داخل `onUpgrade`، مع رفع `DB_VERSION`،
وبلا فقدان بيانات قائمة.

المستودعات: `TaskRepository`, `SubjectRepository`, `StudyEventRepository` — كلها متزامنة وتُستدعى من `Dispatchers.IO`.
ViewModels: `TaskListViewModel`, `SubjectListViewModel`, `StudyCalendarViewModel` — تنشر `StateFlow`.

## 4. التفضيلات

`FocusPreferences` (SharedPreferences): اسم الطالب، هدف يومي بالدقائق، مدة الجلسة الافتراضية،
تذكير صباحي/مسائي وساعتاهما، مظهر/تباين/حجم خط، الحزم المحجوبة دائمًا، اكتمال التهيئة، فهرس الأفاتار.

## 5. مكوّنات المنصة

| المكوّن | المسؤولية | قيد |
|---------|-----------|------|
| `BlockVpnService` | قطع شبكة الحزم المختارة | يحتاج موافقة `VpnService.prepare` |
| `AppBlockAccessibilityService` | كشف التطبيق المفتوح وإخفاؤه | يُفعّل من إعدادات النظام فقط |
| `BlockGate` | قرار الحجب (حزمة محجوبة؟ جلسة نشطة؟) | — |
| `DeviceOwnerController` | تعليق التطبيقات نظاميًا (اختياري) | يحتاج Device Owner |
| `SessionAlarm` | إنهاء الجلسة في وقتها | يحتاج دقة منبّه |
| `StudyNotificationScheduler` | تذكيرات يومية | قناة إشعار + إذن POST_NOTIFICATIONS |
| `PrayerReminderScheduler` | تذكير قبل/عند كل صلاة | حساب محلي + موقع/إحداثيات محفوظة |
| `ScheduleConflictDetector` | تعارض جلسة مع صلاة | — |
| `AppUpdateManager` | فحص الإصدار من الموجز | شبكة |

## 6. حدود التصميم (لا تُخترق)

1. **لا Store منفصل، لا خدمة سحابية، لا حساب مستخدم** — كل البيانات محلية.
2. **لا حجب بلا إذن صريح**، ولا تعطيل لأزرار الطوارئ/الاتصال.
3. **الشاشة الجديدة تُضاف كـ `screen` نصي** في `AppRoot` مع تسجيلها في `BackHandler` وفي شريط التنقل عند الحاجة.
4. **كل نص عربي فصيح**، بلا عامية، وبمصطلح موحّد «جلسة / مهمة / مادة / خطة».
5. **كل تعديل يُبنى ويُثبَّت ويُختبر على الجهاز** قبل اعتباره منتهيًا.

## 7. نقاط التوسّع المقترحة للمراحل التالية

- `TaskCategory` الحالي `enum` ثابت → يتحول إلى كيان `task_types(id, name, color, icon, sort)` مع قيمة افتراضية
  للمهام القديمة، و`tasks.type_id` كعمود إضافي (ترحيل إضافي).
- حوار الإضافة → مسار «سريع» بالعنوان فقط + «خيارات» قابلة للفتح (بدون كسر الحقول الحالية).
- محرك تذكيرات موحّد `ReminderEngine` يقرأ جدول `reminders` ويعيد الجدولة بعد كل تغيير/إقلاع.
