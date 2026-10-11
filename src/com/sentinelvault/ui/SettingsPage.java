package com.sentinelvault.ui;

import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/** Your account: change password, and lock the vault. */
class SettingsPage extends Page {

    private final JLabel nameLabel = Widgets.label(" ", Theme.serif(Font.BOLD, 26f), Theme.NAVY);
    private final JLabel roleLabel = Widgets.label(" ", Theme.sans(Font.BOLD, 12f), Theme.CREAM);
    private final Widgets.PassField current = new Widgets.PassField(false);
    private final Widgets.PassField next = new Widgets.PassField(false);
    private final Widgets.PassField confirm = new Widgets.PassField(false);
    private final Widgets.Btn saveBtn = new Widgets.Btn("Update password", Widgets.Kind.PRIMARY);

    SettingsPage(Ctx ctx) {
        super(ctx);
        JPanel grid = Widgets.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH; c.weighty = 0;
        c.gridx = 0; c.gridy = 0; c.weightx = 0.45; c.insets = new Insets(0, 0, 18, 9);
        grid.add(profileCard(), c);
        c.gridy = 1; c.insets = new Insets(0, 0, 0, 9);
        grid.add(vaultCard(), c);
        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0.55; c.insets = new Insets(0, 9, 0, 0);
        grid.add(passwordCard(), c);

        JPanel top = Widgets.transparent(new BorderLayout());
        top.add(grid, BorderLayout.NORTH);
        add(top, BorderLayout.CENTER);

        saveBtn.addActionListener(e -> save());
    }

    @Override String title() { return "Settings"; }
    @Override String subtitle() { return "Your account and the vault."; }

    @Override void refresh() {
        User u = ctx.user();
        nameLabel.setText(u.getUsername());
        roleLabel.setText(u.getRole().name());
        roleLabel.repaint();
    }

    private Widgets.Card profileCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 14)).pad(24, 26, 22, 26);
        card.add(Widgets.caption("Signed in as", Theme.MUTED), BorderLayout.NORTH);
        JPanel body = Widgets.transparent(new BorderLayout(0, 10));
        body.add(nameLabel, BorderLayout.NORTH);
        JPanel role = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JPanel pill = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.NAVY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        pill.setBorder(new EmptyBorder(5, 14, 5, 14));
        pill.add(roleLabel);
        role.add(pill);
        body.add(role, BorderLayout.CENTER);
        body.add(Widgets.label("<html><div style='width:270px'>Your role is read from the database on every action, so a change by an Owner applies at once.</div></html>",
                Theme.sans(Font.PLAIN, 13f), Theme.MUTED), BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private Widgets.Card vaultCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 14)).fill(Theme.NAVY).line(null).pad(24, 26, 22, 26);
        card.add(Widgets.label("The vault", Theme.serif(Font.BOLD, 20f), Theme.CREAM), BorderLayout.NORTH);
        card.add(Widgets.label("<html><div style='width:270px'>The master key is held in memory only while the vault is unlocked. "
                        + "Locking it signs you out and asks for the master passphrase next time.</div></html>",
                Theme.sans(Font.PLAIN, 13f), Theme.MUTED_DARK), BorderLayout.CENTER);
        Widgets.Btn lock = new Widgets.Btn("Lock vault & sign out", Widgets.Kind.GOLD).icon("lock");
        lock.addActionListener(e -> ctx.signOut(true));
        JPanel south = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(lock);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private Widgets.Card passwordCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 16)).pad(24, 26, 24, 26);
        card.add(Widgets.label("Change password", Theme.serif(Font.BOLD, 20f), Theme.NAVY), BorderLayout.NORTH);
        JPanel form = Widgets.transparent(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Dialogs.labelled("Current password", current));
        form.add(Box.createRigidArea(new Dimension(0, 14)));
        form.add(Dialogs.labelled("New password", next));
        form.add(Box.createRigidArea(new Dimension(0, 14)));
        form.add(Dialogs.labelled("Confirm new password", confirm));
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        JLabel rule = Widgets.label("8 to 72 characters, with at least one letter and one digit.",
                Theme.sans(Font.PLAIN, 12.5f), Theme.MUTED);
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(rule);
        card.add(form, BorderLayout.CENTER);
        JPanel south = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.add(saveBtn);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private void save() {
        char[] a = current.getPassword(), b = next.getPassword(), d = confirm.getPassword();
        String cur = new String(a), nw = new String(b), cf = new String(d);
        Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); Arrays.fill(d, '\0');
        saveBtn.setEnabled(false);
        Async.run(() -> { ctx.api().changePassword(ctx.user(), cur, nw, cf); return null; }, v -> {
            saveBtn.setEnabled(true);
            current.setText(""); next.setText(""); confirm.setText("");
            ctx.toast("Password updated.", false);
        }, t -> { saveBtn.setEnabled(true); fail(t); });
    }
}
