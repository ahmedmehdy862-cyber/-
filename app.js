/* ============================================================
   مكامن — منطق الاختبار والتقييم
   ============================================================ */

(function () {
  "use strict";

  var STORE_KEY = "makamin-progress-v1";
  var TOTAL = QUESTIONS.length; // 102

  /* ترتيب العرض: جولة أولى لكل مكمن، ثم جولة ثانية، ثم ثالثة */
  var ORDER = [];
  for (var round = 0; round < 3; round++) {
    for (var t = 0; t < THEMES.length; t++) {
      ORDER.push(t * 3 + round);
    }
  }

  function arNum(n) {
    return Number(n).toLocaleString("ar-EG");
  }

  var $ = function (id) { return document.getElementById(id); };

  var screens = {
    hero: $("screen-hero"),
    quiz: $("screen-quiz"),
    results: $("screen-results"),
  };

  function show(name) {
    Object.keys(screens).forEach(function (k) {
      screens[k].hidden = k !== name;
    });
    window.scrollTo({ top: 0, behavior: "instant" in window ? "instant" : "auto" });
  }

  /* ---------- الحالة ---------- */
  var answers = new Array(TOTAL).fill(null); // بترتيب العرض
  var pos = 0;

  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify({ answers: answers, pos: pos }));
    } catch (e) { /* تجاهل */ }
  }

  function load() {
    try {
      var raw = localStorage.getItem(STORE_KEY);
      if (!raw) return false;
      var s = JSON.parse(raw);
      if (!s || !Array.isArray(s.answers) || s.answers.length !== TOTAL) return false;
      answers = s.answers;
      pos = Math.min(Math.max(s.pos || 0, 0), TOTAL - 1);
      return answers.some(function (a) { return a !== null; });
    } catch (e) { return false; }
  }

  function clearStore() {
    try { localStorage.removeItem(STORE_KEY); } catch (e) { /* تجاهل */ }
  }

  /* ---------- شاشة الأسئلة ---------- */
  function renderQuestion() {
    var q = QUESTIONS[ORDER[pos]];
    $("quiz-statement").textContent = q.q;
    $("quiz-counter").textContent = "العبارة " + arNum(pos + 1) + " من " + arNum(TOTAL);
    $("progress-bar").style.width = ((pos) / TOTAL) * 100 + "%";

    var box = $("quiz-options");
    box.innerHTML = "";
    OPTIONS.forEach(function (opt) {
      var btn = document.createElement("button");
      btn.className = "option" + (answers[pos] === opt.v ? " selected" : "");
      btn.innerHTML = '<span class="dot"></span><span></span>';
      btn.lastChild.textContent = opt.label;
      btn.addEventListener("click", function () { choose(opt.v); });
      box.appendChild(btn);
    });

    $("btn-prev").style.visibility = pos > 0 ? "visible" : "hidden";
    $("progress-bar").style.width = (pos / TOTAL) * 100 + "%";
  }

  var advancing = false;
  function choose(v) {
    if (advancing) return;
    answers[pos] = v;
    save();

    var opts = $("quiz-options").children;
    for (var i = 0; i < opts.length; i++) opts[i].classList.remove("selected");
    OPTIONS.forEach(function (opt, i) {
      if (opt.v === v) opts[i].classList.add("selected");
    });

    advancing = true;
    setTimeout(function () {
      advancing = false;
      if (pos < TOTAL - 1) {
        pos++;
        save();
        renderQuestion();
      } else {
        $("progress-bar").style.width = "100%";
        finish();
      }
    }, 260);
  }

  $("btn-prev").addEventListener("click", function () {
    if (pos > 0) { pos--; renderQuestion(); }
  });

  $("btn-quit").addEventListener("click", function () {
    save();
    $("btn-resume").hidden = false;
    show("hero");
  });

  /* ---------- الحساب ---------- */
  function computeScores() {
    var scores = new Array(THEMES.length).fill(0);
    for (var i = 0; i < TOTAL; i++) {
      var v = answers[i];
      if (v !== null) scores[QUESTIONS[ORDER[i]].t] += v;
    }
    return scores;
  }

  function rankThemes(scores) {
    return THEMES.map(function (th, i) {
      return { theme: th, index: i, score: scores[i] };
    }).sort(function (a, b) {
      if (b.score !== a.score) return b.score - a.score;
      return a.index - b.index;
    });
  }

  /* ---------- ترميز النتيجة في الرابط ---------- */
  function encodeScores(scores) {
    var s = scores.map(function (v) { return String.fromCharCode(64 + v); }).join("");
    return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }

  function decodeScores(str) {
    try {
      var b64 = str.replace(/-/g, "+").replace(/_/g, "/");
      var s = atob(b64);
      if (s.length !== THEMES.length) return null;
      var scores = [];
      for (var i = 0; i < s.length; i++) {
        var v = s.charCodeAt(i) - 64;
        if (v < 3 || v > 15) return null;
        scores.push(v);
      }
      return scores;
    } catch (e) { return null; }
  }

  /* ---------- شاشة النتائج ---------- */
  function renderResults(scores, shared) {
    var ranked = rankThemes(scores);
    var top5 = ranked.slice(0, 5);

    $("results-sub").textContent = shared
      ? "نتيجة مشتركة عبر رابط — خض الاختبار بنفسك وقارن."
      : "أجبت عن " + arNum(TOTAL) + " عبارة. هذا ترتيب مكامن قوتك اليوم.";

    /* أعلى خمسة */
    var top = $("top5");
    top.innerHTML = "";
    top5.forEach(function (r, i) {
      var card = document.createElement("div");
      card.className = "theme-card";

      var medal = document.createElement("div");
      medal.className = "medal";
      medal.textContent = arNum(i + 1);

      var body = document.createElement("div");
      var h = document.createElement("h4");
      h.textContent = r.theme.name;
      var tag = document.createElement("span");
      tag.className = "tag";
      tag.textContent = r.theme.tag;
      var p = document.createElement("p");
      p.textContent = r.theme.desc;
      var bar = document.createElement("div");
      bar.className = "score-bar";
      var fill = document.createElement("i");
      fill.style.width = (r.score / 15) * 100 + "%";
      bar.appendChild(fill);

      body.appendChild(h); body.appendChild(tag); body.appendChild(p); body.appendChild(bar);
      card.appendChild(medal); card.appendChild(body);
      top.appendChild(card);
    });

    /* الترتيب الكامل */
    var list = $("ranking");
    list.innerHTML = "";
    ranked.forEach(function (r, i) {
      var row = document.createElement("div");
      row.className = "rank-row" + (i < 5 ? " top" : "");

      var posEl = document.createElement("span");
      posEl.className = "pos";
      posEl.textContent = arNum(i + 1);

      var name = document.createElement("span");
      name.className = "name";
      name.textContent = r.theme.name;

      var bar = document.createElement("div");
      bar.className = "bar";
      var fill = document.createElement("i");
      fill.style.width = "0%";
      bar.appendChild(fill);

      var val = document.createElement("span");
      val.className = "val";
      val.textContent = arNum(r.score) + "/١٥";

      row.appendChild(posEl); row.appendChild(name); row.appendChild(bar); row.appendChild(val);
      list.appendChild(row);

      requestAnimationFrame(function () {
        setTimeout(function () { fill.style.width = (r.score / 15) * 100 + "%"; }, 60);
      });
    });

    $("btn-restart").textContent = shared ? "خض الاختبار بنفسك" : "أعد الاختبار";
    show("results");

    /* أزرار المشاركة */
    var link = location.origin + location.pathname + "#r=" + encodeScores(scores);
    $("btn-copy").onclick = function () {
      var lines = ["نتيجتي في اختبار «مكامن» لنقاط القوة:", ""];
      top5.forEach(function (r, i) {
        lines.push(arNum(i + 1) + "️⃣ " + r.theme.name + " — " + r.theme.tag);
      });
      lines.push("", "الترتيب الكامل: " + link);
      copyText(lines.join("\n"));
    };
    $("btn-share").onclick = function () { copyText(link); };
  }

  function copyText(text) {
    var done = function () {
      var hint = $("share-hint");
      hint.hidden = false;
      clearTimeout(hint._t);
      hint._t = setTimeout(function () { hint.hidden = true; }, 2200);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () { legacyCopy(text); done(); });
    } else {
      legacyCopy(text); done();
    }
  }

  function legacyCopy(text) {
    var ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); } catch (e) { /* تجاهل */ }
    document.body.removeChild(ta);
  }

  function finish() {
    var scores = computeScores();
    clearStore();
    history.replaceState(null, "", "#r=" + encodeScores(scores));
    renderResults(scores, false);
  }

  /* ---------- بدء واستئناف ---------- */
  function startQuiz(fromPos) {
    pos = fromPos || 0;
    history.replaceState(null, "", location.pathname);
    show("quiz");
    renderQuestion();
  }

  $("btn-start").addEventListener("click", function () {
    answers = new Array(TOTAL).fill(null);
    clearStore();
    startQuiz(0);
  });

  $("btn-resume").addEventListener("click", function () { startQuiz(pos); });

  $("btn-restart").addEventListener("click", function () {
    answers = new Array(TOTAL).fill(null);
    clearStore();
    history.replaceState(null, "", location.pathname);
    show("hero");
    $("btn-resume").hidden = true;
  });

  /* ---------- نقطة الدخول ---------- */
  function boot() {
    var hash = location.hash || "";

    /* نتيجة مشتركة عبر رابط */
    var m = hash.match(/^#r=([A-Za-z0-9_-]+)$/);
    if (m) {
      var scores = decodeScores(m[1]);
      if (scores) { renderResults(scores, true); return; }
    }

    /* وضع المعاينة: ‎#quiz‎ أو ‎#quiz/12‎ */
    var q = hash.match(/^#quiz(?:\/(\d+))?$/);
    if (q) {
      answers = new Array(TOTAL).fill(null);
      startQuiz(q[1] ? Math.min(parseInt(q[1], 10), TOTAL - 1) : 0);
      return;
    }

    /* استئناف محفوظ */
    if (load()) $("btn-resume").hidden = false;
    show("hero");
  }

  boot();
})();
