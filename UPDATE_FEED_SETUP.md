# ربط إشعارات تحديث مُنظِّم

التطبيق يقرأ سجل JSON عامًّا من `https://update-site-sigma.vercel.app/api/latest` عبر HTTPS، ويقارن `versionCode` المثبت بأحدث إصدار. عند وجود إصدار أعلى، يعرض تنبيهًا مرة واحدة لهذا الإصدار، ويفتح مسار التحديث في المتجر.

## صيغة /api/latest

السجل يحتوي على رابط المتجر **ورابط APK معًا**؛ `apkUrl` و`app.apkUrl` مذكوران صراحةً في الاستجابة وتستخدمهما صفحة التنزيل ولوحة الإدارة. هذه صيغة الاستجابة الفعلية التي يقرأها التطبيق:

```json
{
  "packageName": "com.focusstudy.poc",
  "versionCode": 36,
  "versionName": "0.28.0",
  "storeUrl": "https://qubixstore.vercel.app/focus-study-updates",
  "apkUrl": "https://github.com/ahmedmehdy862-cyber/MONAZZEM/releases/download/focus-v0.28.0/MONAZZEM.apk",
  "app": {
    "id": "focus-study",
    "name": "مُنظِّم",
    "category": "تعليم",
    "developer": "مُنظِّم",
    "description": "نظّم مهامك وموادك وجلسات تركيزك وخطتك الدراسية في مساحة واحدة.",
    "versionCode": 36,
    "versionName": "0.28.0",
    "apkUrl": "https://github.com/ahmedmehdy862-cyber/MONAZZEM/releases/download/focus-v0.28.0/MONAZZEM.apk",
    "storeUrl": "https://qubixstore.vercel.app/focus-study-updates",
    "releaseNotes": "أذكار صباح ومساء وبعد الظهر وقبل النوم في أوقاتها، واختيار صوت مستقل لكل نوع إشعار من لوحة الإدارة، واستخدام ملف الآذان.mp3 المضمّن في التطبيق."
  },
  "releasePageUrl": "https://update-site-sigma.vercel.app/#download",
  "releaseNotes": "أذكار صباح ومساء وبعد الظهر وقبل النوم في أوقاتها، واختيار صوت مستقل لكل نوع إشعار من لوحة الإدارة، واستخدام ملف الآذان.mp3 المضمّن في التطبيق."
}
```

`publishedAt` قد يظهر في سجل التخزين الداخلي ولا يُعرض في `/api/latest`. يجب أن يطابق `packageName` الحزمة، وأن يزداد `versionCode` مع كل إصدار. `storeUrl` يفتح صفحة QUBIXStore، بينما `apkUrl` هو ملف APK المنشور على GitHub Releases؛ كلاهما متاح في الموجز.

## ربط الرابط بالتطبيق

العنوان الافتراضي مضبوط على الموجز الحي، ويمكن تمريره صراحةً عند بناء APK:

```powershell
.\gradlew.bat assembleRelease -PfocusStudyUpdateFeedUrl="https://update-site-sigma.vercel.app/api/latest"
```

`assembleRelease` يفشل الآن إذا لم يكن مفتاح التوقيع مضبوطًا في `~/.focusstudy/keystore.properties`. للتجارب المحلية فقط يمكن تمرير `-PallowUnsignedRelease=true`؛ لا تستخدم هذا الخيار للنشر. إذا كان عنوان الموجز غير مضبوط، ستعرض شاشة «تحديثات التطبيق» أن المصدر غير مربوط. بعد ضبطه، يفحص التطبيق عند فتحه ومرة يوميًا، ويرسل إشعارًا عند اكتشاف إصدار أعلى.

## نشر إصدار من لوحة الإدارة

افتح [لوحة إدارة التحديثات](https://update-site-sigma.vercel.app/admin/)، وسجّل الدخول، واربط رابط المتجر. بعد نشر الإصدار في المتجر، اكتب اسمه ورقمه وملاحظاته ورابط الـ APK في اللوحة وانشر إشعار التحديث. يجب أن يطابق توقيع المتجر النسخة المثبتة حتى يتم التحديث فوقها وتبقى بيانات الطالب. لا تشارك بيانات دخول الإدارة.

## النشر الآلي بالوسم (tag)

للنشر الآلي، ارفع `versionName` و`versionCode` في `app/build.gradle.kts`، ثم ادفع وسمًا مثل `focus-v0.28.0` يطابق `versionName` تمامًا. سير العمل في `.github/workflows/focus-study-release.yml` ينفّذ الترتيب التالي:

1. يتحقق من أن حزمة لوحة الإدارة `app.bundle.js` محدَّثة (`node build-admin.cjs` ثم `git diff --exit-code`).
2. يتحقق من تطابق الوسم مع `versionName`، ثم من أن الإصدار الجديد **ليس أقدم** من `feed.versionCode` في `/api/latest` — أي أن الأكبر مقبول، والمساوي مقبول فقط إذا كان `versionName` نفسه (لإعادة تشغيل الوسم) — وأن `FOCUS_RELEASE_SYNC_TOKEN` غير فارغ. لا يوجد endpoint للتحقق الآمن في واجهة المزامنة، لذلك لا يُرسل أي POST في هذه المرحلة.
3. يبني APK موقعًا، ثم ينشئ GitHub Release ويرفع الملف باسم `MONAZZEM.apk`.
4. يرسل البيانات إلى `https://update-site-sigma.vercel.app/api/admin/publish-ci`. إعادة تشغيل الوسم بنفس الحمولة لا تكتب شيئًا، وأي تغيير في الحمولة بنفس `versionCode` يُعيد الكتابة، بينما يُرفض `versionCode` الأقل.
5. يتحقق بGET جديد أن `/api/latest` يعرض `versionCode` و`versionName` الجديدين.

اضبط أسرار `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, و`FOCUS_RELEASE_SYNC_TOKEN` في مستودع التطبيق، واجعل `FOCUS_RELEASE_SYNC_TOKEN` و`BLOB_READ_WRITE_TOKEN` متاحين لمشروع Vercel الخاص بموجز التحديثات. لا ترفع ملف التوقيع أو الأسرار إلى Git.

## فحص ما بعد النشر (smoke test)

بعد كل نشر أو `vercel deploy --prod`، تحقق من الموجز ومن لوحة الإدارة:

```powershell
curl.exe -fsS https://update-site-sigma.vercel.app/api/latest
curl.exe -fsS -o NUL -w "admin HTTP %{http_code}`n" https://update-site-sigma.vercel.app/admin/
```

الأول يجب أن يعرض `versionCode` و`versionName` الجديدين مع `apkUrl`، والثاني يجب أن يعيد HTTP 200. لا تضع أي رمز أو كلمة مرور في أوامر الفحص.
