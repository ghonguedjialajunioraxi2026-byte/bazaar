package com.ecommerce.service;

import com.ecommerce.dao.AdminDAO;
import com.ecommerce.model.Admin;
import com.ecommerce.util.PasswordUtil;

import java.sql.SQLException;

public class AdminService {
    private final AdminDAO adminDAO = new AdminDAO();

    public Admin login(String username, String password) throws SQLException {
        Admin admin = adminDAO.findByUsername(username);
        if (admin != null && PasswordUtil.verify(password, admin.getPasswordHash())) {
            // Migration transparente vers PBKDF2 (comptes pré-Phase 15).
            if (PasswordUtil.needsRehash(admin.getPasswordHash())) {
                try {
                    adminDAO.setPasswordHash(admin.getId(), PasswordUtil.hash(password));
                } catch (Exception ignored) {}
            }
            return admin;
        }
        return null;
    }

    public void register(String username, String password) throws SQLException {
        adminDAO.create(username, password);
    }
}
