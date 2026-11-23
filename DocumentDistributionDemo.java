import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Runs DocumentDistribution with a pretend invoice and a pretend mail sender (no real email is sent). */
public class DocumentDistributionDemo {

    public static void main(String[] args) {
        Distributable invoice = new Distributable() {
            public int getDocumentId() { return 1; }
            public String getTitle() { return "Invoice"; }
            public List<String> toDocumentLines() {
                return Arrays.asList("Customer: A. Brown", "Order reference: 1042", "Quantity: 2 bottles",
                        "Unit cost: $3,500.00", "Total cost: $7,000.00");
            }
        };

        CustomerContactLookup lookup = id -> "a.brown@example.com";
        MailTransport pretendMail = (to, subject, body, attachment) ->
                System.out.println("Pretend send to " + to + " | " + subject + " | attachment " + attachment.getName());

        DocumentDistributor dd = new DocumentDistributor(invoice, lookup, pretendMail, new File("documents"));

        System.out.println("Contact: " + dd.retrieveCustomerContact());
        File pdf = dd.exportPDF();
        System.out.println("PDF: " + pdf.getPath() + " (" + pdf.length() + " bytes)");
        System.out.println("Sent: " + dd.sendEmail() + ", date: " + dd.getDistributionDate());

        // Error cases
        dd.setCustomerEmail("not-an-email");
        System.out.println("Bad address sent: " + dd.sendEmail() + " -> " + dd.getLastError());

        MailTransport failingMail = (to, subject, body, attachment) -> { throw new Exception("connection refused"); };
        DocumentDistributor failing = new DocumentDistributor(invoice, lookup, failingMail, new File("documents"));
        System.out.println("Mail failure sent: " + failing.sendEmail() + " -> " + failing.getLastError());

        DocumentDistributor noEmail = new DocumentDistributor(invoice, id -> null, pretendMail, new File("documents"));
        System.out.println("No email on file sent: " + noEmail.sendEmail() + " -> " + noEmail.getLastError());
    }
}