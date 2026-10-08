/* ============================================================
   BAZAAR — Mur produits animé (panneaux d'authentification)
   Images RÉELLES : assets/img/showcase (12 visuels, cache
   navigateur : chaque URL chargée une seule fois).
   Flux continu GAUCHE → DROITE : 3 rangées, piste dupliquée,
   boucle translateX imperceptible (34s/39s/44s), images
   collées sans gap, hauteur uniforme, flou modéré (CSS),
   voile de lisibilité (CSS). Aucun setInterval, aucun
   changement d'image : uniquement transform + opacity.
   prefers-reduced-motion : mur statique (une seule rangée).
   Aucune donnée inventée, aucune logique métier.
   Usage : <div class="ms-showcase" data-showcase></div>
   + initShowcase(root) au DOMContentLoaded.
   ============================================================ */
(function () {
  'use strict';

  var BASE = 'assets/img/showcase/p';
  var COUNT = 12;
  var DURS = ['34s', '39s', '44s'];

  function urls() {
    var list = [];
    for (var i = 1; i <= COUNT; i++) {
      list.push(BASE + ('0' + i).slice(-2) + '.jpeg');
    }
    return list;
  }

  // Préchargement en chaîne : chaque URL chargée une seule fois.
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

  // Une rangée = piste [groupe A | groupe B identique], défilement
  // de -50% vers 0 : quand B prend la place de A, la boucle
  // repart sans saut visible.
  function buildRow(wall, list, dur, reduceMotion) {
    var row = el('div', 'ms-wall-row', wall, { 'aria-hidden': 'true' });
    var track = el('div', 'ms-wall-track', row);
    track.style.setProperty('--dur', dur);
    var groups = reduceMotion ? 1 : 2;
    for (var g = 0; g < groups; g++) {
      var group = el('div', 'ms-wall-group', track);
      list.forEach(function (src) {
        el('img', '', group, { src: src, alt: '', loading: 'lazy', decoding: 'async', draggable: 'false' });
      });
    }
    return row;
  }

  function initShowcase(root) {
    if (!root || root.dataset.showcaseInit) return;
    root.dataset.showcaseInit = '1';

    var reduceMotion = window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var list = urls();

    var brand = el('div', 'ms-showcase-brand', root);
    brand.innerHTML = 'BAZAAR <span>· Sélection</span>';

    var wall = el('div', 'ms-wall', root, { 'aria-hidden': 'true' });
    // Décalage circulaire par rangée pour varier la composition.
    for (var r = 0; r < 3; r++) {
      var rotated = list.slice(r * 4).concat(list.slice(0, r * 4));
      buildRow(wall, rotated, DURS[r % DURS.length], reduceMotion);
    }
    el('div', 'ms-showcase-veil', root, { 'aria-hidden': 'true' });

    preload(list, 0);
  }

  window.initShowcase = initShowcase;
  document.addEventListener('DOMContentLoaded', function () {
    Array.prototype.forEach.call(
      document.querySelectorAll('[data-showcase]'),
      initShowcase
    );
  });
})();
