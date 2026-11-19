/** A system user. role: 1 = harvest manager, 2 = order manager, 3 = finance manager. */
public class User {
    private String username;
    private String password; // stored as a SHA-256 hash
    private int role;

    public User(String username, String password, int role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getRole() { return role; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(int role) { this.role = role; }
}
