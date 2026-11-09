/* AmberLauncher: situs unduhan.
   Dua hal saja: (1) tombol unduh mengambil rilis terbaru dari GitHub, (2) demo kontrol di bagian atas.
   Sengaja ditulis dengan sintaks lama (var, function) supaya jalan juga di browser HP lama. */
(function () {
  'use strict';

  var REPO = 'ItsVynzzzz/AmberLauncher';
  var RELEASES = 'https://github.com/' + REPO + '/releases';
  var isEn = document.documentElement.lang === 'en';

  var TXT = isEn ? {
    main: 'Download 64-bit APK',
    sub: function (v, mb) { return 'Version ' + v + (mb ? ' (' + mb + ' MB)' : ''); },
    released: function (d) { return 'Released ' + d + '.'; },
    none: 'No downloadable release was found yet. The button opens the GitHub Releases page.',
    fail: 'Could not load the latest version. The button opens the GitHub Releases page instead.',
    copy: 'Copy', copied: 'Copied', locale: 'en-GB'
  } : {
    main: 'Unduh APK 64-bit',
    sub: function (v, mb) { return 'Versi ' + v + (mb ? ' (' + mb + ' MB)' : ''); },
    released: function (d) { return 'Dirilis ' + d + '.'; },
    none: 'Belum ada rilis yang bisa diunduh. Tombol membuka halaman Releases di GitHub.',
    fail: 'Info versi terbaru tidak bisa dimuat. Tombol membuka halaman Releases di GitHub.',
    copy: 'Salin', copied: 'Tersalin', locale: 'id-ID'
  };

  function $(id) { return document.getElementById(id); }
  function all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

  /* ---------- Rilis terbaru ---------- */

  var CACHE_KEY = 'amber-release-v1';
  var CACHE_MS = 15 * 60 * 1000;

  function readCache() {
    try {
      var raw = window.sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      var c = JSON.parse(raw);
      if (c && c.t && Date.now() - c.t < CACHE_MS) return c.d;
    } catch (e) { /* penyimpanan bisa dimatikan; abaikan */ }
    return null;
  }

  function writeCache(d) {
    try { window.sessionStorage.setItem(CACHE_KEY, JSON.stringify({ t: Date.now(), d: d })); } catch (e) { /* abaikan */ }
  }

  // Ambil rilis bernomor terbaru (bukan build uji "uji") dan pilih APK 64-bit/32-bit dari namanya.
  function pick(list) {
    for (var i = 0; i < list.length; i++) {
      var r = list[i];
      if (r.draft || !/^v\d/.test(r.tag_name || '')) continue;
      var a64 = null, a32 = null;
      var assets = r.assets || [];
      for (var j = 0; j < assets.length; j++) {
        var n = assets[j].name || '';
        if (!/\.apk$/i.test(n)) continue;
        var a = { url: assets[j].browser_download_url, size: assets[j].size };
        if (/32bit|armeabi/i.test(n)) { if (!a32) a32 = a; } else if (!a64) { a64 = a; }
      }
      if (a64 || a32) {
        return { version: r.tag_name.replace(/^v/, ''), date: r.published_at || r.created_at, a64: a64, a32: a32 };
      }
    }
    return null;
  }

  function mb(bytes) { return bytes ? String(Math.max(1, Math.round(bytes / 1048576))) : ''; }

  function setStatus(msg, isErr) {
    var s = $('dl-status');
    if (!s) return;
    s.textContent = msg || '';
    s.className = 'status' + (isErr ? ' err' : '');
  }

  function show(rel) {
    var main = $('dl-main');
    var primary = rel.a64 || rel.a32;
    if (main && primary) {
      main.href = primary.url;
      var l1 = main.querySelector('.l1');
      var l2 = main.querySelector('.l2');
      if (l1) l1.textContent = rel.a64 ? TXT.main : TXT.main.replace('64', '32');
      if (l2) l2.textContent = TXT.sub(rel.version, mb(primary.size));
    }
    all('[data-dl="64"]').forEach(function (el) { if (rel.a64) el.href = rel.a64.url; });
    all('[data-dl="32"]').forEach(function (el) {
      if (rel.a32) { el.href = rel.a32.url; el.removeAttribute('hidden'); } else if (el.hasAttribute('data-hide-missing')) { el.setAttribute('hidden', ''); }
    });
    var when = '';
    try { when = new Date(rel.date).toLocaleDateString(TXT.locale, { day: 'numeric', month: 'long', year: 'numeric' }); } catch (e) { /* abaikan */ }
    setStatus(when ? TXT.released(when) : '', false);
  }

  function loadRelease() {
    if (!$('dl-main')) return;
    var cached = readCache();
    if (cached) { show(cached); return; }
    if (!window.fetch) { setStatus(TXT.fail, true); return; }
    window.fetch('https://api.github.com/repos/' + REPO + '/releases?per_page=12', { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (res) { if (!res.ok) throw new Error('http ' + res.status); return res.json(); })
      .then(function (list) {
        var rel = pick(list);
        if (!rel) { resetMain(); setStatus(TXT.none, true); return; }
        writeCache(rel);
        show(rel);
      })
      .catch(function () { resetMain(); setStatus(TXT.fail, true); });
  }

  // Tanpa info rilis, tombol tetap berguna: membuka halaman Releases.
  function resetMain() {
    var l2 = document.querySelector('#dl-main .l2');
    if (l2) l2.textContent = 'github.com/' + REPO;
  }

  /* ---------- Salin sidik jari ---------- */

  function setupCopy() {
    all('[data-copy]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var src = $(btn.getAttribute('data-copy'));
        if (!src) return;
        var text = src.textContent.trim();
        var done = function () {
          btn.textContent = TXT.copied;
          window.setTimeout(function () { btn.textContent = TXT.copy; }, 1800);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(src); done(); });
        } else { fallbackCopy(src); done(); }
      });
    });
  }

  function fallbackCopy(node) {
    try {
      var range = document.createRange();
      range.selectNodeContents(node);
      var sel = window.getSelection();
      sel.removeAllRanges(); sel.addRange(range);
      document.execCommand('copy');
      sel.removeAllRanges();
    } catch (e) { /* abaikan */ }
  }

  /* ---------- Demo kontrol ---------- */

  function setupRig() {
    var rig = $('rig');
    var svg = $('scene');
    if (!rig || !svg || !svg.setPointerCapture) return;

    var joy = svg.querySelector('.joy');
    var thumb = svg.querySelector('.thumb');
    var far = svg.querySelector('.far');
    var near = svg.querySelector('.near');
    var world = $('world');
    var VW = 324, CX = 52, CY = 98, R = 18;
    var active = null;

    function stopHint() { rig.classList.remove('hint'); }

    function setPos(dx, dy) {
      thumb.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
      far.style.transform = 'translate(' + (-dx * 0.3) + 'px,0)';
      near.style.transform = 'translate(' + (-dx * 0.7) + 'px,0)';
    }

    function move(e) {
      var r = svg.getBoundingClientRect();
      var k = VW / r.width;
      var dx = (e.clientX - r.left) * k - CX;
      var dy = (e.clientY - r.top) * k - CY;
      var d = Math.sqrt(dx * dx + dy * dy);
      if (d > R) { dx *= R / d; dy *= R / d; }
      setPos(dx, dy);
    }

    function setBack(on) {
      [thumb, far, near].forEach(function (n) { n.classList[on ? 'add' : 'remove']('back'); });
    }

    joy.addEventListener('touchstart', function (e) { e.preventDefault(); }, { passive: false });
    joy.addEventListener('pointerdown', function (e) {
      if (active !== null) return;
      stopHint();
      active = e.pointerId;
      try { joy.setPointerCapture(e.pointerId); } catch (err) { /* abaikan */ }
      setBack(false);
      move(e);
      e.preventDefault();
    });
    joy.addEventListener('pointermove', function (e) { if (e.pointerId === active) move(e); });
    function release(e) {
      if (e.pointerId !== active) return;
      active = null;
      setBack(true);
      setPos(0, 0);
    }
    joy.addEventListener('pointerup', release);
    joy.addEventListener('pointercancel', release);
    joy.addEventListener('lostpointercapture', release);

    function play(cls) {
      world.classList.remove('hop', 'swing');
      void world.getBoundingClientRect();
      world.classList.add(cls);
    }
    world.addEventListener('animationend', function () { world.classList.remove('hop', 'swing'); });

    all('#scene .pad').forEach(function (pad) {
      var kind = pad.getAttribute('data-b');
      function down(e) {
        stopHint();
        pad.classList.add('on');
        if (kind === 'jump') play('hop');
        else if (kind === 'attack') play('swing');
        e.preventDefault();
      }
      function up() { pad.classList.remove('on'); }
      pad.addEventListener('pointerdown', down);
      pad.addEventListener('pointerup', up);
      pad.addEventListener('pointerleave', up);
      pad.addEventListener('pointercancel', up);
    });

    var slots = all('#scene .slot');
    slots.forEach(function (slot) {
      slot.addEventListener('pointerdown', function (e) {
        slots.forEach(function (s) { s.classList.remove('sel'); });
        slot.classList.add('sel');
        e.preventDefault();
      });
    });
  }

  loadRelease();
  setupCopy();
  setupRig();
})();
