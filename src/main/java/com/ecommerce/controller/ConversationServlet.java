package com.ecommerce.controller;

import com.ecommerce.model.Conversation;
import com.ecommerce.model.SupplierMessage;
import com.ecommerce.service.ConversationService;
import com.ecommerce.service.OfferService;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Messagerie côté invité (Phase 8). Identité = téléphone + clé navigateur.
 * Le serveur vérifie toujours les deux (anti-IDOR).
 */
@WebServlet("/api/conversations/*")
public class ConversationServlet extends HttpServlet {

    private final ConversationService conversationService = new ConversationService();
    private final OfferService offerService = new OfferService();

    private Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return JsonUtil.fromJson(sb.toString(), Map.class);
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private int num(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private String[] pathParts(HttpServletRequest req) {
        String pi = req.getPathInfo();
        if (pi == null || pi.equals("/")) return new String[0];
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String s : pi.split("/")) if (s != null && !s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            String phone = req.getParameter("phone");
            String cid = req.getParameter("cid");

            if (parts.length == 1 && "unread".equals(parts[0])) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("unread", conversationService.unreadCount(phone, cid));
                ApiResponse.ok(resp, m);
                return;
            }
            if (parts.length == 0) {
                ApiResponse.ok(resp, conversationService.listForGuest(phone, cid));
                return;
            }
            if (parts.length == 1) {
                int convId = Integer.parseInt(parts[0]);
                Conversation c = conversationService.getForGuest(convId, phone, cid);
                List<SupplierMessage> messages = conversationService.getMessages(convId, phone, cid);
                conversationService.markRead(convId, phone, cid);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("conversation", c);
                m.put("messages", messages);
                m.put("offers", offerService.listForConversation(convId));
                ApiResponse.ok(resp, m);
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);
            Map<String, Object> body = readBody(req);

            if (parts.length == 0) {
                int supplierId = num(body.get("supplierId"), 0);
                int productId = num(body.get("productId"), 0);
                Conversation c = conversationService.getOrCreate(supplierId,
                    productId > 0 ? productId : null,
                    str(body.get("clientName")), str(body.get("phone")), str(body.get("cid")));
                ApiResponse.created(resp, c);
                return;
            }
            if (parts.length == 2 && "messages".equals(parts[1])) {
                int convId = Integer.parseInt(parts[0]);
                SupplierMessage m = conversationService.sendClientMessage(convId,
                    str(body.get("phone")), str(body.get("cid")), str(body.get("body")));
                ApiResponse.created(resp, m);
                return;
            }
            if (parts.length == 2 && "read".equals(parts[1])) {
                int convId = Integer.parseInt(parts[0]);
                conversationService.markRead(convId, str(body.get("phone")), str(body.get("cid")));
                ApiResponse.ok(resp, null);
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }
}
