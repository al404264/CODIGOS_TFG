import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;

public class IteracionInversa extends JPanel {

    // --- Imagen ---
    private final int pixels = 1000;   // imagen cuadrada: pixels x pixels

    // --- Parámetro c = cRe + i cIm ---
    private final double cRe = -0.765;
    private final double cIm = 0.12;

    // --- Control de recursión ---
    private final int maxIter = 18;  // genera 2^maxIter puntos
    private final double D = 1e10;   // umbral para derivada acumulada

    // --- Viewport (controla centro y zoom) ---
    // Para ver EXACTAMENTE [-2,2] × [-2,2]:
    private final double cx = 0.0;
    private final double cy = 0.0;
    private final double baseHalf = 2.0; // semiancho base
    private final double zoom = 1.0;     // zoom=1 => [-2,2]×[-2,2]

    // Límites del viewport
    private final double xmin, xmax, ymin, ymax;

    private BufferedImage img;

    public IteracionInversa() {
        double halfX = baseHalf / zoom;
        double halfY = halfX; // imagen cuadrada
        xmin = cx - halfX; xmax = cx + halfX;   // => -2 a 2
        ymin = cy - halfY; ymax = cy + halfY;   // => -2 a 2

        img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        // Fondo + ejes con ticks cada 0.1
        //pintarFondoYEjes();

        // 1) Punto fijo repulsivo de z^2 - z + c = 0
        Complex c = new Complex(cRe, cIm);
        Complex z0 = fixedPointRepulsivo(c);

        // 2) Iteración inversa determinista
        iterarInversamente(z0, 1.0, 0);
    }

    // ---------- Algoritmo principal ----------
    private void iterarInversamente(Complex z, double derAcu, int nIter) {
        if (nIter >= maxIter || derAcu > D) return;

        derAcu *= 2.0 * z.abs();

        int[] p = getPixelPosition(z.re, z.im);
        int px = p[0], py = p[1];
        if (0 <= px && px < pixels && 0 <= py && py < pixels) {
            img.setRGB(px, py, 0xFFFFFF);
        }

        Complex raiz = sqrtPrincipal(z.sub(new Complex(cRe, cIm)));
        iterarInversamente(raiz,       derAcu, nIter + 1);
        iterarInversamente(raiz.neg(), derAcu, nIter + 1);
    }

    // ---------- Complejos ----------
    private static class Complex {
        final double re, im;
        Complex(double re, double im) { this.re = re; this.im = im; }
        Complex sub(Complex w) { return new Complex(re - w.re, im - w.im); }
        Complex neg() { return new Complex(-re, -im); }
        double abs() { return Math.hypot(re, im); }
    }

    private Complex sqrtPrincipal(Complex w) {
        double r = w.abs();
        if (r == 0.0) return new Complex(0, 0);
        double theta = Math.atan2(w.im, w.re);
        double rootR = Math.sqrt(r);
        double a = 0.5 * theta;
        return new Complex(rootR * Math.cos(a), rootR * Math.sin(a));
    }

    private Complex fixedPointRepulsivo(Complex c) {
        // z = (1 ± sqrt(1 - 4c)) / 2
        Complex disc = sqrtPrincipal(new Complex(1 - 4*c.re, -4*c.im));
        Complex zPlus  = new Complex( (1 + disc.re)/2.0,  disc.im/2.0);
        Complex zMinus = new Complex( (1 - disc.re)/2.0, -disc.im/2.0);
        double dPlus  = 2.0 * Math.hypot(zPlus.re,  zPlus.im);
        double dMinus = 2.0 * Math.hypot(zMinus.re, zMinus.im);
        boolean repPlus  = dPlus  > 1.0;
        boolean repMinus = dMinus > 1.0;
        if (repPlus && !repMinus) return zPlus;
        if (repMinus && !repPlus) return zMinus;
        return zPlus; // si ambos o ninguno, toma zPlus
    }

    // ---------- Mapeo a píxel: ¡usar xmin,xmax,ymin,ymax! ----------
    private int[] getPixelPosition(double x, double y) {
        int px = (int) Math.round( (x - xmin) * (pixels - 1) / (xmax - xmin) );
        int py = (int) Math.round( (ymax - y) * (pixels - 1) / (ymax - ymin) ); // Y hacia arriba
        return new int[]{px, py};
    }

    // ---------- Ejes con ticks cada 0.1 ----------
    private void pintarFondoYEjes() {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fondo negro
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pixels, pixels);

        // Ejes principales
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1.5f));

        // Eje X (Im=0)
        if (ymin <= 0 && 0 <= ymax) {
            int[] a = getPixelPosition(xmin, 0);
            int[] b = getPixelPosition(xmax, 0);
            g.drawLine(a[0], a[1], b[0], b[1]);
        }

        // Eje Y (Re=0)
        if (xmin <= 0 && 0 <= xmax) {
            int[] a = getPixelPosition(0, ymin);
            int[] b = getPixelPosition(0, ymax);
            g.drawLine(a[0], a[1], b[0], b[1]);
        }

        // Ticks y etiquetas cada 0.1
        g.setColor(new Color(200, 200, 200));
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        DecimalFormat df = new DecimalFormat("0.0");

        double paso = 0.2;
        int tickLen = 6;
        int tickLenMajor = 10;

        // Ticks sobre el eje X (y=0), variando x
        if (ymin <= 0 && 0 <= ymax) {
            double x0 = Math.ceil(xmin / paso) * paso;
            int yPix = getPixelPosition(0, 0)[1];
            for (double x = x0; x <= xmax + 1e-12; x += paso) {
                int xPix = getPixelPosition(x, 0)[0];
                boolean major = isMultiple(x, 0.5) || isMultiple(x, 1.0);
                int len = major ? tickLenMajor : tickLen;
                g.drawLine(xPix, yPix - len/2, xPix, yPix + len/2);

                String label = fmt(df, x);
                int strW = g.getFontMetrics().stringWidth(label);
                int strH = g.getFontMetrics().getAscent();
                g.drawString(label, xPix - strW/2, yPix + len/2 + 2 + strH);
            }
        }

        // Ticks sobre el eje Y (x=0), variando y
        if (xmin <= 0 && 0 <= xmax) {
            double y0 = Math.ceil(ymin / paso) * paso;
            int xPix = getPixelPosition(0, 0)[0];
            for (double y = y0; y <= ymax + 1e-12; y += paso) {
                int yPix = getPixelPosition(0, y)[1];
                boolean major = isMultiple(y, 0.5) || isMultiple(y, 1.0);
                int len = major ? tickLenMajor : tickLen;
                g.drawLine(xPix - len/2, yPix, xPix + len/2, yPix);

                String label = fmt(df, y) + "i";
                int strW = g.getFontMetrics().stringWidth(label);
                int strH = g.getFontMetrics().getAscent();
                g.drawString(label, xPix + len/2 + 3, yPix + strH/2 - 2);
            }
        }

        // Origen marcado
        if (xmin <= 0 && 0 <= xmax && ymin <= 0 && 0 <= ymax) {
            int[] o = getPixelPosition(0, 0);
            g.setColor(Color.WHITE);
            g.fillOval(o[0]-2, o[1]-2, 4, 4);
        }

        g.dispose();
    }

    private boolean isMultiple(double x, double m) {
        double r = Math.abs(x / m - Math.rint(x / m));
        return r < 1e-9;
    }

    private String fmt(DecimalFormat df, double v) {
        String s = df.format(v);
        if (s.equals("-0.0")) s = "0.0";
        return s;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(img, 0, 0, null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IteracionInversa panel = new IteracionInversa();
            JFrame f = new JFrame("Julia por iteración inversa (ejes cada 0.1, ventana [-2,2]×[-2,2])");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(panel);
            f.setSize(panel.pixels, panel.pixels);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
