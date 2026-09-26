# MBOA STYLE — Durcissement sécurité + robustesse (Phase 20)

## 1. Périmètre

Tests offensifs limités à l'application locale et `mboa_style_test`. Matrice
détaillée : `docs/security-test-report.md` (111 tests). Ne couvre pas : pentest
réseau, audit de code exhaustif ligne à ligne, revue cryptographique des
primitives (PBKDF2 via `PasswordUtil`, JWT non utilisé — sessions opaques).

## 2. Environnement

Java 17.0.19, Maven 3.6.3, MySQL 8.0.46, Tomcat 10.1.59 (port 8082, `DB_URL` →
`mboa_style_test`), Chrome headless. WAR reconstruit avant/après corrections.

## 3. Comptes de test

SUPPLIER_A/B (`seca/se064b…@test.cm`), clients `65521xxx/65529xxx/65541xxx…`,
`SUPER_ADMIN admin`, `GESTIONNAIRE gest` (mot de passe de TEST réinitialisé sur
la base de test uniquement). Produits/commandes/conversations/offres A et B
distincts pour l'isolation.

## 4. Méthodologie

AUDITER → ATTAQUER (requêtes HTTP réelles) → OBSERVER (statut + corps + état DB)
→ CORRIGER → RETESTER → RÉGRESSION (`mvn`, Phase 19) → DOCUMENTER → COMMIT.
Aucun « ça semble sécurisé » sans preuve ; les 500 initiaux ont été départagés
entre vrais bugs et artefacts du harnais par retests ciblés + lecture DB.

## 5. Authentification

Login correct/erroné/vide/invalide : 401/400 propres. Brute force : 429 après
5 échecs (IP+compte, 15 min, `LoginAttemptService`). Google : faux token → 401,
`aud` vérifié contre notre client ID (`GoogleAuthUtil`), timeouts 8 s.

## 6. Autorisation

Matrice GUEST/SUPPLIER/GESTIONNAIRE/ADMIN/SUPER_ADMIN vérifiée : 401 sans token,
403 rôle insuffisant (MONEY, DESTRUCTIVE). `?token=` refusé (anti-fuite logs).
Escalade (`role`, `supplierId`, `is_admin` forgés) : champs ignorés, propriété
résolue serveur.

## 7. IDOR

Clients : suivi/myorders/conversations cloisonnés par téléphone+clé (`403/404`
sans fuite). Fournisseurs : produit/commande/conversation/stock d'autrui → 404
(`findByIdScoped`, `isOwner`). Commande cross-vendeur → 400 (Phase 19).

## 8. Injection

`PreparedStatement` partout (audit DAO) ; `sort` whitelisté (chaîne if/else +
défaut sûr) ; 6 payloads SQLi + login/tri/id/track : 200/4xx sans `SQLException`
ni stack. Erreurs : message générique `INTERNAL_ERROR`.

## 9. XSS

Stockage brut, échappement au rendu (`escHTML/escAttr/notifEsc/esc/escMsg`) +
`textContent` ; JSON `application/json` ; avis modérés avant publication ;
SEO `/produit/` échappé ; aucun `eval/document.write`, `innerHTML` alimenté par
données échappées. Uploads : SVG refusé, servi `text/plain` + `nosniff`.

## 10. CSRF/CORS

Pas de cookie d'auth (Bearer `localStorage`, 0 `getSession/addCookie`) → CSRF
classique inefficace (INFO : contrepartie = vol de token via XSS, d'où §9).
Aucune en-tête CORS émise (same-origin strict).

## 11. Uploads

Whitelist bitmap + magic bytes + refus SVG + 10 Mo + noms UUID + `..` bloqué à
la lecture comme à l'écriture. Correction Phase 20 : refus → 400 (était 500).

## 12. OAuth

Contrat `/api/supplier/google-client-id` public (ID ≠ secret), vérification
serveur via `tokeninfo` + contrôle `aud`, faux token → 401. Pas de flux réel
(sans credential) — risque résiduel documenté.

## 13. Paiements

Montant/devise depuis la commande (`FCFA`), jamais du client ; init_VERIF :
guest init/référence, verify/fail réservés MONEY ; double verify 409, double
référence idempotente, commande fake 404, tél tiers refusé.

## 14. Commandes

Prix/fee/discount recalculés (`OrderService`), stock vérifié + garde
multi-vendeur, qty 1..99, statuts `canTransition` (retour LIVREE→EN_ATTENTE 400),
PUT/DELETE admin-only, cancel guest par numéro+téléphone.

## 15. Concurrence

Stock=1 × 2 commandes // → 200+400 ; double accept d'offre // → 200+409
(`WHERE status=PENDING` atomique) ; double paiement → 1 seul effet.

## 16. Robustesse

Pagination bornée (FIX-2 : `Pagination`, tests JUnit), qty/lignes/msg/JSON
bornés, ids hostiles propres, JSON malformé 400 sans stack, rate limits
(messages 10/min, offres 10/jour/conv, avis 5/h → 429), produit supprimé → 404.

## 17. Vulnérabilités trouvées

VULN-001 LOW (upload refusé → 500), VULN-002 MEDIUM (pagination → 500).
0 CRITICAL, 0 HIGH. Détails + preuves : `docs/security-test-report.md` §5/7/9.

## 18. Corrections

- `SupplierApiServlet` (doPost/doPut) + `ProductImageServlet` (doPost) :
  `IOException` → 400 « Image invalide ou refusée ».
- Nouveau `util/Pagination.java` (`clampPage/clampLimit`) utilisé par
  `ProductServlet`, `CategoryServlet`, `SupplierApiServlet#ledger`,
  `AdminServlet` (control center + logs).
- Fichiers : 1 créé, 5 modifiés. Aucun changement de comportement légitime
  (bornes ≥ usages réels : 50 public, 100 admin, 200 logs).

## 19. Retests

111/111 PASS après corrections ; Phase 19 : 80/80 ; `mvn clean test` 62/62 ;
`mvn package` OK. Intégrité DB : 0 orphelin, 0 stock/montant négatif.

## 20. Risques résiduels

1. Compte MySQL applicatif = `root` local tous privilèges → créer un utilisateur
   dédié least-privilege en production (non modifié en local, cf. §DB du rapport).
2. `DB_PASSWORD` de repli en dur (dev uniquement, avertissement au démarrage,
   présent dans l'historique Git depuis le baseline) → imposer `DB_URL`/`DB_PASSWORD`
   en production + rotation si le repli a fuité.
3. Apps Tomcat par défaut (`manager`, `examples`, `docs`) présentes en local →
   les supprimer/désactiver en production.
4. Pas de CSP (décision documentée : inline + CDN) → durcir si le frontend
   évolue vers bundles sans inline ; `esc()` de `fournisseur.html` n'échappe pas
   les quotes (OK en attributs doubles-quotés, fragile en `onclick='…'` futur).
5. OAuth réel, webhooks PSP, charge simultanée massive : non testés (pas de
   sandbox locale) → Phase 21/23.
6. Dépendances : `gson 2.10.1`, `mysql-connector-j 8.4.0` sans CVE critique
   connue bloquante au 2026-09 ; pas de mise à jour massive (incompatibilités).
   Réévaluer à chaque release (`mvn versions:display-dependency-updates`).
