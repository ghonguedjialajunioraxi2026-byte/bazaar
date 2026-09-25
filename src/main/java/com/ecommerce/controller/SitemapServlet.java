package com.ecommerce.controller;

import com.ecommerce.dao.ProductDAO;
import com.ecommerce.model.Product;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/sitemap.xml")
public class SitemapServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/xml");
        resp.setCharacterEncoding("UTF-8");
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

            // Static pages
            addUrl(sb, req, "/", "1.0", "daily");
            addUrl(sb, req, "/#/shop", "0.8", "daily");
            addUrl(sb, req, "/#/about", "0.5", "monthly");
            addUrl(sb, req, "/#/contact", "0.5", "monthly");

            // Product pages
            ProductDAO productDAO = new ProductDAO();
            List<Product> products = productDAO.findAll();
            for (Product p : products) {
                addUrl(sb, req, "/#/produit/" + p.getSlug(), "0.9", "weekly");
            }

            sb.append("</urlset>");
            resp.getWriter().write(sb.toString());
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>" + e.getMessage() + "</error>");
        }
    }

    private void addUrl(StringBuilder sb, HttpServletRequest req, String path, String priority, String changeFreq) {
        String baseUrl = req.getScheme() + "://" + req.getServerName();
        int port = req.getServerPort();
        if (port != 80 && port != 443) baseUrl += ":" + port;
        sb.append("  <url>\n");
        sb.append("    <loc>").append(baseUrl).append(path).append("</loc>\n");
        sb.append("    <changefreq>").append(changeFreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }
}
