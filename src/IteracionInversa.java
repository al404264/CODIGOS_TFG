import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class IteracionInversa extends JPanel {

    // --- Parámetros de imagen ---
    private final int pixels = 1000;   // imagen pixels x pixels (cuadrada)

    // --- Parámetro c = cRe + i cIm ---
    private final double cRe = -0.39054087021840056;
    private final double cIm = -0.5867879073469685;

    // --- Control de recursión ---
    private final int maxIter = 18;     // # niveles (genera 2^maxIter puntos)
    private final double D = 1e10;      // umbral para derAcu

    // --- Viewport (centrado y zoom) ---
    // Centro de la ventana en el plano complejo:
    private final double cx = 0.0;
    private final double cy = 0.0;

    // Semiancho base = 2.0 equivale a mostrar [-2,2] x [-2,2] cuando zoom=1
    private final double baseHalf = 2.0;

    // zoom > 1: acercar; 0 < zoom < 1: ALEJAR (verás la figura “más pequeña”)
    private final double zoom = 0.5; // p.ej. 0.5 = alejar 2×, 0.25 = alejar 4×

    // Límites del viewport (se calculan a partir de cx,cy, baseHalf y zoom)
    private final double xmin, xmax, ymin, ymax;

    private BufferedImage img;

    public IteracionInversa() {
        // calcular viewport respetando aspecto 1:1 (imagen cuadrada)
        double halfX = baseHalf / zoom;
        double halfY = halfX; // mismo porque pixels x pixels
        xmin = cx - halfX; xmax = cx + halfX;
        ymin = cy - halfY; ymax = cy + halfY;

        img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
        pintarFondo(Color.WHITE);

        // 1) PRECÁLCULO: z0 punto fijo repulsivo de z^2 - z + c = 0
        Complex c = new Complex(cRe, cIm);
        Complex z0 = fixedPointRepulsivo(c); // elige raíz con |2 z0| > 1

        // 2) ENTRADA: z=z0, derAcu=1, nIter=0
        iterarInversamente(z0, 1.0, 0);
    }

    // ---------- Algoritmo principal ----------
    private void iterarInversamente(Complex z, double derAcu, int nIter) {
        // 3) Corte
        if (nIter >= maxIter || derAcu > D) return;

        // 6) derAcu <- derAcu · |2z|
        derAcu *= 2.0 * z.abs();

        // 7-10) pintar
        int[] p = getPixelPosition(z.re, z.im);
        int px = p[0], py = p[1];
        if (0 <= px && px < pixels && 0 <= py && py < pixels) {
            img.setRGB(px, py, 0x000000);
        }

        // 11) raiz <- sqrt(z - c)
        Complex raiz = sqrtPrincipal(z.sub(new Complex(cRe, cIm)));

        // 12-13) Llamadas recursivas con ambas ramas
        iterarInversamente(raiz,       derAcu, nIter + 1);
        iterarInversamente(raiz.neg(), derAcu, nIter + 1);
    }

    // ---------- Utilidades complejas ----------
    private static class Complex {
        final double re, im;
        Complex(double re, double im) { this.re = re; this.im = im; }
        Complex add(Complex w) { return new Complex(re + w.re, im + w.im); }
        Complex sub(Complex w) { return new Complex(re - w.re, im - w.im); }
        Complex neg() { return new Complex(-re, -im); }
        double abs() { return Math.hypot(re, im); }
    }

    // sqrt complejo (rama principal): sqrt(r) * e^{i theta/2}
    private Complex sqrtPrincipal(Complex w) {
        double r = w.abs();
        if (r == 0.0) return new Complex(0, 0);
        double theta = Math.atan2(w.im, w.re);
        double rootR = Math.sqrt(r);
        double a = 0.5 * theta;
        return new Complex(rootR * Math.cos(a), rootR * Math.sin(a));
    }

    // z0 repulsivo: raíz de z^2 - z + c = 0 con |2z|>1
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
        return zPlus; // si ambos o ninguno, coge zPlus
    }

    // ---------- Mapeo a píxel con viewport ----------
    private int[] getPixelPosition(double x, double y) {
        // px = 0 en x = xmin, px = pixels-1 en x = xmax
        int px = (int) Math.round((x - xmin) * (pixels - 1) / (xmax - xmin));
        // Invertimos vertical: y = ymax -> py = 0 ; y = ymin -> py = pixels-1
        int py = (int) Math.round((ymax - y) * (pixels - 1) / (ymax - ymin));
        return new int[]{px, py};
    }

    // ---------- Dibujo ----------
    private void pintarFondo(Color color) {
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, pixels, pixels);
        g.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(img, 0, 0, null);
    }

    // ---------- Arranque ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IteracionInversa panel = new IteracionInversa();
            JFrame f = new JFrame("Julia por iteración inversa (viewport con zoom)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(panel);
            f.setSize(panel.pixels, panel.pixels);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
