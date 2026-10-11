package com.sentinelvault.ui;

import java.awt.*;
import java.awt.geom.*;

/** Small line icons drawn with Java2D, so no image files are needed. */
final class Icons {
    private Icons() {}

    private static Line2D line(float x1, float y1, float x2, float y2) { return new Line2D.Float(x1, y1, x2, y2); }

    static void paint(Graphics g0, String name, int x, int y, int size, Color c) {
        Graphics2D g = Theme.aa(g0);
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "dashboard":
                g.draw(new RoundRectangle2D.Float(3, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Float(14, 3, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Float(3, 14, 7, 7, 2, 2));
                g.draw(new RoundRectangle2D.Float(14, 14, 7, 7, 2, 2));
                break;
            case "files": {
                Path2D p = new Path2D.Float();
                p.moveTo(3, 7); p.lineTo(9, 7); p.lineTo(11, 9); p.lineTo(19, 9);
                p.quadTo(21, 9, 21, 11); p.lineTo(21, 17); p.quadTo(21, 20, 18, 20);
                p.lineTo(6, 20); p.quadTo(3, 20, 3, 17); p.closePath();
                g.draw(p);
                break;
            }
            case "shared":
                g.draw(new Ellipse2D.Float(15, 2.5f, 5, 5));
                g.draw(new Ellipse2D.Float(4, 9.5f, 5, 5));
                g.draw(new Ellipse2D.Float(15, 16.5f, 5, 5));
                g.draw(line(8.6f, 11, 15.4f, 6.8f));
                g.draw(line(8.6f, 13, 15.4f, 17.2f));
                break;
            case "audit":
                g.draw(new RoundRectangle2D.Float(5, 3, 14, 18, 3, 3));
                g.draw(line(8.5f, 9, 15.5f, 9));
                g.draw(line(8.5f, 13, 15.5f, 13));
                g.draw(line(8.5f, 17, 12.5f, 17));
                break;
            case "users": {
                g.draw(new Ellipse2D.Float(5.5f, 4, 7, 7));
                Path2D p = new Path2D.Float();
                p.moveTo(2.5f, 20); p.curveTo(2.5f, 15.5f, 5.5f, 13.5f, 9, 13.5f);
                p.curveTo(12.5f, 13.5f, 15.5f, 15.5f, 15.5f, 20);
                g.draw(p);
                g.draw(new Arc2D.Float(13.5f, 4.5f, 6, 6, 90, -180, Arc2D.OPEN));
                break;
            }
            case "settings":
                g.draw(line(4, 7, 20, 7));
                g.draw(line(4, 12, 20, 12));
                g.draw(line(4, 17, 20, 17));
                g.fill(new Ellipse2D.Float(7, 4.8f, 4.4f, 4.4f));
                g.fill(new Ellipse2D.Float(13.5f, 9.8f, 4.4f, 4.4f));
                g.fill(new Ellipse2D.Float(6, 14.8f, 4.4f, 4.4f));
                break;
            case "lock":
                g.draw(new RoundRectangle2D.Float(5, 11, 14, 10, 3, 3));
                g.draw(new Arc2D.Float(8, 3, 8, 10, 0, 180, Arc2D.OPEN));
                g.draw(line(8, 8, 8, 11));
                g.draw(line(16, 8, 16, 11));
                break;
            case "upload":
                g.draw(line(12, 16, 12, 4.5f));
                g.draw(line(7, 9, 12, 4));
                g.draw(line(17, 9, 12, 4));
                g.draw(line(4, 20, 20, 20));
                break;
            case "download":
                g.draw(line(12, 4, 12, 15.5f));
                g.draw(line(7, 11, 12, 16));
                g.draw(line(17, 11, 12, 16));
                g.draw(line(4, 20, 20, 20));
                break;
            case "trash":
                g.draw(line(4, 7, 20, 7));
                g.draw(line(9, 7, 9, 4));
                g.draw(line(15, 7, 15, 4));
                g.draw(line(9, 4, 15, 4));
                g.draw(new RoundRectangle2D.Float(6, 7, 12, 14, 3, 3));
                g.draw(line(10, 11, 10, 17));
                g.draw(line(14, 11, 14, 17));
                break;
            case "search":
                g.draw(new Ellipse2D.Float(4, 4, 12, 12));
                g.draw(line(14, 14, 20, 20));
                break;
            case "shield": {
                Path2D p = new Path2D.Float();
                p.moveTo(12, 3); p.lineTo(20, 6); p.lineTo(20, 12);
                p.curveTo(20, 17, 16.5f, 20, 12, 21);
                p.curveTo(7.5f, 20, 4, 17, 4, 12);
                p.lineTo(4, 6); p.closePath();
                g.draw(p);
                g.draw(line(8.5f, 12, 11, 14.5f));
                g.draw(line(11, 14.5f, 15.5f, 9.5f));
                break;
            }
            case "logout":
                g.draw(line(10, 4, 5, 4));
                g.draw(line(5, 4, 5, 20));
                g.draw(line(5, 20, 10, 20));
                g.draw(line(10, 12, 20, 12));
                g.draw(line(16, 8, 20, 12));
                g.draw(line(16, 16, 20, 12));
                break;
            case "check":
                g.draw(line(5, 12.5f, 10, 17.5f));
                g.draw(line(10, 17.5f, 19, 7));
                break;
            case "alert": {
                Path2D p = new Path2D.Float();
                p.moveTo(12, 4); p.lineTo(21, 20); p.lineTo(3, 20); p.closePath();
                g.draw(p);
                g.draw(line(12, 10, 12, 14.5f));
                g.fill(new Ellipse2D.Float(11.1f, 16.4f, 1.8f, 1.8f));
                break;
            }
            case "plus":
                g.draw(line(12, 5, 12, 19));
                g.draw(line(5, 12, 19, 12));
                break;
            default:
                break;
        }
        g.dispose();
    }
}
