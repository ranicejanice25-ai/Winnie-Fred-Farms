import java.io.File;

/** Sends one email with one attachment. SmtpMailTransport is the real SMTP version. */
public interface MailTransport {

    void send(String to, String subject, String body, File attachment) throws Exception;
}