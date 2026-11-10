package com.sentinelvault.net;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;

/**
 * TLS for the client/server link, so passwords and files are encrypted on the network.
 *
 * The server owns a private key in tls/server.p12 and a public certificate tls/server.crt.
 * Each client is given ONLY the public certificate and trusts that one certificate and nothing
 * else ("certificate pinning"). A stranger on the Wi-Fi cannot pretend to be the server.
 */
public final class Tls {
    private Tls() {}

    public static final File DIR = new File("tls");
    public static final File KEYSTORE = new File(DIR, "server.p12");
    public static final File CERT = new File(DIR, "server.crt");
    private static final String ALIAS = "sentinelvault";

    /** Password of the server key file. Override with -Dsv.tls.pass=... if you like. */
    private static char[] password() {
        String p = System.getProperty("sv.tls.pass");
        return (p == null || p.isEmpty() ? "sentinelvault-tls" : p).toCharArray();
    }

    /** Creates tls/server.p12 and tls/server.crt on the server's first run, using the JDK's keytool. */
    public static void ensureServerCert() throws Exception {
        if (KEYSTORE.isFile() && CERT.isFile()) return;
        DIR.mkdirs();
        KEYSTORE.delete();
        CERT.delete();
        String keytool = Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool").toString();
        String pass = new String(password());
        run(keytool, "-genkeypair", "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
                "-sigalg", "SHA256withRSA", "-validity", "825", "-dname", "CN=SentinelVault",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
                "-keystore", KEYSTORE.getPath(), "-storepass", pass, "-keypass", pass);
        run(keytool, "-exportcert", "-rfc", "-alias", ALIAS, "-keystore", KEYSTORE.getPath(),
                "-storepass", pass, "-file", CERT.getPath());
    }

    private static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("Could not create the TLS certificate: " + new String(out).trim());
        }
    }

    /** Server side: sockets that present our certificate. */
    public static ServerSocketFactory serverFactory(File keystore) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystore)) { ks.load(in, password()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx.getServerSocketFactory();
    }

    /** Client side: sockets that trust exactly the one certificate in the given file. */
    public static SocketFactory clientFactory(File certFile) throws Exception {
        if (!certFile.isFile()) {
            throw new IllegalStateException("Missing " + certFile.getPath() + ". Copy tls/server.crt from the "
                    + "server laptop into the tls folder of this project, then try again.");
        }
        Certificate cert;
        try (InputStream in = Files.newInputStream(certFile.toPath())) {
            cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("server", cert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }
}
