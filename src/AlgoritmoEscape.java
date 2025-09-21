import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class AlgoritmoEscape extends JPanel {

    private double xMinimo, xMaximo, yMinimo, yMaximo;

    private int maxIteraciones = 1000;
    private double radioEscape = 2.0;

    private BufferedImage imagen;
    private boolean necesitaRedibujar = true;

    private Point inicioArrastre = null, finArrastre = null;
    private Point cursor = null;

    // Separación objetivo (en píxeles) entre marcas principales en los ejes
    private static final int TICK_TARGET_PX = 90;

    public AlgoritmoEscape(int ancho, int alto) {
        this(ancho, alto, -2.5, 1.0, -1.25, 1.25);
    }

    public AlgoritmoEscape(int ancho, int alto,
                           double xMinimo, double xMaximo, double yMinimo, double yMaximo) {
        setPreferredSize(new Dimension(ancho, alto));
        this.xMinimo = xMinimo; this.xMaximo = xMaximo; this.yMinimo = yMinimo; this.yMaximo = yMaximo;

        addMouseWheelListener(e -> {
            double factor = (e.getWheelRotation() < 0) ? 0.8 : 1.25;
            hacerZoomEn(e.getX(), e.getY(), factor);
        });

        MouseAdapter manejadorRaton = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { inicioArrastre = e.getPoint(); finArrastre = null; }
            @Override public void mouseDragged(MouseEvent e) { finArrastre = e.getPoint(); repaint(); }
            @Override public void mouseReleased(MouseEvent e) {
                if (inicioArrastre != null && finArrastre != null) {
                    aplicarZoomCaja(inicioArrastre, finArrastre); // mantiene el panel, recalcula en la nueva región
                }
                inicioArrastre = finArrastre = null;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) recentrarEn(e.getX(), e.getY());
            }
            @Override public void mouseMoved(MouseEvent e) { cursor = e.getPoint(); repaint(); }
            @Override public void mouseExited(MouseEvent e) { cursor = null; repaint(); }
            @Override public void mouseEntered(MouseEvent e) { cursor = e.getPoint(); repaint(); }
        };
        addMouseListener(manejadorRaton);
        addMouseMotionListener(manejadorRaton);

        /* Resize: ajusta aspecto de la región inicial (solo la primera vez) */
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                imagen = null;
                ajustarAspectoAlViewport();
                necesitaRedibujar = true;
                repaint();
            }
        });
    }

    /* ---------- Aspecto inicial ---------- */
    private void ajustarAspectoAlViewport() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double anchuraActual = xMaximo - xMinimo, alturaActual = yMaximo - yMinimo;
        double aspectoVista = w / h, aspectoMundo = anchuraActual / alturaActual;
        double cx = (xMinimo + xMaximo) / 2.0, cy = (yMinimo + yMaximo) / 2.0;
        if (Math.abs(aspectoVista - aspectoMundo) > 1e-12) {
            if (aspectoVista > aspectoMundo) anchuraActual = aspectoVista * alturaActual;
            else alturaActual = anchuraActual / aspectoVista;
            xMinimo = cx - anchuraActual / 2.0; xMaximo = cx + anchuraActual / 2.0;
            yMinimo = cy - alturaActual / 2.0; yMaximo = cy + alturaActual / 2.0;
        }
    }

    /* ---------- Mapeos píxel <-> complejo ---------- */
    private double[] pixelAComplejo(int px, int py) {
        double x = xMinimo + (xMaximo - xMinimo) * (px + 0.5) / getWidth();
        double y = yMaximo - (yMaximo - yMinimo) * (py + 0.5) / getHeight();
        return new double[]{x, y};
    }
    private Point complejoAPixel(double x, double y) {
        int px = (int) Math.round((x - xMinimo) / (xMaximo - xMinimo) * getWidth());
        int py = (int) Math.round((yMaximo - y) / (yMaximo - yMinimo) * getHeight());
        return new Point(px, py);
    }
    private int worldToPixelX(double x) {
        return (int)Math.round((x - xMinimo) / (xMaximo - xMinimo) * getWidth());
    }
    private int worldToPixelY(double y) {
        return (int)Math.round((yMaximo - y) / (yMaximo - yMinimo) * getHeight());
    }

    /* ---------- Dinámica: algoritmo de escape ---------- */
    private int colorEn(double cx, double cy) {
        double zx = 0.0, zy = 0.0, r2 = radioEscape * radioEscape;
        int n = 0;
        for (; n < maxIteraciones; n++) {
            double zx2 = zx * zx - zy * zy + cx;
            double zy2 = 2.0 * zx * zy + cy;
            zx = zx2; zy = zy2;
            if (zx * zx + zy * zy > r2) break;
        }
        if (n == maxIteraciones) return 0xFF000000;

        double modulo = Math.sqrt(zx * zx + zy * zy);
        double nu = n + 1 - Math.log(Math.log(modulo)) / Math.log(2.0);
        double t = Math.max(0.0, Math.min(1.0, nu / maxIteraciones));
        float tono = (float) (0.95f + 10.0 * t); tono = tono - (float) Math.floor(tono);
        int rgb = Color.HSBtoRGB(tono, 0.75f, 1.0f);
        return (0xFF << 24) | (rgb & 0x00FFFFFF);
    }

    private void dibujarFractal() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (imagen == null || imagen.getWidth() != w || imagen.getHeight() != h) {
            imagen = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] c = pixelAComplejo(x, y);
                imagen.setRGB(x, y, colorEn(c[0], c[1]));
            }
        }
        necesitaRedibujar = false;
    }

    private void recalcularTodo() { imagen = null; necesitaRedibujar = true; dibujarFractal(); repaint(); }

    /* ---------- Zoom centrado / recentrar ---------- */
    private void hacerZoomEn(int px, int py, double escala) {
        double[] c = pixelAComplejo(px, py);
        double cx = c[0], cy = c[1];
        double w = (xMaximo - xMinimo) * escala, h = (yMaximo - yMinimo) * escala;
        xMinimo = cx - w / 2.0; xMaximo = cx + w / 2.0;
        yMinimo = cy - h / 2.0; yMaximo = cy + h / 2.0;
        recalcularTodo();
    }
    private void recentrarEn(int px, int py) {
        double[] c = pixelAComplejo(px, py);
        double cx = c[0], cy = c[1];
        double w = (xMaximo - xMinimo), h = (yMaximo - yMinimo);
        xMinimo = cx - w / 2.0; xMaximo = cx + w / 2.0;
        yMinimo = cy - h / 2.0; yMaximo = cy + h / 2.0;
        recalcularTodo();
    }

    /* ---------- Zoom por recuadro (mismo panel, región nueva) ---------- */
    private void aplicarZoomCaja(Point a, Point b) {
        int x1 = Math.max(0, Math.min(a.x, b.x));
        int x2 = Math.min(getWidth() - 1, Math.max(a.x, b.x));
        int y1 = Math.max(0, Math.min(a.y, b.y));
        int y2 = Math.min(getHeight() - 1, Math.max(a.y, b.y));
        if (x2 - x1 < 10 || y2 - y1 < 10) return;

        double[] cInfIzq = pixelAComplejo(x1, y2); // inf-izda
        double[] cSupDer = pixelAComplejo(x2, y1); // sup-dcha
        xMinimo = cInfIzq[0]; yMinimo = cInfIzq[1];
        xMaximo = cSupDer[0]; yMaximo = cSupDer[1];
        ajustarAspectoAlViewport(); // mantén aspecto del viewport
        recalcularTodo();
    }

    /* ---------- Helpers para “paso agradable” y formato ---------- */
    private static double niceStep(double span, int pixels, int targetPixelSpacing) {
        if (span <= 0 || pixels <= 0) return 1.0;
        double raw = span * targetPixelSpacing / Math.max(1, pixels); // paso ideal en mundo
        double pow10 = Math.pow(10, Math.floor(Math.log10(Math.max(raw, 1e-300))));
        double base = raw / pow10;
        double nice;
        if (base <= 1.0) nice = 1.0;
        else if (base <= 2.0) nice = 2.0;
        else if (base <= 5.0) nice = 5.0;
        else nice = 10.0;
        return nice * pow10;
    }
    private static String formatTick(double v, double step) {
        int decimals = Math.max(0, (int)Math.ceil(-Math.log10(Math.max(step, 1e-12))));
        decimals = Math.min(decimals, 10);
        String fmt = "%." + decimals + "f";
        String s = String.format(java.util.Locale.US, fmt, v);
        if (s.startsWith("-0")) s = s.replace("-0", "0");
        return s;
    }

    /* ---------- Dibujo ---------- */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (necesitaRedibujar) dibujarFractal();
        if (imagen != null) g.drawImage(imagen, 0, 0, null);

        // Rejilla y ejes dinámicos
        dibujarEjes((Graphics2D) g);

        // Recuadro de selección mientras arrastras
        if (inicioArrastre != null && finArrastre != null) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = Math.min(inicioArrastre.x, finArrastre.x);
            int y = Math.min(inicioArrastre.y, finArrastre.y);
            int w = Math.abs(inicioArrastre.x - finArrastre.x);
            int h = Math.abs(inicioArrastre.y - finArrastre.y);
            g3.setColor(new Color(255, 255, 255, 160));
            g3.setStroke(new BasicStroke(2f));
            g3.drawRect(x, y, w, h);
            g3.setColor(new Color(255, 255, 255, 40));
            g3.fillRect(x, y, w, h);
            g3.dispose();
        }

        // HUD arriba: región + cursor
        dibujarHUDSuperior(g);
    }

    private void dibujarHUDSuperior(Graphics g) {
        int hudW = 370, hudH = 54, m = 10;
        int xHud = m, yHud = m; // arriba-izquierda
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(xHud, yHud, hudW, hudH);
        g.setColor(Color.WHITE);
        String textoRegion = String.format("Región: [%.6f, %.6f] × [%.6f, %.6f]", xMinimo, xMaximo, yMinimo, yMaximo);
        g.drawString(textoRegion, xHud + 10, yHud + 18);

        String textoCursor = "Cursor: —";
        if (cursor != null) {
            double[] c = pixelAComplejo(cursor.x, cursor.y);
            textoCursor = String.format("Cursor: (%.6f, %.6f)", c[0], c[1]);
        }
        g.drawString(textoCursor, xHud + 10, yHud + 36);
    }

    /* ---------- Ejes y marcas dinámicas ---------- */
    private void dibujarEjes(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double spanX = xMaximo - xMinimo;
        double spanY = yMaximo - yMinimo;
        double stepX = niceStep(spanX, w, TICK_TARGET_PX);
        double stepY = niceStep(spanY, h, TICK_TARGET_PX);

        // Rejilla suave (opcional)
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 35));
        int maxTicks = 2000;

        // Líneas verticales de rejilla
        int kIniX = (int)Math.ceil((xMinimo - 1e-12) / stepX);
        int kFinX = (int)Math.floor((xMaximo + 1e-12) / stepX);
        int count = 0;
        for (int k = kIniX; k <= kFinX && count < maxTicks; k++, count++) {
            double xv = k * stepX;
            int px = worldToPixelX(xv);
            g.drawLine(px, 0, px, h);
        }
        // Líneas horizontales de rejilla
        int kIniY = (int)Math.ceil((yMinimo - 1e-12) / stepY);
        int kFinY = (int)Math.floor((yMaximo + 1e-12) / stepY);
        count = 0;
        for (int j = kIniY; j <= kFinY && count < maxTicks; j++, count++) {
            double yv = j * stepY;
            int py = worldToPixelY(yv);
            g.drawLine(0, py, w, py);
        }

        // Ejes (si 0 está dentro)
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(1.6f));
        boolean dibujaX = (yMinimo <= 0 && 0 <= yMaximo);
        boolean dibujaY = (xMinimo <= 0 && 0 <= xMaximo);
        int y0 = -1, x0 = -1;
        if (dibujaX) { y0 = worldToPixelY(0); g.drawLine(0, y0, w, y0); }
        if (dibujaY) { x0 = worldToPixelX(0); g.drawLine(x0, 0, x0, h); }

        // Ticks y etiquetas (en borde y sobre eje si existe)
        g.setFont(g.getFont().deriveFont(11f));
        int tickLen = 5;

        // X: marcas
        g.setColor(new Color(255, 255, 255, 220));
        count = 0;
        for (int k = kIniX; k <= kFinX && count < maxTicks; k++, count++) {
            double xv = k * stepX;
            int px = worldToPixelX(xv);

            // Ticks en eje X si existe, si no, en borde inferior
            int yTickBase = dibujaX ? y0 : (h - 1);
            g.drawLine(px, yTickBase - tickLen, px, yTickBase + tickLen);

            // Etiqueta
            String lab = formatTick(xv, stepX);
            int labY = Math.min(h - 2, yTickBase + 16);
            g.drawString(lab, px + 3, labY);
        }

        // Y: marcas
        count = 0;
        for (int j = kIniY; j <= kFinY && count < maxTicks; j++, count++) {
            double yv = j * stepY;
            int py = worldToPixelY(yv);

            // Ticks en eje Y si existe, si no, en borde izquierdo
            int xTickBase = dibujaY ? x0 : 0;
            g.drawLine(xTickBase - tickLen, py, xTickBase + tickLen, py);

            // Etiqueta
            String lab = formatTick(yv, stepY);
            int labX = Math.min(w - 40, xTickBase + 8);
            g.drawString(lab, labX, py - 2);
        }

        g.dispose();
    }

    /* ---------- Teclado ---------- */
    private void asignarTeclas(JFrame frame) {
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_PLUS:
                    case KeyEvent.VK_EQUALS:
                        maxIteraciones = (int) Math.min(200000, Math.round(maxIteraciones * 1.25));
                        recalcularTodo(); break;
                    case KeyEvent.VK_MINUS:
                        maxIteraciones = (int) Math.max(10, Math.round(maxIteraciones / 1.25));
                        recalcularTodo(); break;
                    case KeyEvent.VK_R:
                        xMinimo = -2.5; xMaximo = 1.0; yMinimo = -1.25; yMaximo = 1.25;
                        ajustarAspectoAlViewport();
                        maxIteraciones = 1000;
                        recalcularTodo(); break;
                }
            }
        });
    }

    /* ---------- Main ---------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Conjunto de Mandelbrot — Algoritmo de escape (ejes dinámicos)");
            AlgoritmoEscape panel = new AlgoritmoEscape(960, 540);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.asignarTeclas(f);
        });
    }
}
