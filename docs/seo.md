# MBOA STYLE — SEO (Phase 18)

Base SEO technique réelle, sans contenu fictif. Méthode : AUDIT → STRUCTURER
→ TESTER → DOCUMENTER. Aucune promesse de classement.

## 1. Architecture SEO

La boutique est une SPA (`#/...` invisibles aux crawlers). Stratégie :
- **Server-rendered** (`SeoServlet`) : `/produit/<id>[/<slug>]`,
  `/categorie/<slug>`, `/boutique/<id>/<slug>` — même contenu RÉEL MySQL,
  HTML sémantique, JSON-LD, canonique. Slug erroné/absent → **301** vers le
  canonique. Produit supprimé/non public → **404 réelle** (jamais d'accueil).
- **SPA inchangée** pour les humains (liens `#/produit/ID` depuis les pages SEO).

## 2. Pages indexables / non indexables

| URL | Type | Index | Preuve |
|---|---|---|---|
| `/` | Accueil (title/desc/OG/JSON-LD WebSite) | Oui | index.html |
| `/produit/<id>/<slug>` | Produit public (Product+Offer+Review+Breadcrumb) | Oui | SeoServlet |
| `/categorie/<slug>` | Catégorie active (CollectionPage+ItemList) | Oui | SeoServlet |
| `/boutique/<id>/<slug>` | Boutique active (Store, sans email/tél) | Oui | SeoServlet |
| `/sitemap.xml` | Sitemap dynamique | Oui | SitemapServlet |
| `/recherche?q=` | Recherche interne | Non (aucun lien, aucun sitemap) | — |
| `/dashboard.html`, `/fournisseur.html` | App shells privées | Non (`noindex` + `Disallow`) | meta + robots.txt |
| `/404.html` | Erreur | Non (`noindex`) | meta |
| `/api/*`, `/admin/*` | API/privé | Non (`Disallow` ; auth Phase 15) | robots.txt |

## 3. URLs et canonique

Stables (`ID` = identité technique, slug = lisibilité ; collisions impossibles).
Canonique absolu construit depuis la requête (jamais de domaine codé en dur
côté serveur). Seule exception : `index.html`/`robots.txt`/`sitemap`
référencent `https://mboastyle.cm` — domaine officiel déjà déclaré dans le
JSON-LD préexistant ; à reconfigurer si le domaine de production diffère.

## 4. Titles / descriptions

Accueil : titre + description uniques préexistants (conservés). Pages SEO :
`<nom réel> — MBOA STYLE`, descriptions 155 car. max construites des
données (prix XAF, marque, stock, ville) — jamais d'invention.

## 5. Sitemap

Dynamique : accueil + catégories actives + produits publics (garde-fou 8 Mo)
+ boutiques actives. Exclut : hash-routes, recherche, privé, non-publics.
Ancien sitemap (`/#/…`, `/about`, `/contact` fantômes) supprimé — ces URLs
n'ont jamais résolu côté serveur.

## 6. Structured Data (validées par parse Gson en test)

Product (name, image, description, brand, sku, offers XAF + InStock/OutOfStock
réelle), aggregateRating + review **uniquement si avis approuvés réels**,
BreadcrumbList, CollectionPage+ItemList (≤ 50), Store (+PostalAddress CM).
Rupture → page 200 + OutOfStock (jamais de fausse disponibilité).

## 7. Open Graph / partage

Accueil : `og:title/description/type/url/image/locale` + Twitter cards
(image réelle `hero-01.jpeg`). Produits : `og:type=product`, image produit
réelle (ou omise si absente — jamais de visuel fictif).

## 8. Images / ALT / liens internes

Alt = nom réel du produit ; décoratif = `alt=""` (hero-flow déjà `alt=""`).
Maillage : breadcrumb ↔ catégorie ↔ produits ↔ similaires (même catégorie,
≤ 8) ↔ boutique. Pas de page orpheline publique (produits atteignables via
catégorie, boutique, similaires, sitemap).

## 9. Fournisseurs / i18n / Cameroun

Boutiques : nom, ville, description publique, produits — email/téléphone/
chiffres internes exclus. `fr_CM`, prix XAF, villes réelles (Yaoundé,
Douala), sans keyword stuffing. Pas de pages EN (non supportées).

## 10. 404 / performance / Search Console

`404.html` (accueil + retour, `noindex`, statut via `web.xml` conservé).
Phase 17 préservée : +0 JS, +0 image (thumbnails déjà optimisées).
À faire en production : déclarer le sitemap dans Search Console, vérifier le
domaine, surveiller la couverture (cf. §11).

## 11. Tests réalisés

- Unitaires : `SeoServletTest` (7) — parsing, canonique, échappement XSS.
- Rendu réel MySQL : `SeoRenderTest` (5) — 200/301/404, JSON-LD parsé et
  asserté (prix 2000 XAF produit #45), ItemList, Store sans email, XSS neutre.
- HTTP statique : `robots.txt` servi (200). Pas de Tomcat local : les rendus
  servlet sont couverts par les tests proxy (requête/réponse simulées).
- Non-régression : `mvn clean test`, `mvn package`, `node --check` frontends.

## 12. Reste avant production

Domaine canonique à confirmer, Search Console, redirections si renommage de
slugs historiques, surveillance `Crawl Stats`, sitemap index si > 50k URLs.
