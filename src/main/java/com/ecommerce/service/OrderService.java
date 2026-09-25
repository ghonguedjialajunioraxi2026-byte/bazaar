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
            // Prix serveur imposé : toute valeur envoyée par le navigateur est ignorée.
            item.setUnitPrice(p.getPrice());
            itemsTotal += p.getPrice() * item.getQty();
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

    public void updateStatus(int id, String status) throws SQLException {
        orderDAO.updateStatus(id, status);
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
