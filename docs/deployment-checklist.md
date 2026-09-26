# MBOA STYLE — Checklist de déploiement (Phase 22)

## AVANT DÉPLOIEMENT

- [x] Backup DB (pre-déploiement conservé : `/tmp/mboa-style/backups/…`)
- [x] Build OK (`mvn clean test` 62/62)
- [x] Tests OK (Phases 19/20 + smoke §30)
- [x] WAR validé (5,0 Mo, sans secret, versionné `releases/mboa-style-1.0.0.war`)
- [x] Secrets configurés (prod.env 600, hors Git ; `check-production-config.sh`)
- [x] DB prête (fraîche + 12 migrations, ordre snapshot→offers vérifié)
- [ ] Domaine prêt (placeholder `mboastyle.cm` à confirmer — Phase 23)
- [ ] HTTPS prêt (certificat à fournir — Phase 23)
- [ ] OAuth prêt (**NON ACTIVÉ**, credential prod à créer si requis)
- [ ] Paiement prêt (**MANUAL uniquement**, aucun PSP LIVE)
- [ ] Reverse proxy prêt (config fournie `deploy/nginx-mboastyle.conf`, Nginx non installé en local)
- [ ] Firewall prêt (MySQL `0.0.0.0:3306` constaté → restreindre en vrai prod)

## DÉPLOIEMENT

- [x] WAR installé (versionné + `current`)
- [x] Configuration chargée (env vérifié : health UP via `mboa_app`)
- [x] Tomcat redémarré (ports 8080/8006, apps par défaut supprimées)
- [x] Logs vérifiés (0 SEVERE/Exception au démarrage)
- [x] Health check OK (`{"status":"UP"}`)

## APRÈS DÉPLOIEMENT

- [x] Accueil / API / DB / catalogue / produit
- [x] Auth (admin initial `mboa_admin`, default `admin/admin123` supprimé du seed + neutralisé)
- [x] Commandes / messaging / notifications (compte `[TEST-P22]`, **purgé après test**)
- [x] Supplier / admin / rôles (guest→401)
- [x] Upload (lecture ; refus 400 testés Phase 20)
- [ ] HTTPS (non applicable en local)
- [x] Paiement sandbox/LIVE selon état → MANUAL vérifié, LIVE non activé

## ROLLBACK

- [x] ancienne version disponible (`releases/`, procédure §11 `deployment.md`)
- [x] backup disponible (pre-déploiement + testé Phase 21)
- [x] procédure validée (runbook)

## DONNÉES

Base prod laissée propre : seed catégories (402) + 1 admin ; 0 commande/produit/
fournisseur/conversation de test (purgés, vérifié en DB).
