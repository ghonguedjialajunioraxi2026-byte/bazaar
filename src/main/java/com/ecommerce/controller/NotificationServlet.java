package com.ecommerce.controller;

import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.model.Notification;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API Notifications — centre complet.
 *
 * Rétrocompatible :
 *   GET  /api/notifications?role=R            tableau simple (50 dernières)
 *   GET  /api/notifications?action=unread&role=R   {"count":N}
 *   PUT  /api/notifications?action=read&id=N | ?action=readAll&role=R
 *   DELETE /api/notifications?id=N
 *
 * Nouveau (centre de notifications) :
 *   GET  /api/notifications?role=R&type=ORDER&status=unread&q=...&page=1&limit=10
 *        -> {"data":[...],"total":N,"unread":M,"page":1,"limit":10}
 *   POST /api/notifications  {title,message,type,priority,roles[],targetUserId,link}
 *   PUT  /api/notifications  {action:"read"|"unread"|"readAll"|"readMany", id?, ids[]?}
 *   DELETE /api/notifications?action=clearRead&role=R  (purge les lues)
 *   DELETE /api/notifications  {ids:[...]}  (suppression multiple)
 */
@WebServlet("/api/notifications")
public class NotificationServlet extends HttpServlet {
    private final NotificationDAO notificationDAO = new NotificationDAO();

    private static final Set<String> ROLES = new HashSet<>(Arrays.asList(
            "ADMIN", "SUPER_ADMIN", "GESTIONNAIRE", "CLIENT", "SUPPLIER"));
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "ORDER", "STOCK", "SUPPLIER", "PAYMENT", "SYSTEM"));
    private static final Set<String> PRIORITIES = new HashSet<>(Arrays.asList(
            "LOW", "NORMAL", "HIGH", "URGENT"));

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String action = req.getParameter("action");
            String role = validRole(req.getParameter("role"));

            if ("unread".equals(action)) {
                resp.getWriter().write("{\"count\":" + notificationDAO.countUnread(role) + "}");
                return;
            }

            // Mode centre : dès qu'un paramètre de filtre/pagination est présent
            String type = req.getParameter("type");
            String status = req.getParameter("status");
            String q = req.getParameter("q");
            boolean centerMode = req.getParameter("page") != null || req.getParameter("limit") != null
                    || type != null || status != null || q != null;
            if (!centerMode) {
                resp.getWriter().write(JsonUtil.toJson(notificationDAO.findForRole(role)));
                return;
            }

            if (type != null && !TYPES.contains(type.toUpperCase())) type = null;
            else if (type != null) type = type.toUpperCase();
            Boolean unreadOnly = null;
            if ("unread".equalsIgnoreCase(status)) unreadOnly = true;
            else if ("read".equalsIgnoreCase(status)) unreadOnly = false;
            int page = parseInt(req.getParameter("page"), 1);
            int limit = Math.min(50, Math.max(1, parseInt(req.getParameter("limit"), 10)));

            List<Notification> data = notificationDAO.findFiltered(role, type, unreadOnly, q, limit, (page - 1) * limit);
            int total = notificationDAO.countFiltered(role, type, unreadOnly, q);
            int unread = notificationDAO.countUnread(role);

            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("total", total);
            result.put("unread", unread);
            result.put("page", page);
            result.put("limit", limit);
            resp.getWriter().write(JsonUtil.toJson(result));
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"" + safe(e.getMessage()) + "\"}");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
            String title = str(body.get("title"));
            String message = str(body.get("message"));
            if (title == null || title.trim().isEmpty()) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Le titre est obligatoire\"}");
                return;
            }
            if (message == null) message = "";
            if (title.length() > 200) title = title.substring(0, 200);

            String type = str(body.get("type"));
            type = (type != null && TYPES.contains(type.toUpperCase())) ? type.toUpperCase() : "SYSTEM";
            String priority = str(body.get("priority"));
            priority = (priority != null && PRIORITIES.contains(priority.toUpperCase())) ? priority.toUpperCase() : "NORMAL";
            String link = str(body.get("link"));
            Integer targetUserId = body.get("targetUserId") instanceof Number
                    ? ((Number) body.get("targetUserId")).intValue() : null;

            List<String> roles = new ArrayList<>();
            Object rolesObj = body.get("roles");
            if (rolesObj instanceof List) {
                for (Object r : (List<Object>) rolesObj) {
                    String rr = r == null ? null : String.valueOf(r).toUpperCase();
                    if (rr != null && ROLES.contains(rr) && !roles.contains(rr)) roles.add(rr);
                }
            }
            String singleRole = validRole(str(body.get("role")));
            if (roles.isEmpty() && singleRole != null) roles.add(singleRole);
            if (roles.isEmpty()) roles.add("SUPER_ADMIN");

            List<Integer> created = new ArrayList<>();
            for (String r : roles) {
                Notification n = new Notification();
                n.setTargetRole(r);
                n.setTargetUserId(targetUserId);
                n.setTitle(title.trim());
                n.setMessage(message);
                n.setType(type);
                n.setPriority(priority);
                n.setLink(link);
                created.add(notificationDAO.create(n));
            }
            resp.getWriter().write("{\"success\":true,\"ids\":" + JsonUtil.toJson(created) + "}");
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"" + safe(e.getMessage()) + "\"}");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String action = req.getParameter("action");
            String role = validRole(req.getParameter("role"));
            String idParam = req.getParameter("id");
            Map<String, Object> body = null;

            if (action == null) {
                body = JsonUtil.fromJson(readBody(req), Map.class);
                action = str(body.get("action"));
                if (idParam == null && body.get("id") != null) idParam = String.valueOf(parseId(body.get("id")));
            }

            if ("readAll".equals(action)) {
                notificationDAO.markAllRead(role);
            } else if ("read".equals(action)) {
                notificationDAO.markRead(parseId(idParam));
            } else if ("unread".equals(action)) {
                notificationDAO.markUnread(parseId(idParam));
            } else if ("readMany".equals(action)) {
                if (body == null) body = JsonUtil.fromJson(readBody(req), Map.class);
                notificationDAO.markManyRead(role, intList(body.get("ids")));
            } else {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Action inconnue\"}");
                return;
            }
            resp.getWriter().write("{\"success\":true,\"unread\":" + notificationDAO.countUnread(role) + "}");
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"" + safe(e.getMessage()) + "\"}");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String action = req.getParameter("action");
            String role = validRole(req.getParameter("role"));
            int deleted;
            if ("clearRead".equals(action)) {
                deleted = notificationDAO.deleteRead(role);
            } else if (req.getParameter("id") != null) {
                notificationDAO.delete(Integer.parseInt(req.getParameter("id")));
                deleted = 1;
            } else {
                Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
                deleted = notificationDAO.deleteByIds(role, intList(body.get("ids")));
            }
            resp.getWriter().write("{\"success\":true,\"deleted\":" + deleted
                    + ",\"unread\":" + notificationDAO.countUnread(role) + "}");
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"" + safe(e.getMessage()) + "\"}");
        }
    }

    // ---------------- helpers ----------------

    private String validRole(String role) {
        if (role == null) return "SUPER_ADMIN";
        String r = role.toUpperCase();
        return ROLES.contains(r) ? r : "SUPER_ADMIN";
    }

    private int parseInt(String s, int def) {
        try {
            if (s == null) return def;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Parse un id venant du JSON (Gson -> Double) ou des query params (String). */
    private int parseId(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o == null) throw new NumberFormatException("For input string: \"null\"");
        String s = String.valueOf(o).trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return (int) Double.parseDouble(s);
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<Integer> intList(Object o) {
        List<Integer> ids = new ArrayList<>();
        if (o instanceof List) {
            for (Object x : (List<?>) o) {
                try {
                    if (x instanceof Number) ids.add(((Number) x).intValue());
                    else ids.add(Integer.parseInt(String.valueOf(x)));
                } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    private String readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        String body = sb.toString().trim();
        return body.isEmpty() ? "{}" : body;
    }

    private String safe(String s) {
        return s == null ? "Erreur" : s.replace("\\", "\\\\").replace("\"", "'");
    }
}
