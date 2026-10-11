package com.sentinelvault.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Styled tables: calm rows, a small-caps header, pill-shaped status chips. */
final class Tables {
    private Tables() {}

    /** A read-only table model that is easy to refill. */
    static class Model extends AbstractTableModel {
        private final String[] columns;
        private List<Object[]> rows = new ArrayList<>();
        Model(String... columns) { this.columns = columns; }
        void setRows(List<Object[]> r) { rows = r; fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int c) { return columns[c]; }
        @Override public Object getValueAt(int r, int c) { return rows.get(r)[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }
    }

    static JTable create(Model model, int... widths) {
        JTable t = new JTable(model);
        t.setRowHeight(52);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.setBackground(Theme.CARD);
        t.setForeground(Theme.NAVY);
        t.setSelectionBackground(new Color(0xEBDFC2));
        t.setSelectionForeground(Theme.NAVY);
        t.setFont(Theme.sans(Font.PLAIN, 13.5f));
        t.setFocusable(false);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setDefaultRenderer(Object.class, new TextCell());

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setResizingAllowed(false);
        h.setPreferredSize(new Dimension(10, 42));
        h.setDefaultRenderer(new HeaderCell());
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return t;
    }

    static void chipColumn(JTable t, int column) {
        t.getColumnModel().getColumn(column).setCellRenderer(new ChipCell());
    }

    static void fileColumn(JTable t, int column) {
        t.getColumnModel().getColumn(column).setCellRenderer(new FileCell());
    }

    /** Wraps the table in a rounded card with slim scroll bars. */
    static Widgets.Card card(JTable t) {
        JScrollPane sp = new JScrollPane(t, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // an empty (not null) border: Swing re-applies its own grey border over a null one
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.CARD);
        sp.getVerticalScrollBar().setUI(new SlimBar());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, corner());
        Widgets.Card card = new Widgets.Card(new BorderLayout());
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private static JComponent corner() {
        JPanel p = new JPanel();
        p.setBackground(Theme.CREAM_DARK);
        return p;
    }

    // ---------- cell renderers ----------
    private static void line(Graphics g, JComponent c) {
        g.setColor(Theme.LINE);
        g.drawLine(0, c.getHeight() - 1, c.getWidth(), c.getHeight() - 1);
    }

    static class TextCell extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setBorder(new EmptyBorder(0, 18, 0, 12));
            setBackground(sel ? t.getSelectionBackground() : Theme.CARD);
            setForeground(c == 0 ? Theme.NAVY : new Color(0x3C4356));
            return this;
        }
        @Override protected void paintComponent(Graphics g) { super.paintComponent(g); line(g, this); }
    }

    static class HeaderCell extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v == null ? "" : v.toString().toUpperCase(), false, false, r, c);
            setFont(Theme.sans(Font.BOLD, 11f).deriveFont(
                    java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.08f)));
            setForeground(Theme.MUTED);
            setBackground(Theme.CREAM_DARK);
            setBorder(new EmptyBorder(0, 18, 0, 12));
            return this;
        }
    }

    /** Pill-shaped status label, e.g. SUCCESS / DENIED / OWNER. */
    static class ChipCell extends JPanel implements TableCellRenderer {
        private String text = "";
        private Color[] colors = Theme.chip("");
        private boolean selected;
        private Color selBg;

        ChipCell() { setOpaque(false); }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            text = v == null ? "" : v.toString();
            colors = Theme.chip(text);
            selected = sel;
            selBg = t.getSelectionBackground();
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(selected ? selBg : Theme.CARD);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(Theme.sans(Font.BOLD, 11.5f));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 24, h = 26;
            int x = 18, y = (getHeight() - h) / 2;
            g2.setColor(colors[0]);
            g2.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
            g2.setColor(colors[1]);
            g2.drawString(text, x + 12, y + (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.setColor(Theme.LINE);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }

    /** File name with a small badge showing its type (PDF, DOCX, ...). */
    static class FileCell extends JPanel implements TableCellRenderer {
        private String name = "", ext = "";
        private boolean selected;
        private Color selBg;

        FileCell() { setOpaque(false); }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            name = v == null ? "" : v.toString();
            ext = Fmt.extension(name);
            selected = sel;
            selBg = t.getSelectionBackground();
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(selected ? selBg : Theme.CARD);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int size = 34, x = 18, y = (getHeight() - size) / 2;
            g2.setColor(Theme.NAVY);
            g2.fill(new RoundRectangle2D.Float(x, y, size, size, 10, 10));
            g2.setFont(Theme.sans(Font.BOLD, ext.length() > 3 ? 8.5f : 9.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.GOLD);
            g2.drawString(ext, x + (size - fm.stringWidth(ext)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.setFont(Theme.sans(Font.BOLD, 13.5f));
            fm = g2.getFontMetrics();
            g2.setColor(Theme.NAVY);
            String shown = name;
            int max = getWidth() - (x + size + 14) - 8;
            while (fm.stringWidth(shown) > max && shown.length() > 4) shown = shown.substring(0, shown.length() - 2);
            if (!shown.equals(name)) shown += "…";
            g2.drawString(shown, x + size + 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.setColor(Theme.LINE);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }

    /** Thin, quiet scroll bar. */
    static class SlimBar extends BasicScrollBarUI {
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(Theme.CARD);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(new Color(0xCDBF9F));
            g2.fill(new RoundRectangle2D.Float(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 6, 6));
            g2.dispose();
        }
    }
}
