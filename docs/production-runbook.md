# MBOA STYLE — Runbook production (Phase 22)

## Site inaccessible

1. `curl -s -o /dev/null -w "%{http_code}" https://<domaine>/api/health`
   - `200 {"status":"UP"}` → panne réseau/proxy : vérifier Nginx (`error.log`),
     DNS, firewall, certificat (`echo | openssl s_client -connect <domaine>:443`).
   - `503` → MySQL : § ci-dessous.
   - timeout/connexion refusée → Tomcat : `systemctl status tomcat`,
     `catalina.out` (dernières `SEVERE`), mémoire (`free -h`), redémarrer.

## MySQL tombe

1. `systemctl status mysql` + log d'erreurs MySQL.
2. Espace disque (`df -h`) : si plein → purger les vieux backups/binaires
   (jamais sans backup récent vérifié).
3. Redémarrer, puis health check. Si corruption : restaurer le dernier backup
   (`scripts/restore-db-test.sh`, garde `CONFIRM_PROD_RESTORE`) puis rejouer
   les migrations manquantes dans l'ordre (`docs/database-production.md`).

## Tomcat tombe

1. `catalina.out` : `OutOfMemory` → augmenter `-Xmx` (`setenv.sh`) selon RAM
   réelle ; port déjà pris → une seule instance (vérifier `ss -tlnp`).
2. Redémarrer, health check. Incident connu : arrêt ordonné inattendu
   (Phase 22 locale) → `Restart=always` en systemd.

## Disque plein

Seuils : alerte 80 %, critique 90 %. Postes : backups (rétention 30 j),
logs Tomcat (rotation), uploads (quota + nettoyage fichiers temporaires).
Ne jamais supprimer le seul backup récent.

## Paiements échouent

Mode actuel : MANUEL (vérification admin). Vérifier : commande `CONFIRMEE`
uniquement si paiement `REUSSIE` (sauf COD), idempotence (`PAY-<id>`),
`payments` en DB, `activity_logs`. Aucun PSP externe : tout échec « prestataire »
= configuration, pas de transaction réelle perdue.

## Google OAuth échoue

État : NON ACTIVÉ par défaut. Si activé : vérifier origines/redirect URIs
HTTPS dans Google Console, `GOOGLE_CLIENT_ID` serveur, `aud` du token,
horloge serveur (exp).

## Uploads échouent

Vérifier `MBOA_UPLOADS_DIR` (existe, inscriptible), espace disque, taille
(> 10 Mo → 400), format (SVG → 400), logs `IOException`.

## Nouvelle version casse l'application / rollback

1. Conserver les logs. 2. Repointer `current.war` vers la release précédente,
   restart, health. 3. DB : restaurer UNIQUEMENT si la cause est une migration
   (sauvegarde préalable obligatoire). 4. Noter version/commit fautif.
