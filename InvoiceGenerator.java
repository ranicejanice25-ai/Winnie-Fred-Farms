import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Date;

public class InvoiceGenerator extends JFrame {

    private int invoiceID;
    private int orderID;
    private Date invoiceDate;
    private float totalAmount;

    private final DatabaseManager db;
    private final JTable table;
    private final DefaultTableModel model;
    
    private final JButton cmdGenerate = new Theme.HoneyButton("Generate Invoice");
    private final JButton cmdRefresh = new Theme.HoneyButton("Refresh");
    private final JButton cmdClose = new Theme.HoneyButton("Close");

    public InvoiceGenerator(DatabaseManager db) {
        super("Winnie-Fred Farms - Invoice Generator");
        this.db = db;
        
        String[] columns = {"Order ID", "Customer", "Date", "Qty", "Unit Cost", "Total", "Status"};
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        Theme.styleTable(table);

        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        btnPanel.add(cmdRefresh);
        btnPanel.add(cmdGenerate);
        btnPanel.add(cmdClose);

        root.add(Theme.header("Invoice Generator", "Select an order to generate an invoice"), BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(btnPanel, BorderLayout.SOUTH);
        
        cmdGenerate.addActionListener(new GenerateButtonListener());
        cmdRefresh.addActionListener(new RefreshButtonListener());
        cmdClose.addActionListener(new CloseButtonListener());

        retrieveOrderDetails();
        setSize(850, 450);
        setLocationRelativeTo(null);
    }

    public void retrieveOrderDetails() {
        model.setRowCount(0);
        try {
            ResultSet rs = db.retrieveRecord(
                "SELECT o.orderID, c.name, o.orderDate, o.quantity, " +
                "COALESCE((SELECT unitPrice FROM inventory LIMIT 1), o.unitCost) as unitCost, " +
                "o.totalAmount, i.invoiceID " +
                "FROM orders o " +
                "JOIN customers c ON o.customerID = c.customerID " +
                "LEFT JOIN invoices i ON o.orderID = i.orderID " +
                "ORDER BY o.orderID"
            );

            while (rs != null && rs.next()) {
                int invID = rs.getInt("invoiceID");
                String status = (invID > 0) ? "Invoiced (#" + invID + ")" : "Pending";
                String name = rs.getString("name");
                
                int qty = rs.getInt("quantity");
                float price = rs.getFloat("unitCost");
                float total = qty * price;

                model.addRow(new Object[]{
                    rs.getInt("orderID"),
                    name,
                    rs.getString("orderDate"),
                    qty,
                    String.format("$%.2f", price),
                    String.format("$%.2f", total),
                    status
                });
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading orders: " + e.getMessage());
        }
    }

    public void generateInvoice() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please click on an order in the table first.");
            return;
        }

        this.orderID = (Integer) model.getValueAt(row, 0);
        String customer = (String) model.getValueAt(row, 1);
        int qty = (Integer) model.getValueAt(row, 3);
        float price = Float.parseFloat(model.getValueAt(row, 4).toString().replace("$", ""));
        float total = Float.parseFloat(model.getValueAt(row, 5).toString().replace("$", ""));
        String status = (String) model.getValueAt(row, 6);

        StringBuilder missing = new StringBuilder();
        if (customer == null || customer.trim().isEmpty()){
            missing.append("  • Customer Name\n");
        }
        if (qty <= 0) {
            missing.append("  • Quantity (must be greater than 0)\n");
        }
        if (price <= 0) {
            missing.append("  • Unit Cost (must be greater than $0.00)\n");
        }
        if (total <= 0) {
            missing.append("  • Total Amount (must be greater than $0.00)\n");
        }

        if (missing.length() > 0) {
            JOptionPane.showMessageDialog(this, 
                "Cannot generate invoice for Order " + orderID + ":\n\n" +
                "The following data is missing or invalid:\n" + missing.toString() + "\n" +
                "Please update the information first.", 
                "Missing Data", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (status.startsWith("Invoiced")) {
            try {
                ResultSet rs = db.retrieveRecord("SELECT invoiceID, invoiceDate FROM invoices WHERE orderID = ?", orderID);
                if (rs != null && rs.next()) {
                    this.invoiceID = rs.getInt("invoiceID");
                    String dateStr = rs.getString("invoiceDate");
                    this.invoiceDate = java.sql.Date.valueOf(dateStr);
                    this.totalAmount = total;
                    
                    Invoice existingInv = new Invoice(invoiceID, orderID, customer, invoiceDate, qty, price, total);
                    showInvoicePopup(existingInv);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error reloading invoice: " + e.getMessage());
            }
            return;
        }

        try {
            int newID = 1;
            ResultSet rsMax = db.retrieveRecord("SELECT MAX(invoiceID) FROM invoices");
            if (rsMax != null && rsMax.next()) {
                newID = rsMax.getInt(1) + 1;
            }

            String dateStr = LocalDate.now().toString();

            boolean success = db.insertRecord(
                "INSERT INTO invoices (invoiceID, orderID, invoiceDate, totalAmount) VALUES (?,?,?,?)",
                newID, orderID, dateStr, total
            );

            if (success) {
                this.invoiceID = newID;
                this.invoiceDate = new Date();
                this.totalAmount = total;
                
                retrieveOrderDetails();
                Invoice newInv = new Invoice(invoiceID, orderID, customer, invoiceDate, qty, price, total);
                showInvoicePopup(newInv);
            } else {
                JOptionPane.showMessageDialog(this, "Database error. Could not generate invoice.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void showInvoicePopup(Invoice inv) {
        final JDialog dialog = new JDialog(this, "Invoice #" + inv.getInvoiceID(), true);
        dialog.getContentPane().setBackground(Theme.CREAM);
        dialog.setLayout(new BorderLayout());

        JPanel header = Theme.header("Winnie-Fred Farms", "Invoice #" + inv.getInvoiceID());
        dialog.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(6, 2, 10, 10));
        body.setBackground(Theme.CREAM);
        body.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        body.add(Theme.label("Bill To:"));
        body.add(new JLabel(inv.getCustomerName()));
        
        body.add(Theme.label("Order Ref:"));
        body.add(new JLabel(String.valueOf(inv.getOrderID())));
        
        body.add(Theme.label("Date:"));
        body.add(new JLabel(inv.getInvoiceDate().toString()));
        
        body.add(Theme.label("Quantity:"));
        body.add(new JLabel(String.valueOf(inv.getQuantity()) + " Bottles"));
        
        body.add(Theme.label("Unit Cost:"));
        body.add(new JLabel(String.format("$%.2f", inv.getUnitCost())));
        
        body.add(Theme.label("TOTAL:"));
        JLabel lblTotal = new JLabel(String.format("$%.2f", inv.getTotalAmount()));
        lblTotal.setFont(Theme.BODY_BOLD.deriveFont(16f));
        lblTotal.setForeground(Theme.DANGER);
        body.add(lblTotal);

        dialog.add(body, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(Theme.CREAM);
        
        JButton cmdPdf = new Theme.HoneyButton("Download PDF");
        cmdPdf.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                DocumentDistributor dd = new DocumentDistributor(inv, "customer@example.com", new File("documents"));
                try {
                    File f = dd.exportPDF();
                    JOptionPane.showMessageDialog(dialog, "PDF saved to:\n" + f.getAbsolutePath());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Error saving PDF.");
                }
            }
        });

        JButton cmdEmail = new Theme.HoneyButton("Email Customer");
        cmdEmail.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                DocumentDistributor dd = new DocumentDistributor(inv, "customer@example.com", new File("documents"));
                if (dd.sendEmail()) {
                    JOptionPane.showMessageDialog(dialog, "Email sent! Check the console.");
                } else {
                    JOptionPane.showMessageDialog(dialog, "Error: " + dd.getLastError());
                }
            }
        });

        JButton cmdClosePopup = new Theme.HoneyButton("Close");
        cmdClosePopup.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        btnPanel.add(cmdPdf);
        btnPanel.add(cmdEmail);
        btnPanel.add(cmdClosePopup);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private class GenerateButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            generateInvoice();
        }
    }

    private class RefreshButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            retrieveOrderDetails();
        }
    }

    private class CloseButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            dispose();
        }
    }
}