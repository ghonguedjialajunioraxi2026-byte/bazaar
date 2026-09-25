package com.ecommerce.service;

import com.ecommerce.dao.OrderDAO;
import com.ecommerce.model.Order;

import java.sql.SQLException;
import java.util.List;

public class OrderService {
    private final OrderDAO orderDAO = new OrderDAO();

    public int createOrder(Order order) throws SQLException {
        return orderDAO.create(order);
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
