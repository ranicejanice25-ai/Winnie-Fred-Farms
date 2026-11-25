import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Danielle Murray
 * This class allows for the display of a window that prompts the creation of a new order.
 * There is a button that allows an order to be saved, and one to cancel order creation.
 */
public class OrderTracker extends JFrame{
    private static final String PRODUCT="Honey";
    private String failureReason="";
    private int editingOrderID=0;
    

    private final DatabaseManager db;
    private final List<OrderRecord> cache = new ArrayList<>();
    private boolean usingCache = false;

    private final JTextField txtName = new JTextField(25);
    private final JTextField txtEmail = new JTextField(25);
    private final JTextField txtDate = new JTextField(LocalDate.now().toString(), 9);
    private final JTextField txtQuantity = new JTextField(6);
    private final JTextField txtUnitCost = new JTextField(6);
    

    private final JButton cmdAdd = new Theme.HoneyButton("Save Order");
    private final JButton cmdCalcTotal = new Theme.HoneyButton("Calculate Total");
    private final JButton cmdNew = new Theme.HoneyButton("New Order");
    
    private final JButton cmdDelete = new Theme.HoneyButton("Delete Order");
    private final JButton cmdSortDate = new Theme.HoneyButton("Sort by Date");
    private final JButton cmdSortAmount = new Theme.HoneyButton("Sort by Quantity");
    private final JButton cmdClose = new Theme.HoneyButton("Close");
    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Order ID", "Date Created", "Name", "Email", "Quantity", "Unit Cost", "Total Cost", "Balance", "Status"}, 0){
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);

    public OrderTracker(DatabaseManager db){
        super("Winnie-Fred Farms - Order Tracker");
        this.db = db;
        buildGui();

        double price=db.getUnitPrice(PRODUCT);
        if(price >= 0)
            txtUnitCost.setText(String.format("%.2f", price));  

        refreshTable(retrieveOrders());
    }

    public boolean addOrder(String customerName, String customerEmail,
        int quantityOrdered, double unitCost, LocalDate dateCreated){
        failureReason="";
        if (!db.isConnected()){
            failureReason="Database is not connected.";
            return false;
        }
        if (!isValidOrder(customerName, customerEmail, quantityOrdered, unitCost, dateCreated) || quantityOrdered == 0){
            failureReason="Order details not valid.";
            return false;
        }

        int stock=db.getStock(PRODUCT);
        if (stock==-1){
            failureReason = "Honey is not set up in inventory.";
            return false;
        }
        if(quantityOrdered > stock){
            failureReason = "Not enough stock: " + stock + " available, " + quantityOrdered + " ordered.";
            return false;
        }

        int customerID=db.findOrCreateCustomer(customerName.trim(), customerEmail);
        if (customerID==-1){
            failureReason="Could not save the customer.";
            return false;
        }

        if (!db.deductStock(PRODUCT, quantityOrdered)){
            failureReason = "Not enough stock to fill this order.";
            return false;
        }

        OrderRecord order=new OrderRecord(customerID, dateCreated, quantityOrdered, unitCost);
        int orderID=db.insertOrder(order);
        if (orderID==-1){
            db.restoreStock(PRODUCT, quantityOrdered);
            failureReason="Could not save the order.";
            return false;
        }

        order.setOrderID(orderID);
        return true;
    }

    public boolean updateOrder(int orderID, String customerName, String customerEmail,
        int quantityOrdered, double unitCost, LocalDate dateCreated) {
        failureReason = "";
        if (!db.isConnected()) { failureReason = "Database is not connected."; return false; }
        if (!isValidOrder(customerName, customerEmail, quantityOrdered, unitCost, dateCreated)) {
            failureReason = "Order details are not valid.";
            return false;
        }

        OrderRecord old = findOrder(orderID);
        if (old == null) { failureReason = "That order no longer exists."; return false; }

        int customerID = db.findOrCreateCustomer(customerName.trim(), customerEmail);
        if (customerID == -1) { failureReason = "Could not save the customer."; return false; }
        db.updateCustomerName(customerID, customerName.trim());

        // Adjust stock by the difference in quantity
        int diff = quantityOrdered - old.getQuantityOrdered();
        if (diff > 0 && !db.deductStock(PRODUCT, diff)) {
            failureReason = "Not enough stock for the extra " + diff + " bottle(s).";
            return false;
        }
        if (diff < 0) db.restoreStock(PRODUCT, -diff);

        // Recalculate totals, keeping whatever has already been paid
        OrderRecord updated = new OrderRecord(customerID, dateCreated, quantityOrdered, unitCost);
        updated.setOrderID(orderID);
        double paid = old.getTotalCost() - old.getRemainingBalance();
        double newBalance = Math.round((updated.getTotalCost() - paid) * 100) / 100.0;
        updated.setRemainingBalance(newBalance);   // also refreshes the status

        if (!db.updateOrder(updated)) {
            if (diff > 0) db.restoreStock(PRODUCT, diff);        // undo the stock change
            else if (diff < 0) db.deductStock(PRODUCT, -diff);
            failureReason = "Could not update the order.";
            return false;
        }
        return true;
    }

    public boolean deleteOrder(int orderID){
        if (!db.isConnected()) return false;
        return db.deleteRecord("DELETE FROM orders WHERE orderID = ?", orderID);
    }

    public List<OrderRecord> retrieveOrders(){
        List<OrderRecord> items = new ArrayList<>();
        ResultSet rs = db.retrieveRecord("SELECT o.orderID, o.customerID, o.orderDate, c.name, c.email, o.quantity, "
                        + "o.unitCost, o.totalAmount, o.balance, o.orderStatus "
                        + "FROM orders o JOIN customers c ON c.customerID = o.customerID");
            if (rs == null){
                usingCache = true;
                return new ArrayList<>(cache);
            }
            Statement st=null;
            try{
                st=rs.getStatement();
                while (rs.next()){
                    items.add(new OrderRecord(rs.getInt(1), rs.getInt(2), LocalDate.parse(rs.getString(3)),
                    rs.getString(4), rs.getString(5), rs.getInt(6),rs.getDouble(7), rs.getDouble(8),
                    rs.getDouble(9), rs.getString(10)));
                }
            }catch(SQLException | DateTimeParseException e){
                usingCache=true;
                return new ArrayList<>(cache);
            }finally{
                try{
                    rs.close();}
                catch(SQLException ignored){}

                try{
                    if (st != null)
                        st.close();}
                catch(SQLException ignored){}
            }
        cache.clear();
        cache.addAll(items);
        usingCache=false;
        return items;
    }

    private OrderRecord findOrder(int id) {
        for (OrderRecord o : retrieveOrders()) {
            if (o.getOrderID() == id) return o;
        }
        return null;
    }

    private void loadOrderIntoForm(int row) {
        int id = (Integer) model.getValueAt(row, 0);
        OrderRecord o = findOrder(id);
        if (o == null) return;

        editingOrderID = id;
        txtName.setText(o.getCustomerName());
        txtEmail.setText(o.getCustomerEmail());
        txtDate.setText(o.getDateCreated().toString());
        txtQuantity.setText(String.valueOf(o.getQuantityOrdered()));
        txtUnitCost.setText(String.format("%.2f", o.getUnitCost()));
        cmdAdd.setText("Update Order");
        lblStatus.setText("Editing order " + id + ". Click New Order to cancel.");
    }

    private void resetForm() {
        editingOrderID = 0;
        table.clearSelection();
        txtName.setText("");
        txtEmail.setText("");
        txtQuantity.setText("");
        txtDate.setText(LocalDate.now().toString());
        double price = db.getUnitPrice(PRODUCT);
        txtUnitCost.setText(price >= 0 ? String.format("%.2f", price) : "");
        cmdAdd.setText("Save Order");
    }

    private boolean isValidOrder(String name, String email, int qty, double cost, LocalDate date){
        return name != null && !name.trim().isEmpty()
        && email != null && !email.trim().isEmpty()
        && qty > 0 && cost >= 0 && !Double.isNaN(cost) && !Double.isInfinite(cost)
        && date != null;
    }

    private void refreshTable(List<OrderRecord> items){
        model.setRowCount(0);
        for (OrderRecord it : items){
            model.addRow(new Object[]{
                    it.getOrderID(), it.getDateCreated(), it.getCustomerName(), it.getCustomerEmail(), it.getQuantityOrdered(),
                    String.format("$%.2f", it.getUnitCost()), String.format("$%.2f", it.getTotalCost()),
                    String.format("$%.2f", it.getRemainingBalance()), it.getOrderStatus()});}

        lblStatus.setText(usingCache
                ? "Database unavailable - showing last cached orders."
                : items.size() + " record(s) loaded.");
    }

    private Integer parseQuantity(String text){
        try {
            int q = Integer.parseInt(text);
            if (q < 0) { showError("Quantity cannot be negative."); return null; }
            if (q == 0) { showError("Quantity must be greater than zero."); return null; }
            return q;
        } catch (NumberFormatException ex) {
            try {
                Double.parseDouble(text);
                showError("Quantity must be a whole number of bottles.");
            } catch (NumberFormatException ex2) {
                showError("Quantity must be numeric.");
            }
            return null;
        }
    }

    private Double parsePrice(String text, String label){
        try {
            double p = Double.parseDouble(text);
            if (Double.isNaN(p) || Double.isInfinite(p)) { showError(label + " must be a valid number."); return null; }
            if (p < 0) { showError(label + " cannot be negative."); return null; }
            return p;
        } catch (NumberFormatException ex) {
            showError(label + " must be numeric.");
            return null;
        }
    }

    private LocalDate parseDate(String text){
        try{
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            showError("Date must be a valid date in the format yyyy-MM-dd (e.g. 2026-12-12).");
            return null;
        }
    }

    private void showError(String msg){
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void buildGui(){
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        for (JTextField f : new JTextField[]{txtName, txtEmail,txtDate, txtQuantity, txtUnitCost}) Theme.styleField(f);
        Theme.styleTable(table);
        lblStatus.setFont(Theme.BODY);
        lblStatus.setForeground(Theme.BROWN);

        JPanel pnlRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        pnlRow1.setOpaque(false);
        pnlRow1.add(Theme.label("Customer Name:"));   pnlRow1.add(txtName);
        pnlRow1.add(Theme.label("Customer Email:"));  pnlRow1.add(txtEmail);

        JPanel pnlRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        pnlRow2.setOpaque(false);
        pnlRow2.add(Theme.label("Date (yyyy-MM-dd):")); pnlRow2.add(txtDate);
        pnlRow2.add(Theme.label("Quantity:"));          pnlRow2.add(txtQuantity);
        pnlRow2.add(Theme.label("Unit Cost:"));         pnlRow2.add(txtUnitCost);

        JPanel pnlFields = new JPanel(new GridLayout(2, 1));
        pnlFields.setOpaque(false);
        pnlFields.add(pnlRow1);
        pnlFields.add(pnlRow2);
        

        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlActions.setOpaque(false);
        pnlActions.add(cmdAdd);
        pnlActions.add(cmdCalcTotal);
        pnlActions.add(cmdNew);

        JPanel pnlInput = new JPanel(new BorderLayout());
        pnlInput.setOpaque(false);
        pnlInput.add(pnlFields, BorderLayout.NORTH);
        pnlInput.add(pnlActions, BorderLayout.CENTER);
        pnlInput.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setOpaque(false);
        pnlTop.add(Theme.header("Order Tracker", "Customer honey orders"), BorderLayout.NORTH);
        pnlTop.add(pnlInput, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Theme.CREAM);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlCommand.setOpaque(false);
        pnlCommand.add(cmdDelete);
        pnlCommand.add(cmdSortDate);
        pnlCommand.add(cmdSortAmount);
        pnlCommand.add(cmdClose);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.setOpaque(false);
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        pnlBottom.add(lblStatus, BorderLayout.WEST);
        pnlBottom.add(pnlCommand, BorderLayout.EAST);

        root.add(pnlTop, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(pnlBottom, BorderLayout.SOUTH);

        cmdAdd.addActionListener(new AddOrderListener());
        cmdCalcTotal.addActionListener(new CalcTotalListener());
        cmdNew.addActionListener(e -> resetForm());
        cmdDelete.addActionListener(new DeleteOrderListener());
        cmdSortDate.addActionListener(new SortDateListener());
        cmdSortAmount.addActionListener(new SortAmountListener());
        cmdClose.addActionListener(new CloseButtonListener());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting())return;
            int row = table.getSelectedRow();
            if (row >= 0) loadOrderIntoForm(row);
        });

        setSize(900, 500);
        setLocationRelativeTo(null);
    }

    private class AddOrderListener implements ActionListener{
        @Override public void actionPerformed(ActionEvent e){
            String name = txtName.getText();
            String email = txtEmail.getText();
            String qtyText = txtQuantity.getText().trim();
            String unitCostText=txtUnitCost.getText().trim();
            String dateText = txtDate.getText().trim();

            if (name.isEmpty() || email.isEmpty() || qtyText.isEmpty() || unitCostText.isEmpty() || dateText.isEmpty()) {
                showError("Please fill in customer name and email, quantity ordered, unit cost, and date.");
                return;
            }

            Integer qty = parseQuantity(qtyText);
            if (qty == null) return;
            Double unitCost=parsePrice(unitCostText, "Unit cost");
            if(unitCost==null) return;
            LocalDate orderDate = parseDate(dateText);
            if (orderDate == null) return;

            boolean editing = editingOrderID != 0;
            int id = editingOrderID;
            boolean ok = editing
                    ? updateOrder(id, name, email, qty, unitCost, orderDate)
                    : addOrder(name, email, qty, unitCost, orderDate);

            if (ok) {
                resetForm();
                refreshTable(retrieveOrders());
                int left = db.getStock(PRODUCT);
                lblStatus.setText((editing ? "Updated order " + id : "Saved order for " + name)
                        + ". Stock remaining: " + left + ".");
            } else {
                showError(failureReason.isEmpty()
                        ? "Could not save the order record. Check the database connection."
                        : failureReason);
                refreshTable(retrieveOrders());
            }
        }
    }

    private class CalcTotalListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            Integer qty=parseQuantity(txtQuantity.getText().trim());
            if(qty==null) return;

            Double unitCost=parsePrice(txtUnitCost.getText().trim(), "Unit cost");
            if(unitCost==null) return;

            lblStatus.setText(String.format("Total: $%.2f", qty*unitCost));
        }
    }

    private class DeleteOrderListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e){
            int row = table.getSelectedRow();
            if (row < 0){showError("Select an order record to remove."); return;}

            int id = (Integer) model.getValueAt(row, 0);
            int choice = JOptionPane.showConfirmDialog(OrderTracker.this,
                    "Remove order record " + id + "?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (id == editingOrderID) resetForm();
            if (choice != JOptionPane.YES_OPTION) return;
            if (!deleteOrder(id)) showError("Could not remove the record. Check the database connection.");
            refreshTable(retrieveOrders());
        }
    }

    private class SortDateListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            List<OrderRecord> items = retrieveOrders();
            items.sort(Comparator.comparing(OrderRecord::getDateCreated,
                    Comparator.nullsLast(Comparator.reverseOrder())));
            refreshTable(items);
        }
    }

    private class SortAmountListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            List<OrderRecord> items = retrieveOrders();
            items.sort(Comparator.comparingInt(OrderRecord::getQuantityOrdered).reversed());
            refreshTable(items);
        }
    }

    private class CloseButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { dispose(); }
    }
}