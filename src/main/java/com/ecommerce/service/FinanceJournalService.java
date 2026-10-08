package com.ecommerce.service;

import com.ecommerce.dao.DisputeDAO;
import com.ecommerce.dao.MonetizationDAO;
import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.PayoutDAO;
import com.ecommerce.dao.RefundDAO;
import com.ecommerce.model.Dispute;
import com.ecommerce.model.LedgerEntry;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.model.Refund;
import com.ecommerce.model.SupplierPayment;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Journal financier unifié (lecture seule) : DEPOSIT + PAYOUT + REFUND +
 * COMMISSION. Aucune nouvelle table d'écriture : chaque domaine reste
 * la source de vérité (payments, supplier_payments, refunds,
 * financial_ledger) ; ce service les assemble sans jamais les dupliquer.
 */
public class FinanceJournalService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final PayoutDAO payoutDAO = new PayoutDAO();
    private final RefundDAO refundDAO = new RefundDAO();
    private final MonetizationDAO monetizationDAO = new MonetizationDAO();
    private final DisputeDAO disputeDAO = new DisputeDAO();

    /** Historique financier complet d'une commande (client + admin). */
    public Map<String, Object> forOrder(int orderId) throws SQLException {
        Order o = orderDAO.findById(orderId);
        List<Map<String, Object>> entries = new ArrayList<>();

        Payment latest = paymentDAO.findByOrderId(orderId);
        if (latest != null) {
            entries.add(depositEntry(latest));
        }
        try {
            for (LedgerEntry e : monetizationDAO.findByOrder(orderId)) {
                entries.add(ledgerEntry(e));
            }
        } catch (SQLException ignored) {}
        SupplierPayment sp = payoutDAO.findByOrderId(orderId);
        if (sp != null) entries.add(payoutEntry(sp));
        Refund r = refundDAO.findLatestByOrderId(orderId);
        if (r != null) entries.add(refundEntry(r));
        Dispute d = disputeDAO.findByOrderId(orderId);
        if (d != null) entries.add(disputeEntry(d));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("orderNumber", o == null ? null : o.getOrderNumber());
        out.put("orderStatus", o == null ? null : o.getStatus());
        out.put("orderTotal", o == null ? null : o.getTotal());
        out.put("entries", entries);
        return out;
    }

    /** Versements + écritures d'un fournisseur (espace vendeur). */
    public Map<String, Object> forSupplier(int supplierId) throws SQLException {
        List<Map<String, Object>> payouts = new ArrayList<>();
        for (SupplierPayment sp : payoutDAO.findAutoBySupplier(supplierId)) {
            payouts.add(payoutEntry(sp));
        }
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (LedgerEntry e : monetizationDAO.findBySupplier(supplierId, 50)) {
            ledger.add(ledgerEntry(e));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("supplierId", supplierId);
        out.put("payouts", payouts);
        out.put("ledger", ledger);
        return out;
    }

    private static Map<String, Object> depositEntry(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "DEPOSIT");
        m.put("provider", p.getProvider());
        m.put("providerTransactionId", p.getProviderTransactionId());
        m.put("amount", p.getAmount());
        m.put("fee", 0);
        m.put("currency", p.getCurrency());
        m.put("status", p.getStatus());
        m.put("failureReason", p.getFailureReason());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private static Map<String, Object> payoutEntry(SupplierPayment sp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "PAYOUT");
        m.put("provider", sp.getProvider());
        m.put("providerTransactionId", sp.getPayoutId());
        m.put("orderId", sp.getOrderId());
        m.put("reference", sp.getReference());
        m.put("amount", sp.getAmount());
        m.put("grossAmount", sp.getGrossAmount());
        m.put("commission", sp.getCommission());
        m.put("fee", sp.getPawapayFee());
        m.put("currency", "XAF");
        m.put("status", sp.getStatus());
        m.put("failureReason", sp.getFailureReason());
        m.put("createdAt", sp.getCreatedAt());
        return m;
    }

    private static Map<String, Object> refundEntry(Refund r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "REFUND");
        m.put("provider", "PAWAPAY");
        m.put("providerTransactionId", r.getRefundId());
        m.put("amount", r.getAmount());
        m.put("fee", 0);
        m.put("currency", r.getCurrency());
        m.put("status", r.getStatus());
        m.put("failureReason", r.getFailureReason());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private static Map<String, Object> ledgerEntry(LedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "REVERSAL".equals(e.getKind()) ? "COMMISSION_REVERSAL" : "COMMISSION");
        m.put("reference", e.getReference());
        m.put("orderId", e.getOrderId());
        m.put("amount", e.getSupplierEarnings());
        m.put("grossAmount", e.getGross());
        m.put("commission", e.getCommission());
        m.put("commissionBps", e.getCommissionBps());
        m.put("fee", e.getFixedFee());
        m.put("currency", "XAF");
        m.put("status", e.getStatus());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    private static Map<String, Object> disputeEntry(Dispute d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "DISPUTE");
        m.put("reason", d.getReason());
        m.put("status", d.getStatus());
        m.put("resolutionNote", d.getResolutionNote());
        m.put("createdAt", d.getCreatedAt());
        return m;
    }
}
