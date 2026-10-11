package com.sentinelvault.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;

/** Themed pop-up windows (confirmations, small forms) and the toast notice. */
final class Dialogs {
    private Dialogs() {}

    /** Shows a form in a modal dialog. Returns true if the user pressed the main button. */
    static boolean form(Component parent, String title, String intro, JComponent body, String okLabel, boolean danger) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        boolean[] result = {false};

        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 14)).fill(Theme.CREAM).line(Theme.NAVY).arc(18).pad(26, 28, 22, 28);
        JPanel head = Widgets.transparent(new BorderLayout(0, 6));
        head.add(Widgets.label(title, Theme.serif(Font.BOLD, 22f), Theme.NAVY), BorderLayout.NORTH);
        if (intro != null) {
            JLabel l = Widgets.label("<html><div style='width:340px'>" + Fmt.esc(intro) + "</div></html>",
                    Theme.sans(Font.PLAIN, 13.5f), Theme.MUTED);
            head.add(l, BorderLayout.CENTER);
        }
        card.add(head, BorderLayout.NORTH);
        if (body != null) card.add(body, BorderLayout.CENTER);

        Widgets.Btn cancel = new Widgets.Btn("Cancel", Widgets.Kind.GHOST);
        Widgets.Btn ok = new Widgets.Btn(okLabel, danger ? Widgets.Kind.DANGER : Widgets.Kind.PRIMARY);
        cancel.addActionListener(e -> d.dispose());
        ok.addActionListener(e -> { result[0] = true; d.dispose(); });
        JPanel buttons = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
        buttons.add(cancel);
        buttons.add(ok);
        card.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.NAVY);
        root.setBorder(new EmptyBorder(1, 1, 1, 1));
        root.add(card, BorderLayout.CENTER);
        d.setContentPane(root);
        d.getRootPane().registerKeyboardAction((ActionEvent e) -> d.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setLocationRelativeTo(owner);
        try { d.setShape(new RoundRectangle2D.Float(0, 0, d.getWidth(), d.getHeight(), 20, 20)); }
        catch (UnsupportedOperationException ignored) { }   // square corners on screens that can't round them
        d.setVisible(true);
        return result[0];
    }

    static boolean confirm(Component parent, String title, String message, String okLabel, boolean danger) {
        return form(parent, title, message, null, okLabel, danger);
    }

    /** Pairs a caption with a field for use inside a form. */
    static JPanel labelled(String caption, JComponent field) {
        JPanel p = Widgets.transparent(new BorderLayout(0, 6));
        p.add(Widgets.caption(caption, Theme.MUTED), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    // ---------- toast ----------
    /** A small notice that slides in at the bottom right and disappears by itself. */
    static void toast(JRootPane root, String message, boolean error) {
        JLayeredPane layer = root.getLayeredPane();
        JPanel t = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.NAVY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setColor(error ? new Color(0xE0869A) : Theme.GOLD);
                g2.fill(new RoundRectangle2D.Float(0, 10, 4, getHeight() - 20, 4, 4));
                g2.dispose();
            }
        };
        t.setOpaque(false);
        t.setBorder(new EmptyBorder(14, 20, 14, 22));
        JLabel text = Widgets.label("<html><div style='width:300px'>" + Fmt.esc(message) + "</div></html>",
                Theme.sans(Font.PLAIN, 13.5f), Theme.CREAM);
        t.add(text, BorderLayout.CENTER);
        Dimension size = t.getPreferredSize();
        t.setBounds(root.getWidth() - size.width - 28, root.getHeight() - size.height - 28, size.width, size.height);
        layer.add(t, JLayeredPane.POPUP_LAYER);
        layer.repaint();
        Timer timer = new Timer(error ? 5000 : 3200, e -> { layer.remove(t); layer.repaint(); });
        timer.setRepeats(false);
        timer.start();
    }
}
