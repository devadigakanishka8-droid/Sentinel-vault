package com.sentinelvault.ui;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Overview: key numbers, a 7-day activity chart, vault security, recent activity and files. */
class DashboardPage extends Page {

    private final StatCard filesCard = new StatCard("Files in vault", "files");
    private final StatCard mineCard = new StatCard("Uploaded by you", "upload");
    private final StatCard sharedCard = new StatCard("Shared with you", "shared");
    private final StatCard alertCard = new StatCard("Security alerts", "alert");
    private final BarChart chart = new BarChart();
    private final JPanel activityList = Widgets.transparent(null);
    private final JPanel recentFiles = Widgets.transparent(null);
    private final JLabel chainTitle = Widgets.label("Audit log chain", Theme.sans(Font.BOLD, 13.5f), Theme.NAVY);
    private final JLabel chainNote = Widgets.label(" ", Theme.sans(Font.PLAIN, 12.5f), Theme.MUTED);
    private final JLabel activityHeading = Widgets.label("Recent activity", Theme.serif(Font.BOLD, 19f), Theme.NAVY);

    DashboardPage(Ctx ctx) {
        super(ctx);
        activityList.setLayout(new BoxLayout(activityList, BoxLayout.Y_AXIS));
        recentFiles.setLayout(new BoxLayout(recentFiles, BoxLayout.Y_AXIS));

        JPanel content = Widgets.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 0, 8, 6));

        // Row 1: four numbers
        JPanel stats = Widgets.transparent(new GridLayout(1, 4, 18, 0));
        stats.add(filesCard); stats.add(mineCard); stats.add(sharedCard); stats.add(alertCard);
        content.add(row(stats, 128));
        content.add(Box.createRigidArea(new Dimension(0, 18)));

        // Row 2: chart + security
        JPanel middle = Widgets.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH; c.weighty = 1;
        c.gridx = 0; c.weightx = 0.62; c.insets = new Insets(0, 0, 0, 9);
        middle.add(chartCard(), c);
        c.gridx = 1; c.weightx = 0.38; c.insets = new Insets(0, 9, 0, 0);
        middle.add(securityCard(), c);
        content.add(row(middle, 336));
        content.add(Box.createRigidArea(new Dimension(0, 18)));

        // Row 3: recent activity + recent files
        JPanel bottom = Widgets.transparent(new GridBagLayout());
        c.gridx = 0; c.weightx = 0.55; c.insets = new Insets(0, 0, 0, 9);
        bottom.add(listCard(activityHeading, activityList, "audit"), c);
        c.gridx = 1; c.weightx = 0.45; c.insets = new Insets(0, 9, 0, 0);
        bottom.add(listCard(Widgets.label("Recent files", Theme.serif(Font.BOLD, 19f), Theme.NAVY), recentFiles, "files"), c);
        content.add(row(bottom, 360));

        JScrollPane sp = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setUI(new Tables.SlimBar() {
            @Override protected void paintTrack(Graphics g, JComponent comp, Rectangle r) { }
        });
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        add(sp, BorderLayout.CENTER);
    }

    private static JComponent row(JComponent c, int height) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(10, height));
        c.setMinimumSize(new Dimension(10, height));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return c;
    }

    @Override String title() { return "Dashboard"; }
    @Override String subtitle() { return Fmt.greeting() + ", " + ctx.user().getUsername() + ". Here is what is happening in your vault."; }

    // ---------- data ----------
    @Override void refresh() {
        User me = ctx.user();
        Async.run(() -> new Object[]{ctx.api().files(me), ctx.api().audit(me),
                        me.getRole() == Role.OWNER ? (Object) ctx.api().verifyAudit(me) : null},
                data -> {
                    @SuppressWarnings("unchecked") List<FileEntry> files = (List<FileEntry>) data[0];
                    @SuppressWarnings("unchecked") List<AuditLogEntry> logs = (List<AuditLogEntry>) data[1];
                    show(me, files, logs, (Boolean) data[2]);
                }, this::fail);
    }

    private void show(User me, List<FileEntry> files, List<AuditLogEntry> logs, Boolean chainOk) {
        int mine = 0;
        for (FileEntry f : files) if (f.getOwnerId() == me.getId()) mine++;
        int alerts = 0;
        for (AuditLogEntry e : logs) if (!"SUCCESS".equals(e.getStatus())) alerts++;

        filesCard.set(String.valueOf(files.size()), me.getRole() == Role.OWNER ? "all active files" : "files you can open");
        mineCard.set(String.valueOf(mine), "encrypted by you");
        sharedCard.set(String.valueOf(files.size() - mine), "from other users");
        alertCard.set(String.valueOf(alerts), alerts == 0 ? "all clear" : "denied or failed actions");
        alertCard.warn(alerts > 0);

        // 7-day chart
        int[] counts = new int[7];
        String[] labels = new String[7];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) labels[i] = today.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        for (AuditLogEntry e : logs) {
            try {
                long ago = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(e.getTimestamp().substring(0, 10)), today);
                if (ago >= 0 && ago < 7) counts[6 - (int) ago]++;
            } catch (Exception ignored) { }
        }
        chart.set(counts, labels);

        // chain status
        if (chainOk == null) { chainTitle.setText("Tamper-evident log"); chainNote.setText("Verified by the Owner"); }
        else if (chainOk) { chainTitle.setText("Audit chain intact"); chainNote.setText("Every entry matches its hash"); }
        else { chainTitle.setText("Audit chain BROKEN"); chainNote.setText("An entry was changed or removed"); }

        activityHeading.setText(me.getRole() == Role.OWNER ? "Recent activity" : "Your recent activity");
        activityList.removeAll();
        int shown = 0;
        for (AuditLogEntry e : logs) { if (shown++ == 6) break; activityList.add(activityRow(e)); }
        if (logs.isEmpty()) activityList.add(emptyNote("Nothing has happened yet."));

        recentFiles.removeAll();
        shown = 0;
        for (FileEntry f : files) { if (shown++ == 5) break; recentFiles.add(fileRow(f)); }
        if (files.isEmpty()) recentFiles.add(emptyNote("No files yet. Upload your first one."));

        revalidate();
        repaint();
    }

    // ---------- pieces ----------
    private Widgets.Card chartCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 8)).pad(22, 24, 18, 24);
        JPanel head = Widgets.transparent(new BorderLayout());
        head.add(Widgets.label("Activity", Theme.serif(Font.BOLD, 19f), Theme.NAVY), BorderLayout.WEST);
        head.add(Widgets.label("last 7 days", Theme.sans(Font.PLAIN, 12.5f), Theme.MUTED), BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        return card;
    }

    private Widgets.Card securityCard() {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 10)).fill(Theme.NAVY).line(null).pad(22, 24, 18, 24);
        card.add(Widgets.label("Vault security", Theme.serif(Font.BOLD, 19f), Theme.CREAM), BorderLayout.NORTH);
        JPanel list = Widgets.transparent(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.add(secRow("AES-256-GCM encryption", "A separate key for every file"));
        list.add(secRow("Master key unlocked", "Protects all file keys"));
        list.add(secRow(chainTitle, chainNote));
        list.add(secRow("Account lockout", "5 wrong passwords, 5 minute lock"));
        list.add(secRow("BCrypt passwords", "Salted, cost 12"));
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JComponent secRow(String title, String note) {
        return secRow(Widgets.label(title, Theme.sans(Font.BOLD, 13.5f), Theme.CREAM),
                Widgets.label(note, Theme.sans(Font.PLAIN, 12.5f), Theme.MUTED_DARK));
    }

    private JComponent secRow(JLabel title, JLabel note) {
        title.setForeground(Theme.CREAM);
        note.setForeground(Theme.MUTED_DARK);
        JPanel r = Widgets.transparent(new BorderLayout(12, 0));
        r.setBorder(new EmptyBorder(6, 0, 6, 0));
        JComponent icon = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(26, 26); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(new Color(0x1F3A3A));
                g2.fill(new Ellipse2D.Float(0, 0, 26, 26));
                Icons.paint(g2, "check", 5, 5, 16, new Color(0x6FD0BE));
                g2.dispose();
            }
        };
        JPanel text = Widgets.transparent(new GridLayout(2, 1, 0, 1));
        text.add(title);
        text.add(note);
        r.add(icon, BorderLayout.WEST);
        r.add(text, BorderLayout.CENTER);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private Widgets.Card listCard(JLabel heading, JPanel list, String viewAllPage) {
        Widgets.Card card = new Widgets.Card(new BorderLayout(0, 6)).pad(22, 24, 14, 24);
        JPanel head = Widgets.transparent(new BorderLayout());
        head.add(heading, BorderLayout.WEST);
        JLabel more = Widgets.label("View all →", Theme.sans(Font.BOLD, 12.5f), Theme.GOLD_DEEP);
        more.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        more.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { ctx.goTo(viewAllPage); }
        });
        head.add(more, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        JPanel top = Widgets.transparent(new BorderLayout());
        top.add(list, BorderLayout.NORTH);
        card.add(top, BorderLayout.CENTER);
        return card;
    }

    private JComponent activityRow(AuditLogEntry e) {
        Color dot = "SUCCESS".equals(e.getStatus()) ? new Color(0x2F8F83)
                : "DENIED".equals(e.getStatus()) ? new Color(0xB8546B) : new Color(0xC08A1E);
        JPanel r = Widgets.transparent(new BorderLayout(12, 0));
        r.setBorder(new EmptyBorder(9, 0, 9, 0));
        JComponent d = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(10, 10); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(dot);
                g2.fill(new Ellipse2D.Float(1, 1, 8, 8));
                g2.dispose();
            }
        };
        JPanel dw = Widgets.transparent(new GridBagLayout());
        dw.add(d);
        r.add(dw, BorderLayout.WEST);
        r.add(Widgets.label("<html>" + Fmt.sentence(e) + "</html>", Theme.sans(Font.PLAIN, 13.5f), Theme.NAVY), BorderLayout.CENTER);
        r.add(Widgets.label(Fmt.ago(e.getTimestamp()), Theme.sans(Font.PLAIN, 12f), Theme.MUTED), BorderLayout.EAST);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return r;
    }

    private JComponent fileRow(FileEntry f) {
        JPanel r = Widgets.transparent(new BorderLayout(12, 0));
        r.setBorder(new EmptyBorder(7, 0, 7, 0));
        String ext = Fmt.extension(f.getFilename());
        JComponent badge = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(34, 34); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.NAVY);
                g2.fill(new RoundRectangle2D.Float(0, 0, 34, 34, 10, 10));
                g2.setFont(Theme.sans(Font.BOLD, ext.length() > 3 ? 8.5f : 9.5f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.GOLD);
                g2.drawString(ext, (34 - fm.stringWidth(ext)) / 2, (34 + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        JPanel text = Widgets.transparent(new GridLayout(2, 1, 0, 1));
        text.add(Widgets.label(f.getFilename(), Theme.sans(Font.BOLD, 13.5f), Theme.NAVY));
        text.add(Widgets.label(ctx.api().ownerName(f.getOwnerId()) + "  ·  " + Fmt.level(f.getAccessLevel()),
                Theme.sans(Font.PLAIN, 12f), Theme.MUTED));
        r.add(badge, BorderLayout.WEST);
        r.add(text, BorderLayout.CENTER);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        return r;
    }

    private JComponent emptyNote(String text) {
        JLabel l = Widgets.label(text, Theme.sans(Font.PLAIN, 13.5f), Theme.MUTED);
        l.setBorder(new EmptyBorder(24, 0, 0, 0));
        return l;
    }

    // ---------- stat card ----------
    private static class StatCard extends Widgets.Card {
        private final JLabel value = Widgets.label("-", Theme.serif(Font.BOLD, 38f), Theme.NAVY);
        private final JLabel note = Widgets.label(" ", Theme.sans(Font.PLAIN, 12.5f), Theme.MUTED);
        private final String icon;
        private boolean warn;

        StatCard(String caption, String icon) {
            super(new BorderLayout(0, 2));
            this.icon = icon;
            pad(18, 22, 16, 22);
            add(Widgets.caption(caption, Theme.MUTED), BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(note, BorderLayout.SOUTH);
        }

        void set(String v, String n) { value.setText(v); note.setText(n); }
        void warn(boolean w) { warn = w; value.setForeground(w ? new Color(0x9B3A50) : Theme.NAVY); repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(warn ? new Color(0xF0D5D9) : Theme.NAVY);
            g2.fill(new Ellipse2D.Float(getWidth() - 56, 16, 36, 36));
            Icons.paint(g2, icon, getWidth() - 48, 24, 20, warn ? new Color(0x9B3A50) : Theme.GOLD);
            g2.dispose();
        }
    }

    // ---------- bar chart ----------
    private static class BarChart extends JComponent {
        private int[] counts = new int[7];
        private String[] labels = {"", "", "", "", "", "", ""};

        void set(int[] c, String[] l) { counts = c; labels = l; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            int w = getWidth(), h = getHeight();
            int top = 26, bottom = 28;
            int max = 1;
            for (int c : counts) max = Math.max(max, c);
            int plotH = h - top - bottom;

            g2.setColor(Theme.LINE);
            for (int i = 0; i <= 3; i++) {
                int y = top + plotH * i / 3;
                g2.drawLine(0, y, w, y);
            }
            int slot = w / 7, barW = Math.min(44, slot - 26);
            for (int i = 0; i < 7; i++) {
                int bh = counts[i] == 0 ? 3 : Math.max(6, (int) Math.round(plotH * (counts[i] / (double) max)));
                int x = i * slot + (slot - barW) / 2;
                int y = top + plotH - bh;
                boolean today = i == 6;
                g2.setColor(today ? Theme.GOLD : (counts[i] == 0 ? Theme.CREAM_DARK : Theme.NAVY));
                g2.fill(new RoundRectangle2D.Float(x, y, barW, bh, 10, 10));
                g2.setFont(Theme.sans(Font.BOLD, 12f));
                FontMetrics fm = g2.getFontMetrics();
                String v = String.valueOf(counts[i]);
                g2.setColor(Theme.NAVY);
                if (counts[i] > 0) g2.drawString(v, x + (barW - fm.stringWidth(v)) / 2, y - 6);
                g2.setFont(Theme.sans(today ? Font.BOLD : Font.PLAIN, 12f));
                fm = g2.getFontMetrics();
                g2.setColor(today ? Theme.NAVY : Theme.MUTED);
                g2.drawString(labels[i], x + (barW - fm.stringWidth(labels[i])) / 2, h - 8);
            }
            g2.dispose();
        }
    }
}
