import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Modal login dialog. Submits the credentials to AuthManager. */
public class LoginWindow extends JDialog {
    private String username;
    private String password;
    private boolean loggedIn = false;

    private final AuthManager authManager;
    private final JTextField txtUsername = new JTextField(15);
    private final JPasswordField txtPassword = new JPasswordField(15);
    private final JButton cmdLogin = new JButton("Login");
    private final JButton cmdClear = new JButton("Clear");
    private final JButton cmdCancel = new JButton("Cancel");
    private final JLabel lblMessage = new JLabel(" ");

    public LoginWindow(Frame owner, AuthManager authManager) {
        super(owner, "Winnie-Fred Farms - Login", true);
        this.authManager = authManager;
        buildGui();
    }

    /** Reads the fields and asks AuthManager to verify them. */
    public Boolean submitLogin() {
        username = txtUsername.getText().trim();
        password = new String(txtPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            lblMessage.setText("Enter both username and password.");
            return false;
        }
        if (authManager.verifyUser(username, password)) {
            authManager.startSession();
            loggedIn = true;
            dispose();
            return true;
        }
        lblMessage.setText("Invalid username or password.");
        clearFields();
        return false;
    }

    public void clearFields() {
        username = "";
        password = "";
        txtUsername.setText("");
        txtPassword.setText("");
        txtUsername.requestFocusInWindow();
    }

    public boolean isLoggedIn() { return loggedIn; }

    private void buildGui() {
        setLayout(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(2, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        form.add(new JLabel("Username:")); form.add(txtUsername);
        form.add(new JLabel("Password:")); form.add(txtPassword);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cmdLogin); buttons.add(cmdClear); buttons.add(cmdCancel);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        south.add(lblMessage, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        cmdLogin.addActionListener(new LoginButtonListener());
        cmdClear.addActionListener(new ClearButtonListener());
        cmdCancel.addActionListener(new CancelButtonListener());
        txtPassword.addActionListener(new LoginButtonListener()); // Enter key
        getRootPane().setDefaultButton(cmdLogin);

        pack();
        setLocationRelativeTo(getOwner());
    }

    private class LoginButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { submitLogin(); }
    }
    private class ClearButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { clearFields(); lblMessage.setText(" "); }
    }
    private class CancelButtonListener implements ActionListener {
        @Override public void actionPerformed(ActionEvent e) { dispose(); }
    }
}
