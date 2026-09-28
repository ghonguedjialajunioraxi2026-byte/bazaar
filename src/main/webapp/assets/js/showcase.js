/* ============================================================
   MBOA STYLE — Showcase produits (panneau d'animation)
   Images RÉELLES : assets/img/showcase (vendored depuis
   "photo produit"). Crossfade premium : opacity + translate
   + scale (900ms), pause 3000ms, pause au survol, flèches,
   dots, preload progressif, prefers-reduced-motion respecté.
   Aucune donnée inventée, aucune logique métier.
   Usage : <div class="ms-showcase" data-showcase>…</div>
   + initShowcase(root) au DOMContentLoaded.
   ============================================================ */
(function () {
  'use strict';

  var BASE = 'assets/img/showcase/p';
  var COUNT = 12;
  var HOLD_MS = 3000;
  var TRANS_MS = 900;

  function urls() {
    var list = [];
    for (var i = 1; i <= COUNT; i++) {
      list.push(BASE + ('0' + i).slice(-2) + '.jpeg');
    }
    return list;
  }

  // Préchargement : 2 premières immédiates, la suite en chaîne.
  function preload(list, from) {
    var i = from || 0;
    function next() {
      if (i >= list.length) return;
      var img = new Image();
      img.onload = img.onerror = function () { i++; next(); };
      img.src = list[i];
    }
    next();
  }

  function el(tag, cls, parent, attrs) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (attrs) {
      for (var k in attrs) {
        if (Object.prototype.hasOwnProperty.call(attrs, k)) n.setAttribute(k, attrs[k]);
      }
    }
    if (parent) parent.appendChild(n);
    return n;
  }

  function initShowcase(root) {
    if (!root || root.dataset.showcaseInit) return;
    root.dataset.showcaseInit = '1';

    var reduceMotion = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var list = urls();

    var brand = el('div', 'ms-showcase-brand', root);
    brand.innerHTML = 'MBOA STYLE <span>· Sélection</span>';

    var stage = el('div', 'ms-showcase-stage', root);
    var peek = el('div', 'ms-showcase-peek', stage);
    var peekImg = el('img', '', peek, { alt: '', 'aria-hidden': 'true', loading: 'lazy' });
    var main = el('div', 'ms-showcase-main', stage);
    var layerA = el('img', '', main, { alt: 'Produit MBOA STYLE' });
    var layerB = el('img', '', main, { alt: '', 'aria-hidden': 'true' });

    var nav = el('div', 'ms-showcase-nav', root);
    var prev = el('button', 'ms-showcase-arrow', nav, { type: 'button', 'aria-label': 'Produit précédent' });
    prev.textContent = '‹';
    var dots = el('div', 'ms-showcase-dots', nav, { role: 'tablist', 'aria-label': 'Produits' });
    var next = el('button', 'ms-showcase-arrow', nav, { type: 'button', 'aria-label': 'Produit suivant' });
    next.textContent = '›';

    var dotBtns = list.map(function (_, i) {
      var d = el('button', '', dots, {
        type: 'button', role: 'tab', 'aria-label': 'Produit ' + (i + 1)
      });
      d.addEventListener('click', function () { go(i, true); });
      return d;
    });

    var idx = 0;
    var showingA = true;
    var timer = null;
    var paused = false;

    preload(list, 0);
    layerA.src = list[0];
    layerA.classList.add('is-active');
    peekImg.src = list[1 % list.length];
    paintDots();

    function paintDots() {
      dotBtns.forEach(function (d, i) {
        d.classList.toggle('is-active', i === idx);
        d.setAttribute('aria-selected', i === idx ? 'true' : 'false');
      });
    }

    function show(i) {
      idx = (i + list.length) % list.length;
      var incoming = showingA ? layerB : layerA;
      var outgoing = showingA ? layerA : layerB;
      showingA = !showingA;
      // L'image suivante est déjà préchargée (§44) : pas d'écran blanc.
      incoming.classList.remove('is-exit');
      incoming.src = list[idx];
      outgoing.classList.remove('is-active');
      outgoing.classList.add('is-exit');
      // force reflow pour rejouer la transition d'entrée
      void incoming.offsetWidth;
      incoming.classList.add('is-active');
      peekImg.src = list[(idx + 1) % list.length];
      paintDots();
    }

    function go(i, manual) {
      show(i);
      if (manual) restart();
    }

    function tick() {
      if (!paused && !reduceMotion) show(idx + 1);
      schedule();
    }

    function schedule() {
      stop();
      if (reduceMotion) return;
      timer = setTimeout(tick, HOLD_MS + TRANS_MS);
    }

    function stop() {
      if (timer) { clearTimeout(timer); timer = null; }
    }

    function restart() {
      stop();
      schedule();
    }

    prev.addEventListener('click', function () { go(idx - 1, true); });
    next.addEventListener('click', function () { go(idx + 1, true); });
    root.addEventListener('mouseenter', function () { paused = true; });
    root.addEventListener('mouseleave', function () { paused = false; });
    root.addEventListener('focusin', function () { paused = true; });
    root.addEventListener('focusout', function () { paused = false; });
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) stop();
      else schedule();
    });

    schedule();
  }

  window.initShowcase = initShowcase;
  document.addEventListener('DOMContentLoaded', function () {
    Array.prototype.forEach.call(
      document.querySelectorAll('[data-showcase]'),
      initShowcase
    );
  });
})();
