package com.sentinelvault.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Colours, fonts and the logo. Every screen takes its look from this one class. */
public final class Theme {
    private Theme() {}

    // The two brand colours
    public static final Color NAVY  = new Color(0x101826);
    public static final Color CREAM = new Color(0xF1EAD9);

    // Shades made from them
    public static final Color NAVY_2    = new Color(0x182238);   // raised panels on navy
    public static final Color NAVY_3    = new Color(0x223050);   // hover on navy
    public static final Color NAVY_LINE = new Color(0x2C3A57);
    public static final Color CARD      = new Color(0xF8F4EA);   // cards on the cream background
    public static final Color CREAM_DARK = new Color(0xE4DAC3);
    public static final Color LINE      = new Color(0xD9CFB6);
    public static final Color MUTED     = new Color(0x5F6577);   // secondary text on cream
    public static final Color MUTED_DARK = new Color(0xA7ADBA);  // secondary text on navy
    public static final Color GOLD      = new Color(0xD8AD79);   // taken from the logo
    public static final Color GOLD_DEEP = new Color(0xA67C3E);

    // ---------- fonts ----------
    private static final Set<String> FAMILIES = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    private static final String SERIF = pick(Font.SERIF, "Georgia", "Cambria", "Palatino Linotype", "DejaVu Serif");
    private static final String SANS  = pick(Font.SANS_SERIF, "Segoe UI", "Inter", "Helvetica Neue", "Arial", "DejaVu Sans");

    private static String pick(String fallback, String... names) {
        for (String n : names) if (FAMILIES.contains(n)) return n;
        return fallback;
    }
    public static Font serif(int style, float size) { return new Font(SERIF, style, 12).deriveFont(size); }
    public static Font sans(int style, float size)  { return new Font(SANS, style, 12).deriveFont(size); }

    // ---------- status chips: {background, text} ----------
    public static Color[] chip(String key) {
        switch (key.toUpperCase()) {
            case "SUCCESS": case "ACTIVE":
                return new Color[]{new Color(0xD3E6DE), new Color(0x1F6A5E)};
            case "DENIED": case "DISABLED":
                return new Color[]{new Color(0xF0D5D9), new Color(0x9B3A50)};
            case "FAILED":
                return new Color[]{new Color(0xF3E2BF), new Color(0x86570F)};
            case "OWNER": case "OWNERS ONLY":
                return new Color[]{NAVY, CREAM};
            case "EDITOR": case "EDITORS +":
                return new Color[]{new Color(0xE6D2A8), new Color(0x5E4716)};
            default: // VIEWER, EVERYONE and anything else
                return new Color[]{new Color(0xDCD7C8), new Color(0x464C5E)};
        }
    }

    // ---------- logo (assets/logo.png, with a drawn padlock if the file is missing) ----------
    private static BufferedImage logoSource;
    private static boolean logoTried;
    private static final Map<Integer, Image> SCALED = new HashMap<>();

    private static BufferedImage logoSource() {
        if (!logoTried) {
            logoTried = true;
            try {
                File f = new File("assets/logo.png");
                if (f.isFile()) logoSource = ImageIO.read(f);
            } catch (Exception ignored) { }
        }
        return logoSource;
    }

    public static void paintLogo(Graphics2D g, int x, int y, int size) {
        BufferedImage src = logoSource();
        if (src != null) {
            Image scaled = SCALED.computeIfAbsent(size, s -> src.getScaledInstance(s, s, Image.SCALE_SMOOTH));
            g.drawImage(scaled, x, y, null);
            return;
        }
        // Fallback: a simple padlock so the app still looks right without the file
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.translate(x, y);
        d.scale(size / 100.0, size / 100.0);
        d.setColor(GOLD);
        d.setStroke(new BasicStroke(5f));
        d.draw(new Ellipse2D.Float(4, 4, 92, 92));
        d.fill(new RoundRectangle2D.Float(30, 46, 40, 32, 10, 10));
        d.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.draw(new Arc2D.Float(35, 24, 30, 36, 0, 180, Arc2D.OPEN));
        d.dispose();
    }

    public static Image windowIcon() {
        try {
            File f = new File("assets/icon.png");
            if (f.isFile()) return ImageIO.read(f);
        } catch (Exception ignored) { }
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paintLogo(g, 0, 0, 64);
        g.dispose();
        return img;
    }

    /** Smooth edges for everything we draw by hand. */
    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }
}
