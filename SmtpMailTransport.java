import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Real SMTP sender using Jakarta Mail.
 * org.eclipse.angus:jakarta.mail (version 2.x) to your project.
 *
 */
public class SmtpMailTransport implements MailTransport {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;

    public SmtpMailTransport(String host, int port, String username, String password, String from) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body, File attachment)
            throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true"); // encrypts the connection (TLS)
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);

        MimeBodyPart text = new MimeBodyPart();
        text.setText(body);
        MimeBodyPart file = new MimeBodyPart();
        file.attachFile(attachment);

        message.setContent(new MimeMultipart(text, file));
        Transport.send(message);
    }
}