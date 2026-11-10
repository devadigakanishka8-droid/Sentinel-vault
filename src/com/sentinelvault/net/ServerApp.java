package com.sentinelvault.net;

import com.sentinelvault.db.DatabaseManager;
import com.sentinelvault.security.KeyVault;
import com.sentinelvault.ui.BackendApi;
import com.sentinelvault.ui.Theme;
import com.sentinelvault.ui.UnlockDialog;

import javax.swing.*;
import java.awt.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Run this on the laptop that holds the vault. It asks for the master passphrase, then serves
 * the vault to SentinelVault clients on the same network. Keep the window open during the demo.
 */
public class ServerApp {

    public static void main(String[] args) {
        int port = Wire.DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) { }
        }
        final int usePort = port;
        System.setProperty("awt.useSystemAAFontSettings", "on");
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> start(usePort));
    }

    private static void start(int port) {
        try {
            DatabaseManager.initializeTables();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not open the database:\n" + e.getMessage(),
                    "SentinelVault server", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        if (!UnlockDialog.ask()) System.exit(0);

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logArea.setBackground(Theme.NAVY);
        logArea.setForeground(Theme.CREAM);
        logArea.setMargin(new Insets(10, 12, 10, 12));
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm:ss");
        javax.net.ServerSocketFactory tls;
        try {
            Tls.ensureServerCert();
            tls = Tls.serverFactory(Tls.KEYSTORE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not set up TLS:\n" + e.getMessage(),
                    "SentinelVault server", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        VaultServer server = new VaultServer(new BackendApi(), port, tls, line -> SwingUtilities.invokeLater(() -> {
            logArea.append(LocalTime.now().format(clock) + "  " + line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));
        try {
            server.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Could not start the server on port " + port + ":\n" + e.getMessage()
                    + "\n\nIs another copy already running?", "SentinelVault server", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        StringBuilder addresses = new StringBuilder();
        for (String ip : localAddresses()) addresses.append("   ").append(ip).append(":").append(port);
        JLabel head = new JLabel("<html><b>SentinelVault server is running (TLS on).</b><br>Give clients this address:"
                + addresses + "<br>and copy <b>tls/server.crt</b> into their tls folder.</html>");
        head.setForeground(Theme.NAVY);
        head.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JFrame f = new JFrame("SentinelVault server");
        f.setIconImage(Theme.windowIcon());
        f.getContentPane().setBackground(Theme.CREAM);
        f.add(head, BorderLayout.NORTH);
        f.add(new JScrollPane(logArea), BorderLayout.CENTER);
        f.setSize(640, 420);
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (JOptionPane.showConfirmDialog(f, "Stop the server? Connected clients will be cut off.",
                        "SentinelVault server", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    server.stop();
                    KeyVault.lock();
                    System.exit(0);
                }
            }
        });
        f.setVisible(true);
    }

    /** The IPv4 addresses of this computer (the ones other laptops can use). */
    static List<String> localAddresses() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress ia : Collections.list(ni.getInetAddresses())) {
                    if (ia instanceof Inet4Address && !ia.isLinkLocalAddress()) found.add(ia.getHostAddress());
                }
            }
        } catch (Exception ignored) { }
        if (found.isEmpty()) found.add("localhost");
        return found;
    }
}
