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
        List<Conversation> mine = listForGuest(phone.trim(), cid.trim());
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
        c.setClientIdentifier(cid.trim());
        c.setClientName(clientName != null && !clientName.trim().isEmpty() ? clientName.trim().substring(0, Math.min(120, clientName.trim().length())) : null);
        c.setClientPhone(phone.trim());
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

    public SupplierMessage sendClientMessage(int convId, String phone, String cid, String body) throws AppException, SQLException {
        Conversation c = getForGuest(convId, phone, cid);
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

    public int unreadCount(String phone, String cid) throws AppException, SQLException {
        checkPhone(phone);
        checkClientKey(cid);
        return conversationDAO.unreadCountForGuest(phone.trim(), cid.trim());
    }
}
