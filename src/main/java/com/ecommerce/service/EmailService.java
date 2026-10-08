package com.ecommerce.service;

import com.ecommerce.dao.EmailNotificationDAO;
import com.ecommerce.model.EmailNotification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Emails BAZAAR (remplace WhatsApp : notifications internes + email).
 *
 * Transport SMTP 100 % JDK (sockets + TLS + AUTH LOGIN), sans dépendance
 * supplémentaire. Configuration par variables d'environnement :
 * SMTP_HOST, SMTP_PORT (défaut 587), SMTP_USER, SMTP_PASS, SMTP_FROM,
 * SMTP_FROM_NAME, SMTP_TLS (starttls|ssl|off, défaut starttls).
 *
 * Règles :
 * - Identifiants JAMAIS en code, JAMAIS en logs, JAMAIS en base.
 * - Sans SMTP configuré : mise en file QUEUED + log (jamais de faux envoi).
 * - Tout déclencheur est best-effort : un échec email ne casse jamais
 *   la commande, le paiement ou le message.
 */
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());
    private static final int SOCKET_TIMEOUT_MS = 15000;
    private static final int MAX_BODY_CHARS = 200000;

    private final EmailNotificationDAO dao = new EmailNotificationDAO();

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    public static boolean isConfigured() {
        return env("SMTP_HOST") != null && env("SMTP_FROM") != null;
    }

    static int smtpPort() {
        String p = env("SMTP_PORT");
        if (p != null) {
            try {
                int port = Integer.parseInt(p);
                if (port > 0 && port <= 65535) return port;
            } catch (NumberFormatException ignored) {}
        }
        return 587;
    }

    /** Adresse email syntaxiquement valable (garde anti-fuite, pas de vérification MX). */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String e = email.trim();
        if (e.length() < 5 || e.length() > 160) return false;
        return e.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    }

    // ------------------------------------------------------------------
    // File + envoi
    // ------------------------------------------------------------------

    /**
     * Met en file puis tente l'envoi immédiat. Retourne l'id de file,
     * -1 si destinataire invalide. Ne lève jamais (best-effort).
     */
    public int queueAndSend(String recipient, String subject, String htmlBody,
            String eventCode, Integer orderId) {
        if (!isValidEmail(recipient) || subject == null || subject.trim().isEmpty()
                || htmlBody == null || htmlBody.isEmpty()) {
            return -1;
        }
        String body = htmlBody.length() > MAX_BODY_CHARS
            ? htmlBody.substring(0, MAX_BODY_CHARS) : htmlBody;
        EmailNotification e = new EmailNotification();
        e.setRecipient(recipient.trim().toLowerCase());
        e.setSubject(subject.trim().substring(0, Math.min(200, subject.trim().length())));
        e.setBody(body);
        e.setEventCode(eventCode);
        e.setOrderId(orderId);
        final int id;
        try {
            id = dao.enqueue(e);
        } catch (Exception ex) {
            LOG.warning("Email queue failed (DB)");
            return -1;
        }
        if (!isConfigured()) {
            LOG.info("Email NOT CONFIGURED (queued): event=" + eventCode + " id=" + id);
            return id;
        }
        try {
            sendRaw(e.getRecipient(), e.getSubject(), e.getBody());
            dao.markSent(id);
            LOG.info("Email sent: event=" + eventCode + " id=" + id);
        } catch (Exception ex) {
            try {
                dao.markFailed(id, safeError(ex));
            } catch (Exception ignored) {}
            LOG.warning("Email failed: event=" + eventCode + " id=" + id);
        }
        return id;
    }

    /** Rejoue les emails en file (cron/admin, borné). */
    public int flushQueue(int limit) {
        int sent = 0;
        if (!isConfigured()) return 0;
        java.util.List<EmailNotification> queued;
        try {
            queued = dao.findQueued(limit);
        } catch (Exception e) {
            return 0;
        }
        for (EmailNotification e : queued) {
            try {
                sendRaw(e.getRecipient(), e.getSubject(), e.getBody());
                dao.markSent(e.getId());
                sent++;
            } catch (Exception ex) {
                try {
                    dao.markFailed(e.getId(), safeError(ex));
                } catch (Exception ignored) {}
            }
        }
        return sent;
    }

    private static String safeError(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        // Ceinture + bretelles : aucun secret ne transite ici (pas de user/pass).
        return m.substring(0, Math.min(255, m.length()));
    }

    // ------------------------------------------------------------------
    // SMTP minimal (EHLO, STARTTLS/SSL, AUTH LOGIN, MAIL/RCPT/DATA/QUIT)
    // ------------------------------------------------------------------

    void sendRaw(String to, String subject, String htmlBody) throws Exception {
        String host = env("SMTP_HOST");
        int port = smtpPort();
        String tlsMode = env("SMTP_TLS");
        if (tlsMode == null) tlsMode = "starttls";
        tlsMode = tlsMode.trim().toLowerCase();
        String from = env("SMTP_FROM");

        Socket socket;
        if ("ssl".equals(tlsMode)) {
            SSLSocket ssl = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            ssl.setSoTimeout(SOCKET_TIMEOUT_MS);
            ssl.startHandshake();
            socket = ssl;
        } else {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        }
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            expect(in, 220);
            send(out, "EHLO bazaar");
            readMultiline(in, 250);
            if (!"off".equals(tlsMode) && !"ssl".equals(tlsMode)) {
                // STARTTLS exigé par défaut : refus serveur = échec franc
                // (jamais de repli silencieux sans chiffrement).
                send(out, "STARTTLS");
                expect(in, 220);
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(socket, host, port, true);
                ssl.setSoTimeout(SOCKET_TIMEOUT_MS);
                ssl.startHandshake();
                try (BufferedReader in2 = new BufferedReader(
                        new InputStreamReader(ssl.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter out2 = new BufferedWriter(
                        new OutputStreamWriter(ssl.getOutputStream(), StandardCharsets.UTF_8))) {
                    transact(in2, out2, host, from, to, subject, htmlBody);
                }
                return;
            }
            transact(in, out, host, from, to, subject, htmlBody);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void transact(BufferedReader in, BufferedWriter out, String host,
            String from, String to, String subject, String htmlBody) throws Exception {
        String user = env("SMTP_USER");
        String pass = env("SMTP_PASS");
        if (user != null && pass != null) {
            send(out, "AUTH LOGIN");
            expect(in, 334);
            send(out, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
            expect(in, 334);
            send(out, Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8)));
            expect(in, 235);
        }
        send(out, "MAIL FROM:<" + from + ">");
        expect(in, 250);
        send(out, "RCPT TO:<" + to + ">");
        expect(in, new int[]{250, 251});
        send(out, "DATA");
        expect(in, 354);
        String fromName = env("SMTP_FROM_NAME");
        if (fromName == null) fromName = "BAZAAR";
        out.write("From: " + encodeHeader(fromName) + " <" + from + ">\r\n");
        out.write("To: <" + to + ">\r\n");
        out.write("Subject: " + encodeHeader(subject) + "\r\n");
        out.write("MIME-Version: 1.0\r\n");
        out.write("Content-Type: text/html; charset=UTF-8\r\n");
        out.write("Content-Transfer-Encoding: 8bit\r\n");
        out.write("\r\n");
        out.write(dotStuff(htmlBody));
        out.write("\r\n.\r\n");
        out.flush();
        expect(in, 250);
        try {
            send(out, "QUIT");
        } catch (Exception ignored) {}
    }

    private static String encodeHeader(String s) {
        if (s.matches("[\\x20-\\x7E]*")) return s;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String dotStuff(String body) {
        return body.replace("\r\n.", "\r\n..").replace("\n.", "\n..");
    }

    private static void send(BufferedWriter out, String line) throws Exception {
        out.write(line + "\r\n");
        out.flush();
    }

    private static String readMultiline(BufferedReader in, int expected) throws Exception {
        String last = null;
        String line;
        // Réponse multi-lignes : "250-..." puis "250 ...".
        for (int i = 0; i < 50; i++) {
            line = in.readLine();
            if (line == null) throw new java.io.IOException("SMTP closed");
            last = line;
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
            if (line.length() < 4) break;
        }
        int code = codeOf(last);
        if (code != expected) throw new java.io.IOException("SMTP " + code + " (attendu " + expected + ")");
        return last;
    }

    private static void expect(BufferedReader in, int expected) throws Exception {
        String line = in.readLine();
        if (line == null) throw new java.io.IOException("SMTP closed");
        if (codeOf(line) != expected) throw new java.io.IOException("SMTP refus (" + codeOf(line) + ")");
    }

    private static void expect(BufferedReader in, int[] accepted) throws Exception {
        String line = in.readLine();
        if (line == null) throw new java.io.IOException("SMTP closed");
        int code = codeOf(line);
        for (int a : accepted) if (a == code) return;
        throw new java.io.IOException("SMTP refus (" + code + ")");
    }

    private static int codeOf(String line) {
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Gabarits (FR, marque BAZAAR, HTML inline)
    // ------------------------------------------------------------------

    static String layout(String title, String content) {
        return "<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\"></head>"
            + "<body style=\"margin:0;padding:0;background:#F4F7F2;font-family:Arial,sans-serif;\">"
            + "<div style=\"max-width:560px;margin:0 auto;padding:24px;\">"
            + "<div style=\"background:#123B16;color:#fff;border-radius:16px 16px 0 0;padding:20px 24px;\">"
            + "<div style=\"font-size:22px;font-weight:bold;letter-spacing:1px;\">BAZAAR</div>"
            + "<div style=\"font-size:13px;color:#B7F000;\">" + escape(title) + "</div></div>"
            + "<div style=\"background:#fff;border-radius:0 0 16px 16px;padding:24px;color:#123B16;\">"
            + content
            + "<p style=\"font-size:12px;color:#5F6B61;margin-top:24px;\">"
            + "Ceci est un message automatique BAZAAR, ne pas répondre directement.</p>"
            + "</div></div></body></html>";
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    static String money(int amount) {
        return String.format(java.util.Locale.US, "%,d", amount).replace(',', ' ') + " XAF";
    }

    public static String orderCreatedSubject(String orderNumber) {
        return "Votre commande " + orderNumber + " a été créée";
    }

    public static String orderCreatedBody(String name, String orderNumber, int total) {
        return layout("Commande créée",
            "<p>Bonjour " + escape(name) + ",</p>"
            + "<p>Votre commande <strong>" + escape(orderNumber) + "</strong> a bien été créée "
            + "pour un total de <strong>" + money(total) + "</strong>.</p>"
            + "<p>Suivez-la à tout moment dans BAZAAR : <strong>Mes commandes</strong>.</p>");
    }

    public static String paymentSucceededSubject(String orderNumber) {
        return "Paiement confirmé pour " + orderNumber;
    }

    public static String paymentSucceededBody(String name, String orderNumber, int amount) {
        return layout("Paiement confirmé",
            "<p>Bonne nouvelle " + escape(name) + " !</p>"
            + "<p>Votre paiement de <strong>" + money(amount) + "</strong> pour la commande "
            + "<strong>" + escape(orderNumber) + "</strong> a été confirmé.</p>"
            + "<p>Votre vendeur prépare déjà votre commande.</p>");
    }

    public static String paymentFailedSubject(String orderNumber) {
        return "Paiement non confirmé pour " + orderNumber;
    }

    public static String paymentFailedBody(String name, String orderNumber) {
        return layout("Paiement non confirmé",
            "<p>Bonjour " + escape(name) + ",</p>"
            + "<p>Votre paiement pour la commande <strong>" + escape(orderNumber) + "</strong> "
            + "n'a pas pu être confirmé. Aucun montant n'a été débité définitivement.</p>"
            + "<p>Vous pouvez réessayer depuis <strong>Mes commandes</strong>.</p>");
    }

    public static String shipmentSubject(String orderNumber, String label) {
        return "Commande " + orderNumber + " : " + label;
    }

    public static String shipmentBody(String name, String orderNumber, String label, String tracking) {
        return layout("Livraison",
            "<p>Bonjour " + escape(name) + ",</p>"
            + "<p>Votre commande <strong>" + escape(orderNumber) + "</strong> : "
            + "<strong>" + escape(label) + "</strong>.</p>"
            + (tracking != null ? "<p>Suivi : <strong>" + escape(tracking) + "</strong></p>" : ""));
    }

    public static String messageReceivedSubject(String supplierName) {
        return "Nouveau message de " + supplierName + " sur BAZAAR";
    }

    public static String messageReceivedBody(String supplierName, String excerpt) {
        return layout("Nouveau message",
            "<p><strong>" + escape(supplierName) + "</strong> vous a envoyé un nouveau message :</p>"
            + "<p style=\"background:#F4F7F2;border-radius:12px;padding:12px;\"><em>« "
            + escape(excerpt) + " »</em></p>"
            + "<p>Répondez directement dans votre messagerie BAZAAR.</p>");
    }

    public static String refundSubject(String orderNumber) {
        return "Remboursement traité pour " + orderNumber;
    }

    public static String refundBody(String name, String orderNumber) {
        return layout("Remboursement",
            "<p>Bonjour " + escape(name) + ",</p>"
            + "<p>Le remboursement de votre commande <strong>" + escape(orderNumber) + "</strong> "
            + "a été traité.</p>");
    }

    // ------------------------------------------------------------------
    // Connexion fournisseur : code de vérification à 6 chiffres
    // ------------------------------------------------------------------

    public static String supplierLoginCodeSubject() {
        return "Votre code de connexion BAZAAR";
    }

    /**
     * Alerte + code : l'email est notifié que son compte est en cours
     * d'utilisation pour se connecter à BAZAAR. Le code à 6 chiffres
     * prouve que l'adresse appartient bien à celui qui se connecte.
     */
    public static String supplierLoginCodeBody(String shopName, String code) {
        String who = (shopName == null || shopName.trim().isEmpty()) ? "Fournisseur" : shopName.trim();
        return layout("Sécurité du compte",
            "<p>Bonjour " + escape(who) + ",</p>"
            + "<p>Votre compte fournisseur est <strong>en train d'être utilisé "
            + "pour se connecter à BAZAAR</strong>.</p>"
            + "<p>Si c'est bien vous, saisissez ce code à 6 chiffres sur la page "
            + "de connexion pour vérifier qu'il s'agit de votre adresse email :</p>"
            + "<p style=\"text-align:center;margin:20px 0;\">"
            + "<span style=\"display:inline-block;font-size:32px;font-weight:bold;letter-spacing:8px;"
            + "background:#F4F7F2;border:2px dashed #4D7C0F;border-radius:12px;padding:12px 24px;color:#123B16;\">"
            + escape(code) + "</span></p>"
            + "<p>Ce code est valable <strong>10 minutes</strong> et à usage unique. "
            + "Ne le partagez avec personne, même si on vous le demande.</p>"
            + "<p style=\"background:#FEF2F2;border-left:4px solid #DC2626;border-radius:8px;padding:12px;\">"
            + "<strong>Ce n'était pas vous ?</strong> Ne saisissez pas ce code et "
            + "changez immédiatement votre mot de passe. Votre compte reste protégé "
            + "tant que ce code n'est pas saisi.</p>");
    }
}
