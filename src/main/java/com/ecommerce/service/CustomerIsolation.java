package com.ecommerce.service;

import com.ecommerce.model.Conversation;
import com.ecommerce.model.Order;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

/**
 * Isolation des comptes clients (mission individualisation).
 *
 * Règle centrale : quand une session client valide est présente, toute
 * donnée privée n'est visible que si elle appartient au compte connecté.
 * Le catalogue (produits, catégories, tarifs) reste public et commun.
 *
 * Modèle d'identité :
 * - sessionCustomerId != null : compte authentifié (Bearer -> customer_id,
 *   résolu CÔTÉ SERVEUR, jamais depuis le navigateur).
 * - sessionCustomerId == null : invité (aucune session). La preuve reste
 *   le téléphone / phone+cid, vérifiée par les finders existants.
 *
 * Cas des commandes invitées historiques (customer_id 0/NULL) : un client
 * connecté dont le téléphone correspond peut toujours les voir — même
 * preuve que le parcours invité, sans élargir l'accès aux commandes
 * d'un AUTRE compte.
 */
public final class CustomerIsolation {

    private CustomerIsolation() {}

    /** Normalise un customerId de session (0 = pas de session = invité). */
    public static Integer sessionOf(int customerId) {
        return customerId > 0 ? customerId : null;
    }

    /**
     * Une commande est visible pour le demandeur si : pas de session
     * (preuve téléphone déjà faite par le finder), ou commande du compte,
     * ou commande invitée historique (customer_id 0, téléphone prouvé).
     */
    public static void requireOrderVisible(Order o, Integer sessionCustomerId) throws AppException {
        if (o == null || sessionCustomerId == null) return;
        if (o.getCustomerId() > 0 && o.getCustomerId() != sessionCustomerId.intValue()) {
            throw new AppException(ErrorCodes.ORDER_ACCESS_DENIED, 403, "Commande inaccessible");
        }
    }

    /**
     * Une conversation est visible pour le demandeur si : pas de session
     * (preuve phone+cid déjà faite), ou conversation du compte,
     * ou conversation invitée non liée (rattachement possible).
     */
    public static void requireConversationVisible(Conversation c, Integer sessionCustomerId)
            throws AppException {
        if (c == null || sessionCustomerId == null) return;
        if (c.getCustomerId() > 0 && c.getCustomerId() != sessionCustomerId.intValue()) {
            throw new AppException(ErrorCodes.CONVERSATION_ACCESS_DENIED, 403,
                "Conversation inaccessible");
        }
    }
}
