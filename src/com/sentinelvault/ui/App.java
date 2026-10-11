package com.sentinelvault.ui;

import com.sentinelvault.db.DatabaseManager;
import com.sentinelvault.security.KeyVault;

import javax.swing.*;

/** Starts SentinelVault: unlock the vault, sign in, work. Run this class to launch the app. */
public class App {

    private static Api api;

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
        Runtime.getRuntime().addShutdownHook(new Thread(KeyVault::lock));   // keys never outlive the program
        showLogin();
    }

    /** Asks for the master passphrase if the vault is locked, then shows the sign-in screen. */
    static void showLogin() {
        if (!KeyVault.isUnlocked() && !UnlockDialog.ask()) System.exit(0);
        new LoginFrame(api, user -> {
            MainFrame main = new MainFrame(api, user, lockVault -> {
                if (lockVault) KeyVault.lock();
                showLogin();
            });
            main.setVisible(true);
        }).setVisible(true);
    }
}
