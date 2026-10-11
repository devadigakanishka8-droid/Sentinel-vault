package com.sentinelvault.ui;

import com.sentinelvault.security.KeyVault;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

/** Asks for the master passphrase that unlocks the vault's encryption keys. */
final class UnlockDialog {
    private UnlockDialog() {}

    private static int attempts = 0;

    /** Returns true once the vault is unlocked, false if the user quits. */
    static boolean ask() {
        boolean firstRun = !new File("vault_storage/master.salt").exists();
        Widgets.PassField pass = new Widgets.PassField(true);
        Widgets.PassField again = new Widgets.PassField(true);
        JLabel msg = Widgets.label(" ", Theme.sans(Font.PLAIN, 13f), new Color(0xE0869A));

        JDialog d = new JDialog((Frame) null, "SentinelVault", true);
        d.setUndecorated(true);
        d.setIconImage(Theme.windowIcon());
        boolean[] unlocked = {false};

        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(Theme.NAVY);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.NAVY_LINE);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                Theme.paintLogo(g2, (getWidth() - 84) / 2, 30, 84);
                g2.dispose();
            }
        };
        JPanel body = Widgets.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(130, 40, 28, 40));

        JLabel title = Widgets.label(firstRun ? "Master passphrase" : "Unlock the vault",
                Theme.serif(Font.BOLD, 26f), Theme.CREAM);
        JTextArea note = new JTextArea(firstRun
                ? "It protects every file key. At least 8 characters. If you forget it, files cannot be recovered."
                : "Enter the master passphrase to decrypt your files.");
        note.setEditable(false);
        note.setFocusable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setBorder(null);
        note.setFont(Theme.sans(Font.PLAIN, 13f));
        note.setForeground(Theme.MUTED_DARK);
        Dimension noteSize = new Dimension(360, firstRun ? 58 : 40);
        note.setPreferredSize(noteSize);
        note.setMaximumSize(noteSize);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        pass.setAlignmentX(Component.LEFT_ALIGNMENT);
        again.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        body.add(note);
        body.add(Box.createRigidArea(new Dimension(0, 20)));
        body.add(pass);
        if (firstRun) { body.add(Box.createRigidArea(new Dimension(0, 10))); body.add(again); }
        body.add(Box.createRigidArea(new Dimension(0, 8)));
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(msg);
        body.add(Box.createRigidArea(new Dimension(0, 12)));

        Widgets.Btn ok = new Widgets.Btn(firstRun ? "Create & unlock" : "Unlock", Widgets.Kind.GOLD);
        Widgets.Btn quit = new Widgets.Btn("Quit", Widgets.Kind.GHOST_DARK);
        JPanel buttons = Widgets.transparent(new GridLayout(1, 2, 10, 0));
        buttons.add(quit);
        buttons.add(ok);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(buttons);
        root.add(body, BorderLayout.CENTER);
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(ok);
        quit.addActionListener(e -> d.dispose());

        ok.addActionListener(e -> {
            char[] a = pass.getPassword(), b = again.getPassword();
            try {
                if (firstRun && !Arrays.equals(a, b)) { msg.setText("The two passphrases do not match."); return; }
                d.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                KeyVault.unlock(a);
                unlocked[0] = true;
                d.dispose();
            } catch (SecurityException ex) {
                msg.setText("Wrong master passphrase.");
                pass.setText("");
                if (++attempts >= 5) { d.dispose(); }
            } catch (Exception ex) {
                msg.setText(ex.getMessage() == null ? "Could not unlock the vault." : ex.getMessage());
            } finally {
                Arrays.fill(a, '\0');
                Arrays.fill(b, '\0');
                d.setCursor(Cursor.getDefaultCursor());
            }
        });

        d.setSize(440, firstRun ? 580 : 520);
        d.setLocationRelativeTo(null);
        d.setVisible(true);
        return unlocked[0];
    }
}
