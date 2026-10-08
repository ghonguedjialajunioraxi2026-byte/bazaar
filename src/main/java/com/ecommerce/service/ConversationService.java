package com.ecommerce.service;

import com.ecommerce.dao.ConversationDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.dao.SupplierDAO;
import com.ecommerce.model.Conversation;
import com.ecommerce.model.Product;
import com.ecommerce.model.SupplierMessage;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Messagerie côté invité (Phase 8). Identité = téléphone + clé navigateur
 * (`client_identifier`, stockée en localStorage). Documenté comme facteur
 * faible mais suffisant : le serveur vérifie TOUJOURS les deux.
 */
public class ConversationService {

    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();

    public static final int MESSAGE_MAX = 2000;
    public static final int MESSAGE_PER_MINUTE = 10;

    public String newClientKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void checkPhone(String phone) throws AppException {
        if (phone == null || phone.replaceAll("\\D", "").length() < 9) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de téléphone invalide");
        }
    }

    public void checkClientKey(String cid) throws AppException {
        if (cid == null || cid.trim().length() < 8) {
            throw new AppException(ErrorCodes.AUTH_INVALID, 401, "Session de messagerie invalide");
        }
    }

    /**
     * Récupère la conversation ouverte (même fournisseur + produit + téléphone),
     * sinon la crée. Évite le spam de conversations (§10, §39).
     */
    public Conversation getOrCreate(int supplierId, Integer productId, String clientName,
                                    String phone, String cid) throws AppException, SQLException {
        return getOrCreate(supplierId, productId, null, clientName, phone, cid, 0, null, null);
    }

    /**
     * Création/réutilisation avec commande + compte : réutilise la conversation
     * OPEN du compte (ou invitée qu'elle lie), sinon la crée déjà liée.
     */
    public Conversation getOrCreate(int supplierId, Integer productId, Integer orderId,
                                    String clientName, String phone, String cid,
                                    int customerId, String customerName, String customerEmail)
            throws AppException, SQLException {
        checkPhone(phone);
        checkClientKey(cid);
        if (supplierDAO.findById(supplierId) == null) {
            throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Fournisseur introuvable");
        }
        Product product = null;
        if (productId != null && productId > 0) {
            product = productDAO.findById(productId);
            if (product == null) {
                throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Produit introuvable");
            }
            // Intégrité : le produit doit appartenir au fournisseur contacté.
            if (product.getSupplierId() != supplierId) {
                throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Produit d'un autre vendeur");
            }
        }
        List<Conversation> mine = customerId > 0
            ? listForCustomerOrGuest(customerId, phone.trim(), cid.trim())
            : listForGuest(phone.trim(), cid.trim());
        for (Conversation c : mine) {
            if (c.getSupplierId() == supplierId
                    && "OPEN".equals(c.getStatus())
                    && ((productId == null && c.getProductId() == null)
                        || (productId != null && productId.equals(c.getProductId())))) {
                return c;
            }
        }
        Conversation c = new Conversation();
        c.setSupplierId(supplierId);
        if (customerId > 0) c.setCustomerId(customerId);
        c.setClientIdentifier(cid.trim());
        String nm = clientName;
        if ((nm == null || nm.trim().isEmpty()) && customerName != null) nm = customerName;
        c.setClientName(nm != null && !nm.trim().isEmpty() ? nm.trim().substring(0, Math.min(120, nm.trim().length())) : null);
        c.setClientPhone(phone.trim());
        if (orderId != null && orderId > 0) c.setOrderId(orderId);
        c.setProductId(productId);
        c.setStatus("OPEN");
        c.setSubject(product != null ? product.getName() : "Discussion");
        int id = conversationDAO.create(c);
        if (id <= 0) throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.");
        c.setId(id);
        return c;
    }

    public List<Conversation> listForGuest(String phone, String cid) throws AppException, SQLException {
        checkPhone(phone);
        checkClientKey(cid);
        return conversationDAO.findByGuest(phone.trim(), cid.trim());
    }

    /**
     * Liste du compte authentifié : lie d'abord l'historique invité
     * (même téléphone) au compte — migration douce, une seule source.
     */
    public List<Conversation> listForCustomerOrGuest(int customerId, String phone, String cid)
            throws SQLException {
        if (phone != null && phone.replaceAll("\\D", "").length() >= 9) {
            try {
                String c = (cid == null) ? "" : cid.trim();
                for (Conversation g : conversationDAO.findByGuest(phone.trim(), c.isEmpty() ? "\0" : c)) {
                    if (g.getCustomerId() <= 0) conversationDAO.linkCustomer(g.getId(), customerId);
                }
            } catch (SQLException ignored) {}
        }
        return conversationDAO.findByCustomer(customerId);
    }

    /**
     * Détail : compte prioritaire, sinon invité avec liaison au compte
     * (si phone+cid prouvent la propriété).
     */
    public Conversation getForCustomerOrLink(int convId, int customerId, String phone, String cid)
            throws AppException, SQLException {
        Conversation c = conversationDAO.findByIdCustomer(convId, customerId);
        if (c != null) return c;
        if (phone != null && cid != null) {
            try {
                checkPhone(phone);
                checkClientKey(cid);
                Conversation g = conversationDAO.findByIdGuest(convId, phone.trim(), cid.trim());
                if (g != null) {
                    if (g.getCustomerId() <= 0) conversationDAO.linkCustomer(convId, customerId);
                    return conversationDAO.findByIdCustomer(convId, customerId);
                }
            } catch (AppException | SQLException ignored) {}
        }
        throw new AppException(ErrorCodes.CONVERSATION_ACCESS_DENIED, 403, "Conversation inaccessible");
    }

    /** Enrichit un détail avec produit + commande (contexte, lecture seule). */
    public java.util.Map<String, Object> detailContext(Conversation c) throws SQLException {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (c.getProductId() != null) {
            try {
                java.util.Map<String, Object> p = conversationDAO.productSnippet(c.getProductId());
                if (p != null) m.put("product", p);
            } catch (SQLException ignored) {}
        }
        if (c.getOrderId() != null) {
            try {
                String num = conversationDAO.findOrderNumber(c.getOrderId());
                if (num != null) m.put("orderNumber", num);
            } catch (SQLException ignored) {}
        }
        return m;
    }

    /** Liste du client authentifié (compte prioritaire). */
    public List<Conversation> listForCustomer(int customerId) throws SQLException {
        return conversationDAO.findByCustomer(customerId);
    }

    /** Détail du client authentifié (anti-IDOR par compte). */
    public Conversation getForCustomer(int convId, int customerId) throws AppException, SQLException {
        Conversation c = conversationDAO.findByIdCustomer(convId, customerId);
        if (c == null) {
            throw new AppException(ErrorCodes.CONVERSATION_ACCESS_DENIED, 403, "Conversation inaccessible");
        }
        return c;
    }

    public int unreadCountForCustomer(int customerId) throws SQLException {
        return conversationDAO.unreadCountForCustomer(customerId);
    }

    public Conversation getForGuest(int convId, String phone, String cid) throws AppException, SQLException {
        checkPhone(phone);
        checkClientKey(cid);
        Conversation c = conversationDAO.findByIdGuest(convId, phone.trim(), cid.trim());
        if (c == null) {
            throw new AppException(ErrorCodes.CONVERSATION_ACCESS_DENIED, 403, "Conversation inaccessible");
        }
        return c;
    }

    public List<SupplierMessage> getMessages(int convId, String phone, String cid) throws AppException, SQLException {
        getForGuest(convId, phone, cid);
        return conversationDAO.findMessages(convId);
    }

    /** Polling incrémental : seuls les messages après afterId (0 = historique borné). */
    public List<SupplierMessage> getMessagesAfter(int convId, String phone, String cid, int afterId) throws AppException, SQLException {
        getForGuest(convId, phone, cid);
        return conversationDAO.findMessagesAfter(convId, afterId);
    }

    /** Messages du compte authentifié (propriété déjà vérifiée). */
    public List<SupplierMessage> getMessagesForCustomer(int convId, int customerId)
            throws AppException, SQLException {
        getForCustomer(convId, customerId);
        return conversationDAO.findMessages(convId);
    }

    /** Polling incrémental du compte authentifié. */
    public List<SupplierMessage> getMessagesAfterForCustomer(int convId, int customerId, int afterId)
            throws AppException, SQLException {
        getForCustomer(convId, customerId);
        return conversationDAO.findMessagesAfter(convId, afterId);
    }

    public SupplierMessage sendClientMessage(int convId, String phone, String cid, String body) throws AppException, SQLException {
        return sendClientMessage(convId, phone, cid, body, 0);
    }

    /** Envoi client : compte authentifié prioritaire (lié si invité prouvé), sinon invité. */
    public SupplierMessage sendClientMessage(int convId, String phone, String cid, String body, int customerId)
            throws AppException, SQLException {
        Conversation c;
        if (customerId > 0) {
            c = getForCustomerOrLink(convId, customerId, phone, cid);
        } else {
            c = getForGuest(convId, phone, cid);
        }
        if (!"OPEN".equals(c.getStatus())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Conversation fermée");
        }
        String clean = body == null ? "" : body.trim();
        if (clean.isEmpty()) {
            throw new AppException(ErrorCodes.MESSAGE_EMPTY, 400, "Message vide");
        }
        if (clean.length() > MESSAGE_MAX) {
            throw new AppException(ErrorCodes.MESSAGE_TOO_LONG, 400, "Message trop long (max " + MESSAGE_MAX + ")");
        }
        if (conversationDAO.countRecentFrom(convId, "CLIENT", 1) >= MESSAGE_PER_MINUTE) {
            throw new AppException(ErrorCodes.RATE_LIMITED, 429, "Ralentissez : trop de messages");
        }
        int id = conversationDAO.addMessage(convId, "CLIENT", clean);
        conversationDAO.updateActivity(convId);
        try {
            new NotificationService().notifySupplier(c.getSupplierId(), "MESSAGE_RECEIVED",
                "Nouveau message",
                "Nouveau message de " + (c.getClientName() != null ? c.getClientName() : "un client"),
                "CONVERSATION", convId, null,
                "CONV:" + convId + ":MSG:" + id);
        } catch (Exception ignored) {}
        // Email fournisseur (best-effort) : remplace la notification WhatsApp.
        try {
            com.ecommerce.model.Supplier s = supplierDAO.findById(c.getSupplierId());
            if (s != null && EmailService.isValidEmail(s.getEmail())) {
                String excerpt = clean.length() > 160 ? clean.substring(0, 160) + "…" : clean;
                new EmailService().queueAndSend(s.getEmail(),
                    EmailService.messageReceivedSubject(s.getName() != null ? s.getName() : "BAZAAR"),
                    EmailService.messageReceivedBody(
                        c.getClientName() != null ? c.getClientName() : "Un client", excerpt),
                    "MESSAGE_RECEIVED", null);
            }
        } catch (Exception ignored) {}
        SupplierMessage m = new SupplierMessage();
        m.setId(id);
        m.setConversationId(convId);
        m.setSender("CLIENT");
        m.setBody(clean);
        return m;
    }

    public void markRead(int convId, String phone, String cid) throws AppException, SQLException {
        getForGuest(convId, phone, cid);
        conversationDAO.markGuestRead(convId);
    }

    /** Marquage lu du compte authentifié. */
    public void markReadForCustomer(int convId, int customerId) throws AppException, SQLException {
        getForCustomer(convId, customerId);
        conversationDAO.markGuestRead(convId);
    }

    public int unreadCount(String phone, String cid) throws AppException, SQLException {
        checkPhone(phone);
        checkClientKey(cid);
        return conversationDAO.unreadCountForGuest(phone.trim(), cid.trim());
    }
}
