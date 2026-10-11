package com.sentinelvault.ui;

import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PermissionService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Owner only: change roles, enable or disable accounts, and see what each role may do. */
class UsersPage extends Page {

    private final Tables.Model model = new Tables.Model("User", "Role", "Status");
    private final JTable table = Tables.create(model, 260, 140, 140);
    private final Widgets.Btn roleBtn = new Widgets.Btn("Change role", Widgets.Kind.PRIMARY).icon("users");
    private final Widgets.Btn toggleBtn = new Widgets.Btn("Disable account", Widgets.Kind.GHOST);
    private List<Api.UserRow> rows = new ArrayList<>();

    UsersPage(Ctx ctx) {
        super(ctx);
        Tables.chipColumn(table, 1);
        Tables.chipColumn(table, 2);

        JPanel bar = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setBorder(new EmptyBorder(0, 0, 16, 0));
        bar.add(toggleBtn);
        bar.add(roleBtn);

        JPanel left = Widgets.transparent(new BorderLayout());
        left.add(bar, BorderLayout.NORTH);
        left.add(Tables.card(table), BorderLayout.CENTER);

        JPanel right = Widgets.transparent(new BorderLayout());
        right.setBorder(new EmptyBorder(0, 20, 0, 0));
        right.setPreferredSize(new Dimension(360, 10));
        right.add(matrixCard(), BorderLayout.NORTH);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);

        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        roleBtn.addActionListener(e -> changeRole());
        toggleBtn.addActionListener(e -> toggle());
        updateButtons();
    }

    @Override String title() { return "Users & roles"; }
    @Override String subtitle() { return "Decide who can upload, download and delete. Changes apply immediately."; }

    @Override void refresh() {
        User me = ctx.user();
        Async.run(() -> ctx.api().users(me), list -> {
            rows = list;
            List<Object[]> data = new ArrayList<>();
            for (Api.UserRow u : list) {
                data.add(new Object[]{u.username() + (u.id() == me.getId() ? "  (you)" : ""), u.role().name(),
                        u.active() ? "ACTIVE" : "DISABLED"});
            }
            model.setRows(data);
            updateButtons();
        }, this::fail);
    }

    private Api.UserRow selected() {
        int r = table.getSelectedRow();
        return r >= 0 && r < rows.size() ? rows.get(r) : null;
    }

    private void updateButtons() {
        Api.UserRow u = selected();
        boolean other = u != null && u.id() != ctx.user().getId();
        roleBtn.setEnabled(other);
        toggleBtn.setEnabled(other);
        toggleBtn.setText(u != null && !u.active() ? "Enable account" : "Disable account");
    }

    private void changeRole() {
        Api.UserRow u = selected();
        if (u == null) return;
        Widgets.Combo<Role> combo = new Widgets.Combo<>(Role.values());
        combo.setSelectedItem(u.role());
        if (!Dialogs.form(this, "Change role", "Choose the new role for " + u.username() + ".",
                Dialogs.labelled("Role", combo), "Save role", false)) return;
        Role chosen = (Role) combo.getSelectedItem();
        Async.run(() -> { ctx.api().changeRole(ctx.user(), u.id(), chosen); return null; },
                v -> { ctx.toast(u.username() + " is now " + chosen + ".", false); refresh(); }, this::fail);
    }

    private void toggle() {
        Api.UserRow u = selected();
        if (u == null) return;
        boolean enable = !u.active();
        if (!Dialogs.confirm(this, enable ? "Enable this account?" : "Disable this account?",
                enable ? u.username() + " will be able to sign in again."
                        : u.username() + " will be signed out of every action immediately and cannot sign in.",
                enable ? "Enable" : "Disable", !enable)) return;
        Async.run(() -> { ctx.api().setActive(ctx.user(), u.id(), enable); return null; },
                v -> { ctx.toast(u.username() + (enable ? " was enabled." : " was disabled."), false); refresh(); }, this::fail);
    }

    // ---------- role permission matrix (read live from PermissionService) ----------
    private Widgets.Card matrixCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 12)).pad(22, 24, 18, 24);
        card.add(Widgets.label("What each role can do", Theme.serif(Font.BOLD, 19f), Theme.NAVY), BorderLayout.NORTH);
        card.add(new JComponent() {
            final String[][] actions = {
                    {"View files", PermissionService.VIEW}, {"Download", PermissionService.DOWNLOAD},
                    {"Upload", PermissionService.UPLOAD}, {"Delete", PermissionService.DELETE},
                    {"Manage roles", PermissionService.MANAGE_ROLES}};
            @Override public Dimension getPreferredSize() { return new Dimension(300, 40 + actions.length * 40); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                Role[] roles = {Role.OWNER, Role.EDITOR, Role.VIEWER};
                int colW = 58, labelW = getWidth() - colW * 3;
                g2.setFont(Theme.sans(Font.BOLD, 10.5f).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.08f)));
                g2.setColor(Theme.MUTED);
                FontMetrics fm = g2.getFontMetrics();
                for (int i = 0; i < 3; i++) {
                    String n = roles[i].name();
                    g2.drawString(n, labelW + i * colW + (colW - fm.stringWidth(n)) / 2, 16);
                }
                for (int r = 0; r < actions.length; r++) {
                    int y = 30 + r * 40;
                    g2.setColor(Theme.LINE);
                    g2.drawLine(0, y, getWidth(), y);
                    g2.setFont(Theme.sans(Font.PLAIN, 13.5f));
                    g2.setColor(Theme.NAVY);
                    g2.drawString(actions[r][0], 0, y + 25);
                    for (int i = 0; i < 3; i++) {
                        boolean yes = PermissionService.canPerform(roles[i], actions[r][1]);
                        int cx = labelW + i * colW + colW / 2;
                        if (yes) Icons.paint(g2, "check", cx - 9, y + 11, 18, new Color(0x1F8A7A));
                        else { g2.setColor(new Color(0xB9B4A6)); g2.fillRect(cx - 5, y + 19, 10, 2); }
                    }
                }
                g2.dispose();
            }
        }, BorderLayout.CENTER);
        return card;
    }
}
