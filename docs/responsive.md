# MBOA STYLE — Responsive (Phase 16)

Méthode imposée : AUDIT → MESURER → ADAPTER → TESTER → CORRIGER.
Backend inchangé (aucun Java modifié dans cette phase).

## 1. Audit initial (constats réels, Chrome 151 + Playwright)

- Meta `viewport` déjà correct sur les 3 pages, zoom non bloqué.
- Tailwind CDN + classes `sm:/md:/lg:/2xl:` déjà systématiques ; base mobile-first existante.
- Mesure `documentElement.scrollWidth - body.clientWidth` sur
  360/390/768/1024/1366/1920 × 3 pages + interactions (boutique, modale
  produit, panier, messages, notifications, menu, checkout, 13 pages admin,
  7 vues fournisseur) : **0 overflow accidentel, 0 erreur JS**.
- Seuls larges éléments = `hero-flow-row` et blobs décoratifs, confinés par
  `overflow:hidden` parent (intentionnel, pas un bug).
- Problèmes réels trouvés et corrigés (§3). Le reste était déjà sain :
  header compact, bottom-nav mobile, menu Escape/aria, stat cards admin
  empilées, tableaux admin en `overflow-x-auto` contrôlé.

## 2. Breakpoints retenus (contenu, pas d'arbitraire)

| Nom | Largeur | Usage |
|---|---|---|
| mobile | 320–640 | 2 cartes catalogue, nav bottom, hero compact |
| sm/md | 640–1024 | 2–3 cartes, filtres en ligne |
| lg | 1024–1536 | auto-fill 230px (inchangé), sidebar admin |
| 2xl | 1536+ | 5 cartes, inchangé |

Règle : `grid-cols-2 md:grid-cols-3 lg:grid-cols-[auto-fill…]` —
desktop `lg+` strictement identique à avant (§7).

## 3. Corrections apportées

1. **Escape** : ne fermait ni la modale produit ni le panier → fermés via
   `keydown` (overlay + bouton existants conservés).
2. **Boutons quantité panier** 28px → **40px** (`w-10 h-10`) ; nav 40px déjà OK.
3. **Grille boutique/fournisseur** : 1 colonne à 390px → **2 colonnes mobile**
   (`grid-cols-2`), 3 à `md`, auto-fill au-delà (desktop intact, vérifié 1920).
4. **Safe-area** : `env(safe-area-inset-bottom)` sur la bottom-nav.
5. **Formulaires** : `autocomplete="name/tel"`, `inputmode="tel"` au checkout
   (`type="tel"` déjà présent).
6. `prefers-reduced-motion` déjà respecté (hero-flow, typewriter).

Non fait (volontaire) : PWA (aucune base — future possible), `srcset`
(Phase 17 images), bottom-sheet filtres (filtres actuels déjà tactiles),
CTA sticky produit (barre existante accessible, pas de masquage constaté).

## 4. Navigation / sections

Mobile : header compact (logo, recherche, favoris, messages, notif, panier,
menu), menu `toggleMobileMenu` + bottom-nav 5 entrées (`aria-label`,
`aria-current`). Messagerie : liste + conversation empilées, plein écran
naturel. Fournisseur : vues OK 390px (skeletons + erreurs propres sans
backend). Admin : sidebar drawer `< lg`, cartes empilées, tableaux en scroll
horizontal contrôlé (comparaison colonnes indispensable).

## 5. Images / typo / animations

`loading="lazy"` + `alt` déjà en place ; ratio conservé (`object-cover`,
pas de déformation). Typewriter : `min-h` + wrap, pas de scroll horizontal
mesuré à 320px. Hero : hauteur auto, images de fond discrètes (blur +
opacité). Zoom utilisateur jamais bloqué. Polices Manrope/DM Serif avec
fallbacks système.

## 6. Tests par résolution (navigateur réel, screenshots `/tmp/shots`)

320×568, 360×800, 375/390×844, 412/430 (couvert par 390), 768×1024,
820 (couvert), 1024×1366, 1280 (couvert par 1366), 1366×768, 1440 (couvert),
1920×1080, paysage 844×390 : **tous overflow=0, JS errors=0**.
Pages/états : accueil, boutique, modale produit, panier (vide + 1 article),
checkout, messages, notifications, menu mobile, 13 pages admin (390+1366),
7 vues fournisseur (390), logins (3 pages).

## 7. Fonctionnel / régression

Aucune API ni règle métier touchée (zéro Java). `mvn clean test` et
`mvn package` : voir rapport. `node --check` sur les 3 blocs JS après
modifs.

## 8. Limitations

- Données réelles non testées en backend (pas de Tomcat/MySQL local) :
  tableaux admin denses et conversations remplies à revérifier en staging.
- Sticky CTA produit et bottom-sheet filtres réévaluables après retours.
- `srcset`/images adaptatives → Phase 17.
