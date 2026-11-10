package com.sentinelvault.ui;

import com.sentinelvault.net.Wire;

import javax.swing.*;

/**
 * Starts SentinelVault as a CLIENT: it holds no vault of its own and connects to a VaultServer
 * running on another laptop. Usage: ClientApp [host[:port]]
 */
public class ClientApp {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        String preset = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> start(preset));
    }

    private static void start(String preset) {
        String address = preset;
        while (true) {
            if (address == null || address.isBlank()) {
                address = (String) JOptionPane.showInputDialog(null,
                        "Address of the vault server (shown in the server window):",
                        "SentinelVault - connect", JOptionPane.QUESTION_MESSAGE, null, null, "localhost:" + Wire.DEFAULT_PORT);
                if (address == null) System.exit(0);
            }
            String host = address.trim();
            int port = Wire.DEFAULT_PORT;
            int colon = host.lastIndexOf(':');
            if (colon > 0) {
                try { port = Integer.parseInt(host.substring(colon + 1).trim()); host = host.substring(0, colon).trim(); }
                catch (NumberFormatException ignored) { }
            }
            RemoteApi api;
            try {
                api = new RemoteApi(host, port, com.sentinelvault.net.Tls.clientFactory(com.sentinelvault.net.Tls.CERT));
                api.ping();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "SentinelVault - connect", JOptionPane.ERROR_MESSAGE);
                address = null;
                continue;
            }
            App.launch(api, false);
            return;
        }
    }
}
