import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/** Bee / honey look and feel shared by every window. Call Theme.apply() before creating any window. */
public final class Theme {
    public static final Color HONEY = new Color(0xF9B233);   // main honey gold
    public static final Color AMBER = new Color(0xE08E0B);   // hover / selection
    public static final Color DARK  = new Color(0x3E2410);   // text, bee stripes
    public static final Color BROWN = new Color(0x7A4A0F);   // borders
    public static final Color CREAM = new Color(0xFFF6DC);   // background
    public static final Color WAX   = new Color(0xFFE9A8);   // alternate rows
    public static final Color COMB  = new Color(0xF6DFA0);   // honeycomb lines
    public static final Color DANGER = new Color(0x9B2C1A);

    public static final Font TITLE = new Font(Font.SERIF, Font.BOLD, 22);
    public static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);

    private Theme() {}

    public static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        UIManager.put("Panel.background", CREAM);
        UIManager.put("OptionPane.background", CREAM);
        UIManager.put("OptionPane.messageForeground", DARK);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", BODY);
        UIManager.put("Label.foreground", DARK);
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.disabledText", new Color(0x9A7B3F));
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(BODY_BOLD);
        l.setForeground(DARK);
        return l;
    }

    public static void styleField(JTextField f) {
        f.setFont(BODY);
        f.setForeground(DARK);
        f.setCaretColor(DARK);
        f.setBackground(new Color(0xFFFDF5));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AMBER, 2), new EmptyBorder(4, 6, 4, 6)));
    }

    /** Honey-gold banner with a bee, a title and an optional subtitle. */
    public static JPanel header(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(HONEY);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, DARK), new EmptyBorder(10, 14, 10, 14)));
        p.add(new JLabel(new BeeIcon(44)), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(TITLE);
        t.setForeground(DARK);
        text.add(t);
        if (subtitle != null) {
            JLabel s = new JLabel(subtitle);
            s.setFont(BODY);
            s.setForeground(BROWN);
            text.add(s);
        }
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    /** Honey header, wax/cream striped rows, amber selection, red "Out of Stock". */
    public static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setGridColor(COMB);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setFont(BODY);
        table.getTableHeader().setReorderingAllowed(false);

        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                    boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                l.setBackground(HONEY);
                l.setForeground(DARK);
                l.setFont(BODY_BOLD);
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, BROWN), new EmptyBorder(4, 6, 4, 6)));
                return l;
            }
        });

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                    boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, focus, row, col);
                l.setBorder(new EmptyBorder(0, 6, 0, 6));
                l.setBackground(sel ? AMBER : (row % 2 == 0 ? CREAM : WAX));
                boolean out = "Out of Stock".equals(v);
                l.setForeground(out && !sel ? DANGER : DARK);
                l.setFont(out ? BODY_BOLD : BODY);
                return l;
            }
        });
    }

    // ------------------------------------------------------------------
    //  Components
    // ------------------------------------------------------------------

    /** Pill-shaped honey button: gold normally, amber on hover, pale wax when disabled. */
    public static class HoneyButton extends JButton {
        public HoneyButton(String text) {
            super(text);
            setFont(BODY_BOLD);
            setForeground(DARK);
            setContentAreaFilled(false);
            setOpaque(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(7, 18, 7, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color fill = !isEnabled() ? WAX
                    : getModel().isPressed() ? new Color(0xC47A08)
                    : getModel().isRollover() ? AMBER : HONEY;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.setColor(isEnabled() ? BROWN : new Color(0xD8BF7A));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Cream background with a faint honeycomb pattern. */
    public static class HoneycombPanel extends JPanel {
        public HoneycombPanel(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(CREAM);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(COMB);
            g2.setStroke(new BasicStroke(1.5f));
            double r = 22, w = Math.sqrt(3) * r, h = 1.5 * r;
            for (int row = -1; row * h < getHeight() + r; row++) {
                for (int col = -1; col * w < getWidth() + w; col++) {
                    double cx = col * w + (row % 2 != 0 ? w / 2 : 0);
                    double cy = row * h;
                    Path2D hex = new Path2D.Double();
                    for (int i = 0; i < 6; i++) {
                        double a = Math.toRadians(60 * i - 30);
                        double px = cx + r * Math.cos(a), py = cy + r * Math.sin(a);
                        if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
                    }
                    hex.closePath();
                    g2.draw(hex);
                }
            }
            g2.dispose();
        }
    }

    /** A small hand-drawn bee (no image files needed). */
    public static class BeeIcon implements Icon {
        private final int s;
        public BeeIcon(int size) { this.s = size; }
        @Override public int getIconWidth() { return s; }
        @Override public int getIconHeight() { return s; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.5f));

            // wings
            g2.setColor(new Color(255, 255, 255, 220));
            Ellipse2D w1 = new Ellipse2D.Double(x + .26 * s, y + .06 * s, .24 * s, .40 * s);
            Ellipse2D w2 = new Ellipse2D.Double(x + .50 * s, y + .10 * s, .22 * s, .36 * s);
            g2.fill(w1); g2.fill(w2);
            g2.setColor(BROWN);
            g2.draw(w1); g2.draw(w2);

            // stinger
            Path2D sting = new Path2D.Double();
            sting.moveTo(x + .17 * s, y + .52 * s);
            sting.lineTo(x + .03 * s, y + .60 * s);
            sting.lineTo(x + .17 * s, y + .66 * s);
            sting.closePath();
            g2.setColor(DARK);
            g2.fill(sting);

            // striped body
            Ellipse2D body = new Ellipse2D.Double(x + .15 * s, y + .38 * s, .62 * s, .44 * s);
            g2.setColor(new Color(0xFFD24D));
            g2.fill(body);
            Shape oldClip = g2.getClip();
            g2.setClip(body);
            g2.setColor(DARK);
            g2.fillRect((int) (x + .32 * s), y, (int) (.09 * s) + 1, s);
            g2.fillRect((int) (x + .52 * s), y, (int) (.09 * s) + 1, s);
            g2.setClip(oldClip);
            g2.setColor(DARK);
            g2.draw(body);

            // head and antennae
            g2.fill(new Ellipse2D.Double(x + .72 * s, y + .44 * s, .24 * s, .26 * s));
            g2.drawLine((int) (x + .84 * s), (int) (y + .46 * s), (int) (x + .80 * s), (int) (y + .30 * s));
            g2.drawLine((int) (x + .88 * s), (int) (y + .48 * s), (int) (x + .94 * s), (int) (y + .32 * s));
            g2.dispose();
        }
    }
}
