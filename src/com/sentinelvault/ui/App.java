package com.sentinelvault.ui;

import com.sentinelvault.db.DatabaseManager;
import com.sentinelvault.security.KeyVault;

import javax.swing.*;

/** Starts SentinelVault: unlock the vault, sign in, work. Run this class to launch the app. */
public class App {

    private static Api api;
    /** True when this program holds the vault itself; false when it is only a client of a server. */
    private static boolean local = true;

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(App::start);
    }

    private static void start() {
        try {
            DatabaseManager.initializeTables();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open the database:\n" + e.getMessage(),
                    "SentinelVault", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        api = new BackendApi();
        local = true;
        Runtime.getRuntime().addShutdownHook(new Thread(KeyVault::lock));   // keys never outlive the program
        showLogin();
    }

    /** Starts the sign-in flow against a given Api. Used by ClientApp, which has no vault of its own. */
    static void launch(Api remote, boolean holdsVault) {
        api = remote;
        local = holdsVault;
        showLogin();
    }

    /** Asks for the master passphrase if the vault is locked, then shows the sign-in screen. */
    static void showLogin() {
        if (local && !KeyVault.isUnlocked() && !UnlockDialog.ask()) System.exit(0);
        new LoginFrame(api, user -> {
            MainFrame main = new MainFrame(api, user, lockVault -> {
                if (local) { if (lockVault) KeyVault.lock(); }
                else if (api instanceof RemoteApi r) r.logout();
                showLogin();
            });
            main.setVisible(true);
        }).setVisible(true);
    }
}
