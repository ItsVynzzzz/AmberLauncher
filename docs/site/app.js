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
    copy: 'Copy', copied: 'Copied', locale: 'en-GB', dls: 'downloads'
  } : {
    main: 'Unduh APK 64-bit',
    sub: function (v, mb) { return 'Versi ' + v + (mb ? ' (' + mb + ' MB)' : ''); },
    released: function (d) { return 'Dirilis ' + d + '.'; },
    none: 'Belum ada rilis yang bisa diunduh. Tombol membuka halaman Releases di GitHub.',
    fail: 'Info versi terbaru tidak bisa dimuat. Tombol membuka halaman Releases di GitHub.',
    copy: 'Salin', copied: 'Tersalin', locale: 'id-ID', dls: 'unduhan'
  };

  function $(id) { return document.getElementById(id); }
  function all(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

  /* ---------- Rilis terbaru ---------- */

  var CACHE_KEY = 'amber-release-v2';
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
    showStats(rel);
  }

  function loadRelease() {
    if (!$('dl-main')) return;
    var cached = readCache();
    if (cached) { show(cached); return; }
    if (!window.fetch) { setStatus(TXT.fail, true); return; }
    window.fetch('https://api.github.com/repos/' + REPO + '/releases?per_page=100', { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (res) { if (!res.ok) throw new Error('http ' + res.status); return res.json(); })
      .then(function (list) {
        var rel = pick(list);
        if (!rel) { resetMain(); setStatus(TXT.none, true); return; }
        rel.stats = tally(list);
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

  /* ---------- Statistik unduhan (jumlah unduhan aset tiap rilis bernomor, dari GitHub) ---------- */

  function tally(list) {
    var total = 0, items = [];
    for (var i = 0; i < list.length; i++) {
      var r = list[i];
      if (r.draft || !/^v\d/.test(r.tag_name || '')) continue;
      var n = 0, a = r.assets || [];
      for (var j = 0; j < a.length; j++) n += a[j].download_count || 0;
      total += n;
      if (items.length < 6) items.push({ tag: r.tag_name, url: r.html_url, date: r.published_at || r.created_at, n: n });
    }
    return { total: total, items: items };
  }

  function fmt(n) { try { return Number(n).toLocaleString(TXT.locale); } catch (e) { return String(n); } }

  function countUp(el, to) {
    if (!el) return;
    var still = !window.requestAnimationFrame || (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
    if (still) { el.textContent = fmt(to); return; }
    var t0 = null;
    function step(t) {
      if (t0 === null) t0 = t;
      var p = Math.min(1, (t - t0) / 900);
      el.textContent = fmt(Math.round(to * (1 - Math.pow(1 - p, 3))));
      if (p < 1) window.requestAnimationFrame(step);
    }
    window.requestAnimationFrame(step);
  }

  function showStats(rel) {
    var s = rel.stats;
    if (!s) return;
    countUp($('st-total'), s.total);
    var v = $('st-ver'); if (v) v.textContent = 'v' + rel.version;
    var b = $('hero-badge'); if (b) b.textContent = 'v' + rel.version + ' \u00b7 ' + fmt(s.total) + ' ' + TXT.dls;
    var ol = $('rel-list');
    if (!ol || !s.items.length) return;
    var max = 1;
    s.items.forEach(function (it) { if (it.n > max) max = it.n; });
    ol.innerHTML = '';
    s.items.forEach(function (it) {
      var li = document.createElement('li'), a = document.createElement('a'), d = document.createElement('span'), c = document.createElement('span'), bar = document.createElement('i');
      a.href = it.url; a.textContent = it.tag;
      d.className = 'd';
      try { d.textContent = new Date(it.date).toLocaleDateString(TXT.locale, { day: 'numeric', month: 'short', year: 'numeric' }); } catch (e) { /* abaikan */ }
      c.className = 'c'; c.textContent = fmt(it.n) + ' ' + TXT.dls;
      bar.style.width = Math.max(2, Math.round(it.n / max * 100)) + '%';
      li.appendChild(a); li.appendChild(d); li.appendChild(c); li.appendChild(bar);
      ol.appendChild(li);
    });
    ol.hidden = false;
  }

  function loadStars() {
    var el = $('st-stars');
    if (!el || !window.fetch) return;
    window.fetch('https://api.github.com/repos/' + REPO, { headers: { Accept: 'application/vnd.github+json' } })
      .then(function (r) { if (!r.ok) throw new Error('http ' + r.status); return r.json(); })
      .then(function (d) { if (d && typeof d.stargazers_count === 'number') { el.textContent = fmt(d.stargazers_count); var nv = $('nav-stars'); if (nv) nv.textContent = '\u2605 ' + fmt(d.stargazers_count); } })
      .catch(function () { /* angka tetap "-" */ });
  }

  // Bagian muncul pelan saat digulir ke layar. Kelasnya baru dipasang oleh skrip ini, jadi tanpa JS semuanya tetap terlihat.
  function setupReveal() {
    if (!window.IntersectionObserver) return;
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { rootMargin: '0px 0px -8% 0px' });
    all('.sec .head, .tile, .apk, .qa li, .total, .rels, .verify, .creds .panel, .steps li, .end-cta .wrap').forEach(function (el) { el.classList.add('rv'); io.observe(el); });
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

  loadRelease();
  setupCopy();
  loadStars();
  setupReveal();
})();
