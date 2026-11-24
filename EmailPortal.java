import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class EmailPortal {

    public enum Provider { GMAIL, OUTLOOK, MAIL_APP }

    private EmailPortal() { }

    public static void open(Provider provider, String to, String subject, String body, File pdf) throws Exception {
        String url;
        switch (provider) {
            case GMAIL:
                url = "https://mail.google.com/mail/?view=cm&fs=1&to=" + enc(to)
                        + "&su=" + enc(subject) + "&body=" + enc(body);
                break;
            case OUTLOOK:
                url = "https://outlook.office.com/mail/deeplink/compose?to=" + enc(to)
                        + "&subject=" + enc(subject) + "&body=" + enc(body);
                break;
            default:
                url = "mailto:" + enc(to).replace("%40", "@") + "?subject=" + enc(subject) + "&body=" + enc(body);
        }
        launch(url);
        reveal(pdf); // links cannot attach files, so show the PDF for drag-and-drop
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void launch(String url) throws Exception {
        if (isWindows()) {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
        } else {
            Desktop.getDesktop().browse(new URI(url));
        }
    }

    private static void reveal(File pdf) throws Exception {
        if (isWindows()) {
            new ProcessBuilder("explorer.exe", "/select,", pdf.getAbsolutePath()).start();
        } else {
            Desktop.getDesktop().open(pdf.getParentFile());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}