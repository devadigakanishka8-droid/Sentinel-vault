package com.sentinelvault.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;

/** The building blocks every screen is made from: cards, buttons, text fields, drop-downs. */
final class Widgets {
    private Widgets() {}

    // ---------- labels ----------
    static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** SMALL CAPS caption used above fields and numbers. */
    static JLabel caption(String text, Color color) {
        Font f = Theme.sans(Font.BOLD, 11f)
                .deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.1f));
        return label(text.toUpperCase(), f, color);
    }

    // ---------- card ----------
    static class Card extends JPanel {
        private Color fill = Theme.CARD;
        private Color line = Theme.LINE;
        private int arc = 18;

        Card(LayoutManager lm) { super(lm); setOpaque(false); }
        Card() { this(new BorderLayout()); }

        Card fill(Color c) { fill = c; return this; }
        Card line(Color c) { line = c; return this; }
        Card arc(int a) { arc = a; return this; }
        Card pad(int top, int left, int bottom, int right) { setBorder(new EmptyBorder(top, left, bottom, right)); return this; }
        Card pad(int all) { return pad(all, all, all, all); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(fill);
            g2.fill(shape);
            if (line != null) { g2.setColor(line); g2.draw(shape); }
            g2.dispose();
        }

        /** Keeps children (tables, lists) inside the rounded corners. */
        @Override protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    // ---------- button ----------
    enum Kind { PRIMARY, GOLD, GHOST, GHOST_DARK, DANGER }

    static class Btn extends JButton {
        private final Kind kind;
        private String icon;
        private boolean hover;

        Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(Theme.sans(Font.BOLD, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        Btn icon(String name) { this.icon = name; return this; }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(getText()) + 36 + (icon != null ? 24 : 0);
            return new Dimension(Math.max(w, 44), 40);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            Color bg, fg, border = null;
            boolean on = hover && isEnabled();
            switch (kind) {
                case PRIMARY:    bg = on ? Theme.NAVY_3 : Theme.NAVY; fg = Theme.CREAM; break;
                case GOLD:       bg = on ? new Color(0xE4BE8E) : Theme.GOLD; fg = Theme.NAVY; break;
                case DANGER:     bg = on ? new Color(0xA24259) : new Color(0xB8546B); fg = Color.WHITE; break;
                case GHOST_DARK: bg = on ? Theme.NAVY_3 : new Color(0, 0, 0, 0); fg = Theme.CREAM; border = Theme.NAVY_LINE; break;
                default:         bg = on ? Theme.CREAM_DARK : new Color(0, 0, 0, 0); fg = Theme.NAVY; border = new Color(0xC9BEA2); break;
            }
            RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(bg);
            g2.fill(r);
            if (border != null) { g2.setColor(border); g2.draw(r); }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textW = fm.stringWidth(getText());
            int iconW = icon != null ? 24 : 0;
            int x = (getWidth() - textW - iconW) / 2;
            if (icon != null) { Icons.paint(g2, icon, x, (getHeight() - 17) / 2, 17, fg); x += iconW; }
            g2.setColor(fg);
            g2.drawString(getText(), x, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ---------- text fields ----------
    /** Draws the rounded box behind a text field. */
    static void paintBox(Graphics g, JComponent c, boolean dark) {
        Graphics2D g2 = Theme.aa(g);
        RoundRectangle2D r = new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, 14, 14);
        g2.setColor(dark ? Theme.NAVY_2 : new Color(0xFFFDF8));
        g2.fill(r);
        g2.setColor(c.hasFocus() ? Theme.GOLD : (dark ? Theme.NAVY_LINE : new Color(0xCFC4A9)));
        g2.setStroke(new BasicStroke(c.hasFocus() ? 1.6f : 1f));
        g2.draw(r);
        g2.dispose();
    }

    private static void styleText(javax.swing.text.JTextComponent c, boolean dark) {
        c.setOpaque(false);
        c.setFont(Theme.sans(Font.PLAIN, 14f));
        c.setForeground(dark ? Theme.CREAM : Theme.NAVY);
        c.setCaretColor(dark ? Theme.CREAM : Theme.NAVY);
        c.setSelectionColor(Theme.GOLD);
        c.setSelectedTextColor(Theme.NAVY);
        c.setBorder(new EmptyBorder(9, 14, 9, 14));
        c.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { c.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { c.repaint(); }
        });
    }

    static class Field extends JTextField {
        private final boolean dark;
        private String hint = "";
        Field(boolean dark) { this.dark = dark; styleText(this, dark); }
        Field hint(String h) { hint = h; return this; }
        @Override protected void paintComponent(Graphics g) {
            paintBox(g, this, dark);
            super.paintComponent(g);
            if (getText().isEmpty() && !hint.isEmpty()) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(dark ? Theme.MUTED_DARK : new Color(0x8F8E88));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, getInsets().left, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        }
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(d.width, 42); }
    }

    static class PassField extends JPasswordField {
        private final boolean dark;
        PassField(boolean dark) { this.dark = dark; styleText(this, dark); }
        @Override protected void paintComponent(Graphics g) { paintBox(g, this, dark); super.paintComponent(g); }
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(d.width, 42); }
    }

    // ---------- drop-down ----------
    static class Combo<E> extends JComboBox<E> {
        Combo(E[] items) {
            super(items);
            setOpaque(false);
            setFont(Theme.sans(Font.PLAIN, 13.5f));
            setForeground(Theme.NAVY);
            setBackground(new Color(0xFFFDF8));
            setBorder(new EmptyBorder(0, 6, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                        boolean sel, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                    l.setBorder(new EmptyBorder(8, 12, 8, 12));
                    l.setFont(Theme.sans(Font.PLAIN, 13.5f));
                    l.setForeground(Theme.NAVY);
                    l.setBackground(sel ? Theme.CREAM_DARK : new Color(0xFFFDF8));
                    return l;
                }
            });
            setUI(new BasicComboBoxUI() {
                @Override protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override protected void paintComponent(Graphics g) {
                            Graphics2D g2 = Theme.aa(g);
                            g2.setColor(Theme.MUTED);
                            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            int cx = getWidth() / 2, cy = getHeight() / 2;
                            g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                            g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                            g2.dispose();
                        }
                    };
                    b.setBorder(BorderFactory.createEmptyBorder());
                    b.setContentAreaFilled(false);
                    b.setFocusPainted(false);
                    return b;
                }
                @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
                /** Draw the chosen value ourselves, so Swing's blue "selected" colour never appears. */
                @Override public void paintCurrentValue(Graphics g, Rectangle b, boolean hasFocus) {
                    Object v = comboBox.getSelectedItem();
                    if (v == null) return;
                    Graphics2D g2 = Theme.aa(g);
                    g2.setFont(comboBox.getFont());
                    g2.setColor(Theme.NAVY);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(v.toString(), b.x + 8, b.y + (b.height + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
                    javax.swing.plaf.basic.BasicComboPopup p = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
                    p.setBorder(BorderFactory.createLineBorder(new Color(0xCFC4A9)));
                    p.getList().setSelectionBackground(Theme.CREAM_DARK);
                    p.getList().setSelectionForeground(Theme.NAVY);
                    p.getList().setBackground(new Color(0xFFFDF8));
                    return p;
                }
            });
        }
        @Override protected void paintComponent(Graphics g) { paintBox(g, this, false); super.paintComponent(g); }
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(Math.max(d.width, 200), 42); }
    }

    // ---------- segmented control (tabs) ----------
    static class Segmented extends JComponent {
        private final String[] options;
        private int selected;
        private final IntConsumer onChange;

        Segmented(String[] options, IntConsumer onChange) {
            this.options = options;
            this.onChange = onChange;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.sans(Font.BOLD, 13f));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int w = segmentWidth();
                    int i = Math.min(options.length - 1, Math.max(0, (e.getX() - 4) / w));
                    select(i);
                }
            });
        }

        void select(int i) {
            if (i == selected) return;
            selected = i;
            repaint();
            onChange.accept(i);
        }

        private int segmentWidth() {
            FontMetrics fm = getFontMetrics(getFont());
            int max = 0;
            for (String o : options) max = Math.max(max, fm.stringWidth(o));
            return max + 30;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(segmentWidth() * options.length + 8, 42); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(Theme.CREAM_DARK);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            int w = segmentWidth();
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < options.length; i++) {
                int x = 4 + i * w;
                if (i == selected) {
                    g2.setColor(Theme.NAVY);
                    g2.fill(new RoundRectangle2D.Float(x, 4, w, getHeight() - 8, 12, 12));
                }
                g2.setColor(i == selected ? Theme.CREAM : Theme.MUTED);
                g2.drawString(options[i], x + (w - fm.stringWidth(options[i])) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    // ---------- small helpers ----------
    static JPanel transparent(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    static Component gap(int w, int h) { return Box.createRigidArea(new Dimension(w, h)); }
}
