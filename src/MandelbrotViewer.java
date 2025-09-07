import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class MandelbrotViewer extends JPanel {

    /* ====== Parámetros matemáticos ====== */
    private double xmin, xmax, ymin, ymax;
    private int maxIter = 1000;
    private double escapeRadius = 2.0;

    /* ====== Render ====== */
    private BufferedImage img;
    private boolean needsRedraw = true;

    /* ====== Interacción ====== */
    private Point dragStart = null, dragEnd = null;
    private Point lastMouse = null; // para mostrar coordenadas del cursor

    /* ====== Ejes: marcas fijas cada 0.1 ====== */
    private static final double FIXED_STEP = 0.2;

    /* ====== Constructores ====== */
    public MandelbrotViewer(int width, int height) {
        this(width, height, -2.5, 1.0, -1.25, 1.25);
    }

    public MandelbrotViewer(int width, int height,
                            double xmin, double xmax, double ymin, double ymax) {
        setPreferredSize(new Dimension(width, height));
        this.xmin = xmin; this.xmax = xmax; this.ymin = ymin; this.ymax = ymax;

        /* Rueda: zoom centrado en cursor (redibuja en este mismo panel) */
        addMouseWheelListener(e -> {
            double factor = (e.getWheelRotation() < 0) ? 0.8 : 1.25;
            zoomAt(e.getX(), e.getY(), factor);
        });

        /* Arrastre: recuadro; doble clic: recentrar; mover: actualiza HUD */
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); dragEnd = null; }
            @Override public void mouseDragged(MouseEvent e) { dragEnd = e.getPoint(); repaint(); }
            @Override public void mouseReleased(MouseEvent e) {
                if (dragStart != null && dragEnd != null) {
                    applyBoxZoomRecreate(dragStart, dragEnd); // << sustituye el panel por uno nuevo
                }
                dragStart = dragEnd = null;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) recenterAt(e.getX(), e.getY());
            }
            @Override public void mouseMoved(MouseEvent e) { lastMouse = e.getPoint(); repaint(); }
            @Override public void mouseExited(MouseEvent e) { lastMouse = null; repaint(); }
            @Override public void mouseEntered(MouseEvent e) { lastMouse = e.getPoint(); repaint(); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        /* Resize: ajusta aspecto de la región inicial (solo la primera vez) */
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                img = null;
                adjustAspectToViewport();
                needsRedraw = true;
                repaint();
            }
        });
    }

    /* ---------- Aspecto inicial ---------- */
    private void adjustAspectToViewport() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double curW = xmax - xmin, curH = ymax - ymin;
        double viewAspect = w / h, worldAspect = curW / curH;
        double cx = (xmin + xmax) / 2.0, cy = (ymin + ymax) / 2.0;
        if (Math.abs(viewAspect - worldAspect) > 1e-12) {
            if (viewAspect > worldAspect) curW = viewAspect * curH;
            else curH = curW / viewAspect;
            xmin = cx - curW / 2.0; xmax = cx + curW / 2.0;
            ymin = cy - curH / 2.0; ymax = cy + curH / 2.0;
        }
    }

    /* ---------- Mapeos píxel <-> complejo ---------- */
    private double[] pixelToComplex(int px, int py) {
        double x = xmin + (xmax - xmin) * (px + 0.5) / getWidth();
        double y = ymax - (ymax - ymin) * (py + 0.5) / getHeight();
        return new double[]{x, y};
    }
    private Point complexToPixel(double x, double y) {
        int px = (int) Math.round((x - xmin) / (xmax - xmin) * getWidth());
        int py = (int) Math.round((ymax - y) / (ymax - ymin) * getHeight());
        return new Point(px, py);
    }

    /* ---------- Dinámica: algoritmo de escape ---------- */
    private int colorAt(double cx, double cy) {
        double zx = 0.0, zy = 0.0, r2 = escapeRadius * escapeRadius;
        int n = 0;
        for (; n < maxIter; n++) {
            double zx2 = zx * zx - zy * zy + cx;
            double zy2 = 2.0 * zx * zy + cy;
            zx = zx2; zy = zy2;
            if (zx * zx + zy * zy > r2) break;
        }
        if (n == maxIter) return 0xFF000000;

        double mod = Math.sqrt(zx * zx + zy * zy);
        double nu = n + 1 - Math.log(Math.log(mod)) / Math.log(2.0);
        double t = Math.max(0.0, Math.min(1.0, nu / maxIter));
        float hue = (float) (0.95f + 10.0 * t); hue = hue - (float)Math.floor(hue);
        int rgb = Color.HSBtoRGB(hue, 0.75f, 1.0f);
        return (0xFF << 24) | (rgb & 0x00FFFFFF);
    }

    private void render() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (img == null || img.getWidth() != w || img.getHeight() != h) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] c = pixelToComplex(x, y);
                img.setRGB(x, y, colorAt(c[0], c[1]));
            }
        }
        needsRedraw = false;
    }

    private void recomputeAll() { img = null; needsRedraw = true; render(); repaint(); }

    /* ---------- Zoom centrado / recentrar ---------- */
    private void zoomAt(int px, int py, double scale) {
        double[] c = pixelToComplex(px, py);
        double cx = c[0], cy = c[1];
        double w = (xmax - xmin) * scale, h = (ymax - ymin) * scale;
        xmin = cx - w / 2.0; xmax = cx + w / 2.0;
        ymin = cy - h / 2.0; ymax = cy + h / 2.0;
        recomputeAll();
    }
    private void recenterAt(int px, int py) {
        double[] c = pixelToComplex(px, py);
        double cx = c[0], cy = c[1];
        double w = (xmax - xmin), h = (ymax - ymin);
        xmin = cx - w / 2.0; xmax = cx + w / 2.0;
        ymin = cy - h / 2.0; ymax = cy + h / 2.0;
        recomputeAll();
    }

    /* ---------- Zoom por recuadro (recrear viewer) ---------- */
    private void applyBoxZoomRecreate(Point a, Point b) {
        int x1 = Math.max(0, Math.min(a.x, b.x));
        int x2 = Math.min(getWidth() - 1, Math.max(a.x, b.x));
        int y1 = Math.max(0, Math.min(a.y, b.y));
        int y2 = Math.min(getHeight() - 1, Math.max(a.y, b.y));
        if (x2 - x1 < 10 || y2 - y1 < 10) return;

        double[] cLL = pixelToComplex(x1, y2); // inf-izda
        double[] cUR = pixelToComplex(x2, y1); // sup-dcha
        double nxmin = cLL[0], nymin = cLL[1], nxmax = cUR[0], nymax = cUR[1];

        // Creamos un NUEVO viewer con los nuevos límites y sustituimos el panel actual.
        SwingUtilities.invokeLater(() -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (!(win instanceof JFrame)) {
                // fallback: si no hay frame, actualizamos en este mismo panel
                this.xmin = nxmin; this.xmax = nxmax; this.ymin = nymin; this.ymax = nymax;
                recomputeAll();
                return;
            }
            JFrame frame = (JFrame) win;
            MandelbrotViewer nuevo = new MandelbrotViewer(getWidth(), getHeight(), nxmin, nxmax, nymin, nymax);
            nuevo.maxIter = this.maxIter;
            nuevo.escapeRadius = this.escapeRadius;
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
        if (needsRedraw) render();
        if (img != null) g.drawImage(img, 0, 0, null);

        // Ejes + marcas cada 0.1
        drawAxes((Graphics2D) g);

        // Recuadro de selección mientras arrastras
        if (dragStart != null && dragEnd != null) {
            Graphics2D g3 = (Graphics2D) g.create();
            g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = Math.min(dragStart.x, dragEnd.x);
            int y = Math.min(dragStart.y, dragEnd.y);
            int w = Math.abs(dragStart.x - dragEnd.x);
            int h = Math.abs(dragStart.y - dragEnd.y);
            g3.setColor(new Color(255, 255, 255, 160));
            g3.setStroke(new BasicStroke(2f));
            g3.drawRect(x, y, w, h);
            g3.setColor(new Color(255, 255, 255, 40));
            g3.fillRect(x, y, w, h);
            g3.dispose();
        }

        // HUD arriba: región + cursor
        drawTopHUD(g);
    }

    /* ---------- HUD superior ---------- */
    private void drawTopHUD(Graphics g) {
        int hudW = 520, hudH = 48, m = 10;
        int xHud = m, yHud = m; // arriba-izquierda
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(xHud, yHud, hudW, hudH);
        g.setColor(Color.WHITE);
        g.drawString(String.format("Región: [%.6f, %.6f] × [%.6f, %.6f]", xmin, xmax, ymin, ymax),
                xHud + 10, yHud + 20);

        String cursorTxt = "Cursor: —";
        if (lastMouse != null) {
            double[] c = pixelToComplex(lastMouse.x, lastMouse.y);
            cursorTxt = String.format("Cursor: (%.6f, %.6f)", c[0], c[1]);
        }
        g.drawString(cursorTxt, xHud + 10, yHud + 36);
    }

    /* ---------- Ejes y marcas cada 0.1 ---------- */
    private void drawAxes(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        g2 = (Graphics2D) g2.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean drawX = (ymin <= 0 && 0 <= ymax);
        boolean drawY = (xmin <= 0 && 0 <= xmax);

        g2.setColor(new Color(255, 255, 255, 180));
        g2.setStroke(new BasicStroke(1.2f));
        int y0 = -1, x0 = -1;

        if (drawX) { Point p1 = complexToPixel(xmin, 0), p2 = complexToPixel(xmax, 0); y0 = p1.y; g2.drawLine(0, y0, w, y0); }
        if (drawY) { Point p1 = complexToPixel(0, ymin), p2 = complexToPixel(0, ymax); x0 = p1.x; g2.drawLine(x0, 0, x0, h); }

        g2.setFont(g2.getFont().deriveFont(11f));
        g2.setColor(new Color(255, 255, 255, 220));
        int tick = 5;

        // X: marcas cada 0.1
        if (drawX) {
            int kStart = (int) Math.ceil((xmin - 1e-12) / FIXED_STEP);
            int kEnd   = (int) Math.floor((xmax + 1e-12) / FIXED_STEP);
            int maxTicks = 5000;
            for (int k = kStart, cnt = 0; k <= kEnd && cnt < maxTicks; k++, cnt++) {
                double x = roundToStep(k * FIXED_STEP, FIXED_STEP);
                Point p = complexToPixel(x, 0);
                g2.drawLine(p.x, y0 - tick, p.x, y0 + tick);
                String lab = formatFixed01(x);
                g2.drawString(lab, p.x + 3, Math.min(h - 2, y0 + 14));
            }
        }
        // Y: marcas cada 0.1
        if (drawY) {
            int jStart = (int) Math.ceil((ymin - 1e-12) / FIXED_STEP);
            int jEnd   = (int) Math.floor((ymax + 1e-12) / FIXED_STEP);
            int maxTicks = 5000;
            for (int j = jStart, cnt = 0; j <= jEnd && cnt < maxTicks; j++, cnt++) {
                double y = roundToStep(j * FIXED_STEP, FIXED_STEP);
                Point p = complexToPixel(0, y);
                g2.drawLine(x0 - tick, p.y, x0 + tick, p.y);
                String lab = formatFixed01(y);
                g2.drawString(lab, Math.min(w - 30, x0 + 8), p.y - 2);
            }
        }
        g2.dispose();
    }

    private static double roundToStep(double x, double step) { return Math.rint(x / step) * step; }
    private static String formatFixed01(double v) {
        if (Math.abs(v) < 1e-12) v = 0.0;
        String s = String.format(java.util.Locale.US, "%.1f", v);
        if (s.equals("-0.0")) s = "0.0";
        return s;
    }

    /* ---------- Teclado ---------- */
    private void bindKeys(JFrame frame) {
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_PLUS:
                    case KeyEvent.VK_EQUALS:
                        maxIter = (int) Math.min(200000, Math.round(maxIter * 1.25));
                        recomputeAll(); break;
                    case KeyEvent.VK_MINUS:
                        maxIter = (int) Math.max(10, Math.round(maxIter / 1.25));
                        recomputeAll(); break;
                    case KeyEvent.VK_R:
                        xmin = -2.5; xmax = 1.0; ymin = -1.25; ymax = 1.25;
                        adjustAspectToViewport();
                        maxIter = 1000;
                        recomputeAll(); break;
                }
            }
        });
    }

    /* ---------- Main ---------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Conjunto de Mandelbrot — Algoritmo de escape");
            MandelbrotViewer panel = new MandelbrotViewer(960, 540);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            panel.bindKeys(f);
        });
    }
}

