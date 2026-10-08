package com.ecommerce.service;

import com.ecommerce.model.Conversation;
import com.ecommerce.model.Order;
import com.ecommerce.util.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Isolation des comptes clients (mission individualisation).
 *
 * Couvre : Test A (commandes A), B (commandes B), C (A lit commande B ->
 * REFUS), D (A confirme réception B -> REFUS), E (compteurs messages/notifs
 * par compte), F (A lit conversation B -> REFUS), G (adresses liées aux
 * commandes du compte), H (même produit, commandes séparées).
 *
 * Données 100 % temporaires et marquées (préfixe ISO-<ts>), créées puis
 * supprimées par le test. Si MySQL est injoignable, les tests sont
 * ignorés (pas d'échec) pour ne jamais casser `mvn clean test`.
 */
class CustomerIsolationTest {

    private static final String MARK = "ISO-" + System.currentTimeMillis() + "-";
    private static final String PHONE_A = "+237699001101";
    private static final String PHONE_B = "+237699001102";

    private int customerA = 0;
    private int customerB = 0;
    private int orderA = 0;
    private int orderB = 0;
    private int guestOrder = 0;
    private String numberA;
    private String numberB;
    private String numberGuest;
    private int convA = 0;
    private int convB = 0;
    private int productId = 0;
    private int supplierId = 0;

    private static String dbUrl() {
        String v = System.getenv("DB_URL");
        return (v == null || v.isBlank())
            ? "jdbc:mysql://localhost:3306/mboa_style?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Douala"
            : v;
    }

    private static String dbUser() {
        String v = System.getenv("DB_USER");
        return (v == null || v.isBlank()) ? "root" : v;
    }

    private static String dbPass() {
        String v = System.getenv("DB_PASSWORD");
        return v == null ? "TonMotDePasse123" : v;
    }

    private static Connection connect() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(dbUrl(), dbUser(), dbPass());
    }

    private static boolean dbAvailable() {
        try (Connection c = connect()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private static int insertCustomer(Connection c, String email) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO customers (name, email) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, MARK + email);
            ps.setString(2, MARK + email + "@test.cm");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int insertOrder(Connection c, String number, int customerId, String phone,
                                   String address, int supplier) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders (order_number, fullname, phone, city, address, total, supplier_id, status, customer_id)"
                    + " VALUES (?, ?, ?, 'Yaounde', ?, 15000, ?, 'CONFIRMEE', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, number);
            ps.setString(2, MARK + "client");
            ps.setString(3, phone);
            ps.setString(4, address);
            if (supplier > 0) ps.setInt(5, supplier); else ps.setNull(5, java.sql.Types.INTEGER);
            if (customerId > 0) ps.setInt(6, customerId); else ps.setNull(6, java.sql.Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int insertConversation(Connection c, int supplier, int customerId, String phone)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO conversations (supplier_id, customer_id, client_identifier, client_phone, subject, status)"
                    + " VALUES (?, ?, ?, ?, 'iso', 'OPEN')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, supplier);
            if (customerId > 0) ps.setInt(2, customerId); else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setString(3, MARK + "cid");
            ps.setString(4, phone);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @BeforeEach
    void seed() throws Exception {
        assumeTrue(dbAvailable(), "MySQL injoignable : tests d'isolation ignorés");
        try (Connection c = connect()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM products LIMIT 1")) {
                assumeTrue(rs.next(), "Aucun produit pour les tests");
                productId = rs.getInt(1);
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM suppliers LIMIT 1")) {
                if (rs.next()) supplierId = rs.getInt(1);
            }
            customerA = insertCustomer(c, "a");
            customerB = insertCustomer(c, "b");
            numberA = MARK + "A";
            numberB = MARK + "B";
            numberGuest = MARK + "G";
            orderA = insertOrder(c, numberA, customerA, PHONE_A, MARK + "adresse-A", supplierId);
            orderB = insertOrder(c, numberB, customerB, PHONE_B, MARK + "adresse-B", supplierId);
            guestOrder = insertOrder(c, numberGuest, 0, PHONE_A, MARK + "adresse-G", supplierId);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO order_items (order_id, product_id, qty, unit_price) VALUES (?, ?, 1, 15000)")) {
                for (int oid : new int[]{orderA, orderB}) {
                    ps.setInt(1, oid);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }
            }
            convA = insertConversation(c, supplierId > 0 ? supplierId : 1, customerA, PHONE_A);
            convB = insertConversation(c, supplierId > 0 ? supplierId : 1, customerB, PHONE_B);
            // Message fournisseur non lu dans la conversation de B uniquement.
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO messages (conversation_id, sender, body, is_read) VALUES (?, 'SUPPLIER', ?, 0)")) {
                ps.setInt(1, convB);
                ps.setString(2, MARK + "bonjour B");
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (!dbAvailable()) return;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM messages WHERE body LIKE '" + MARK + "%'");
            st.executeUpdate("DELETE FROM conversations WHERE client_identifier LIKE '" + MARK + "%'");
            st.executeUpdate("DELETE FROM order_items WHERE order_id IN (" + orderA + "," + orderB + "," + guestOrder + ")");
            st.executeUpdate("DELETE FROM orders WHERE id IN (" + orderA + "," + orderB + "," + guestOrder + ")");
            st.executeUpdate("DELETE FROM customers WHERE id IN (" + customerA + "," + customerB + ")");
        } catch (Exception ignored) {}
    }

    // ---------- Test A : commandes du compte A ----------
    @Test
    void commandesDuCompteA() throws Exception {
        assumeTrue(dbAvailable());
        List<Order> orders = new OrderService().getOrdersByCustomer(customerA);
        assertTrue(orders.stream().anyMatch(o -> o.getId() == orderA), "Commande A absente");
        assertTrue(orders.stream().noneMatch(o -> o.getId() == orderB), "Commande B visible par A !");
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId() == customerA));
    }

    // ---------- Test B : commandes du compte B ----------
    @Test
    void commandesDuCompteB() throws Exception {
        assumeTrue(dbAvailable());
        List<Order> orders = new OrderService().getOrdersByCustomer(customerB);
        assertTrue(orders.stream().anyMatch(o -> o.getId() == orderB), "Commande B absente");
        assertTrue(orders.stream().noneMatch(o -> o.getId() == orderA), "Commande A visible par B !");
    }

    // ---------- Test C : A lit la commande de B -> REFUS ----------
    @Test
    void lectureCroiseeRefusee() {
        assumeTrue(dbAvailable());
        AppException ex1 = assertThrows(AppException.class, () ->
            new OrderService().trackOrder(numberB, PHONE_B, customerA));
        assertEquals(403, ex1.getHttpStatus());
        AppException ex2 = assertThrows(AppException.class, () ->
            new OrderService().getOrderFullView(numberB, PHONE_B, customerA));
        assertEquals(403, ex2.getHttpStatus());
        // Invité avec le bon téléphone : parcours conservé (preuve téléphone).
        assertDoesNotThrow(() -> new OrderService().trackOrder(numberB, PHONE_B, null));
    }

    // ---------- Test D : A confirme la réception de B -> REFUS ----------
    @Test
    void receptionCroiseeRefusee() {
        assumeTrue(dbAvailable());
        AppException ex = assertThrows(AppException.class, () ->
            new DeliveryFlowService().confirmReceiptByCustomer(customerA, numberB));
        assertEquals(403, ex.getHttpStatus());
    }

    // ---------- Test E : compteurs messages/notifs par compte ----------
    @Test
    void compteursParCompte() throws Exception {
        assumeTrue(dbAvailable());
        ConversationService convs = new ConversationService();
        assertEquals(0, convs.unreadCountForCustomer(customerA),
            "A ne doit voir aucun non-lu (message destiné à B)");
        assertTrue(convs.unreadCountForCustomer(customerB) >= 1,
            "B doit voir son message non lu");
        assertTrue(convs.listForCustomer(customerA).stream().noneMatch(c -> c.getId() == convB));
    }

    // ---------- Test F : A lit la conversation de B -> REFUS ----------
    @Test
    void conversationCroiseeRefusee() {
        assumeTrue(dbAvailable());
        ConversationService convs = new ConversationService();
        AppException ex = assertThrows(AppException.class, () ->
            convs.getForCustomer(convB, customerA));
        assertEquals(403, ex.getHttpStatus());
        assertDoesNotThrow(() -> convs.getForCustomer(convB, customerB));
    }

    // ---------- Test G : adresses liées aux commandes du compte ----------
    @Test
    void adressesLieesAuCompte() throws Exception {
        assumeTrue(dbAvailable());
        List<Order> orders = new OrderService().getOrdersByCustomer(customerA);
        assertFalse(orders.isEmpty());
        for (Order o : orders) {
            assertNotNull(o.getAddress());
            assertFalse(o.getAddress().contains("adresse-B"),
                "Adresse de B visible par A : " + o.getAddress());
        }
    }

    // ---------- Test H : même produit, commandes séparées ----------
    @Test
    void memeProduitCommandesSeparees() throws Exception {
        assumeTrue(dbAvailable());
        assertNotEquals(orderA, orderB);
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT order_id, product_id FROM order_items WHERE order_id IN (?, ?)")) {
            ps.setInt(1, orderA);
            ps.setInt(2, orderB);
            int lignes = 0;
            boolean produitCommun = false;
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lignes++;
                    if (rs.getInt("product_id") == productId) produitCommun = true;
                }
            }
            assertEquals(2, lignes);
            assertTrue(produitCommun, "Les deux commandes doivent contenir le même produit");
        }
        List<Order> a = new OrderService().getOrdersByCustomer(customerA);
        List<Order> b = new OrderService().getOrdersByCustomer(customerB);
        assertTrue(a.stream().noneMatch(o -> o.getId() == orderB));
        assertTrue(b.stream().noneMatch(o -> o.getId() == orderA));
    }
}
