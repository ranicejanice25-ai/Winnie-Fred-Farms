import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Presentation for SalesReport: pick a period, then generate the sales report or the
 * outstanding payment report and see it on screen (SRS 1.2.6, SR-1 and SR-6).
 */
public class SalesReportWindow extends JFrame {

    private final SalesReport report;

    private final JTextField txtStart = new JTextField(LocalDate.now().withDayOfMonth(1).toString(), 9);
    private final JTextField txtEnd = new JTextField(LocalDate.now().toString(), 9);
    private final JButton cmdSales = new Theme.HoneyButton("Generate Sales Report");
    private final JButton cmdOutstanding = new Theme.HoneyButton("Generate Outstanding Payment Report");
    private final JButton cmdClose = new Theme.HoneyButton("Close");
    private final JLabel lblStatus = new JLabel(" ");
    private final JEditorPane pane = new JEditorPane("text/html", "");

    public SalesReportWindow(DatabaseManager db) {
        super("Winnie-Fred Farms - Sales Reports");
        this.report = new SalesReport(db);
        buildGui();
        showMessage("Choose a period and press <b>Generate Sales Report</b>, or press "
                + "<b>Generate Outstanding Payment Report</b>.");
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    private void generateSales() {
        LocalDate start = parseDate(txtStart.getText().trim());
        LocalDate end = parseDate(txtEnd.getText().trim());
        if (start == null || end == null) {
            showError("Start and end dates must be valid dates in the format yyyy-MM-dd (e.g. 2026-12-12).");
            clearReport();
            return;
        }
        String problem = SalesReport.validatePeriod(start, end);
        if (problem != null) {
            showError(problem);
            clearReport();
            return;
        }
        try {
            SalesReport.Summary s = report.generateSalesReport(start, end);
            setHtml(buildSalesHtml(s));
            lblStatus.setText("Sales report generated for " + start + " to " + end + ".");
            lblStatus.setForeground(Theme.BROWN);
        } catch (IllegalStateException ex) {
            showDbError(ex);
        }
    }

    private void generateOutstanding() {
        try {
            List<SalesReport.OutstandingItem> items = report.generateOutstandingReport();
            setHtml(buildOutstandingHtml(items, report.totalOutstanding(items), LocalDate.now()));
            lblStatus.setText(items.size() + " unpaid invoice(s).");
            lblStatus.setForeground(Theme.BROWN);
        } catch (IllegalStateException ex) {
            showDbError(ex);
        }
    }

    private LocalDate parseDate(String text) {
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException ex) { return null; }
    }

    // ------------------------------------------------------------------
    //  Report text (SR-6: company name, title, period, calculated values)
    // ------------------------------------------------------------------

    static String buildSalesHtml(SalesReport.Summary s) {
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart(SalesReport.COMPANY, "Sales Report"));
        sb.append("<p align='center'><b>Period:</b> ").append(s.getStartDate())
          .append(" to ").append(s.getEndDate()).append("</p>");
        if (s.isEmpty()) {
            sb.append("<p align='center'><i>No records were found for this period.</i></p>");
        }
        sb.append("<table width='100%' cellpadding='6' cellspacing='0' border='0'>");
        row(sb, 0, "Orders in period", String.valueOf(s.getOrderCount()));
        row(sb, 1, "Bottles sold", String.valueOf(s.getBottlesSold()));
        row(sb, 0, "Total sales value", fmt(s.getSalesValue()));
        row(sb, 1, "Amount collected", fmt(s.getAmountCollected()));
        row(sb, 0, "Amount outstanding", fmt(s.getAmountOutstanding()));
        row(sb, 1, "Bottles currently in stock", String.valueOf(s.getStockQuantity()));
        row(sb, 0, "Current stock value", fmt(s.getStockValue()));
        sb.append("</table>");
        sb.append("<p><font size='2' color='#7A4A0F'>Sales and outstanding cover orders dated in the period; "
                + "collected covers payments dated in the period; stock is the current inventory.</font></p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    static String buildOutstandingHtml(List<SalesReport.OutstandingItem> items, BigDecimal total, LocalDate asAt) {
        StringBuilder sb = new StringBuilder();
        sb.append(pageStart(SalesReport.COMPANY, "Outstanding Payment Report"));
        sb.append("<p align='center'><b>As at:</b> ").append(asAt).append("</p>");
        if (items.isEmpty()) {
            sb.append("<p align='center'><i>No unpaid invoices were found.</i></p>");
        } else {
            sb.append("<table width='100%' cellpadding='5' cellspacing='0' border='0'>");
            sb.append("<tr bgcolor='#F9B233'>")
              .append(th("Customer", "34%", "left")).append(th("Order Ref", "9%", "right"))
              .append(th("Invoice", "9%", "right")).append(th("Qty", "7%", "right"))
              .append(th("Invoice Total", "14%", "right")).append(th("Paid", "13%", "right"))
              .append(th("Outstanding", "14%", "right")).append("</tr>");
            int i = 0;
            for (SalesReport.OutstandingItem it : items) {
                String name = it.getCustomerName().isEmpty() ? "[Missing Name]" : it.getCustomerName();
                sb.append("<tr bgcolor='").append(i++ % 2 == 0 ? "#FFF6DC" : "#FFE9A8").append("'>")
                  .append(td(esc(name), "left")).append(td(String.valueOf(it.getOrderID()), "right"))
                  .append(td(String.valueOf(it.getInvoiceID()), "right"))
                  .append(td(String.valueOf(it.getQuantity()), "right"))
                  .append(td(fmt(it.getInvoiceTotal()), "right")).append(td(fmt(it.getAmountPaid()), "right"))
                  .append(td(fmt(it.getAmountOutstanding()), "right")).append("</tr>");
            }
            sb.append("</table>");
        }
        sb.append("<p align='right'><b>Total outstanding: ").append(fmt(total)).append("</b></p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String pageStart(String company, String title) {
        return "<html><body style='font-family:SansSerif; color:#3E2410'>"
             + "<h1 align='center'>" + esc(company) + "</h1>"
             + "<h2 align='center'>" + esc(title) + "</h2>";
    }

    private static void row(StringBuilder sb, int shade, String label, String value) {
        sb.append("<tr bgcolor='").append(shade == 0 ? "#FFF6DC" : "#FFE9A8").append("'>")
          .append("<td width='60%'>").append(esc(label)).append("</td>")
          .append("<td width='40%' align='right'><b>").append(esc(value)).append("</b></td></tr>");
    }

    private static String th(String text, String width, String align) {
        return "<th width='" + width + "' align='" + align + "'>" + text + "</th>";
    }

    private static String td(String text, String align) {
        return "<td align='" + align + "' valign='top'>" + text + "</td>";
    }

    private static String fmt(BigDecimal v) { return String.format("$%.2f", v); }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------
    //  GUI
    // ------------------------------------------------------------------

    private void setHtml(String html) {
        pane.setText(html);
        pane.setCaretPosition(0);
    }

    private void showMessage(String html) {
        setHtml("<html><body style='font-family:SansSerif; color:#3E2410'><p>" + html + "</p></body></html>");
    }

    private void clearReport() {
        showMessage("No report generated.");
        lblStatus.setText("No report generated.");
        lblStatus.setForeground(Theme.DANGER);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showDbError(IllegalStateException ex) {
        System.err.println("[SalesReport] " + ex.getMessage());
        showError("Could not generate the report.\n\n" + ex.getMessage());
        clearReport();
    }

    private void buildGui() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Theme.HoneycombPanel root = new Theme.HoneycombPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        setContentPane(root);

        Theme.styleField(txtStart);
        Theme.styleField(txtEnd);
        lblStatus.setFont(Theme.BODY);
        lblStatus.setForeground(Theme.BROWN);

        pane.setEditable(false);
        pane.setBackground(Theme.CREAM);

        JPanel pnlPeriod = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        pnlPeriod.setOpaque(false);
        pnlPeriod.add(Theme.label("Start date (yyyy-MM-dd):")); pnlPeriod.add(txtStart);
        pnlPeriod.add(Theme.label("End date (yyyy-MM-dd):"));   pnlPeriod.add(txtEnd);

        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pnlActions.setOpaque(false);
        pnlActions.add(cmdSales);
        pnlActions.add(cmdOutstanding);

        JPanel pnlInput = new JPanel(new BorderLayout());
        pnlInput.setOpaque(false);
        pnlInput.add(pnlPeriod, BorderLayout.NORTH);
        pnlInput.add(pnlActions, BorderLayout.CENTER);
        pnlInput.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setOpaque(false);
        pnlTop.add(Theme.header("Sales Reports", "Sales, collections and outstanding payments"), BorderLayout.NORTH);
        pnlTop.add(pnlInput, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.getViewport().setBackground(Theme.CREAM);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 0, 10),
                BorderFactory.createLineBorder(Theme.BROWN, 2)));

        JPanel pnlCommand = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlCommand.setOpaque(false);
        pnlCommand.add(cmdClose);

        JPanel pnlBottom = new JPanel(new BorderLayout());
        pnlBottom.setOpaque(false);
        pnlBottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        pnlBottom.add(lblStatus, BorderLayout.WEST);
        pnlBottom.add(pnlCommand, BorderLayout.EAST);

        root.add(pnlTop, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(pnlBottom, BorderLayout.SOUTH);

        cmdSales.addActionListener(e -> generateSales());
        cmdOutstanding.addActionListener(e -> generateOutstanding());
        cmdClose.addActionListener(e -> dispose());

        setSize(900, 620);
        setLocationRelativeTo(null);
    }
}
