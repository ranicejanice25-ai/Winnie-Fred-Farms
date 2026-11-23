import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This specific classes is implemented to aid in Document Distribution (Reporting component & Document Distribution)
 * 
 * Centralises PDF export and SMTP email sending for invoices and receipts, so InvoiceGenerator and ReceiptGenerator do not each handle their own output (SDS 3.2.1). Serves SRS feature 1.2.5.
 * 
 * All attributes and methods follow the class diagram in SDS 3.2
 * 
 */

public class DocumentDistributor {

    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String COMPANY_NAME = "Winnie Fred Farms";
    private static final int LINES_PER_PAGE = 44; 

    //Attributes stated in the class diagram 
    private int documentID;
    private String customerEmail;
    private Date distributionDate;

    //Collaborators 
    private final Distributable document;
    private final CustomerContactLookup contactLookup;
    private final MailTransport mailTransport; 
    private final File outputDirectory;

    private File exportedFile;
    private String lastError;

    public DocumentDistributor(Distributable document, CustomerContactLookup contactLookup, MailTransport mailTransport, File outputDirectory){
        if (document == null){
            throw new IllegalArgumentException("A document is required.");
        }
        this.document = document;
        this.documentID = document.getDocumentId();
        this.contactLookup = contactLookup;
        this.mailTransport = mailTransport;
        this.outputDirectory = outputDirectory;
    }
    /**
     * Looks up the customer's email address for this document, stores it in customerEmail and returns it. 
     * Returns an empty string and sets the error message, if no address is on file.
     *  
     */
    public String retrieveCustomerContact(){
        lastError = null;
        String email = (contactLookup == null) ? null: contactLookup.findCustomerEmail(documentID);
            if (email == null || email.trim().isEmpty()){
                customerEmail = null;
                lastError = "No customer email address is on file for this document.";
                    return "";
            }
            customerEmail = email.trim();
            return customerEmail;
    }
    /**
     * Emails the document to the customer as a PDF attachment. Checks the address format first, exports the PDF if needed, and records the distribution date on successs.
     * 
     * @return true if the email was sent, false otherwise (calls getLastError() for the message to show the user.)
     * */

    public boolean sendEmail(){
        lastError = null;
        try{
            if (customerEmail == null || customerEmail.trim().isEmpty()){
                retrieveCustomerContact();
            }
            if (!isValidEmail(customerEmail)){
                lastError = "The customer's email address is missing or not in a valid format.";
                return false;
            }
            File pdf = (exportedFile != null && exportedFile.exists()) ? exportedFile : exportPDF();
            mailTransport.send(customerEmail, subject(), bodyText(), pdf);
            distributionDate = new Date();
            return true;
        } catch (Exception e){
            lastError = "The email could not be sent: " + e.getMessage();
            return false;
        }
    }
    /**
     * Creates a  standrad PDF of the document in the output directory and returns the file
     * @throws DocumentDistributionException if the file cannot be written
     * 
     */
    public File exportPDF(){
        lastError = null;
        try{
            Files.createDirectories(outputDirectory.toPath());
            String safeTitle = document.getTitle().replaceAll("[^A-Za-z0-9]+", "-");
            File pdf = new File(outputDirectory, safeTitle + "-" + documentID + ".pdf");
            Files.write(pdf.toPath(), buildPdf(document.getTitle() + "#" + documentID, document.toDocumentLines()));
            exportedFile = pdf;
            return pdf;
        }catch (IOException e){
            lastError = "The PDF could not be saved: " + e.getMessage();
            throw new DocumentDistributionException(lastError, e);
        }
    }
    //Accessors 
    public int getDocumentID(){
        return documentID;
    }

    public String getCustomerEmail(){
        return customerEmail;
    }

    /**The recipient typed in by the user. The address is checked when the email is sent.*/
    public void setCustomerEmail(String customerEmail){
        this.customerEmail = customerEmail;
    }

    /**When the document was last emailed, or null if it has not been sent.*/
    public Date getDistributionDate(){
        return distributionDate;
    }

    /**The message to show the user after a failed action, or null */
    public String getLastError(){
        return lastError;
    }

    public boolean isValidEmail(String address){
        return address != null && EMAIL_FORMAT.matcher(address.trim()).matches();
    }

    //Helpers 
    private String subject(){
        return document.getTitle() + "+" + documentID + " from " + COMPANY_NAME;
    }
    private String bodyText(){
        return "Hello,\n\nPlease find your " + document.getTitle().toLowerCase() + " (#" + documentID + ") attached as a PDF.\n\nThank you,\n" + COMPANY_NAME;
    }


    /** 
     * Builds a simple multi-page PDF using only the JDK (Helvetica text, A4). 
     * 
     * */
    private static byte[] buildPdf(String heading, List<String> lines) {
        int pageCount = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        List<String> objects = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pageCount; p++) {
            kids.append(5 + 2 * p).append(" 0 R ");
        }
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [ " + kids + "] /Count " + pageCount + " >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");
        for (int p = 0; p < pageCount; p++) {
            int contentObj = 6 + 2 * p;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents " + contentObj
                    + " 0 R /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> >>");
            StringBuilder s = new StringBuilder();
            s.append("BT /F2 18 Tf 50 790 Td (").append(escape(COMPANY_NAME + " - " + heading)).append(") Tj ET\n");
            s.append("BT /F1 11 Tf 50 755 Td 16 TL\n");
            int from = p * LINES_PER_PAGE;
            int to = Math.min(lines.size(), from + LINES_PER_PAGE);
            for (int i = from; i < to; i++) {
                s.append("(").append(escape(lines.get(i))).append(") Tj T*\n");
            }
            s.append("ET");
            objects.add("<< /Length " + s.length() + " >>\nstream\n" + s + "\nendstream");
        }

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int off : offsets) {
            pdf.append(String.format("%010d 00000 n \n", off));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Escapes text for a PDF string. Characters outside Latin-1 become '?'. */
    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                out.append('\\').append(c);
            } else if (c > 255 || c < 32) {
                out.append('?');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Unchecked exception for failures the user interface should report. */
    public static class DocumentDistributionException extends RuntimeException {
        public DocumentDistributionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}