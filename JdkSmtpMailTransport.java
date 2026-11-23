import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * SMTP sender that uses only the JDK (no jars needed). Replaces SmtpMailTransport / Jakarta Mail.
 *
 * Typical settings (Gmail needs an "app password", not the normal password):
 *   new JdkSmtpMailTransport("smtp.gmail.com", 587, user, appPassword, from, Security.STARTTLS)
 *   new JdkSmtpMailTransport("smtp.gmail.com", 465, user, appPassword, from, Security.SSL)
 */
public class JdkSmtpMailTransport implements MailTransport {

    public enum Security { STARTTLS, SSL, NONE }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final Security security;

    public JdkSmtpMailTransport(String host, int port, String username, String password,
                                String from, Security security) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.security = (security == null) ? Security.STARTTLS : security;
    }

    /** Port 465 uses SSL, anything else uses STARTTLS. */
    public JdkSmtpMailTransport(String host, int port, String username, String password, String from) {
        this(host, port, username, password, from, port == 465 ? Security.SSL : Security.STARTTLS);
    }

    @Override
    public void send(String to, String subject, String body, File attachment) throws Exception {
        String sender = cleanAddress(from);
        String recipient = cleanAddress(to);
        byte[] message = buildMessage(sender, recipient, subject, body, attachment);

        Socket socket = (security == Security.SSL) ? sslSocket(plainSocket()) : plainSocket();
        try {
            Session s = new Session(socket);
            s.expect(220);
            String ehlo = s.command("EHLO winniefred.local", 250);

            if (security == Security.STARTTLS) {
                if (!ehlo.toUpperCase().contains("STARTTLS")) {
                    throw new IOException("The mail server does not offer an encrypted connection (STARTTLS).");
                }
                s.command("STARTTLS", 220);
                socket = sslSocket(socket);
                s = new Session(socket);
                ehlo = s.command("EHLO winniefred.local", 250);
            }

            if (username != null && !username.isEmpty()) {
                String plain = "\0" + username + "\0" + password;
                s.command("AUTH PLAIN " + b64(plain.getBytes(StandardCharsets.UTF_8)), 235);
            }
            s.command("MAIL FROM:<" + sender + ">", 250);
            s.command("RCPT TO:<" + recipient + ">", 250, 251);
            s.command("DATA", 354);
            s.sendRaw(dotStuff(message));
            s.expect(250);
            try { s.command("QUIT", 221); } catch (IOException ignored) { }
        } finally {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    // ---- sockets ----
    private Socket plainSocket() throws IOException {
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        sock.setSoTimeout(READ_TIMEOUT_MS);
        return sock;
    }

    private Socket sslSocket(Socket base) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(base, host, port, true);
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // checks the certificate matches the host
        ssl.setSSLParameters(params);
        ssl.setSoTimeout(READ_TIMEOUT_MS);
        ssl.startHandshake();
        return ssl;
    }

    /** One SMTP conversation: send a line, read the (possibly multi-line) reply, check the code. */
    private static class Session {
        private final BufferedReader in;
        private final OutputStream out;

        Session(Socket socket) throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            out = socket.getOutputStream();
        }

        String command(String line, int... okCodes) throws IOException {
            if (line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                throw new IOException("Invalid characters in mail command.");
            }
            out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return expect(okCodes);
        }

        void sendRaw(byte[] data) throws IOException {
            out.write(data);
            out.flush();
        }

        String expect(int... okCodes) throws IOException {
            StringBuilder all = new StringBuilder();
            String line;
            int code;
            while (true) {
                line = in.readLine();
                if (line == null) throw new IOException("The mail server closed the connection.");
                all.append(line).append('\n');
                if (line.length() < 3) throw new IOException("Unexpected reply from mail server: " + line);
                try { code = Integer.parseInt(line.substring(0, 3)); }
                catch (NumberFormatException e) { throw new IOException("Unexpected reply from mail server: " + line); }
                if (line.length() == 3 || line.charAt(3) != '-') break; // last line of the reply
            }
            for (int ok : okCodes) if (ok == code) return all.toString();
            throw new IOException("Mail server said: " + line);
        }
    }

    // ---- message building ----
    private static byte[] buildMessage(String from, String to, String subject, String body, File attachment)
            throws IOException {
        String boundary = "=_WFF_" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder m = new StringBuilder();
        m.append("From: ").append(from).append("\r\n");
        m.append("To: ").append(to).append("\r\n");
        m.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
        m.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
        m.append("Message-ID: <").append(UUID.randomUUID()).append("@winniefred.local>\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");

        m.append("--").append(boundary).append("\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(mime64((body == null ? "" : body).getBytes(StandardCharsets.UTF_8))).append("\r\n");

        if (attachment != null) {
            String name = attachment.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            m.append("--").append(boundary).append("\r\n");
            m.append("Content-Type: application/pdf; name=\"").append(name).append("\"\r\n");
            m.append("Content-Disposition: attachment; filename=\"").append(name).append("\"\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            m.append(mime64(Files.readAllBytes(attachment.toPath()))).append("\r\n");
        }
        m.append("--").append(boundary).append("--\r\n");
        return m.toString().getBytes(StandardCharsets.ISO_8859_1); // everything above is ASCII
    }

    /** Doubles a leading '.' on any line and ends the data with CRLF.CRLF (SMTP rule). */
    private static byte[] dotStuff(byte[] message) {
        String s = new String(message, StandardCharsets.ISO_8859_1);
        if (s.startsWith(".")) s = "." + s;
        s = s.replace("\r\n.", "\r\n..");
        if (!s.endsWith("\r\n")) s += "\r\n";
        return (s + ".\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Plain address only; blocks line breaks and angle brackets so nobody can inject extra headers. */
    private static String cleanAddress(String address) throws IOException {
        String a = (address == null) ? "" : address.trim();
        if (a.isEmpty() || java.util.regex.Pattern.compile("[\\s<>,;\"]").matcher(a).find()) {
            throw new IOException("Invalid email address: " + address);
        }
        return a;
    }

    private static String encodeHeader(String text) {
        String t = (text == null ? "" : text).replaceAll("[\\r\\n]+", " ");
        for (char c : t.toCharArray()) {
            if (c > 126 || c < 32) {
                return "=?UTF-8?B?" + b64(t.getBytes(StandardCharsets.UTF_8)) + "?=";
            }
        }
        return t;
    }

    private static String b64(byte[] data) { return Base64.getEncoder().encodeToString(data); }

    private static String mime64(byte[] data) {
        return Base64.getMimeEncoder(76, new byte[]{'\r', '\n'}).encodeToString(data);
    }
}