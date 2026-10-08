package com.ecommerce.pawapay;

import com.ecommerce.util.AppException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client HTTP contre un mock local (JDK HttpServer) : vérifie le Bearer,
 * le JSON et la gestion d'erreurs. Aucun appel réel, aucun token réel.
 */
class PawaPayClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int statusToReturn = 200;
    private volatile String bodyToReturn = "{}";

    @BeforeEach
    void startMock() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastPath.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body = bodyToReturn.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusToReturn, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopMock() {
        if (server != null) server.stop(0);
    }

    private PawaPayClient client() {
        return new PawaPayClient(baseUrl, baseUrl + "/v2/deposits",
            "TEST-TOKEN-NEVER-REAL", HttpClient.newHttpClient());
    }

    @Test
    void initiationEnvoieBearerEtParseAccepted() throws Exception {
        bodyToReturn = "{\"depositId\":\"afb57b93-7849-49aa-babb-4c3ccbfe3d79\","
            + "\"status\":\"ACCEPTED\"}";
        Map<String, Object> resp = client().initiateDeposit(
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79", "525000", "XAF",
            "237690123456", "MTN_MOMO_CMR", "CMD-2026-000001", "MBOA STYLE", null);
        assertEquals("ACCEPTED", resp.get("status"));
        assertEquals("Bearer TEST-TOKEN-NEVER-REAL", lastAuth.get());
        assertEquals("POST /v2/deposits", lastPath.get());
    }

    @Test
    void statutDepot() throws Exception {
        bodyToReturn = "{\"status\":\"FOUND\",\"data\":"
            + "{\"depositId\":\"afb57b93-7849-49aa-babb-4c3ccbfe3d79\",\"status\":\"COMPLETED\"}}";
        Map<String, Object> resp = client().getDeposit("afb57b93-7849-49aa-babb-4c3ccbfe3d79");
        assertEquals("FOUND", resp.get("status"));
        assertEquals("Bearer TEST-TOKEN-NEVER-REAL", lastAuth.get());
        assertTrue(lastPath.get().startsWith("GET /v2/deposits/"));
    }

    @Test
    void rejetAuthSansExposerLeToken() {
        statusToReturn = 401;
        bodyToReturn = "{\"status\":\"REJECTED\"}";
        AppException e = assertThrows(AppException.class, () ->
            client().getDeposit("afb57b93-7849-49aa-babb-4c3ccbfe3d79"));
        assertEquals("PROVIDER_UNAVAILABLE", e.getCode());
        assertFalse(e.getMessage().contains("TEST-TOKEN"));
    }

    // Test 10 — PawaPay retourne une erreur métier (HTTP 200 + REJECTED).
    @Test
    void rejetMetierRemonteSansException() throws Exception {
        bodyToReturn = "{\"depositId\":\"afb57b93-7849-49aa-babb-4c3ccbfe3d79\","
            + "\"status\":\"REJECTED\",\"failureReason\":{\"failureCode\":\"INVALID_PHONE_NUMBER\","
            + "\"failureMessage\":\"The phone number seems invalid\"}}";
        Map<String, Object> resp = client().initiateDeposit(
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79", "525000", "XAF",
            "237690123456", "MTN_MOMO_CMR", "CMD-2026-000123", "MBOA STYLE", null);
        assertEquals("REJECTED", resp.get("status"));
        assertNotNull(resp.get("failureReason"));
    }

    // Test 10 — DUPLICATE_IGNORED : pas de deuxième transaction, suivi de l'existant.
    @Test
    void duplicateIgnoredSansNouvelleTransaction() throws Exception {
        bodyToReturn = "{\"depositId\":\"afb57b93-7849-49aa-babb-4c3ccbfe3d79\","
            + "\"status\":\"DUPLICATE_IGNORED\"}";
        Map<String, Object> resp = client().initiateDeposit(
            "afb57b93-7849-49aa-babb-4c3ccbfe3d79", "525000", "XAF",
            "237690123456", "MTN_MOMO_CMR", "CMD-2026-000123", "BAZAAR PAYMENT", null);
        assertEquals("DUPLICATE_IGNORED", resp.get("status"));
    }

    @Test
    void erreurServeurEtLimitesMappeesSansSecret() {
        statusToReturn = 500;
        AppException e500 = assertThrows(AppException.class, () ->
            client().getDeposit("afb57b93-7849-49aa-babb-4c3ccbfe3d79"));
        assertEquals("PROVIDER_UNAVAILABLE", e500.getCode());
        assertFalse(e500.getMessage().contains("TEST-TOKEN"));

        statusToReturn = 429;
        AppException e429 = assertThrows(AppException.class, () ->
            client().getDeposit("afb57b93-7849-49aa-babb-4c3ccbfe3d79"));
        assertEquals(503, e429.getHttpStatus());

        statusToReturn = 404;
        AppException e404 = assertThrows(AppException.class, () ->
            client().getDeposit("afb57b93-7849-49aa-babb-4c3ccbfe3d79"));
        assertEquals("PAYMENT_NOT_FOUND", e404.getCode());
    }
}
