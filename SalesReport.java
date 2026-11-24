import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reporting layer (SRS 1.2.6 / SDS "SalesReport"). Reads orders, invoices, payments and
 * inventory through DatabaseManager and produces:
 *   - the Outstanding Payment Report (every order with an unpaid invoice), and
 *   - the Sales Report for a chosen period.
 * This class has no GUI code so it can be tested on its own; SalesReportWindow displays it.
 */
public class SalesReport {

    public static final String COMPANY = "Winnie-Fred Farms";

    private final DatabaseManager db;

    public SalesReport(DatabaseManager db) {
        this.db = db;
        // Payments are not stored anywhere else yet, so make sure the table exists.
        db.runSql("CREATE TABLE IF NOT EXISTS payments ("
                + "paymentID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, amountPaid REAL NOT NULL, "
                + "method TEXT, paymentDate DATE NOT NULL)");
    }

    // ------------------------------------------------------------------
    //  Result holders
    // ------------------------------------------------------------------

    /** One row of the outstanding payment report. */
    public static class OutstandingItem {
        private final String customerName;
        private final int orderID;
        private final int invoiceID;
        private final int quantity;
        private final BigDecimal invoiceTotal;
        private final BigDecimal amountPaid;
        private final BigDecimal amountOutstanding;

        public OutstandingItem(String customerName, int orderID, int invoiceID, int quantity,
                               BigDecimal invoiceTotal, BigDecimal amountPaid, BigDecimal amountOutstanding) {
            this.customerName = customerName == null ? "" : customerName.trim();
            this.orderID = orderID;
            this.invoiceID = invoiceID;
            this.quantity = quantity;
            this.invoiceTotal = invoiceTotal;
            this.amountPaid = amountPaid;
            this.amountOutstanding = amountOutstanding;
        }

        public String getCustomerName() { return customerName; }
        public int getOrderID() { return orderID; }
        public int getInvoiceID() { return invoiceID; }
        public int getQuantity() { return quantity; }
        public BigDecimal getInvoiceTotal() { return invoiceTotal; }
        public BigDecimal getAmountPaid() { return amountPaid; }
        public BigDecimal getAmountOutstanding() { return amountOutstanding; }
    }

    /** The calculated values of a sales report. */
    public static class Summary {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int orderCount;
        private final int bottlesSold;
        private final BigDecimal salesValue;
        private final BigDecimal amountCollected;
        private final BigDecimal amountOutstanding;
        private final int stockQuantity;
        private final BigDecimal stockValue;

        public Summary(LocalDate startDate, LocalDate endDate, int orderCount, int bottlesSold,
                       BigDecimal salesValue, BigDecimal amountCollected, BigDecimal amountOutstanding,
                       int stockQuantity, BigDecimal stockValue) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.orderCount = orderCount;
            this.bottlesSold = bottlesSold;
            this.salesValue = salesValue;
            this.amountCollected = amountCollected;
            this.amountOutstanding = amountOutstanding;
            this.stockQuantity = stockQuantity;
            this.stockValue = stockValue;
        }

        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public int getOrderCount() { return orderCount; }
        public int getBottlesSold() { return bottlesSold; }
        public BigDecimal getSalesValue() { return salesValue; }
        public BigDecimal getAmountCollected() { return amountCollected; }
        public BigDecimal getAmountOutstanding() { return amountOutstanding; }
        public int getStockQuantity() { return stockQuantity; }
        public BigDecimal getStockValue() { return stockValue; }
        /** True when nothing at all was found for the period (orders or payments). */
        public boolean isEmpty() { return orderCount == 0 && amountCollected.signum() == 0; }
    }

    private static class PaymentRow {
        final int orderID;
        final BigDecimal amount;
        final LocalDate date;
        PaymentRow(int orderID, BigDecimal amount, LocalDate date) {
            this.orderID = orderID; this.amount = amount; this.date = date;
        }
    }

    // ------------------------------------------------------------------
    //  Outstanding payment report (SR-2, SR-3)
    // ------------------------------------------------------------------

    /**
     * Every order with an unpaid invoice: customer name, order reference, order quantity and the
     * amount still outstanding (invoice total minus payments logged against the order).
     * Fully paid invoices and cancelled orders are left out.
     */
    public List<OutstandingItem> generateOutstandingReport() {
        Map<Integer, BigDecimal> paidByOrder = paidByOrder(loadPayments());
        List<OutstandingItem> items = new ArrayList<>();
        try (ResultSet rs = db.retrieveRecord(
                "SELECT i.invoiceID, i.orderID, i.totalAmount, o.quantity, o.orderStatus, c.name "
              + "FROM invoices i "
              + "LEFT JOIN orders o ON i.orderID = o.orderID "
              + "LEFT JOIN customers c ON o.customerID = c.customerID "
              + "ORDER BY i.orderID, i.invoiceID")) {
            if (rs == null) throw new IllegalStateException(readError("invoices"));
            while (rs.next()) {
                if (isCancelled(rs.getString("orderStatus"))) continue;
                int orderID = rs.getInt("orderID");
                BigDecimal total = money(rs.getDouble("totalAmount"));
                BigDecimal paid = paidByOrder.getOrDefault(orderID, BigDecimal.ZERO);
                BigDecimal due = total.subtract(paid);
                if (due.signum() <= 0) continue; // paid in full
                items.add(new OutstandingItem(rs.getString("name"), orderID, rs.getInt("invoiceID"),
                        rs.getInt("quantity"), total, paid, due));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read invoices: " + e.getMessage(), e);
        }
        return items;
    }

    /** Sum of the amounts outstanding, rounded to 2 decimal places. */
    public BigDecimal totalOutstanding(List<OutstandingItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OutstandingItem it : items) sum = sum.add(it.getAmountOutstanding());
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    //  Sales report (SR-4, SR-5)
    // ------------------------------------------------------------------

    /** Returns null if the period is usable, otherwise a message describing the problem. */
    public static String validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null) return "Please enter both a start date and an end date.";
        if (end.isBefore(start)) {
            return "Invalid period: the end date (" + end + ") is earlier than the start date (" + start + ").";
        }
        return null;
    }

    /**
     * Sales report for start..end (both inclusive).
     *   bottles sold / sales value : orders dated in the period (cancelled orders ignored)
     *   amount collected           : payments dated in the period
     *   amount outstanding         : what is still unpaid on those same orders
     *   stock quantity / value     : current inventory (bottles in stock x unit price)
     *
     * @throws IllegalArgumentException if the period is invalid (no report is generated)
     * @throws IllegalStateException    if the database could not be read
     */
    public Summary generateSalesReport(LocalDate start, LocalDate end) {
        String problem = validatePeriod(start, end);
        if (problem != null) throw new IllegalArgumentException(problem);

        List<PaymentRow> payments = loadPayments();
        Map<Integer, BigDecimal> paidByOrder = paidByOrder(payments);

        BigDecimal collected = BigDecimal.ZERO;
        for (PaymentRow p : payments) {
            if (inPeriod(p.date, start, end)) collected = collected.add(p.amount);
        }

        int orderCount = 0;
        int bottles = 0;
        BigDecimal salesValue = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        Set<Integer> seen = new HashSet<>();

        // Same price rule as InvoiceGenerator: current inventory price, else the order's own unit cost.
        try (ResultSet rs = db.retrieveRecord(
                "SELECT o.orderID, o.orderDate, o.orderStatus, o.quantity, "
              + "COALESCE((SELECT unitPrice FROM inventory LIMIT 1), o.unitCost) AS unitCost, "
              + "i.totalAmount AS invoiceTotal "
              + "FROM orders o LEFT JOIN invoices i ON o.orderID = i.orderID "
              + "ORDER BY o.orderID")) {
            if (rs == null) throw new IllegalStateException(readError("orders"));
            while (rs.next()) {
                int orderID = rs.getInt("orderID");
                if (!seen.add(orderID)) continue; // an order is only counted once
                if (isCancelled(rs.getString("orderStatus"))) continue;
                if (!inPeriod(readDate(rs, "orderDate"), start, end)) continue;

                int qty = rs.getInt("quantity");
                double invoiceTotal = rs.getDouble("invoiceTotal");
                boolean invoiced = !rs.wasNull();
                BigDecimal value = invoiced
                        ? money(invoiceTotal)
                        : BigDecimal.valueOf(rs.getDouble("unitCost"))
                                .multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

                orderCount++;
                bottles += qty;
                salesValue = salesValue.add(value);
                BigDecimal due = value.subtract(paidByOrder.getOrDefault(orderID, BigDecimal.ZERO));
                if (due.signum() > 0) outstanding = outstanding.add(due);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read orders: " + e.getMessage(), e);
        }

        int stockQty = 0;
        BigDecimal stockValue = BigDecimal.ZERO;
        try (ResultSet rs = db.retrieveRecord("SELECT quantity, unitPrice FROM inventory")) {
            if (rs == null) throw new IllegalStateException(readError("inventory"));
            while (rs.next()) {
                int q = rs.getInt("quantity");
                stockQty += q;
                stockValue = stockValue.add(
                        BigDecimal.valueOf(rs.getDouble("unitPrice")).multiply(BigDecimal.valueOf(q)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read inventory: " + e.getMessage(), e);
        }

        return new Summary(start, end, orderCount, bottles,
                salesValue.setScale(2, RoundingMode.HALF_UP),
                collected.setScale(2, RoundingMode.HALF_UP),
                outstanding.setScale(2, RoundingMode.HALF_UP),
                stockQty, stockValue.setScale(2, RoundingMode.HALF_UP));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private List<PaymentRow> loadPayments() {
        List<PaymentRow> rows = new ArrayList<>();
        try (ResultSet rs = db.retrieveRecord("SELECT orderID, amountPaid, paymentDate FROM payments")) {
            if (rs == null) throw new IllegalStateException(readError("payments"));
            while (rs.next()) {
                rows.add(new PaymentRow(rs.getInt("orderID"), money(rs.getDouble("amountPaid")),
                        readDate(rs, "paymentDate")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read payments: " + e.getMessage(), e);
        }
        return rows;
    }

    private static Map<Integer, BigDecimal> paidByOrder(List<PaymentRow> payments) {
        Map<Integer, BigDecimal> map = new HashMap<>();
        for (PaymentRow p : payments) map.merge(p.orderID, p.amount, BigDecimal::add);
        return map;
    }

    private static boolean inPeriod(LocalDate d, LocalDate start, LocalDate end) {
        return d != null && !d.isBefore(start) && !d.isAfter(end);
    }

    private static boolean isCancelled(String status) {
        return status != null && status.trim().toUpperCase().startsWith("CANCEL");
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Reads a date stored as text ("2026-11-10"), a timestamp, or epoch milliseconds. */
    private static LocalDate readDate(ResultSet rs, String column) {
        try {
            String v = rs.getString(column);
            if (v == null) return null;
            v = v.trim();
            if (v.isEmpty()) return null;
            if (v.matches("\\d{9,}")) {
                return Instant.ofEpochMilli(Long.parseLong(v)).atZone(ZoneId.systemDefault()).toLocalDate();
            }
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (Exception e) {
            System.err.println("[SalesReport] Could not read date in column " + column + ": " + e.getMessage());
            return null;
        }
    }

    private String readError(String what) {
        return "Could not read " + what + " from the database: " + db.getLastError();
    }
}
