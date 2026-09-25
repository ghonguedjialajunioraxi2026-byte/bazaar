package com.ecommerce.service;

import com.ecommerce.dao.ProductDAO;
import com.ecommerce.model.Product;

import java.sql.SQLException;
import java.util.List;

public class ProductService {
    private final ProductDAO productDAO = new ProductDAO();

    public List<Product> getAllProducts() throws SQLException {
        return productDAO.findAll();
    }

    public List<Product> getByCategory(String category) throws SQLException {
        return productDAO.findByCategory(category);
    }

    public List<Product> getPopular(int limit) throws SQLException {
        return productDAO.findPopular(limit);
    }

    public List<Product> getNew(int limit) throws SQLException {
        return productDAO.findNew(limit);
    }

    public List<Product> getPromo(int limit) throws SQLException {
        return productDAO.findPromo(limit);
    }

    public List<Product> getTopRated(int limit) throws SQLException {
        return productDAO.findTopRated(limit);
    }

    public List<Product> getRecommended(int limit) throws SQLException {
        return productDAO.findRecommended(limit);
    }

    public List<Product> search(String query) throws SQLException {
        return productDAO.search(query);
    }

    public List<Product> filter(String category, String brand, String badge,
                                 Integer minPrice, Integer maxPrice,
                                 Boolean inStock, String sort,
                                 int page, int limit) throws SQLException {
        return productDAO.filter(category, brand, badge, minPrice, maxPrice, inStock, sort, page, limit);
    }

    public int countFiltered(String category, String brand, String badge,
                              Integer minPrice, Integer maxPrice,
                              Boolean inStock) throws SQLException {
        return productDAO.countFiltered(category, brand, badge, minPrice, maxPrice, inStock);
    }
}
