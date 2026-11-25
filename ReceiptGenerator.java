import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * The Receipt Generator screen. It logs payments against invoiced orders and generates receipts.
 * Opened from the Receipts button in MainWindow.
 */
public class ReceiptGenerator extends JFrame {
    private static final String COMPANY = "Winnie-Fred Farms";

    private static final String ORDERS_SQL =
            "SELECT o.orderID, o.customerID, o.orderDate, c.name, c.email, o.quantity, o.unitCost, "
                    + "o.totalAmount AS orderTotal, i.invoiceID, i.totalAmount AS invoiceTotal, "
                    + "COALESCE((SELECT SUM(p.amountPaid) FROM payments p WHERE p.orderID = o.orderID), 0) AS paid "
                    + "FROM orders o "
                    + "JOIN customers c ON c.customerID = o.customerID "
                    + "LEFT JOIN invoices i ON i.orderID = o.orderID "
                    + "ORDER BY o.orderID";

    private final DatabaseManager db;
    private final JTable table;
    private final DefaultTableModel model;
    private final List<OrderRecord> rowOrders = new ArrayList<>();

    private String receiptID;
    private String orderID;
    private int paymentID;
    private LocalDateTime paymentDate;
    private BigDecimal amountPaid;

    private final JTextField txtAmount = new JTextField(10);
    private final JComboBox<String> cmbMethod = new JComboBox<>(new String[]{"Cash", "Bank Transfer"});

    /** Builds the screen, makes sure the payments and receipts tables exist, and loads the orders. */
    public ReceiptGenerator(DatabaseManager db) {
        super("Winnie-Fred Farms - Receipt Generator");
        this.db = db;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        String[] columns = {"Order ID", "Customer", "Invoice Total", "Paid", "Balance", "Status"};
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        Theme.styleTable(table);
        Theme.styleField(txtAmount);
        cmbMethod.setFont(Theme.BODY);

        JButton cmdGenerate = new Theme.HoneyButton("Generate Receipt");
        JButton cmdRefresh = new Theme.HoneyButton("Refresh");
        JButton cmdClose = new Theme.HoneyButton("Close");

        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel pnlPayment = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pnlPayment.setOpaque(false);
        pnlPayment.add(Theme.label("Amount (JMD):"));
        pnlPayment.add(txtAmount);
        pnlPayment.add(Theme.label("Method:"));
        pnlPayment.add(cmbMethod);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        btnPanel.add(cmdRefresh);
        btnPanel.add(cmdGenerate);
        btnPanel.add(cmdClose);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        south.add(pnlPayment, BorderLayout.NORTH);
        south.add(btnPanel, BorderLayout.SOUTH);

        root.add(Theme.header("Receipt Generator", "Select an invoiced order, enter the payment and generate a receipt"), BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        cmdGenerate.addActionListener(new GenerateButtonListener());
        cmdRefresh.addActionListener(new RefreshButtonListener());
        cmdClose.addActionListener(new CloseButtonListener());

        db.runSql("CREATE TABLE IF NOT EXISTS payments ("
                + "paymentID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, amountPaid REAL NOT NULL, "
                + "method TEXT, paymentDate DATE NOT NULL)");
        db.runSql("CREATE TABLE IF NOT EXISTS receipts ("
                + "receiptID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, paymentID INTEGER NOT NULL, "
                + "receiptDate DATE NOT NULL)");

        loadOrders();
        setSize(850, 480);
        setLocationRelativeTo(null);
    }

    /**
     * Logs a payment against an order. It checks the method and amount, saves the payment in the
     * database, subtracts the amount from the order's balance and updates the order's balance and status.
     * Throws IllegalArgumentException for invalid input and IllegalStateException for a database problem.
     */
    public Payment logPayment(OrderRecord order, String amountEntered, String method) {
        if (method == null || method.isBlank())
           throw new IllegalArgumentException("Payment method is required.");
        BigDecimal amountPaid;

        try {
            amountPaid = new BigDecimal(amountEntered.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("Payment amount is not valid. It must be a number.");
        }

        BigDecimal balance = balanceOf(order);
        if (amountPaid.signum() <= 0)
            throw new IllegalArgumentException("Payment amount must be greater than 0.");
        if (amountPaid.compareTo(balance) > 0)
            throw new IllegalArgumentException("Payment exceeds outstanding balance.");

        int paymentID = nextPaymentID();
        Payment payment = new Payment(paymentID, String.valueOf(order.getOrderID()), amountPaid, method);

        boolean saved = db.insertRecord(
                "INSERT INTO payments (paymentID, orderID, amountPaid, method, paymentDate) VALUES (?,?,?,?,?)",
                paymentID, order.getOrderID(), amountPaid.doubleValue(), method,
                payment.getPaymentDate().toLocalDate().toString());
        if (!saved)
            throw new IllegalStateException("Database error. The payment could not be saved.");

        order.setRemainingBalance(balance.subtract(amountPaid).doubleValue());
        boolean updated = db.updateRecord("UPDATE orders SET balance = ?, orderStatus = ? WHERE orderID = ?",
                order.getRemainingBalance(), order.getOrderStatus(), order.getOrderID());
        if (!updated)
            throw new IllegalStateException("Database error. The order's balance could not be updated.");

        this.paymentID = payment.getPaymentID();
        this.orderID = payment.getOrderID();
        this.paymentDate = payment.getPaymentDate();
        this.amountPaid = payment.getAmountPaid();
        return payment;
    }

    /**
     * Builds the receipt for a payment and records it in the receipts table
     * (DocumentDistributor uses that table to find the customer's email).
     */
    public Receipt generateReceipt(Payment payment, OrderRecord order) {
        boolean saved = db.insertRecord(
                "INSERT INTO receipts (receiptID, orderID, paymentID, receiptDate) VALUES (?,?,?,?)",
                payment.getPaymentID(), order.getOrderID(), payment.getPaymentID(),
                payment.getPaymentDate().toLocalDate().toString());
        if (!saved)
            throw new IllegalStateException("The receipt could not be recorded in the database.");

        this.receiptID = "WFF-" + String.format("%05d", payment.getPaymentID());
        return new Receipt(receiptID,
                COMPANY, order.getCustomerName(), payment.getPaymentID(), String.valueOf(order.getOrderID()),
                payment.getAmountPaid(), payment.getMethod(), balanceOf(order),
                payment.getPaymentDate().toLocalDate());
    }

    /** Finds a saved payment by its ID. Returns null if there is no such payment. */
    public Payment retrievePaymentDetails(int paymentID) {
        ResultSet rs = db.retrieveRecord(
                "SELECT paymentID, orderID, amountPaid, method, paymentDate FROM payments WHERE paymentID = ?", paymentID);
        try {
            if (rs != null && rs.next()) {
                String date = rs.getString("paymentDate");
                return new Payment(rs.getInt("paymentID"), String.valueOf(rs.getInt("orderID")),
                        money(rs.getDouble("amountPaid")), rs.getString("method"),
                        LocalDate.parse(date.substring(0, 10)).atStartOfDay());
            }
        } catch (SQLException e) {
            System.err.println("Could not read the payment: " + e.getMessage());
        } finally {
            closeQuietly(rs);
        }
        return null;
    }

    /** Returns the next unused payment ID. */
    private int nextPaymentID() {
        ResultSet rs = db.retrieveRecord("SELECT COALESCE(MAX(paymentID), 0) FROM payments");
        if (rs == null)
            throw new IllegalStateException("Database error. Could not read the saved payments.");
        try {
            return rs.next() ? rs.getInt(1) + 1 : 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Database error. Could not read the saved payments.");
        } finally {
            closeQuietly(rs);
        }
    }

    /** Fills the table with each order's invoice total, amount paid, balance and status. */
    private void loadOrders() {
        model.setRowCount(0);
        rowOrders.clear();
        ResultSet rs = db.retrieveRecord(ORDERS_SQL);
        try {
            while (rs != null && rs.next()) {
                int orderID = rs.getInt("orderID");
                String name = rs.getString("name");
                if (name == null) name = "";
                boolean invoiced = rs.getInt("invoiceID") > 0;
                BigDecimal total = money(rs.getDouble("invoiceTotal"));
                BigDecimal paid = money(rs.getDouble("paid"));
                BigDecimal balance = invoiced ? total.subtract(paid).max(BigDecimal.ZERO) : BigDecimal.ZERO;

                String status;
                if (!invoiced) status = "No invoice";
                else if (balance.signum() <= 0) status = "Paid in full";
                else if (paid.signum() > 0) status = "Partially paid";
                else status = "Unpaid";

                OrderRecord order = new OrderRecord(orderID, rs.getInt("customerID"), toDate(rs.getString("orderDate")),
                        name, rs.getString("email"), rs.getInt("quantity"), rs.getDouble("unitCost"),
                        rs.getDouble("orderTotal"), balance.doubleValue(), status);
                rowOrders.add(order);
                model.addRow(new Object[]{orderID, name,
                        invoiced ? "$" + total : "-", "$" + paid,
                        invoiced ? "$" + balance : "-", status});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading orders: " + e.getMessage());
        } finally {
            closeQuietly(rs);
        }
    }

    /** Shows the receipt in a pop-up with buttons to save it as a PDF or email it to the customer. */
    private void showReceiptPopup(Receipt receipt) {
        JDialog dialog = new JDialog(this, "Receipt " + receipt.getReceiptID(), true);
        dialog.getContentPane().setBackground(Theme.CREAM);
        dialog.setLayout(new BorderLayout());
        dialog.add(Theme.header(COMPANY, "Receipt " + receipt.getReceiptID()), BorderLayout.NORTH);

        JTextArea area = new JTextArea(receipt.toText());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setForeground(Theme.DARK);
        area.setBackground(Theme.CREAM);
        area.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        dialog.add(area, BorderLayout.CENTER);

        DocumentDistributor distributor = new DocumentDistributor(receipt,
                DatabaseCustomerContactLookup.forReceipts(db), null, new File("documents"));

        JButton cmdPdf = new Theme.HoneyButton("Download PDF");
        cmdPdf.addActionListener(e -> {
            try {
                File f = distributor.exportPDF();
                JOptionPane.showMessageDialog(dialog, "PDF saved to:\n" + f.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving PDF: " + ex.getMessage());
            }
        });

        JButton cmdEmail = new Theme.HoneyButton("Email Customer");
        cmdEmail.addActionListener(e -> {
            String[] choices = {"Gmail", "Outlook", "Mail app"};
            int pick = JOptionPane.showOptionDialog(dialog, "Open the email in:", "Email Customer",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            if (pick < 0) return; // closed the dialog
            
            try {
                String to = distributor.retrieveCustomerContact(); // blank if none on file
                File pdf = distributor.exportPDF();
                EmailPortal.open(EmailPortal.Provider.values()[pick], to, distributor.subject(), distributor.bodyText(), pdf);
                JOptionPane.showMessageDialog(dialog,
                    "Your email window is open. Attach the PDF shown in the folder window, then send.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Could not open email: " + ex.getMessage());
            }
        });

        JButton cmdClose = new Theme.HoneyButton("Close");
        cmdClose.addActionListener(e -> dialog.dispose());

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(Theme.CREAM);
        btnPanel.add(cmdPdf);
        btnPanel.add(cmdEmail);
        btnPanel.add(cmdClose);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setSize(460, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** Returns the ID of the last receipt generated. */
    public String getReceiptID() { return receiptID; }

    /** Returns the order ID of the last payment logged. */
    public String getOrderID() { return orderID; }

    /** Returns the ID of the last payment logged. */
    public int getPaymentID() { return paymentID; }

    /** Returns the date and time of the last payment logged. */
    public LocalDateTime getPaymentDate() { return paymentDate; }

    /** Returns the amount of the last payment logged. */
    public BigDecimal getAmountPaid() { return amountPaid; }

    /** Shows an error message to the user. */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Returns the order's remaining balance as money with 2 decimal places. */
    private static BigDecimal balanceOf(OrderRecord order) {
        return money(order.getRemainingBalance());
    }

    /** Converts a database number into money with 2 decimal places. */
    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Converts the order date text from the database into a date. */
    private static LocalDate toDate(String text) {
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (RuntimeException e) {
            return LocalDate.now();
        }
    }

    /** Closes a query result and its statement. */
    private static void closeQuietly(ResultSet rs) {
        if (rs == null) return;
        Statement st = null;
        try { st = rs.getStatement(); } catch (SQLException ignored) { }
        try { rs.close(); } catch (SQLException ignored) { }
        try { if (st != null) st.close(); } catch (SQLException ignored) { }
    }

    /**
     * Generate button: checks the selected order, then logs the payment, updates the order and
     * saves the receipt together (all or nothing) and shows the receipt.
     */
    private class GenerateButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            int row = table.getSelectedRow();
            if (row < 0) {
                showError("Please click on an order in the table first.");
                return;
            }
            OrderRecord order = rowOrders.get(row);
            String status = (String) model.getValueAt(row, 5);

            if (status.equals("No invoice")) {
                showError("Order " + order.getOrderID() + " has no invoice yet.\nGenerate the invoice first.");
                return;
            }
            if (order.getCustomerName().trim().isEmpty()) {
                showError("Cannot generate a receipt for Order " + order.getOrderID() + ": the customer name is missing.");
                return;
            }
            if (balanceOf(order).signum() <= 0) {
                showError("Order " + order.getOrderID() + " is already paid in full.");
                return;
            }

            boolean started = db.runSql("BEGIN");
            boolean committed = false;
            Receipt receipt = null;
            try {
                Payment payment = logPayment(order, txtAmount.getText(), (String) cmbMethod.getSelectedItem());
                receipt = generateReceipt(payment, order);
                committed = !started || db.runSql("COMMIT");
                if (!committed)
                    throw new IllegalStateException("Database error. The payment could not be saved.");
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showError(ex.getMessage());
            } finally {
                if (started && !committed) db.runSql("ROLLBACK");
            }

            loadOrders();
            if (committed && receipt != null) {
                txtAmount.setText("");
                showReceiptPopup(receipt);
            }
        }
    }

    /** Refresh button: reloads the orders table. */
    private class RefreshButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { loadOrders(); }
    }

    /** Close button: closes the screen. */
    private class CloseButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { dispose(); }
    }
}