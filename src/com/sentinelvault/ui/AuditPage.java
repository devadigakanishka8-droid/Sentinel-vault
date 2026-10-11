package com.sentinelvault.ui;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The audit trail. Owners see everyone's activity and can verify the hash chain; others see their own. */
class AuditPage extends Page {

    private final Tables.Model model = new Tables.Model("Time", "User", "Action", "File", "Status");
    private final JTable table = Tables.create(model, 190, 150, 190, 260, 120);
    private final Widgets.Field search = new Widgets.Field(false).hint("Search user, action or file");
    private final Widgets.Segmented filter;
    private final Widgets.Btn verifyBtn = new Widgets.Btn("Verify integrity", Widgets.Kind.PRIMARY).icon("shield");
    private final Widgets.Btn exportBtn = new Widgets.Btn("Export CSV", Widgets.Kind.GHOST).icon("download");
    private final Banner banner = new Banner();
    private List<AuditLogEntry> all = new ArrayList<>();
    private List<AuditLogEntry> shown = new ArrayList<>();
    private String statusFilter = "";

    AuditPage(Ctx ctx) {
        super(ctx);
        Tables.chipColumn(table, 4);
        filter = new Widgets.Segmented(new String[]{"All", "Success", "Denied", "Failed"}, i -> {
            statusFilter = i == 0 ? "" : new String[]{"", "SUCCESS", "DENIED", "FAILED"}[i];
            applyFilter();
        });

        JPanel bar = Widgets.transparent(new BorderLayout(14, 0));
        bar.setBorder(new EmptyBorder(0, 0, 14, 0));
        JPanel left = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        search.setPreferredSize(new Dimension(250, 42));
        left.add(search);
        left.add(filter);
        JPanel right = Widgets.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(exportBtn);
        right.add(verifyBtn);
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        JPanel top = Widgets.transparent(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        top.add(banner, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(Tables.card(table), BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        verifyBtn.addActionListener(e -> verify());
        exportBtn.addActionListener(e -> export());
    }

    @Override String title() { return "Audit log"; }
    @Override String subtitle() {
        return ctx.user().getRole() == Role.OWNER
                ? "A tamper-evident record of everything that happens in the vault."
                : "Your own activity. Only an Owner can see the full log.";
    }

    @Override void refresh() {
        User me = ctx.user();
        boolean owner = me.getRole() == Role.OWNER;
        verifyBtn.setVisible(owner);
        exportBtn.setVisible(owner);
        banner.clear();
        Async.run(() -> ctx.api().audit(me), list -> { all = list; applyFilter(); }, this::fail);
    }

    private void applyFilter() {
        String q = search.getText().trim().toLowerCase(Locale.ENGLISH);
        shown = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (AuditLogEntry e : all) {
            if (!statusFilter.isEmpty() && !statusFilter.equals(e.getStatus())) continue;
            String action = pretty(e.getAction());
            if (!q.isEmpty() && !(e.getUsername() + " " + action + " " + e.getFilename()).toLowerCase(Locale.ENGLISH).contains(q)) continue;
            shown.add(e);
            rows.add(new Object[]{Fmt.audit(e.getTimestamp()), e.getUsername(), action, e.getFilename(), e.getStatus()});
        }
        model.setRows(rows);
    }

    private static String pretty(String action) {
        String s = action.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void verify() {
        User me = ctx.user();
        verifyBtn.setEnabled(false);
        Async.run(() -> ctx.api().verifyAudit(me), ok -> {
            verifyBtn.setEnabled(true);
            if (ok) banner.show(true, "Audit chain intact", "All " + all.size() + " entries match their hashes. Nothing was edited or removed from the middle of the log.");
            else banner.show(false, "Tampering detected", "At least one audit entry was changed or removed after it was written.");
        }, t -> { verifyBtn.setEnabled(true); fail(t); });
    }

    private void export() {
        FileDialog fd = new FileDialog((Frame) ctx.window(), "Export audit log", FileDialog.SAVE);
        fd.setFile("audit-log.csv");
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        File target = new File(fd.getDirectory(), fd.getFile());
        List<AuditLogEntry> rows = new ArrayList<>(shown);
        Async.run(() -> {
            try (PrintWriter w = new PrintWriter(target, StandardCharsets.UTF_8)) {
                w.println("Time,User,Action,File,Status");
                for (AuditLogEntry e : rows) {
                    w.println(csv(e.getTimestamp()) + "," + csv(e.getUsername()) + "," + csv(e.getAction()) + ","
                            + csv(e.getFilename()) + "," + csv(e.getStatus()));
                }
            }
            return rows.size();
        }, n -> ctx.toast("Exported " + n + " entries to " + target.getName(), false), this::fail);
    }

    /** Quotes a CSV value, and defuses spreadsheet formulas ("=", "+", "-", "@") in filenames. */
    private static String csv(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.equals("-")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---------- result banner ----------
    private static class Banner extends JPanel {
        private boolean good;
        private final JLabel title = Widgets.label(" ", Theme.sans(Font.BOLD, 14f), Theme.NAVY);
        private final JLabel text = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), Theme.NAVY);

        Banner() {
            super(new BorderLayout(0, 2));
            setOpaque(false);
            setBorder(new EmptyBorder(14, 54, 14, 20));
            JPanel t = Widgets.transparent(new GridLayout(2, 1, 0, 2));
            t.add(title);
            t.add(text);
            add(t, BorderLayout.CENTER);
            setVisible(false);
        }

        void show(boolean ok, String heading, String body) {
            good = ok;
            title.setText(heading);
            text.setText(body);
            Color fg = ok ? new Color(0x1F6A5E) : new Color(0x9B3A50);
            title.setForeground(fg);
            setVisible(true);
            getParent().revalidate();
            repaint();
        }

        void clear() { setVisible(false); }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(good ? new Color(0xDCEBE4) : new Color(0xF4DCDF));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight() - 14, 14, 14));
            Icons.paint(g2, good ? "shield" : "alert", 16, 12, 24, good ? new Color(0x1F6A5E) : new Color(0x9B3A50));
            g2.dispose();
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, d.height + 14);
        }
    }
}
