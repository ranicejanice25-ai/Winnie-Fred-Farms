import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Honey-themed main menu. Opens the login window first and only enables features after authentication. */
public class MainWindow extends JFrame {
    private User currentUser;
    private boolean isAuthenticated = false;

    private final AuthManager authManager;
    private final DatabaseManager db;

    private final JButton cmdClose = new Theme.HoneyButton("Close");
    private final JPanel pnlDisplay = new JPanel(new GridLayout(0, 1, 10, 10));
    private final JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    private final JButton cmdInventory = new Theme.HoneyButton("Inventory");
    private final JButton cmdOrders = new Theme.HoneyButton("Orders");
    private final JButton cmdInvoices = new Theme.HoneyButton("Invoices");
    private final JButton cmdReceipts = new Theme.HoneyButton("Receipts");
    private final JButton cmdReports = new Theme.HoneyButton("Sales Reports");

    public MainWindow(DatabaseManager db, AuthManager authManager) {
        super("Winnie-Fred Farms");
        this.db = db;
        this.authManager = authManager;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        setContentPane(root);

        pnlDisplay.setOpaque(false);
        pnlDisplay.setBorder(BorderFactory.createEmptyBorder(16, 50, 8, 50));
        pnlCommand.setOpaque(false);
        pnlCommand.add(cmdClose);

        root.add(Theme.header("Winnie-Fred Farms", "Fresh from the hive"), BorderLayout.NORTH);
        root.add(pnlDisplay, BorderLayout.CENTER);
        root.add(pnlCommand, BorderLayout.SOUTH);

        CloseButtonListener closeListener = new CloseButtonListener();
        cmdClose.addActionListener(closeListener);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { closeListener.actionPerformed(null); }
        });

        cmdInventory.addActionListener(new InventoryButtonListener());
        
        // Connect your Invoice Generator
        cmdInvoices.addActionListener(e -> new InvoiceGenerator(db).setVisible(true));

        // Keep the placeholders for the other unfinished modules
        for (JButton b : new JButton[]{cmdOrders, cmdReceipts, cmdReports}) {
            b.addActionListener(new OtherModuleListener(b.getText()));
        }

        setSize(400, 470);
        setLocationRelativeTo(null);
    }

    /** Shows the login dialog. On success the menu is displayed and the features are enabled. */
    public void openLoginWindow() {
        LoginWindow login = new LoginWindow(this, authManager);
        login.setVisible(true); // blocks until the dialog closes (modal)

        isAuthenticated = login.isLoggedIn();
        if (isAuthenticated) {
            currentUser = authManager.getCurrentUser();
            displayMenu();
            enableFeatures();
            setVisible(true);
        } else {
            System.exit(0);
        }
    }

    public void displayMenu() {
        pnlDisplay.removeAll();
        String who = currentUser == null ? "" : currentUser.getUsername();
        JLabel welcome = new JLabel("Welcome, " + who, SwingConstants.CENTER);
        welcome.setFont(Theme.TITLE.deriveFont(18f));
        welcome.setForeground(Theme.DARK);
        pnlDisplay.add(welcome);
        pnlDisplay.add(cmdInventory);
        pnlDisplay.add(cmdOrders);
        pnlDisplay.add(cmdInvoices);
        pnlDisplay.add(cmdReceipts);
        pnlDisplay.add(cmdReports);
        pnlDisplay.revalidate();
        pnlDisplay.repaint();
    }

    /** Role 1 = harvest manager, 2 = order manager, 3 = finance manager. */
    public void enableFeatures() {
        int role = authManager.verifyRole();
        boolean in = isAuthenticated;
        cmdInventory.setEnabled(in && (role == 1 || role == 2 || role == 3));
        cmdOrders.setEnabled(in && role == 2);
        cmdInvoices.setEnabled(in && (role == 2 || role == 3));
        cmdReceipts.setEnabled(in && (role == 2 || role == 3));
        cmdReports.setEnabled(in && role == 3);
    }

    private class InventoryButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            new InventoryManager(db).setVisible(true);
        }
    }

    /** Placeholder for modules owned by other team members. */
    private class OtherModuleListener implements ActionListener {
        private final String name;
        OtherModuleListener(String name) { this.name = name; }
        @Override public void actionPerformed(ActionEvent e) {
            JOptionPane.showMessageDialog(MainWindow.this, name + " module is not connected yet.");
        }
    }

    private class CloseButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) {
            authManager.endSession();
            dispose();
            System.exit(0);
        }
    }

    public User getCurrentUser() { return currentUser; }
    public boolean isAuthenticated() { return isAuthenticated; }
}
