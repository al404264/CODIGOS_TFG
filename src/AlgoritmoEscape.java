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

    private static final double PASO_FIJO = 0.2;

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
                    aplicarZoomCajaRecrear(inicioArrastre, finArrastre); // sustituye el panel por uno nuevo
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

    /* ---------- Zoom por recuadro (recrear viewer) ---------- */
    private void aplicarZoomCajaRecrear(Point a, Point b) {
        int x1 = Math.max(0, Math.min(a.x, b.x));
        int x2 = Math.min(getWidth() - 1, Math.max(a.x, b.x));
        int y1 = Math.max(0, Math.min(a.y, b.y));
        int y2 = Math.min(getHeight() - 1, Math.max(a.y, b.y));
        if (x2 - x1 < 10 || y2 - y1 < 10) return;

        double[] cInfIzq = pixelAComplejo(x1, y2); // inf-izda
        double[] cSupDer = pixelAComplejo(x2, y1); // sup-dcha
        double nuevoXMin = cInfIzq[0], nuevoYMin = cInfIzq[1], nuevoXMax = cSupDer[0], nuevoYMax = cSupDer[1];

        SwingUtilities.invokeLater(() -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (!(win instanceof JFrame)) {
                // fallback: si no hay frame, actualizamos en este mismo panel
                this.xMinimo = nuevoXMin; this.xMaximo = nuevoXMax; this.yMinimo = nuevoYMin; this.yMaximo = nuevoYMax;
                recalcularTodo();
                return;
            }
            JFrame frame = (JFrame) win;
            AlgoritmoEscape nuevo = new AlgoritmoEscape(getWidth(), getHeight(), nuevoXMin, nuevoXMax, nuevoYMin, nuevoYMax);
            nuevo.maxIteraciones = this.maxIteraciones;
            nuevo.radioEscape = this.radioEscape;
            frame.setContentPane(nuevo);
            frame.revalidate();
            frame.repaint();
            nuevo.requestFocusInWindow();
        });
    }

    /* ---------- Dibujo ---------- */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (necesitaRedibujar) dibujarFractal();
        if (imagen != null) g.drawImage(imagen, 0, 0, null);

        // Ejes + marcas
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

    /* ---------- HUD superior ---------- */
    private void dibujarHUDSuperior(Graphics g) {
        int hudW = 180, hudH = 48, m = 10;
        int xHud = m, yHud = m; // arriba-izquierda
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(xHud, yHud, hudW, hudH);
        g.setColor(Color.WHITE);

        String textoCursor = "Cursor: —";
        if (cursor != null) {
            double[] c = pixelAComplejo(cursor.x, cursor.y);
            textoCursor = String.format("Cursor: (%.6f, %.6f)", c[0], c[1]);
        }
        g.drawString(textoCursor, xHud + 10, yHud + 36);
    }

    /* ---------- Ejes y marcas ---------- */
    private void dibujarEjes(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean dibujaX = (yMinimo <= 0 && 0 <= yMaximo);
        boolean dibujaY = (xMinimo <= 0 && 0 <= xMaximo);

        g2.setColor(new Color(255, 255, 255, 180));
        g2.setStroke(new BasicStroke(1.2f));
        int y0 = -1, x0 = -1;

        if (dibujaX) { Point p1 = complejoAPixel(xMinimo, 0), p2 = complejoAPixel(xMaximo, 0); y0 = p1.y; g2.drawLine(0, y0, w, y0); }
        if (dibujaY) { Point p1 = complejoAPixel(0, yMinimo), p2 = complejoAPixel(0, yMaximo); x0 = p1.x; g2.drawLine(x0, 0, x0, h); }

        g2.setFont(g2.getFont().deriveFont(11f));
        g2.setColor(new Color(255, 255, 255, 220));
        int tick = 5;

        // X: marcas cada PASO_FIJO
        if (dibujaX) {
            int kInicio = (int) Math.ceil((xMinimo - 1e-12) / PASO_FIJO);
            int kFin   = (int) Math.floor((xMaximo + 1e-12) / PASO_FIJO);
            int maxMarcas = 5000;
            for (int k = kInicio, cnt = 0; k <= kFin && cnt < maxMarcas; k++, cnt++) {
                double x = redondearAlPaso(k * PASO_FIJO, PASO_FIJO);
                Point p = complejoAPixel(x, 0);
                g2.drawLine(p.x, y0 - tick, p.x, y0 + tick);
                String lab = formatearFijo01(x);
                g2.drawString(lab, p.x + 3, Math.min(h - 2, y0 + 14));
            }
        }
        // Y: marcas cada PASO_FIJO
        if (dibujaY) {
            int jInicio = (int) Math.ceil((yMinimo - 1e-12) / PASO_FIJO);
            int jFin    = (int) Math.floor((yMaximo + 1e-12) / PASO_FIJO);
            int maxMarcas = 5000;
            for (int j = jInicio, cnt = 0; j <= jFin && cnt < maxMarcas; j++, cnt++) {
                double y = redondearAlPaso(j * PASO_FIJO, PASO_FIJO);
                Point p = complejoAPixel(0, y);
                g2.drawLine(x0 - tick, p.y, x0 + tick, p.y);
                String lab = formatearFijo01(y);
                g2.drawString(lab, Math.min(w - 30, x0 + 8), p.y - 2);
            }
        }
        g2.dispose();
    }

    private static double redondearAlPaso(double x, double paso) { return Math.rint(x / paso) * paso; }
    private static String formatearFijo01(double v) {
        if (Math.abs(v) < 1e-12) v = 0.0;
        String s = String.format(java.util.Locale.US, "%.1f", v);
        if (s.equals("-0.0")) s = "0.0";
        return s;
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
            JFrame f = new JFrame("Conjunto de Mandelbrot — Algoritmo de escape");
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