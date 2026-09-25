# MBOA STYLE — Navigation (Phase 3)

## Carte des routes

```text
PUBLIC (aucun rôle)
  /                     → index.html (accueil, sections dynamiques)
  /index.html           → idem
  /dashboard.html       → login admin puis app (token requis pour l'API)
  /fournisseur.html     → login/register fournisseur (email + Google)
  /404.html             → page introuvable (erreur 404 Tomcat)
  /sitemap.xml          → SEO (servlet)
  /uploads/*            → fichiers persistants (servlet)
  /api/products, /categories, /suppliers, /delivery, /promotions,
  /reviews, /orders, /product-images (GET/POST ciblés)
```

```text
FOURNISSEUR (token supplier, 12h, Bearer)
  /fournisseur.html (vues : dashboard, produits, commandes, revenus,
  statistiques, paiements, messages, notifications, profil)
  /api/supplier/* (401 sans token, géré par forceLogout → login)
```

```text
ADMIN (token admin, 8h, Bearer)
  /dashboard.html (pages : dashboard, orders, notifications, categories,
  promotions, suppliers, visitors, stats, settings)
  /api/admin/* + écritures (401 sans token)
```

Sections SPA (`index.html`, `showSection`) : `home` · `shop` · `checkout`.
État actif : liens desktop (`aria-current`), bottom nav mobile, sidebars
(`aria-current` ajouté Phase 3). Breadcrumbs : boutique (+ cat paths),
checkout (étapes). Retour : "Continuer mes achats" (panier),
bannière catégorie, footer, 404 (Accueil/Retour).

## Règles
- Cacher un lien ≠ protéger : toute route sensible est vérifiée serveur.
- 401 API → retour login (dashboard/fournisseur).
- Aucun lien vers une fonctionnalité inexistante (sociaux fictifs supprimés).
- URLs API gelées (voir `docs/api.md`).
