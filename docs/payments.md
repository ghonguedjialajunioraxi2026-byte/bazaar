# MBOA STYLE — Paiements (Phase 11, sans PSP externe)

## Principe honnête
Aucune intégration PayPal / carte / API MTN / API Orange :
**pas de credentials, pas de sandbox → non intégrés** (documenté,
jamais simulé). Deux flux réels :
- **COD** (`PAIEMENT_LIVRAISON`) : rien en ligne, confirmation
  automatique à la livraison (événement serveur).
- **MoMo manuel** (`MOBILE_MONEY_MTN/ORANGE`) : le client paie au
  numéro marchand, saisit la référence, un **admin vérifie**.
`CART_BANCAIRE` : non proposé (aucun PSP).

## Architecture
`PaymentProvider` (interface) + `CashOnDeliveryProvider` +
`ManualMobileMoneyProvider` + registre `PaymentProviders`
(méthodes proposées, rejet du reste). `PaymentService` :
init idempotente (`PAY-<orderId>` UNIQUE), statut, référence,
vérification/échec admin, confirm-on-delivery, remboursement comptable.
Montant/devise (FCFA) recalculés serveur ; `orderId`+téléphone vérifient
la propriété. Timeouts : connexions JDBC courtes, aucun appel HTTP
externe (rien à timeoutter).

## Données (`migrate_payments.sql`)
`payments` += `provider`, `idempotence_key` UNIQUE, `failure_reason`,
`verified_by/at`. Statuts : EN_ATTENTE → REUSSIE/ECHOUEE/ANNULEE.
Ligne créée atomiquement avec la commande. Tentative après échec :
réouverture explicite (seule la dernière référence est gardée).

## Règles métier
CONFIRMEE exige paiement REUSSIE sauf COD (ordres historiques WHATSAPP
tolérés). Double init → même ligne. Remboursement = REVERSAL ledger +
paiement ANNULEE (mouvement PSP manuel, documenté).

## Frontend
Checkout : choix COD/MTN/Orange + réf MoMo (transmise après création).
Suivi : bloc paiement (statut, montant, envoi référence). Admin : vérif/
rejet dans le détail commande. Fournisseur : badge statut dans le détail.

## Webhooks / sandbox / PSP
Sans objet : aucun PSP intégré, donc aucun webhook/secret. À l'arrivée
d'un PSP : implémenter `PaymentProvider`, vérifier signatures, garder
l'idempotence (clé + statuts conditionnels déjà prêts).

## Env / limites
Aucune variable PSP. Limites : pas de paiement partiel auto, pas
d'historique multi-tentatives, pas de page carte (volontaire).
