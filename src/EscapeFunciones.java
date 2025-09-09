import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class EscapeFunciones extends JPanel {

    private double xmin, xmax, ymin, ymax;
    private int maxIter = 100;
    private double escape = 2.0;

    private BufferedImage img;

    // Valor fijo de c (puedes cambiarlo directamente en el código)
    private double cRe = -0.122561;
    private double cIm = 0.744862;

    public EscapeFunciones(int width, int height,
                           double xmin, double xmax,
                           double ymin, double ymax) {
        setPreferredSize(new Dimension(width, height));
        this.xmin = xmin; this.xmax = xmax;
        this.ymin = ymin; this.ymax = ymax;
    }

    private double[] pixelToComplex(int px, int py) {
        double x = xmin + (xmax - xmin) * (px + 0.5) / getWidth();
        double y = ymax - (ymax - ymin) * (py + 0.5) / getHeight();
        return new double[]{x, y};
    }

    private int colorAt(double zx, double zy) {
        double r2 = escape * escape;
        int n = 0;
        for (; n < maxIter; n++) {
            double zx2 = zx * zx - zy * zy + cRe;
            double zy2 = 2.0 * zx * zy + cIm;
            zx = zx2;
            zy = zy2;
            if (zx * zx + zy * zy > r2) break;
        }
        if (n == maxIter) return 0xFF000000; // negro para el conjunto de Julia

        // Coloración sencilla con HSB
        float hue = (float) n / maxIter;
        return Color.HSBtoRGB(hue, 0.8f, 1.0f);
    }

    private void render() {
        int w = getWidth(), h = getHeight();
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] z0 = pixelToComplex(x, y);
                img.setRGB(x, y, colorAt(z0[0], z0[1]));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (img == null) render();
        g.drawImage(img, 0, 0, null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Conjunto de Julia — Algoritmo de Escape");
            EscapeFunciones panel = new EscapeFunciones(800, 600, -2.0, 2.0, -1.5, 1.5);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
