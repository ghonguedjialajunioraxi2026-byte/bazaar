package com.ecommerce.controller;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Headers de sécurité (Phase 15).
 * Volontairement sans Content-Security-Policy : le frontend utilise des
 * scripts/styles inline et des CDN (Tailwind, Chart.js, Google) qu'une CSP
 * stricte casserait — décision documentée dans docs/security.md.
 * HSTS volontairement absent : actif uniquement derrière HTTPS (production).
 */
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse resp = (HttpServletResponse) response;
            // Bloque le MIME-sniffing (renfort anti-XSS uploads).
            resp.setHeader("X-Content-Type-Options", "nosniff");
            // Anti-clickjacking : intégration iframe même origine uniquement.
            resp.setHeader("X-Frame-Options", "SAMEORIGIN");
            // Ne pas fuir l'URL complète vers les sites externes.
            resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            // Surface navigateur réduite.
            resp.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            // Workflow livraison : l'interface pilote les boutons sur l'état
            // SERVEUR (GET /api/delivery-flow/state). Aucune réponse d'API
            // ne doit être servie depuis le cache du navigateur.
            if (request instanceof jakarta.servlet.http.HttpServletRequest) {
                String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
                if (uri != null && uri.contains("/api/")) {
                    resp.setHeader("Cache-Control", "no-store");
                    resp.setHeader("Pragma", "no-cache");
                }
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
