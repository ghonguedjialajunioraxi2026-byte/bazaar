package com.ecommerce.service;

import com.ecommerce.dao.ConversationDAO;
import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.dao.OfferDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.model.Conversation;
import com.ecommerce.model.Offer;
import com.ecommerce.model.Product;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.List;

/**
 * Négociation (Phase 8). Prix négocié isolé du catalogue ; historique
 * jamais écrasé ; transitions atomiques (WHERE status='PENDING').
 */
public class OfferService {

    private final OfferDAO offerDAO = new OfferDAO();
    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final ConversationService conversationService = new ConversationService();

    public static final int MAX_OFFERS_PER_DAY = 10;

    public List<Offer> listForConversation(int convId) throws SQLException {
        return offerDAO.findByConversation(convId);
    }

    /**
     * Crée une offre (ou contre-offre : parent_id + ancienne marquée COUNTERED).
     * @param by "CLIENT" ou "SUPPLIER" (jamais depuis le navigateur : résolu serveur).
     */
    public Offer createOffer(int convId, int productId, int qty, String size, String color,
                             int unitPrice, String by, String phone, String cid, int supplierId)
            throws AppException, SQLException {
        Conversation c = resolveConversation(convId, by, phone, cid, supplierId);
        if (!"OPEN".equals(c.getStatus())) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Conversation fermée");
        }
        Product p = productDAO.findById(productId);
        if (p == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Produit introuvable");
        }
        if (c.getProductId() != null && c.getProductId() != productId) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Produit différent de la conversation");
        }
        if (p.getSupplierId() != c.getSupplierId()) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Produit d'un autre vendeur");
        }
        if (qty <= 0 || qty > 99) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Quantité invalide");
        }
        if (p.getStock() < qty) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400,
                "Stock insuffisant (disponible : " + Math.max(0, p.getStock()) + ")");
        }
        if (unitPrice <= 0) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Prix proposé invalide");
        }
        if (offerDAO.countToday(convId) >= MAX_OFFERS_PER_DAY) {
            throw new AppException(ErrorCodes.RATE_LIMITED, 429, "Trop d'offres aujourd'hui");
        }
        String other = "CLIENT".equals(by) ? "SUPPLIER" : "CLIENT";
        Offer parent = offerDAO.findPendingFromOther(convId, by);
        Offer o = new Offer();
        o.setConversationId(convId);
        o.setProductId(productId);
        o.setQty(qty);
        o.setSize(size != null && size.length() > 20 ? size.substring(0, 20) : size);
        o.setColor(color != null && color.length() > 40 ? color.substring(0, 40) : color);
        o.setCatalogPrice(p.getPrice());
        o.setUnitPrice(unitPrice);
        o.setCreatedBy(by);
        o.setStatus("PENDING");
        o.setParentOfferId(parent != null ? parent.getId() : null);
        int id = offerDAO.create(o);
        if (id <= 0) throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.");
        if (parent != null) {
            offerDAO.transition(parent.getId(), "PENDING", "COUNTERED");
        }
        conversationDAO.addMessage(convId, by,
            "Proposition : " + unitPrice + " FCFA / unité (x" + qty + ")");
        conversationDAO.updateActivity(convId);
        o.setId(id);
        o.setProductName(p.getName());
        // Notifie l'autre partie côté fournisseur (les invités voient via polling).
        try {
            if ("CLIENT".equals(by)) {
                notificationDAO.createForSupplier(c.getSupplierId(), "Nouvelle offre",
                    "Offre de " + unitPrice + " FCFA sur « " + p.getName() + " »", "ORDER");
            }
        } catch (Exception ignored) {}
        return o;
    }

    /** Accepte ou refuse : seul l'AUTRE auteur, offre encore PENDING (atomique). */
    public Offer decide(int offerId, boolean accept, String by, String phone, String cid, int supplierId)
            throws AppException, SQLException {
        Offer o = offerDAO.findById(offerId);
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Offre introuvable");
        }
        resolveConversation(o.getConversationId(), by, phone, cid, supplierId);
        if (by.equals(o.getCreatedBy())) {
            throw new AppException(ErrorCodes.OFFER_ACCESS_DENIED, 403, "Vous ne pouvez pas répondre à votre propre offre");
        }
        boolean ok = offerDAO.transition(offerId, "PENDING", accept ? "ACCEPTED" : "REJECTED");
        if (!ok) {
            throw new AppException(ErrorCodes.OFFER_NOT_AVAILABLE, 409, "Offre plus disponible");
        }
        conversationDAO.addMessage(o.getConversationId(), by,
            accept ? "Offre acceptée : " + o.getUnitPrice() + " FCFA / unité."
                   : "Offre refusée : " + o.getUnitPrice() + " FCFA / unité.");
        conversationDAO.updateActivity(o.getConversationId());
        o.setStatus(accept ? "ACCEPTED" : "REJECTED");
        return o;
    }

    public void cancel(int offerId, String by, String phone, String cid, int supplierId)
            throws AppException, SQLException {
        Offer o = offerDAO.findById(offerId);
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Offre introuvable");
        }
        resolveConversation(o.getConversationId(), by, phone, cid, supplierId);
        if (!by.equals(o.getCreatedBy())) {
            throw new AppException(ErrorCodes.OFFER_ACCESS_DENIED, 403, "Seul l'auteur peut annuler");
        }
        boolean ok = offerDAO.transition(offerId, "PENDING", "CANCELLED");
        if (!ok) {
            throw new AppException(ErrorCodes.OFFER_NOT_AVAILABLE, 409, "Offre plus annulable");
        }
    }

    /**
     * Consomme une offre ACCEPTED pour une commande (compare-and-set atomique).
     * Vérifie produit/qty/variantes/phone. Le prix négocié est alors figé.
     */
    public Offer consumeForOrder(int offerId, String phone, int qty, String size, String color)
            throws AppException, SQLException {
        Offer o = offerDAO.findById(offerId);
        if (o == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Offre introuvable");
        }
        String ownerPhone = conversationDAO.findClientPhone(o.getConversationId());
        if (ownerPhone == null || phone == null || !ownerPhone.trim().equals(phone.trim())) {
            throw new AppException(ErrorCodes.OFFER_ACCESS_DENIED, 403, "Offre inaccessible");
        }
        if (qty != o.getQty() || !eq(size, o.getSize()) || !eq(color, o.getColor())) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Ligne différente de l'offre acceptée");
        }
        Product p = productDAO.findById(o.getProductId());
        if (p == null || p.getStock() < qty) {
            throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Stock insuffisant pour cette offre");
        }
        boolean ok = offerDAO.transition(offerId, "ACCEPTED", "CONSUMED");
        if (!ok) {
            throw new AppException(ErrorCodes.OFFER_NOT_AVAILABLE, 409, "Offre déjà utilisée");
        }
        o.setStatus("CONSUMED");
        return o;
    }

    private boolean eq(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.trim().equals(b.trim());
    }

    private Conversation resolveConversation(int convId, String by, String phone, String cid, int supplierId)
            throws AppException, SQLException {
        if ("SUPPLIER".equals(by)) {
            Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
            if (c == null) {
                throw new AppException(ErrorCodes.CONVERSATION_ACCESS_DENIED, 403, "Conversation inaccessible");
            }
            return c;
        }
        return conversationService.getForGuest(convId, phone, cid);
    }
}
