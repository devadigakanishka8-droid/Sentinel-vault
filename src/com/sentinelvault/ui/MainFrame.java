package com.sentinelvault.ui;

import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** The main window: navy sidebar on the left, cream workspace on the right. */
public class MainFrame extends JFrame implements Ctx {

    private final Api api;
    private User user;
    private final Consumer<Boolean> onSignOut;

    private final CardLayout cards = new CardLayout();
    private final JPanel pageHolder = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, NavItem> navs = new LinkedHashMap<>();
    private final JLabel titleLabel = Widgets.label(" ", Theme.serif(Font.BOLD, 31f), Theme.NAVY);
    private final JLabel subtitleLabel = Widgets.label(" ", Theme.sans(Font.PLAIN, 14f), Theme.MUTED);
    private final UserBadge badge = new UserBadge();
    private String current = "dashboard";

    /** onSignOut receives true when the user also asked to lock the vault. */
    public MainFrame(Api api, User user, Consumer<Boolean> onSignOut) {
        super("SentinelVault");
        this.api = api;
        this.user = user;
        this.onSignOut = onSignOut;

        setIconImage(Theme.windowIcon());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // Fit smaller laptop screens (e.g. 1366 x 768) as well as large ones
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1100, screen.width), Math.min(660, screen.height)));
        setSize(Math.min(1340, screen.width - 30), Math.min(840, screen.height - 20));
        setLocationRelativeTo(null);

        pages.put("dashboard", new DashboardPage(this));
        pages.put("files", new FilesPage(this));
        pages.put("shared", new SharedPage(this));
        pages.put("audit", new AuditPage(this));
        pages.put("users", new UsersPage(this));
        pages.put("settings", new SettingsPage(this));
        pageHolder.setOpaque(false);
        pages.forEach((k, p) -> pageHolder.add(p, k));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CREAM);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(workspace(), BorderLayout.CENTER);
        setContentPane(root);

        applyRole();
        show("dashboard");
    }

    // ---------- Ctx ----------
    @Override public Api api() { return api; }
    @Override public User user() { return user; }
    @Override public Window window() { return this; }
    @Override public void toast(String message, boolean error) { Dialogs.toast(getRootPane(), message, error); }
    @Override public void goTo(String page) { show(page); }
    @Override public void signOut(boolean lockVault) { dispose(); onSignOut.accept(lockVault); }

    // ---------- navigation ----------
    /** Shows a page. The user is re-read first, so role changes and disabled accounts take effect at once. */
    public void show(String key) {
        Async.run(() -> api.refresh(user), fresh -> {
            if (fresh == null) {
                JOptionPane.showMessageDialog(this, "Your account is no longer available.");
                signOut(false);
                return;
            }
            user = fresh;
            applyRole();
            String target = ("users".equals(key) && user.getRole() != Role.OWNER) ? "dashboard" : key;
            current = target;
            Page p = pages.get(target);
            titleLabel.setText(p.title());
            subtitleLabel.setText(p.subtitle());
            navs.forEach((k, n) -> n.setActive(k.equals(target)));
            cards.show(pageHolder, target);
            p.refresh();
        }, t -> toast(Async.message(t), true));
    }

    private void applyRole() {
        NavItem users = navs.get("users");
        if (users != null) users.setVisible(user.getRole() == Role.OWNER);
        badge.repaint();
    }

    // ---------- layout ----------
    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.NAVY);
        side.setPreferredSize(new Dimension(262, 10));

        side.add(new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(262, 104); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                Theme.paintLogo(g2, 24, 28, 48);
                g2.setFont(Theme.serif(Font.BOLD, 20f));
                g2.setColor(Theme.CREAM);
                g2.drawString("SentinelVault", 84, 52);
                g2.setFont(Theme.sans(Font.BOLD, 9.5f).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.16f)));
                g2.setColor(Theme.GOLD);
                g2.drawString("SECURE FILE VAULT", 85, 70);
                g2.dispose();
            }
        }, BorderLayout.NORTH);

        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(6, 0, 0, 0));
        navSection(nav, "Workspace");
        navItem(nav, "dashboard", "Dashboard", "dashboard");
        navItem(nav, "files", "Vault", "files");
        navItem(nav, "shared", "Shared files", "shared");
        navSection(nav, "Security");
        navItem(nav, "audit", "Audit log", "audit");
        navItem(nav, "users", "Users & roles", "users");
        navSection(nav, "Account");
        navItem(nav, "settings", "Settings", "settings");
        side.add(nav, BorderLayout.CENTER);

        badge.setPreferredSize(new Dimension(262, 92));
        badge.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getX() > 262 - 64) signOut(false);
            }
        });
        side.add(badge, BorderLayout.SOUTH);
        return side;
    }

    private void navSection(JPanel nav, String text) {
        JLabel l = Widgets.caption(text, new Color(0x6F7889));
        l.setBorder(new EmptyBorder(18, 30, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(l);
    }

    private void navItem(JPanel nav, String key, String label, String icon) {
        NavItem n = new NavItem(label, icon);
        n.setAlignmentX(Component.LEFT_ALIGNMENT);
        n.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { show(key); }
        });
        navs.put(key, n);
        nav.add(n);
    }

    private JComponent workspace() {
        JPanel ws = new JPanel(new BorderLayout());
        ws.setOpaque(false);

        JPanel header = Widgets.transparent(new BorderLayout(20, 0));
        header.setBorder(new EmptyBorder(30, 40, 22, 40));
        JPanel titles = Widgets.transparent(new GridLayout(2, 1, 0, 4));
        titles.add(titleLabel);
        titles.add(subtitleLabel);
        header.add(titles, BorderLayout.CENTER);
        header.add(statusChip(), BorderLayout.EAST);
        ws.add(header, BorderLayout.NORTH);

        pageHolder.setBorder(new EmptyBorder(0, 40, 32, 34));
        ws.add(pageHolder, BorderLayout.CENTER);
        return ws;
    }

    /** "Vault unlocked" pill with today's date. */
    private JComponent statusChip() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH));
        return new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(250, 44); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setFont(Theme.sans(Font.PLAIN, 13f));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Theme.MUTED);
                g2.drawString(date, getWidth() - fm.stringWidth(date), 17);
                String chip = "Vault unlocked";
                g2.setFont(Theme.sans(Font.BOLD, 12f));
                fm = g2.getFontMetrics();
                int w = fm.stringWidth(chip) + 42, h = 26, x = getWidth() - w, y = 22;
                g2.setColor(Theme.NAVY);
                g2.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
                Icons.paint(g2, "lock", x + 12, y + 5, 16, Theme.GOLD);
                g2.setColor(Theme.CREAM);
                g2.drawString(chip, x + 32, y + (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
    }

    // ---------- sidebar pieces ----------
    private static class NavItem extends JComponent {
        private final String label, icon;
        private boolean active, hover;

        NavItem(String label, String icon) {
            this.label = label;
            this.icon = icon;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
        void setActive(boolean a) { active = a; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(262, 46); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 46); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            if (active || hover) {
                g2.setColor(active ? Theme.NAVY_2 : new Color(0x151F32));
                g2.fill(new RoundRectangle2D.Float(14, 2, getWidth() - 28, getHeight() - 4, 14, 14));
            }
            if (active) {
                g2.setColor(Theme.GOLD);
                g2.fill(new RoundRectangle2D.Float(14, 12, 3.5f, getHeight() - 24, 4, 4));
            }
            Icons.paint(g2, icon, 34, 12, 22, active ? Theme.GOLD : Theme.MUTED_DARK);
            g2.setFont(Theme.sans(active ? Font.BOLD : Font.PLAIN, 14f));
            g2.setColor(active ? Theme.CREAM : new Color(0xC7CBD4));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, 68, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    /** Avatar, name, role and a sign-out button at the bottom of the sidebar. */
    private class UserBadge extends JComponent {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(Theme.NAVY_LINE);
            g2.drawLine(24, 0, getWidth() - 24, 0);
            String name = user.getUsername();
            g2.setColor(Theme.GOLD);
            g2.fill(new Ellipse2D.Float(24, 26, 42, 42));
            g2.setColor(Theme.NAVY);
            g2.setFont(Theme.serif(Font.BOLD, 19f));
            FontMetrics fm = g2.getFontMetrics();
            String initial = name.substring(0, 1).toUpperCase(Locale.ENGLISH);
            g2.drawString(initial, 24 + (42 - fm.stringWidth(initial)) / 2, 26 + (42 + fm.getAscent() - fm.getDescent()) / 2);
            g2.setFont(Theme.sans(Font.BOLD, 14f));
            g2.setColor(Theme.CREAM);
            g2.drawString(name.length() > 14 ? name.substring(0, 13) + "…" : name, 78, 44);
            g2.setFont(Theme.sans(Font.BOLD, 10.5f).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.12f)));
            g2.setColor(Theme.GOLD);
            g2.drawString(user.getRole().name(), 78, 61);
            setCursor(Cursor.getDefaultCursor());
            Icons.paint(g2, "logout", getWidth() - 56, 36, 22, Theme.MUTED_DARK);
            g2.dispose();
        }
        @Override public String getToolTipText(MouseEvent e) { return e.getX() > getWidth() - 64 ? "Sign out" : null; }
        { setToolTipText(""); }
    }
}
