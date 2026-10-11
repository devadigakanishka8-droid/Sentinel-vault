package com.sentinelvault.ui;

import com.sentinelvault.model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Arrays;
import java.util.function.Consumer;

/** Sign in, create an account, or (on the very first run) create the Owner account. */
public class LoginFrame extends JFrame {

    private enum Mode { SIGN_IN, REGISTER, SETUP }

    private final Api api;
    private final Consumer<User> onLogin;
    private Mode mode = Mode.SIGN_IN;

    private final JLabel heading = Widgets.label(" ", Theme.serif(Font.BOLD, 34f), Theme.NAVY);
    private final JLabel sub = Widgets.label(" ", Theme.sans(Font.PLAIN, 14f), Theme.MUTED);
    private final Widgets.Field username = new Widgets.Field(false);
    private final Widgets.PassField password = new Widgets.PassField(false);
    private final Widgets.PassField confirm = new Widgets.PassField(false);
    private final JPanel confirmRow = Dialogs.labelled("Confirm password", confirm);
    private final Widgets.Btn submit = new Widgets.Btn("Sign in", Widgets.Kind.PRIMARY);
    private final JLabel message = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), new Color(0x9B3A50));
    private final JTextArea hint = new JTextArea(" ");
    private Widgets.Segmented tabs;
    private final JPanel tabsRow = Widgets.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));

    public LoginFrame(Api api, Consumer<User> onLogin) {
        super("SentinelVault — Sign in");
        this.api = api;
        this.onLogin = onLogin;
        setIconImage(Theme.windowIcon());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(brandPanel(), BorderLayout.WEST);
        root.add(formPanel(), BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(submit);

        setMode(Mode.SIGN_IN);
        // First run: no accounts exist yet, so the first account must be the Owner.
        Async.run(api::needsSetup, first -> { if (first) setMode(Mode.SETUP); }, t -> { });
        submit.addActionListener(e -> submit());
    }

    // ---------- left: brand ----------
    private JComponent brandPanel() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.NAVY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // quiet decorative rings
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(0x1B2740));
                for (int i = 0; i < 4; i++) {
                    int r = 300 + i * 110;
                    g2.draw(new Ellipse2D.Float(getWidth() - r / 2f - 60, getHeight() - r / 2f + 40, r, r));
                }
                Theme.paintLogo(g2, 64, 96, 116);
                g2.setFont(Theme.serif(Font.BOLD, 46f));
                g2.setColor(Theme.CREAM);
                g2.drawString("SentinelVault", 64, 262);
                g2.setFont(Theme.serif(Font.ITALIC, 19f));
                g2.setColor(Theme.GOLD);
                g2.drawString("Every file locked. Every action recorded.", 64, 300);

                String[][] points = {
                        {"AES-256-GCM encryption", "A separate key for every file"},
                        {"Role-based access", "Owner, Editor and Viewer roles"},
                        {"Tamper-evident audit log", "Every entry chained by hash"}};
                int y = 380;
                for (String[] pt : points) {
                    g2.setColor(new Color(0x1F2B45));
                    g2.fill(new Ellipse2D.Float(64, y - 20, 30, 30));
                    Icons.paint(g2, "check", 70, y - 14, 18, Theme.GOLD);
                    g2.setFont(Theme.sans(Font.BOLD, 14.5f));
                    g2.setColor(Theme.CREAM);
                    g2.drawString(pt[0], 110, y - 3);
                    g2.setFont(Theme.sans(Font.PLAIN, 13f));
                    g2.setColor(Theme.MUTED_DARK);
                    g2.drawString(pt[1], 110, y + 16);
                    y += 62;
                }
                g2.setFont(Theme.sans(Font.PLAIN, 12f));
                g2.setColor(new Color(0x6F7889));
                g2.drawString("Team SE_A_06  ·  Guided by Prof. Rujata Chaudhari", 64, getHeight() - 36);
                g2.dispose();
            }
        };
        p.setPreferredSize(new Dimension(520, 10));
        return p;
    }

    // ---------- right: form ----------
    private JComponent formPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.CREAM);

        JPanel form = Widgets.transparent(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(392, 520));

        tabs = new Widgets.Segmented(new String[]{"Sign in", "Create account"},
                i -> setMode(i == 0 ? Mode.SIGN_IN : Mode.REGISTER));
        tabsRow.add(tabs);

        add(form, tabsRow);
        add(form, Box.createRigidArea(new Dimension(0, 22)));
        add(form, heading);
        add(form, Box.createRigidArea(new Dimension(0, 8)));
        add(form, sub);
        add(form, Box.createRigidArea(new Dimension(0, 26)));
        add(form, Dialogs.labelled("Username", username));
        add(form, Box.createRigidArea(new Dimension(0, 16)));
        add(form, Dialogs.labelled("Password", password));
        add(form, Box.createRigidArea(new Dimension(0, 16)));
        add(form, confirmRow);
        add(form, Box.createRigidArea(new Dimension(0, 8)));
        add(form, message);
        add(form, Box.createRigidArea(new Dimension(0, 10)));
        submit.setPreferredSize(new Dimension(392, 46));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        add(form, submit);
        add(form, Box.createRigidArea(new Dimension(0, 16)));
        add(form, hint);

        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setOpaque(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setBorder(null);
        hint.setFont(Theme.sans(Font.PLAIN, 12.5f));
        hint.setForeground(Theme.MUTED);
        hint.setPreferredSize(new Dimension(392, 40));
        hint.setMaximumSize(new Dimension(392, 40));
        outer.add(form);
        return outer;
    }

    private static void add(JPanel form, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (c instanceof JPanel || c instanceof JButton) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        form.add(c);
    }

    private static void add(JPanel form, Component strut) { form.add(strut); }

    private void setMode(Mode m) {
        mode = m;
        message.setText(" ");
        confirm.setText("");
        boolean signIn = m == Mode.SIGN_IN;
        confirmRow.setVisible(!signIn);
        tabsRow.setVisible(m != Mode.SETUP);
        switch (m) {
            case SIGN_IN:
                heading.setText("Welcome back");
                sub.setText("Sign in to open your vault.");
                submit.setText("Sign in");
                hint.setText("Five wrong passwords lock an account for five minutes.");
                break;
            case REGISTER:
                heading.setText("Create account");
                sub.setText("Choose a username and a strong password.");
                submit.setText("Create account");
                hint.setText("New accounts start as Viewer. An Owner can change your role later. Passwords need 8 to 72 characters, with a letter and a digit.");
                break;
            default:
                heading.setText("Set up your vault");
                sub.setText("Create the Owner account. It manages users and roles.");
                submit.setText("Create Owner account");
                hint.setText("This is the first run, so there are no accounts yet. "
                        + "Passwords need 8 to 72 characters, with a letter and a digit.");
                break;
        }
        revalidate();
        repaint();
        if (m != Mode.SETUP && tabs != null) tabs.select(m == Mode.SIGN_IN ? 0 : 1);
        username.requestFocusInWindow();
    }

    // ---------- actions ----------
    private void submit() {
        String user = username.getText().trim();
        char[] p1 = password.getPassword(), p2 = confirm.getPassword();
        String pass = new String(p1), pass2 = new String(p2);
        Arrays.fill(p1, '\0');
        Arrays.fill(p2, '\0');

        if (user.isEmpty() || pass.isEmpty()) { error("Enter your username and password."); return; }
        if (mode != Mode.SIGN_IN && !pass.equals(pass2)) { error("The two passwords do not match."); return; }

        submit.setEnabled(false);
        message.setForeground(Theme.MUTED);
        message.setText("Working…");
        Mode m = mode;
        Async.run(() -> {
            if (m == Mode.SIGN_IN) return api.login(user, pass);
            if (m == Mode.SETUP) { api.registerFirstOwner(user, pass); return api.login(user, pass); }
            api.register(user, pass);
            return null;
        }, result -> {
            submit.setEnabled(true);
            if (m == Mode.REGISTER) {
                password.setText("");
                setMode(Mode.SIGN_IN);
                message.setForeground(new Color(0x1F6A5E));
                message.setText("Account created. You can sign in now.");
                return;
            }
            if (result == null) { error("Wrong username or password."); password.setText(""); return; }
            dispose();
            onLogin.accept(result);
        }, t -> { submit.setEnabled(true); error(Async.message(t)); });
    }

    private void error(String text) {
        message.setForeground(new Color(0x9B3A50));
        message.setText("<html>" + Fmt.esc(text) + "</html>");
    }
}
