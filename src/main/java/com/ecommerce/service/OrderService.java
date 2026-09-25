package com.ecommerce.service;

import com.ecommerce.dao.DeliveryZoneDAO;
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
        int itemsTotal = 0;
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
            if (item.getOfferId() != null && item.getOfferId() > 0) {
                // Ligne au prix négocié : offre ACCEPTED du même téléphone, mêmes variantes.
                com.ecommerce.model.Offer off;
                try {
                    off = new OfferService().consumeForOrder(item.getOfferId(), order.getPhone(),
                        item.getQty(), item.getSize(), item.getColor());
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
        }
        int deliveryFee = 0;
        try {
            deliveryFee = deliveryDAO.getFee(order.getCity());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        order.setTotal(itemsTotal + deliveryFee);
        try {
            return orderDAO.create(order);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("STOCK_INSUFFISANT:")) {
                throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                    e.getMessage().substring("STOCK_INSUFFISANT:".length()).trim());
            }
            throw e;
        }
    }

    public List<Order> getAllOrders() throws SQLException {
        return orderDAO.findAll();
    }

    public Order getOrderById(int id) throws SQLException {
        return orderDAO.findById(id);
    }

    public void updateStatus(int id, String status) throws AppException, SQLException {
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
        orderDAO.updateStatus(id, status);
    }

    public java.util.List<com.ecommerce.model.Order> getOrdersByPhone(String phone) throws AppException, SQLException {
        if (phone == null || phone.replaceAll("\\D", "").length() < 9) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de téléphone invalide");
        }
        return orderDAO.findByPhone(phone.trim());
    }

    public com.ecommerce.model.Order trackOrder(String number, String phone) throws AppException, SQLException {
        if (number == null || number.trim().isEmpty() || phone == null || phone.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de commande et téléphone requis");
        }
        com.ecommerce.model.Order o = orderDAO.findByNumberAndPhone(number.trim(), phone.trim());
        if (o == null) {
            throw new AppException(ErrorCodes.ORDER_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    public void cancelGuestOrder(String number, String phone) throws AppException, SQLException {
        com.ecommerce.model.Order o = trackOrder(number, phone);
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

    public void deleteOrder(int id) throws SQLException {
        orderDAO.delete(id);
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
