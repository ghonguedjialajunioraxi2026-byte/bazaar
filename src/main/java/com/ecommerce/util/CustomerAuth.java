package com.ecommerce.util;

import com.ecommerce.dao.CustomerSessionDAO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Authentification client BAZAAR : même discipline que AdminAuth /
 * SupplierApiServlet — token uniquement via header Authorization Bearer
 * (jamais en query : fuite logs/historique/Referer).
 */
public final class CustomerAuth {

    private CustomerAuth() {}

    public static int validateCustomer(HttpServletRequest req) {
        int customerId = 0;
        try {
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else token = null;
            if (token != null && !token.isBlank()) {
                customerId = new CustomerSessionDAO().validateSession(token);
            }
        } catch (Exception ignored) {
            customerId = 0;
        }
        return customerId;
    }

    public static int requireCustomer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int customerId = validateCustomer(req);
        if (customerId <= 0) {
            resp.setStatus(401);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"Authentification requise\",\"errorCode\":\"AUTH_REQUIRED\"}");
        }
        return customerId;
    }
}
