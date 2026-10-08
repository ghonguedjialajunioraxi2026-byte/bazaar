package com.ecommerce.controller;

import com.ecommerce.dao.ConversationDAO;
import com.ecommerce.dao.OfferDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.ShipmentDAO;
import com.ecommerce.model.Conversation;
import com.ecommerce.model.Offer;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Shipment;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Timeline invité (Phase 13).
 *
 * Les clients sont des INVITÉS (pas de comptes) : aucune notification
 * persistée ne leur est adressée (un broadcast CLIENT fuirait entre invités).
 * Cette timeline est DÉRIVÉE EN DIRECT de données réelles et vérifiées :
 * commandes (téléphone), paiements, expéditions, messages non lus, offres.
 * Identité = téléphone (+ clé messagerie), comme le suivi et la messagerie.
 */
@WebServlet("/api/guest-notifications")
public class GuestNotificationServlet extends HttpServlet {

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ShipmentDAO shipmentDAO = new ShipmentDAO();
    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final OfferDAO offerDAO = new OfferDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            // Isolation de compte : un client connecté ne voit QUE sa timeline
            // (commandes de son customer_id + conversations de son compte).
            // Sans session : parcours invité au téléphone (+ clé messagerie).
            Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                com.ecommerce.util.CustomerAuth.validateCustomer(req));
            String phone = req.getParameter("phone");
            if (session == null && (phone == null || phone.replaceAll("\\D", "").length() < 9)) {
                ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "Numéro de téléphone invalide");
                return;
            }
            phone = phone == null ? "" : phone.trim();
            String cid = req.getParameter("cid");

            List<Map<String, Object>> timeline = new ArrayList<>();
            int unread = 0;

            // 1) Commandes + paiement + livraison (données réelles).
            List<Order> orders;
            try {
                orders = session != null
                    ? orderDAO.findByCustomer(session)
                    : orderDAO.findByPhone(phone);
            } catch (Exception e) {
                orders = new ArrayList<>();
            }
            for (Order o : orders) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("kind", "order");
                item.put("orderNumber", o.getOrderNumber());
                item.put("status", o.getStatus());
                item.put("total", o.getTotal());
                item.put("createdAt", o.getCreatedAt());
                try {
                    Payment p = paymentDAO.findByOrderId(o.getId());
                    if (p != null) {
                        item.put("paymentStatus", p.getStatus());
                        timeline.add(event("PAYMENT_" + p.getStatus(),
                            paymentTitle(p.getStatus()), "Commande " + o.getOrderNumber()
                                + " : " + paymentDetail(p.getStatus()),
                            o.getCreatedAt(), o.getOrderNumber()));
                    }
                } catch (Exception ignored) {}
                try {
                    Shipment s = shipmentDAO.findByOrder(o.getId());
                    if (s != null) {
                        item.put("shipmentStatus", s.getStatus());
                        item.put("trackingNumber", s.getTrackingNumber());
                        item.put("deliveryStarted", s.isDeliveryStarted());
                        item.put("receiptConfirmed", s.isReceiptConfirmed());
                        item.put("deliveryValidated", s.isDeliveryValidated());
                        // Workflow v2 : notification client dérivée (données réelles,
                        // jamais de broadcast entre invités).
                        if (s.isDeliveryStarted()) {
                            unread++;
                            timeline.add(event("DELIVERY_STARTED",
                                "Votre commande est en cours de livraison",
                                "Votre commande " + o.getOrderNumber()
                                    + " est en cours de livraison. Votre fournisseur est"
                                    + " en route. Lorsque vous avez physiquement reçu votre"
                                    + " colis, confirmez sa réception depuis « Mes commandes ».",
                                s.getDeliveryStartedAt(), o.getOrderNumber()));
                        }
                        if (s.isDeliveryValidated()) {
                            timeline.add(event("DELIVERY_VALIDATED",
                                "Livraison validée",
                                "Commande " + o.getOrderNumber()
                                    + " : livraison validée, règlement fournisseur en cours.",
                                s.getDeliveryValidatedAt(), o.getOrderNumber()));
                        }
                        timeline.add(event("DELIVERY_" + s.getStatus(),
                            "Livraison : " + s.getStatus(),
                            "Commande " + o.getOrderNumber() + " : suivi "
                                + s.getTrackingNumber() + ".",
                            s.getCreatedAt(), o.getOrderNumber()));
                    }
                } catch (Exception ignored) {}
                timeline.add(event("ORDER_" + o.getStatus(),
                    "Commande " + o.getOrderNumber(),
                    "Statut : " + o.getStatus() + " (" + o.getTotal() + " FCFA).",
                    o.getCreatedAt(), o.getOrderNumber()));
            }

            // 2) Messagerie : non lus du compte connecté, sinon invité (téléphone + clé).
            if (session != null) {
                try {
                    int pourCompte = conversationDAO.unreadCountForCustomer(session);
                    unread += pourCompte;
                    if (pourCompte > 0) {
                        timeline.add(event("MESSAGE_RECEIVED", "Nouveaux messages",
                            pourCompte + " message(s) non lu(s) de vos vendeurs.",
                            null, null));
                    }
                    // 3) Offres PENDING des conversations du compte.
                    List<Conversation> convsCompte = conversationDAO.findByCustomer(session);
                    for (Conversation c : convsCompte) {
                        List<Offer> offers;
                        try {
                            offers = offerDAO.findByConversation(c.getId());
                        } catch (Exception e) {
                            continue;
                        }
                        for (Offer off : offers) {
                            if ("PENDING".equals(off.getStatus()) && "SUPPLIER".equals(off.getCreatedBy())) {
                                unread++;
                                timeline.add(event("OFFER_RECEIVED", "Nouvelle contre-offre",
                                    "Contre-offre de " + off.getUnitPrice()
                                        + " FCFA / unité (conversation #" + c.getId() + ").",
                                    off.getCreatedAt(), null));
                            } else if ("ACCEPTED".equals(off.getStatus())) {
                                timeline.add(event("OFFER_ACCEPTED", "Offre acceptée",
                                    "Offre de " + off.getUnitPrice() + " FCFA / unité acceptée.",
                                    off.getCreatedAt(), null));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else if (cid != null && cid.trim().length() >= 8) {
                try {
                    int verspre = conversationDAO.unreadCountForGuest(phone, cid.trim());
                    unread += verspre;
                    if (verspre > 0) {
                        timeline.add(event("MESSAGE_RECEIVED", "Nouveaux messages",
                            verspre + " message(s) non lu(s) de vos vendeurs.",
                            null, null));
                    }
                    // 3) Offres PENDING adressées à l'invité (ses conversations).
                    List<Conversation> convs = conversationDAO.findByGuest(phone, cid.trim());
                    for (Conversation c : convs) {
                        List<Offer> offers;
                        try {
                            offers = offerDAO.findByConversation(c.getId());
                        } catch (Exception e) {
                            continue;
                        }
                        for (Offer off : offers) {
                            if ("PENDING".equals(off.getStatus()) && "SUPPLIER".equals(off.getCreatedBy())) {
                                unread++;
                                timeline.add(event("OFFER_RECEIVED", "Nouvelle contre-offre",
                                    "Contre-offre de " + off.getUnitPrice()
                                        + " FCFA / unité (conversation #" + c.getId() + ").",
                                    off.getCreatedAt(), null));
                            } else if ("ACCEPTED".equals(off.getStatus())) {
                                timeline.add(event("OFFER_ACCEPTED", "Offre acceptée",
                                    "Offre de " + off.getUnitPrice() + " FCFA / unité acceptée.",
                                    off.getCreatedAt(), null));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("unread", unread);
            data.put("timeline", timeline.size() > 50 ? timeline.subList(0, 50) : timeline);
            ApiResponse.ok(resp, data);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    private Map<String, Object> event(String code, String title, String message,
                                      String at, String orderNumber) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventCode", code);
        m.put("title", title);
        m.put("message", message);
        m.put("createdAt", at);
        if (orderNumber != null) m.put("orderNumber", orderNumber);
        return m;
    }

    private String paymentTitle(String status) {
        if ("REUSSIE".equals(status) || "COMPLETED".equals(status)) return "Paiement réussi";
        if ("ECHOUEE".equals(status) || "FAILED".equals(status)) return "Paiement échoué";
        if ("ANNULEE".equals(status)) return "Paiement remboursé";
        if ("PROCESSING".equals(status)) return "Paiement en cours";
        return "Paiement en attente";
    }

    private String paymentDetail(String status) {
        if ("REUSSIE".equals(status) || "COMPLETED".equals(status)) return "paiement confirmé";
        if ("ECHOUEE".equals(status) || "FAILED".equals(status)) return "paiement échoué";
        if ("ANNULEE".equals(status)) return "paiement remboursé";
        if ("PROCESSING".equals(status)) return "validez sur votre téléphone";
        return "paiement en attente de vérification";
    }
}
