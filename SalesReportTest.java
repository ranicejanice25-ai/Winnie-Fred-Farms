import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Plain-Java checks for SalesReport (SRS 1.2.6 test scenarios TS-2 to TS-12).
 * Uses its own temporary database, so winniefred.db is never touched.
 *
 * Run:  javac -cp lib/sqlite-jdbc-3.36.0.3.jar *.java
 *       java  -cp .;lib/sqlite-jdbc-3.36.0.3.jar SalesReportTest      (use : instead of ; on Mac/Linux)
 */
public class SalesReportTest {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        File f = File.createTempFile("salesreport-test", ".db");
        f.deleteOnExit();
        DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + f.getAbsolutePath(), "test");
        if (!db.connectDatabase()) { System.out.println("Could not open test database"); System.exit(1); }
        createTables(db);
        SalesReport report = new SalesReport(db);
        LocalDate d10 = LocalDate.of(2026, 11, 10);

        // TS-2 / TS-6 / TS-10: nothing stored yet
        List<SalesReport.OutstandingItem> none = report.generateOutstandingReport();
        check("TS-2  no invoices -> empty outstanding list", none.isEmpty());
        check("TS-6  total outstanding is 0.00", report.totalOutstanding(none).toPlainString().equals("0.00"));
        SalesReport.Summary empty = report.generateSalesReport(d10, d10);
        check("TS-2  empty sales report flagged as 'no records'", empty.isEmpty());
        check("TS-10 zero stock shows 0 and 0.00",
                empty.getStockQuantity() == 0 && empty.getStockValue().toPlainString().equals("0.00"));

        // Same sample data as schema.sql: 22 bottles over 9 orders, stock 100 @ 3500
        db.insertRecord("INSERT INTO inventory VALUES (1,'Honey',100,3500.0,'2026-11-10')");
        db.insertRecord("INSERT INTO customers VALUES (1,'John Brown','j@x.com')");
        db.insertRecord("INSERT INTO customers VALUES (2,'Anne Shirley','a@x.com')");
        db.insertRecord("INSERT INTO customers VALUES (3,'Alexandria Catherine Montgomery Kensington the 3rd','al@x.com')");
        db.insertRecord("INSERT INTO customers VALUES (4,'Mary-Jane O''Connor <&>','m@x.com')");
        int[] qty = {3, 2, 5, 2, 1, 4, 2, 2, 1};
        for (int i = 0; i < 9; i++) {
            db.insertRecord("INSERT INTO orders VALUES (?,?,?,?,?,?,?)",
                    i + 1, (i % 4) + 1, "2026-11-10", "COMPLETED", qty[i] * 3500.0, qty[i], 3500.0);
        }

        // TS-9 / TS-10: known data
        SalesReport.Summary s = report.generateSalesReport(d10, d10);
        check("TS-9  bottles sold = 22", s.getBottlesSold() == 22);
        check("TS-9  sales value = 77000.00", s.getSalesValue().toPlainString().equals("77000.00"));
        check("TS-9  nothing collected yet", s.getAmountCollected().toPlainString().equals("0.00"));
        check("TS-9  all of it outstanding", s.getAmountOutstanding().toPlainString().equals("77000.00"));
        check("TS-10 10 bottles x 3500 style: stock 100 -> 350000.00",
                s.getStockQuantity() == 100 && s.getStockValue().toPlainString().equals("350000.00"));

        // TS-3 / TS-5: invoices for order 3 (5 bottles), order 5 (1 bottle) and order 1 (3 bottles)
        db.insertRecord("INSERT INTO invoices VALUES (1,3,'2026-11-10',17500.0)");
        db.insertRecord("INSERT INTO invoices VALUES (2,5,'2026-11-10',3500.0)");
        db.insertRecord("INSERT INTO invoices VALUES (3,1,'2026-11-10',10500.0)");
        List<SalesReport.OutstandingItem> out = report.generateOutstandingReport();
        check("TS-3  three unpaid invoices listed (only invoiced orders)", out.size() == 3);
        SalesReport.OutstandingItem o3 = find(out, 3);
        check("TS-3  order 3 shows customer, qty and amount as stored",
                o3 != null && o3.getCustomerName().startsWith("Alexandria") && o3.getQuantity() == 5
                && o3.getAmountOutstanding().toPlainString().equals("17500.00"));
        check("TS-5  total outstanding = 31500.00",
                report.totalOutstanding(out).toPlainString().equals("31500.00"));

        // TS-4: order 5 paid in full, order 3 part paid
        db.insertRecord("INSERT INTO payments VALUES (1,5,3500.0,'Cash','2026-11-12')");
        db.insertRecord("INSERT INTO payments VALUES (2,3,5000.0,'Bank','2026-11-12')");
        out = report.generateOutstandingReport();
        check("TS-4  fully paid order 5 no longer listed", find(out, 5) == null && out.size() == 2);
        check("TS-3  part-paid order 3 shows 12500.00 outstanding",
                find(out, 3).getAmountOutstanding().toPlainString().equals("12500.00"));
        check("TS-5  total outstanding = 23000.00",
                report.totalOutstanding(out).toPlainString().equals("23000.00"));

        // TS-7: periods pick up only orders and payments inside the period
        s = report.generateSalesReport(d10, d10);
        check("TS-7  orders on 10 Nov: collected 0.00 (payments are dated 12 Nov)",
                s.getAmountCollected().toPlainString().equals("0.00"));
        check("TS-9  outstanding on the 10 Nov orders = 77000 - 8500 = 68500.00",
                s.getAmountOutstanding().toPlainString().equals("68500.00"));
        s = report.generateSalesReport(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 30));
        check("TS-7  11-30 Nov: no orders", s.getOrderCount() == 0 && s.getBottlesSold() == 0);
        check("TS-7  11-30 Nov: payments collected = 8500.00", s.getAmountCollected().toPlainString().equals("8500.00"));
        check("TS-7  11-30 Nov: not treated as empty (payments exist)", !s.isEmpty());
        s = report.generateSalesReport(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12));
        check("TS-7  start/end dates are inclusive", s.getAmountCollected().toPlainString().equals("8500.00"));
        s = report.generateSalesReport(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 9));
        check("TS-7  period before any order: 0 orders", s.getOrderCount() == 0);

        // TS-8: end before start
        boolean rejected = false;
        try { report.generateSalesReport(LocalDate.of(2026, 11, 30), d10); }
        catch (IllegalArgumentException ex) { rejected = ex.getMessage().contains("earlier than the start date"); }
        check("TS-8  end before start -> message, no report", rejected);

        // Cancelled orders are not sales; epoch-millisecond dates still read
        db.insertRecord("INSERT INTO orders VALUES (10,1,'2026-11-10','CANCELLED',3500.0,1,3500.0)");
        long nov15 = LocalDate.of(2026, 11, 15).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        db.insertRecord("INSERT INTO orders VALUES (11,2,?,?,?,?,?)", nov15, "COMPLETED", 7000.0, 2, 3500.0);
        s = report.generateSalesReport(d10, d10);
        check("      cancelled order ignored (still 22 bottles)", s.getBottlesSold() == 22);
        s = report.generateSalesReport(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15));
        check("      order dated as epoch millis is found", s.getOrderCount() == 1 && s.getBottlesSold() == 2);

        // Overpayment never gives a negative outstanding
        db.insertRecord("INSERT INTO payments VALUES (3,1,99999.0,'Cash','2026-11-13')");
        out = report.generateOutstandingReport();
        check("      overpaid invoice is not listed", find(out, 1) == null);

        // TS-10: stock zero
        db.updateRecord("UPDATE inventory SET quantity = 0");
        s = report.generateSalesReport(d10, d10);
        check("TS-10 stock 0 -> 0 and 0.00", s.getStockQuantity() == 0 && s.getStockValue().toPlainString().equals("0.00"));
        db.updateRecord("UPDATE inventory SET quantity = 10");
        s = report.generateSalesReport(d10, d10);
        check("TS-10 10 bottles x 3500.00 = 35000.00", s.getStockValue().toPlainString().equals("35000.00"));

        // TS-11 / TS-12: what appears on screen
        String sales = SalesReportWindow.buildSalesHtml(s);
        check("TS-11 sales report shows company, title, period, values",
                sales.contains("Winnie-Fred Farms") && sales.contains("Sales Report")
                && sales.contains("2026-11-10 to 2026-11-10") && sales.contains("$77000.00")
                && sales.contains("$35000.00"));
        String outHtml = SalesReportWindow.buildOutstandingHtml(report.generateOutstandingReport(),
                report.totalOutstanding(report.generateOutstandingReport()), d10);
        check("TS-11 outstanding report shows company, title, total",
                outHtml.contains("Winnie-Fred Farms") && outHtml.contains("Outstanding Payment Report")
                && outHtml.contains("Total outstanding: $"));
        check("TS-12 long customer name is in the report in full (wraps in its cell)",
                outHtml.contains("Alexandria Catherine Montgomery Kensington the 3rd"));
        check("      names are HTML-escaped", !outHtml.contains("<&>"));
        String noneHtml = SalesReportWindow.buildOutstandingHtml(new java.util.ArrayList<>(), BigDecimal.ZERO.setScale(2), d10);
        check("TS-6  empty outstanding report says so and totals $0.00",
                noneHtml.contains("No unpaid invoices were found") && noneHtml.contains("$0.00"));

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static SalesReport.OutstandingItem find(List<SalesReport.OutstandingItem> list, int orderID) {
        for (SalesReport.OutstandingItem it : list) if (it.getOrderID() == orderID) return it;
        return null;
    }

    private static void check(String name, boolean ok) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
    }

    private static void createTables(DatabaseManager db) {
        db.runSql("CREATE TABLE inventory (inventoryID INTEGER PRIMARY KEY, itemName TEXT NOT NULL, "
                + "quantity INTEGER NOT NULL, unitPrice REAL NOT NULL, dateUpdated DATE)");
        db.runSql("CREATE TABLE customers (customerID INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT)");
        db.runSql("CREATE TABLE orders (orderID INTEGER PRIMARY KEY, customerID INTEGER NOT NULL, "
                + "orderDate DATE NOT NULL, orderStatus TEXT NOT NULL, totalAmount REAL NOT NULL, "
                + "quantity INTEGER, unitCost REAL)");
        db.runSql("CREATE TABLE invoices (invoiceID INTEGER PRIMARY KEY, orderID INTEGER NOT NULL, "
                + "invoiceDate DATE NOT NULL, totalAmount REAL NOT NULL)");
        // payments table is created by SalesReport itself
    }
}
