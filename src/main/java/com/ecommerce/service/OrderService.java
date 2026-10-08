package com.ecommerce.service;

import com.ecommerce.dao.DeliveryZoneDAO;
import com.ecommerce.dao.MonetizationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.model.Product;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.sql.SQLException;
import java.util.List;

public class OrderService {
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final DeliveryZoneDAO deliveryDAO = new DeliveryZoneDAO();

    /**
     * Crée une commande. Le serveur est la SEULE source de vérité :
     * prix rechargés depuis la base (jamais ceux du navigateur),
     * produits inactifs/invisibles refusés, stock vérifié.
     */
    public int createOrder(Order order) throws AppException, SQLException {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Panier vide");
        }
        // Méthode de paiement proposée uniquement (Phase 11) ; WHATSAPP = historique.
        String pm = order.getPaymentMethod();
        if (pm == null || pm.trim().isEmpty()) {
            order.setPaymentMethod("PAIEMENT_LIVRAISON");
        } else if (!PaymentProviders.offeredMethods().contains(pm) && !"WHATSAPP".equals(pm)) {
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 400, "Moyen de paiement indisponible");
        }
        int itemsTotal = 0;
        // Frais de livraison saisis par le fournisseur (max des lignes > 0),
        // sinon tarif de la zone par ville. Commandes mono-fournisseur :
        // le max reflète le tarif du vendeur. Calcul 100 % serveur.
        int supplierDeliveryFee = 0;
        java.util.List<com.ecommerce.model.Offer> consumedOffers = new java.util.ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.getQty() <= 0 || item.getQty() > 99) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Quantité invalide");
            }
            Product p;
            try {
                p = productDAO.findById(item.getProductId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (p == null) {
                throw new AppException(ErrorCodes.RESOURCE_NOT_FOUND, 404, "Produit introuvable");
            }
            String status = p.getStatus();
            if (status != null && !"ACTIVE".equals(status) && !"APPROVED".equals(status)) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                    "« " + p.getName() + " » n'est plus disponible à la commande");
            }
            if (p.getStock() < item.getQty()) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                    "Stock insuffisant pour « " + p.getName() + " » (disponible : " + Math.max(0, p.getStock()) + ")");
            }
            // Intégrité multi-fournisseurs : une commande affectée à un vendeur
            // ne peut contenir que ses produits (sinon fuite + traitement impossible).
            if (order.getSupplierId() > 0 && p.getSupplierId() != order.getSupplierId()) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                    "« " + p.getName() + " » n'appartient pas à ce vendeur");
            }
            if (item.getOfferId() != null && item.getOfferId() > 0) {
                // Ligne au prix négocié : offre ACCEPTED du même téléphone, mêmes variantes,
                // et conversation du compte connecté (isolation entre comptes clients).
                com.ecommerce.model.Offer off;
                try {
                    off = new OfferService().consumeForOrder(item.getOfferId(), order.getPhone(),
                        item.getQty(), item.getSize(), item.getColor(),
                        CustomerIsolation.sessionOf(order.getCustomerId()));
                } catch (SQLException e) {
                    throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
                }
                if (off.getProductId() != item.getProductId()) {
                    throw new AppException(ErrorCodes.OFFER_INVALID, 400, "Offre d'un autre produit");
                }
                item.setProductName(p.getName());
                item.setUnitPrice(off.getUnitPrice());
                consumedOffers.add(off);
            } else {
                // Prix serveur imposé : toute valeur envoyée par le navigateur est ignorée.
                // Snapshot : le nom est figé dans la ligne (§8 Phase 7).
                item.setProductName(p.getName());
                item.setUnitPrice(p.getPrice());
            }
            itemsTotal += item.getUnitPrice() * item.getQty();
            if (p.getDeliveryPrice() > 0) {
                supplierDeliveryFee = Math.max(supplierDeliveryFee, p.getDeliveryPrice());
            }
        }
        int deliveryFee = 0;
        try {
            deliveryFee = deliveryDAO.getFee(order.getCity());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (supplierDeliveryFee > 0) deliveryFee = supplierDeliveryFee;
        order.setTotal(itemsTotal + deliveryFee);
        // Email optionnel mais validé s'il est fourni.
        if (order.getEmail() != null && !order.getEmail().trim().isEmpty()
                && !com.ecommerce.service.EmailService.isValidEmail(order.getEmail().trim())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Email invalide");
        }
        final int createdId;
        try {
            createdId = orderDAO.create(order);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("STOCK_INSUFFISANT:")) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                    e.getMessage().substring("STOCK_INSUFFISANT:".length()).trim());
            }
            throw e;
        }
        // Email de confirmation (best-effort : jamais bloquant).
        if (order.getEmail() != null && !order.getEmail().trim().isEmpty()) {
            try {
                EmailService emails = new EmailService();
                emails.queueAndSend(order.getEmail().trim(),
                    EmailService.orderCreatedSubject(order.getOrderNumber()),
                    EmailService.orderCreatedBody(order.getFullname(),
                        order.getOrderNumber(), order.getTotal()),
                    "ORDER_CREATED", createdId);
            } catch (Exception ignored) {}
        }
        return createdId;
    }

    public List<Order> getAllOrders() throws SQLException {
        return orderDAO.findAll();
    }

    public Order getOrderById(int id) throws SQLException {
        return orderDAO.findById(id);
    }

    public void updateStatus(int id, String status) throws AppException, SQLException {
        updateStatus(id, status, false);
    }

    /**
     * Workflow v2 : transitions internes du flux de livraison
     * (start-delivery → EN_LIVRAISON, validate-delivery → LIVREE).
     * Les gardes métier (paiement, transition, compta) restent actives ;
     * seules les gardes d'orchestration v2 (départeffectif/réception
     * effective) sont levées car le flux les a déjà vérifiées juste avant.
     */
    public void transitionForFlow(int id, String status) throws AppException, SQLException {
        if (!"EN_LIVRAISON".equals(status) && !"LIVREE".equals(status)) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Transition interne invalide");
        }
        updateStatus(id, status, true);
    }

    private void updateStatus(int id, String status, boolean flowInternal)
            throws AppException, SQLException {
        if (!com.ecommerce.util.OrderStatus.isKnown(status)) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400, "Statut inconnu");
        }
        com.ecommerce.model.Order o = orderDAO.findById(id);
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        if (!com.ecommerce.util.OrderStatus.canTransition(o.getStatus(), status)) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400,
                "Transition refusée : " + o.getStatus() + " → " + status);
        }
        if ("CONFIRMEE".equals(status)) {
            // Phase 11 : pas de confirmation sans paiement vérifié,
            // sauf paiement à la livraison (soldé à la livraison).
            // Commandes historiques sans ligne (méthode WHATSAPP) : tolérées.
            com.ecommerce.model.Payment pay;
            try {
                pay = new com.ecommerce.dao.PaymentDAO().findByOrderId(id);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            boolean cod = "PAIEMENT_LIVRAISON".equals(o.getPaymentMethod());
            boolean legacy = pay == null && "WHATSAPP".equals(o.getPaymentMethod());
            // PawaPay : COMPLETED (confirmé par callback/polling serveur) vaut paiement vérifié.
            boolean paid = pay != null
                && ("REUSSIE".equals(pay.getStatus()) || "COMPLETED".equals(pay.getStatus()));
            if (!cod && !paid && !legacy) {
                throw new AppException(ErrorCodes.PAYMENT_REQUIRED, 400,
                    "Paiement vérifié requis avant confirmation");
            }
        }
        if ("ANNULEE".equals(status)) {
            // Workflow v2 : après confirmation de réception par le client,
            // plus d'annulation normale (passer par litige/remboursement).
            try {
                com.ecommerce.model.Shipment sh =
                    new com.ecommerce.dao.ShipmentDAO().findByOrder(id);
                if (sh != null && sh.isReceiptConfirmed()) {
                    throw new AppException(ErrorCodes.CONFLICT, 409,
                        "Réception déjà confirmée : utilisez le litige/remboursement");
                }
            } catch (AppException ae) {
                throw ae;
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        if ("LIVREE".equals(status)) {
            // Workflow v2 : aucune commande ne passe LIVRÉE sans confirmation
            // de réception client (ni admin, ni fournisseur, ni expédition).
            // Seul validateDelivery réunit réception + validation + payout.
            try {
                com.ecommerce.model.Shipment sh =
                    new com.ecommerce.dao.ShipmentDAO().findByOrder(id);
                if (sh == null || !sh.isReceiptConfirmed()) {
                    throw new AppException(ErrorCodes.CONFLICT, 409,
                        "Réception client requise avant de solder la commande");
                }
            } catch (AppException ae) {
                throw ae;
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        orderDAO.updateStatus(id, status);        // Notifications fournisseur déduites du serveur (destinataire = propriétaire).
        try {
            NotificationService notifs = new NotificationService();
            com.ecommerce.model.Order updated = orderDAO.findById(id);
            int sup = updated != null ? updated.getSupplierId() : o.getSupplierId();
            String num = updated != null && updated.getOrderNumber() != null
                ? updated.getOrderNumber() : ("#" + id);
            if (sup > 0 && ("CONFIRMEE".equals(status) || "ANNULEE".equals(status))) {
                notifs.notifySupplier(sup,
                    "CONFIRMEE".equals(status) ? "ORDER_CONFIRMED" : "ORDER_CANCELLED",
                    "CONFIRMEE".equals(status) ? "Commande confirmée" : "Commande annulée",
                    "Commande " + num + ("CONFIRMEE".equals(status)
                        ? " confirmée (paiement vérifié)." : " annulée."),
                    "ORDER", id, null,
                    "ORDER:" + id + ":" + status);
            }
        } catch (Exception ignored) {}
        if ("LIVREE".equals(status)) {
            // Comptabilisation idempotente (retry sûr en cas d'échec).
            new MonetizationService().settleDeliveredOrder(id);
            // COD : la livraison vaut preuve de paiement.
            try {
                new PaymentService().confirmOnDelivery(id);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        if ("ANNULEE".equals(status)) {
            // Contre-passation idempotente si la commande était comptabilisée.
            new MonetizationService().reverseOrder(id, "ADMIN_CANCEL");
            // Expédition non terminale annulée aussi (Phase 12).
            try {
                new ShipmentService().cancelForOrder(id);
            } catch (Exception ignored) {}
        }
    }

    public void deleteOrder(int id) throws AppException, SQLException {
        com.ecommerce.model.Order o = orderDAO.findById(id);
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        // Une commande comptabilisée ne se supprime pas (CASCADE effacerait
        // le ledger) : passer par l'annulation, qui contre-passe proprement.
        if (new MonetizationDAO().hasEntries(id, "SALE")) {
            throw new AppException(ErrorCodes.CONFLICT, 409,
                "Commande comptabilisée : annulez-la au lieu de la supprimer");
        }
        orderDAO.delete(id);
    }

    public java.util.List<com.ecommerce.model.Order> getOrdersByPhone(String phone) throws AppException, SQLException {
        if (phone == null || phone.replaceAll("\\D", "").length() < 9) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de téléphone invalide");
        }
        return orderDAO.findByPhone(phone.trim());
    }

    /** Commandes d'un client connecté (session Bearer, sans numéro à saisir). */
    public java.util.List<com.ecommerce.model.Order> getOrdersByCustomer(int customerId) throws AppException, SQLException {
        if (customerId <= 0) {
            throw new AppException(ErrorCodes.AUTH_REQUIRED, 401, "Authentification requise");
        }
        return orderDAO.findByCustomer(customerId);
    }

    public com.ecommerce.model.Order trackOrder(String number, String phone) throws AppException, SQLException {
        return trackOrder(number, phone, null);
    }

    /**
     * Suivi avec isolation de compte : si une session client est présente,
     * la commande doit appartenir au compte (ou être une commande invitée
     * historique au même téléphone, déjà prouvé par le finder).
     */
    public com.ecommerce.model.Order trackOrder(String number, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        if (number == null || number.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de commande et téléphone requis");
        }
        com.ecommerce.model.Order o = orderDAO.findByNumberAndPhone(number.trim(), phone.trim());
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        CustomerIsolation.requireOrderVisible(o, sessionCustomerId);
        return o;
    }

    /**
     * Vue complète d'une commande pour le suivi client (1 seul appel) :
     * commande + paiement + livraison (+événements) + litige + remboursement
     * + écritures financières. Propriété vérifiée comme trackOrder.
     * Aucune logique métier modifiée : simple agrégation en lecture.
     */
    public java.util.Map<String, Object> getOrderFullView(String number, String phone)
            throws AppException, SQLException {
        return getOrderFullView(number, phone, null);
    }

    public java.util.Map<String, Object> getOrderFullView(String number, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        com.ecommerce.model.Order o = trackOrder(number, phone, sessionCustomerId);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("order", o);
        try {
            // Boutique (facture client) : champs publics uniquement, jamais le hash.
            com.ecommerce.model.Supplier shop = null;
            try {
                if (o.getSupplierId() > 0) {
                    shop = new com.ecommerce.dao.SupplierDAO().findById(o.getSupplierId());
                }
            } catch (Exception ignored) {}
            if (shop != null) {
                java.util.Map<String, Object> sm = new java.util.LinkedHashMap<>();
                sm.put("id", shop.getId());
                sm.put("name", shop.getName());
                sm.put("phone", shop.getPhone());
                sm.put("city", shop.getCity());
                sm.put("address", shop.getAddress());
                sm.put("email", shop.getEmail());
                out.put("supplier", sm);
            } else {
                out.put("supplier", null);
            }
        } catch (Exception ignored) {
            out.put("supplier", null);
        }
        try {
            out.put("payment", new com.ecommerce.dao.PaymentDAO().findByOrderId(o.getId()));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        try {
            com.ecommerce.model.Shipment s =
                new com.ecommerce.dao.ShipmentDAO().findByOrder(o.getId());
            out.put("shipment", s);
            out.put("shipmentEvents", s == null ? new java.util.ArrayList<>()
                : new com.ecommerce.dao.ShipmentDAO().events(s.getId(), 200));
            // Workflow v2 : état du flux (mêmes flags que l'espace fournisseur).
            java.util.Map<String, Object> flow = new java.util.LinkedHashMap<>();
            flow.put("deliveryStarted", s != null && s.isDeliveryStarted());
            flow.put("deliveryStartedAt", s == null ? null : s.getDeliveryStartedAt());
            flow.put("receiptConfirmed", s != null && s.isReceiptConfirmed());
            flow.put("receiptConfirmedAt", s == null ? null : s.getReceiptConfirmedAt());
            flow.put("deliveryValidated", s != null && s.isDeliveryValidated());
            flow.put("deliveryValidatedAt", s == null ? null : s.getDeliveryValidatedAt());
            out.put("flow", flow);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        try {
            out.put("dispute", new com.ecommerce.dao.DisputeDAO().findByOrderId(o.getId()));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        try {
            out.put("refund", new com.ecommerce.dao.RefundDAO().findLatestByOrderId(o.getId()));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        try {
            java.util.Map<String, Object> journal =
                new com.ecommerce.service.FinanceJournalService().forOrder(o.getId());
            out.put("journal", journal == null ? null : journal.get("entries"));
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return out;
    }

    public void cancelGuestOrder(String number, String phone) throws AppException, SQLException {
        cancelGuestOrder(number, phone, null);
    }

    public void cancelGuestOrder(String number, String phone, Integer sessionCustomerId)
            throws AppException, SQLException {
        com.ecommerce.model.Order o = trackOrder(number, phone, sessionCustomerId);
        boolean ok;
        try {
            ok = orderDAO.cancelGuestOrder(o.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (!ok) {
            throw new AppException(ErrorCodes.INVALID_ORDER_STATUS, 400,
                "Seule une commande en attente peut être annulée");
        }
    }

    public List<Order> searchOrders(String query) throws SQLException {
        if (query == null || query.isEmpty()) return orderDAO.findAll();
        return orderDAO.search(query);
    }

    public List<Order> getOrdersByStatus(String status) throws SQLException {
        if (status == null || status.isEmpty()) return orderDAO.findAll();
        return orderDAO.findByStatus(status);
    }

    public List<Order> getOrdersBySupplier(int supplierId) throws SQLException {
        return orderDAO.findBySupplier(supplierId);
    }
}
