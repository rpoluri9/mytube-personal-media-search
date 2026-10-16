package com.mytube.service;

import com.mytube.service.image.DHashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Generates deterministic 320x180 thumbnails with plain Java2D: a diagonal
 * gradient whose colours are derived from the title's hash code, plus the
 * title and a type label drawn on top. Also computes the item's dHash.
 */
@Service
public class ThumbnailService {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;

    private final Path thumbnailDir;

    public ThumbnailService(@Value("${mytube.thumbnail-dir}") String thumbnailDir) throws IOException {
        this.thumbnailDir = Path.of(thumbnailDir);
        Files.createDirectories(this.thumbnailDir);
    }

    /**
     * Generates (or regenerates) the thumbnail for a title and returns its dHash.
     *
     * @param filename file name to write under the thumbnail dir, e.g. "rrr.png"
     * @param title    title text drawn on the thumbnail
     * @param typeLabel label such as "MOVIE" / "SONG"
     * @return the 64-bit dHash of the generated image
     */
    public long generateThumbnail(String filename, String title, String typeLabel) throws IOException {
        BufferedImage img = render(title, typeLabel);
        Path out = thumbnailDir.resolve(filename);
        ImageIO.write(img, "png", out.toFile());
        return DHashUtil.dhash(img);
    }

    /** Renders the thumbnail in memory (also handy for tests). */
    public BufferedImage render(String title, String typeLabel) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Deterministic gradient from the title hash.
        Random rnd = new Random(title.hashCode());
        Color c1 = new Color(30 + rnd.nextInt(90), 30 + rnd.nextInt(90), 60 + rnd.nextInt(120));
        Color c2 = new Color(10 + rnd.nextInt(60), 10 + rnd.nextInt(60), 30 + rnd.nextInt(80));
        GradientPaint gp = new GradientPaint(0, 0, c1, WIDTH, HEIGHT, c2);
        g.setPaint(gp);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Soft vignette circles for texture.
        g.setColor(new Color(255, 255, 255, 18));
        for (int i = 0; i < 5; i++) {
            int r = 30 + rnd.nextInt(70);
            g.fillOval(rnd.nextInt(WIDTH), rnd.nextInt(HEIGHT), r, r);
        }

        // Type badge (top-left).
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(12, 12, 12 + typeLabel.length() * 11, 30, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.drawString(typeLabel, 22, 33);

        // Title (bottom), wrapped to two lines.
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        String[] lines = wrap(title, fm, WIDTH - 32);
        int y = HEIGHT - 14 - (lines.length - 1) * 28;
        for (String line : lines) {
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(line, 18, y + 2);
            g.setColor(Color.WHITE);
            g.drawString(line, 16, y);
            y += 28;
        }
        g.dispose();
        return img;
    }

    private String[] wrap(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return new String[]{text};
        }
        String[] words = text.split(" ");
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        for (String w : words) {
            if (fm.stringWidth(line1 + " " + w) <= maxWidth || line1.length() == 0) {
                if (line1.length() > 0) line1.append(' ');
                line1.append(w);
            } else {
                if (line2.length() > 0) line2.append(' ');
                line2.append(w);
            }
        }
        return new String[]{line1.toString(), line2.toString()};
    }

    public Path getThumbnailDir() {
        return thumbnailDir;
    }
}
