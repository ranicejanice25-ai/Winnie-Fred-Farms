import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InvoiceGenerator extends JFrame {

    private int invoiceID;
    private int orderID;
    private Date invoiceDate;
    private float totalAmount;
    private int documentID;

    private final DatabaseManager db;
    private final List<Invoice> cache = new ArrayList<>();
    private boolean usingCache = false;
    private boolean errorPopupShown = false;
    private String lastError = "";

    private final JTextField txtSearch      = new JTextField(16);
    private final JTextField txtInvoiceDate = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField txtCustomer    = new JTextField(16);
    private final JTextField txtOrderTotal  = new JTextField(10);
    private final JTextField txtOrderStatus = new JTextField(10);

    private final JButton cmdLookup   = new Theme.HoneyButton("Search");
    private final JButton cmdClear    = new Theme.HoneyButton("Clear");
    private final JButton cmdRefresh  = new Theme.HoneyButton("Refresh");
    private final JButton cmdGenerate = new Theme.HoneyButton("Generate Invoice");
    private final JButton cmdClose    = new Theme.HoneyButton("Close");

    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Order ID", "Customer", "Order Date", "Qty", "Unit Cost", "Total", "Invoice"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public InvoiceGenerator(DatabaseManager db) {
        super("Winnie-Fred Farms - Invoice Generator");
        this.db = db;
        buildGui();
        refreshTable(retrieveAllOrders());
    }

    public List<Invoice> retrieveAllOrders() {
        System.out.println("[InvoiceGenerator] Loading orders…");
        List<Invoice> results = new ArrayList<>();
        try (ResultSet rs = db.retrieveRecord(
                "SELECT o.orderID, o.orderDate, o.orderStatus, o.quantity, "
              + "COALESCE((SELECT unitPrice FROM inventory LIMIT 1), o.unitCost) as unitCost, "
              + "c.customerID, c.name, i.invoiceID, i.invoiceDate "
              + "FROM orders o "
              + "LEFT JOIN customers c ON o.customerID = c.customerID "
              + "LEFT JOIN invoices i ON o.orderID = i.orderID "
              + "ORDER BY o.orderID")) {
            if (rs == null) {
                usingCache = true;
                lastError = db.getLastError();
                return new ArrayList<>(cache);
            }
            while (rs.next()) {
                int oid = rs.getInt("orderID");
                int qty = rs.getInt("quantity");
                float unitCost = rs.getFloat("unitCost");
                String name = rs.getString("name");
                if (name == null) name = "";
                float total = calculateTotal(qty, unitCost);
                int invId = rs.getInt("invoiceID"); // 0 when no invoice
                
                java.util.Date invDate = getDateSafe(rs, "invoiceDate");
                java.util.Date ordDate = getDateSafe(rs, "orderDate");
                
                results.add(new Invoice(invId, oid, invDate, total, qty, unitCost,
                        name, rs.getString("orderStatus"), ordDate));
            }
        } catch (SQLException e) {
            System.err.println("Could not load orders: " + e.getMessage());
            usingCache = true;
            lastError = e.getMessage();
            return new ArrayList<>(cache);
        }
        cache.clear();
        cache.addAll(results);
        usingCache = false;
        return results;
    }

    public Invoice retrieveOrderDetails(int orderID) {
        if (orderID <= 0) return null;
        int customerID = -1;
        String status = "";
        int qty = 0;
        float unitCost = 0f;
        java.util.Date ordDate = null;
        try (ResultSet rs = db.retrieveRecord(
                "SELECT customerID, orderStatus, quantity, "
              + "COALESCE((SELECT unitPrice FROM inventory LIMIT 1), unitCost) as unitCost, "
              + "orderDate FROM orders WHERE orderID = ?",
                orderID)) {
            if (rs == null || !rs.next()) return null;
            customerID = rs.getInt("customerID");
            status     = rs.getString("orderStatus");
            qty        = rs.getInt("quantity");
            unitCost   = rs.getFloat("unitCost");
            ordDate    = getDateSafe(rs, "orderDate");
        } catch (SQLException e) {
            System.err.println("Could not retrieve order " + orderID + ": " + e.getMessage());
            return null;
        }
        float calculatedTotal = calculateTotal(qty, unitCost);
        String name = lookupCustomerName(customerID);
        return new Invoice(0, orderID, null, calculatedTotal, qty, unitCost, name, status, ordDate);
    }

    private java.util.Date getDateSafe(ResultSet rs, String column) {
        try {
            String val = rs.getString(column);
            if (val == null || val.trim().isEmpty()) return null;
            if (val.length() > 10) val = val.substring(0, 10);
            return java.sql.Date.valueOf(val);
        } catch (Exception e) {
            System.err.println("Could not parse date for column " + column + ": " + e.getMessage());
            return null;
        }
    }

    public boolean generateInvoice(int orderID, Date invoiceDate) {
        if (!db.isConnected() || orderID <= 0 || invoiceDate == null) return false;
        Invoice details = retrieveOrderDetails(orderID);
        if (details == null) return false;
        if (details.getCustomerName() == null || details.getCustomerName().trim().isEmpty()) return false;
        if (invoiceExistsForOrder(orderID)) return false;

        int newId = getMaxInvoiceID() + 1;
        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(invoiceDate);
        
        boolean ok = db.insertRecord(
                "INSERT INTO invoices (invoiceID, orderID, invoiceDate, totalAmount) VALUES (?,?,?,?)",
                newId, orderID, dateStr, details.getTotalAmount());
        if (ok) {
            this.invoiceID = newId;
            this.orderID = orderID;
            this.invoiceDate = invoiceDate;
            this.totalAmount = details.getTotalAmount();
            this.documentID = 0;
        }
        return ok;
    }

    private boolean invoiceExistsForOrder(int orderID) {
        try (ResultSet rs = db.retrieveRecord("SELECT 1 FROM invoices WHERE orderID = ?", orderID)) {
            return rs != null && rs.next();
        } catch (SQLException e) { return false; }
    }

    private int getMaxInvoiceID() {
        try (ResultSet rs = db.retrieveRecord("SELECT MAX(invoiceID) FROM invoices")) {
            if (rs != null && rs.next()) {
                int max = rs.getInt(1);
                if (!rs.wasNull()) return max;
            }
        } catch (SQLException ignored) { }
        return 0;
    }

    private float calculateTotal(int qty, float unitCost) {
        BigDecimal bd = BigDecimal.valueOf(qty * unitCost);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    private String lookupCustomerName(int customerID) {
        try (ResultSet rs = db.retrieveRecord("SELECT name FROM customers WHERE customerID = ?", customerID)) {
            if (rs != null && rs.next()) {
                String name = rs.getString("name");
                return name == null ? "" : name.trim();
            }
        } catch (SQLException ignored) { }
        return "";
    }

    private Date parseInvoiceDate(String text) {
        try { return java.sql.Date.valueOf(LocalDate.parse(text)); }
        catch (DateTimeParseException ex) {
            showError("Invoice date must be a valid date in the format yyyy-MM-dd (e.g. 2026-12-12).");
            return null;
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void refreshTable(List<Invoice> orders) {
        model.setRowCount(0);
        for (Invoice inv : orders) {
            String displayName = inv.getCustomerName().isEmpty() ? "[Missing Name]" : inv.getCustomerName();
            String dateStr = inv.getOrderDate() != null ? inv.getOrderDate().toString() : "";
            String invoiceStatus = inv.getInvoiceID() > 0 ? "Invoiced (#" + inv.getInvoiceID() + ")" : "Pending";
            model.addRow(new Object[]{
                    inv.getOrderID(), displayName, dateStr, inv.getQuantity(),
                    String.format("$%.2f", inv.getUnitCost()),
                    String.format("$%.2f", inv.getTotalAmount()),
                    invoiceStatus});
        }
        if (usingCache) {
            String msg = lastError.isEmpty() ? "unknown error" : lastError;
            lblStatus.setText("DB ERROR: " + msg + " (showing cached)");
            lblStatus.setForeground(Theme.DANGER);
            if (!errorPopupShown) {
                errorPopupShown = true;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Could not load orders.\n\nDB error: " + msg + "\n\nSee console for details.",
                        "Database error", JOptionPane.ERROR_MESSAGE));
            }
        } else if (orders.isEmpty()) {
            lblStatus.setText("0 order(s) loaded — orders table is empty.");
            lblStatus.setForeground(Theme.BROWN);
        } else {
            lblStatus.setText(orders.size() + " order(s) loaded.");
            lblStatus.setForeground(Theme.BROWN);
        }
    }

    private void buildGui() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        for (JTextField f : new JTextField[]{txtSearch, txtInvoiceDate, txtCustomer, txtOrderTotal, txtOrderStatus}) {
            Theme.styleField(f);
        }
        for (JTextField f : new JTextField[]{txtCustomer, txtOrderTotal, txtOrderStatus}) {
            f.setEditable(false);
            f.setBackground(new Color(0xFFF1D0));
        }
        Theme.styleTable(table);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                int row = table.getSelectedRow();
                if (row < 0) return;
                int oid = (Integer) model.getValueAt(row, 0);
                selectedRowToDetails(row);
                if (evt.getClickCount() == 2) openInvoiceForOrder(oid);
            }
        });

        lblStatus.setFont(Theme.BODY);
        lblStatus.setForeground(Theme.BROWN);

        JPanel pnlSearch = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        pnlSearch.setOpaque(false);
        pnlSearch.add(Theme.label("Search (Order ID or Customer):"));
        pnlSearch.add(txtSearch);
        pnlSearch.add(cmdLookup); pnlSearch.add(cmdClear); pnlSearch.add(cmdRefresh);

        JPanel pnlDetails = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlDetails.setOpaque(false);
        pnlDetails.add(Theme.label("Customer:"));     pnlDetails.add(txtCustomer);
        pnlDetails.add(Theme.label("Order Total:"));  pnlDetails.add(txtOrderTotal);
        pnlDetails.add(Theme.label("Order Status:")); pnlDetails.add(txtOrderStatus);

        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        pnlActions.setOpaque(false);
        pnlActions.add(Theme.label("Invoice Date:"));
        pnlActions.add(txtInvoiceDate);
        pnlActions.add(cmdGenerate);

        JPanel pnlInput = new JPanel();
        pnlInput.setLayout(new BoxLayout(pnlInput, BoxLayout.Y_AXIS));
        pnlInput.setOpaque(false);
        pnlInput.add(pnlSearch); pnlInput.add(pnlDetails); pnlInput.add(pnlActions);
        pnlInput.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setOpaque(false);
        pnlTop.add(Theme.header("Invoice Generator", "Double-click an order to view or create its invoice"),
                BorderLayout.NORTH);
        pnlTop.add(pnlInput, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Theme.CREAM);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlCommand.setOpaque(false); pnlCommand.add(cmdClose);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.setOpaque(false);
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        pnlBottom.add(lblStatus, BorderLayout.WEST);
        pnlBottom.add(pnlCommand, BorderLayout.EAST);

        root.add(pnlTop, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(pnlBottom, BorderLayout.SOUTH);

        cmdLookup.addActionListener(e -> applySearch());
        cmdClear.addActionListener(e -> { txtSearch.setText(""); errorPopupShown = false; refreshTable(retrieveAllOrders()); });
        cmdRefresh.addActionListener(e -> { errorPopupShown = false; refreshTable(retrieveAllOrders()); });
        cmdGenerate.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { showError("Select an order from the table first."); return; }
            openInvoiceForOrder((Integer) model.getValueAt(row, 0));
        });
        cmdClose.addActionListener(e -> dispose());
        txtSearch.addActionListener(e -> applySearch());

        setSize(980, 580);
        setLocationRelativeTo(null);
    }

    private void applySearch() {
        String query = txtSearch.getText().trim();
        if (query.isEmpty()) { refreshTable(retrieveAllOrders()); return; }
        List<Invoice> all = retrieveAllOrders();
        List<Invoice> filtered = new ArrayList<>();
        boolean byId = query.matches("\\d+");
        for (Invoice inv : all) {
            boolean match = byId
                    ? String.valueOf(inv.getOrderID()).equals(query)
                    : inv.getCustomerName().toLowerCase().contains(query.toLowerCase());
            if (match) filtered.add(inv);
        }
        refreshTable(filtered);
        if (filtered.isEmpty()) lblStatus.setText("No orders match: " + query);
    }

    private void selectedRowToDetails(int row) {
        int oid = (Integer) model.getValueAt(row, 0);
        String cust = (String) model.getValueAt(row, 1);
        String totalStr = model.getValueAt(row, 5).toString();
        String status = "";
        for (Invoice inv : cache) {
            if (inv.getOrderID() == oid) { status = inv.getOrderStatus(); break; }
        }
        txtCustomer.setText(cust);
        txtOrderTotal.setText(totalStr);
        txtOrderStatus.setText(status);
    }

    private void openInvoiceForOrder(int oid) {
        Invoice details = retrieveOrderDetails(oid);
        if (details == null) { showError("Could not load order #" + oid + "."); return; }
        if (details.getCustomerName() == null || details.getCustomerName().trim().isEmpty()) {
            showError("Cannot invoice Order " + oid + ":\nCustomer name is missing.\n\n"
                    + "Please update the customer information first.");
            return;
        }
        for (Invoice inv : retrieveAllOrders()) {
            if (inv.getOrderID() == oid && inv.getInvoiceID() > 0) {
                showInvoicePopup(inv, false);
                return;
            }
        }
        showInvoicePopup(details, true);
    }

    private void showInvoicePopup(Invoice inv, boolean isPreview) {
        JDialog dialog = new JDialog(this,
                isPreview ? "Invoice Preview - Order #" + inv.getOrderID() : "Invoice #" + inv.getInvoiceID(),
                true);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(Theme.CREAM);

        JPanel pnlBody = new JPanel();
        pnlBody.setLayout(new BoxLayout(pnlBody, BoxLayout.Y_AXIS));
        pnlBody.setBackground(Theme.CREAM);
        pnlBody.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel lblTitle = new JLabel(isPreview ? "INVOICE PREVIEW" : "WINNIE-FRED FARMS");
        lblTitle.setFont(Theme.TITLE); lblTitle.setForeground(Theme.DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Crescent, Horsefording Bonny Gate, St. Mary");
        lblSub.setFont(Theme.BODY); lblSub.setForeground(Theme.BROWN);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        pnlBody.add(lblTitle); pnlBody.add(lblSub);
        pnlBody.add(Box.createRigidArea(new Dimension(0, 20)));

        String custName = inv.getCustomerName().isEmpty() ? "[No Name Provided]" : inv.getCustomerName();
        JLabel lblCustomer = new JLabel("<html><b>Bill To:</b><br><div style='width: 250px;'>" + custName + "</div></html>");
        lblCustomer.setFont(Theme.BODY);

        String dateBlock = isPreview
                ? "<b>Order Date:</b> " + (inv.getOrderDate() != null ? inv.getOrderDate() : "-")
                  + "<br><b>Order Ref:</b> " + inv.getOrderID()
                  + "<br><b>Status:</b> " + (inv.getOrderStatus().isEmpty() ? "-" : inv.getOrderStatus())
                : "<b>Date:</b> " + (inv.getInvoiceDate() != null ? inv.getInvoiceDate() : "-") 
                  + "<br><b>Order Ref:</b> " + inv.getOrderID();
                  
        JLabel lblDate = new JLabel("<html>" + dateBlock + "</html>");
        lblDate.setFont(Theme.BODY);

        JPanel pnlDetails = new JPanel(new BorderLayout());
        pnlDetails.setBackground(Theme.CREAM);
        pnlDetails.add(lblCustomer, BorderLayout.WEST);
        pnlDetails.add(lblDate, BorderLayout.EAST);
        pnlBody.add(pnlDetails);
        pnlBody.add(Box.createRigidArea(new Dimension(0, 20)));

        JPanel pnlBreakdown = new JPanel(new GridLayout(2, 3, 10, 5));
        pnlBreakdown.setBackground(Theme.CREAM);
        pnlBreakdown.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BROWN),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));
        pnlBreakdown.add(new JLabel("<html><b>Description</b></html>"));
        pnlBreakdown.add(new JLabel("<html><b>Quantity</b></html>", SwingConstants.RIGHT));
        pnlBreakdown.add(new JLabel("<html><b>Unit Cost</b></html>", SwingConstants.RIGHT));
        pnlBreakdown.add(new JLabel("Honey (Bottles)"));
        pnlBreakdown.add(new JLabel(String.valueOf(inv.getQuantity()), SwingConstants.RIGHT));
        pnlBreakdown.add(new JLabel(String.format("$%.2f", inv.getUnitCost()), SwingConstants.RIGHT));
        pnlBody.add(pnlBreakdown);

        JPanel pnlTotal = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pnlTotal.setBackground(Theme.CREAM);
        pnlTotal.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        JLabel lblTotal = new JLabel(String.format("TOTAL: $%.2f", inv.getTotalAmount()));
        lblTotal.setFont(Theme.BODY_BOLD.deriveFont(18f));
        lblTotal.setForeground(Theme.DARK);
        pnlTotal.add(lblTotal);
        pnlBody.add(pnlTotal);

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        pnlButtons.setBackground(Theme.CREAM);

        if (isPreview) {
            JButton cmdGen = new Theme.HoneyButton("Generate Invoice");
            cmdGen.addActionListener(e -> {
                Date day = parseInvoiceDate(txtInvoiceDate.getText().trim());
                if (day == null) return;
                dialog.dispose();
                if (generateInvoice(inv.getOrderID(), day)) {
                    refreshTable(retrieveAllOrders());
                    lblStatus.setText("Invoice generated for order " + inv.getOrderID() + ".");
                    
                    Invoice newInvoice = new Invoice(
                        this.invoiceID, 
                        inv.getOrderID(), 
                        day, 
                        inv.getTotalAmount(), 
                        inv.getQuantity(), 
                        inv.getUnitCost(), 
                        inv.getCustomerName(), 
                        inv.getOrderStatus(), 
                        inv.getOrderDate()
                    );
                    showInvoicePopup(newInvoice, false);
                    
                } else {
                    showError("Could not generate the invoice. Check the database connection.");
                }
            });
            pnlButtons.add(cmdGen);
        } else {
            JButton cmdPdf = new Theme.HoneyButton("Download PDF");
            cmdPdf.addActionListener(e -> JOptionPane.showMessageDialog(dialog,
                    "PDF Export functionality will be handled by the DocumentDistributor module.",
                    "Coming Soon", JOptionPane.INFORMATION_MESSAGE));
            pnlButtons.add(cmdPdf);
        }

        JButton cmdClose = new Theme.HoneyButton("Close");
        cmdClose.addActionListener(e -> dialog.dispose());
        pnlButtons.add(cmdClose);

        dialog.add(pnlBody, BorderLayout.CENTER);
        dialog.add(pnlButtons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public int getInvoiceID() { return invoiceID; }
    public int getOrderID() { return orderID; }
    public Date getInvoiceDate() { return invoiceDate; }
    public float getTotalAmount() { return totalAmount; }
    public int getDocumentID() { return documentID; }
}