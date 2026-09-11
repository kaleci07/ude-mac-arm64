package macostextkeys;

import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * "İmzaları Birleştir" — Araçlar sekmesi › İmza bandına şerit düğmesi ekler. Tıklanınca
 * gömülü UDF İmza Birleştirici yardımcısını (Contents/Helpers/UDF Imza Birlestirici.app;
 * © 2026 Av. Arb. Mevlana İbrahim Asım Bilir, ücretsiz — satılamaz; build.sh imza())
 * açık belgenin yoluyla başlatır. Birleştirme, imza doğrulaması ve 8 güvenlik kapısı
 * tamamen yardımcıdadır; burada yalnız düğme + başlatma var.
 *
 * <ul>
 * <li>Yardımcı pakette yoksa (IMZA=0 ya da derleme uyarıyla atladı) HİÇBİR ŞEY eklenmez.</li>
 * <li>Yer (canlı keşif, 5.4.21): "Araçlar" task'ı › "İmza" bandı (İmzala, Mobil İmzala,
 *     İmzalar, Sertifikalar…). Bulunamazsa "İmzala" düğmesini taşıyan band; o da yoksa
 *     düğme hiçbir yere eklenmez (rastgele bir sekmeye koyulmaz).</li>
 * <li>Belge yolu: MacLook'un sakladığı {@code macoslook.path}; SKIN=0'da ham başlığın
 *     " (…/ad.udf)" kuyruğu. Kaydedilmemiş yeni belge UDE'nin {@code ~/.uki/} geçici
 *     dosyasıdır → yardımcı boş açılır, kullanıcı nüshaları kendisi ekler.</li>
 * <li>Tamamı yansıma: agent jar Flamingo classpath'i olmadan derlenir.</li>
 * <li>Flamingo tuzakları (CLAUDE.md): setToolTipText ÇAĞIRMA (koşulsuz UOE) → RichTooltip;
 *     komut bandına JRibbonComponent KOYMA; raster ikon büyütülünce bulanık → vektör
 *     ResizableIcon (Proxy).</li>
 * </ul>
 */
final class ImzaBirlestir {
    static final String HELPER_NAME = "UDF Imza Birlestirici.app";
    /** UDE'nin MEDIUM şerit düğmeleri iki boşlukla başlar ("  İmzalar"); aynı görünüm. */
    static final String LABEL = "  İmzaları Birleştir";
    static final String TASK = "Araçlar";
    static final String BAND = "İmza";
    private static final String DONE = "imzabirlestir.done";
    private static final int TRIES = 8;

    private ImzaBirlestir() { }

    static void install() {
        final File helper = helper();
        if (helper == null) return;                       // yardımcı yok → düğme yok
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() != WindowEvent.WINDOW_OPENED || !(e.getSource() instanceof JFrame)) return;
            final JFrame f = (JFrame) e.getSource();
            SwingUtilities.invokeLater(() -> attach(f, helper, TRIES));
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    /** Şerit modeli pencere açılışında tam kurulmamış olabilir → kısa aralıklarla yeniden dene. */
    private static void attach(JFrame f, File helper, int left) {
        try {
            JRootPane root = f.getRootPane();
            if (Boolean.TRUE.equals(root.getClientProperty(DONE))) return;
            Component ribbon = find(f, "JRibbon");
            if (ribbon == null) return;                   // şeritsiz pencere (diyalog vb.)
            Object band = findBand(ribbon);
            if (band == null) {
                if (left > 1) {
                    Timer t = new Timer(500, ev -> attach(f, helper, left - 1));
                    t.setRepeats(false);
                    t.start();
                } else {
                    AgentLog.failed("imza-birlestir", new IllegalStateException(
                        "\"" + TASK + " › " + BAND + "\" bandı ve \"İmzala\" düğmesi bulunamadı; düğme eklenmedi"));
                }
                return;
            }
            root.putClientProperty(DONE, Boolean.TRUE);
            if (hasButton(band, LABEL.trim())) return;
            addButton(ribbon, band, f, helper);
        } catch (Throwable t) {
            AgentLog.failed("imza-birlestir", t);
        }
    }

    private static void addButton(Component ribbon, Object band, JFrame f, File helper) throws Exception {
        ClassLoader cl = ribbon.getClass().getClassLoader();
        Class<?> iconC = Class.forName("org.pushingpixels.flamingo.api.common.icon.ResizableIcon", false, cl);
        Class<?> btnC = Class.forName("org.pushingpixels.flamingo.api.common.JCommandButton", false, cl);
        Class<?> absC = Class.forName("org.pushingpixels.flamingo.api.common.AbstractCommandButton", false, cl);
        Class<?> prioC = Class.forName("org.pushingpixels.flamingo.api.ribbon.RibbonElementPriority", false, cl);
        Class<?> tipC = Class.forName("org.pushingpixels.flamingo.api.common.RichTooltip", false, cl);

        Object icon = Proxy.newProxyInstance(cl, new Class<?>[] { iconC }, new Glyph());
        Object btn = btnC.getConstructor(String.class, iconC).newInstance(LABEL, icon);
        Object tip = tipC.getConstructor(String.class, String.class).newInstance("İmzaları Birleştir",
            "Aynı belgenin ayrı ayrı e-imzalanmış UDF nüshalarındaki imzaları tek dosyada toplar. "
            + "Açık belge ilk nüsha olarak eklenir; diğer nüshaları pencereye sürükleyin.");
        tipC.getMethod("addDescriptionSection", String.class).invoke(tip,
            "UDF İmza Birleştirici — © 2026 Av. Arb. Mevlana İbrahim Asım Bilir. "
            + "Ücretsiz kullanılabilir ve dağıtılabilir; satılamaz.");
        absC.getMethod("setActionRichTooltip", tipC).invoke(btn, tip);
        absC.getMethod("addActionListener", ActionListener.class)
            .invoke(btn, (ActionListener) ev -> launch(f, helper));
        band.getClass().getMethod("addCommandButton", absC, prioC)
            .invoke(band, btn, prioC.getField("MEDIUM").get(null));
        if (band instanceof JComponent) ((JComponent) band).revalidate();
        ((JComponent) ribbon).revalidate();
        ribbon.repaint();
    }

    // ------------------------------------------------------------------ başlatma

    static void launch(JFrame f, File helper) {
        String doc = docPath(f);
        List<String> cmd = new ArrayList<>(Arrays.asList("/usr/bin/open", "-n", "-a", helper.getAbsolutePath()));
        if (doc != null) { cmd.add("--args"); cmd.add(doc); }
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (Throwable t) {
            AgentLog.failed("imza-birlestir başlatma", t);
            JOptionPane.showMessageDialog(f, "İmza Birleştirici başlatılamadı:\n" + t,
                "İmzaları Birleştir", JOptionPane.ERROR_MESSAGE);
        }
    }

    static File helper() {
        String o = System.getProperty("imzabirlestir.helper");        // sınama/teşhis için
        if (o != null) { File h = new File(o); return h.isDirectory() ? h : null; }
        try {
            File jar = new File(ImzaBirlestir.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File contents = jar.getParentFile().getParentFile();       // Contents/app/x.jar → Contents
            File h = new File(new File(contents, "Helpers"), HELPER_NAME);
            return h.isDirectory() ? h : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String docPath(JFrame f) {
        Object p = f.getRootPane().getClientProperty("macoslook.path");
        String s = p instanceof String ? (String) p : pathFromTitle(f.getTitle());
        return usable(s) ? s : null;
    }

    /** Ham UDE başlığından yol: "Doküman Editörü v5.4.21 - ad.udf (/tam/yol/ad.udf)"
     *  (satıcı başa ~100 boşluk koyar). Yol yoksa null. */
    static String pathFromTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        int p = t.indexOf(" (/");                   // MacLook ile aynı kural: yol " (/" ile başlar
        if (p < 0 || !t.endsWith(")")) return null;
        return t.substring(p + 2, t.length() - 1);
    }

    /** Yalnız diskte duran, kaydedilmiş bir .udf; UDE'nin ~/.uki/ geçici dosyası sayılmaz. */
    static boolean usable(String s) {
        if (s == null || !s.toLowerCase(Locale.ROOT).endsWith(".udf")) return false;
        if (s.contains("/.uki/")) return false;
        return new File(s).isFile();
    }

    // ------------------------------------------------------------------ şerit modeli

    static Object findBand(Object ribbon) throws Exception {
        int n = (Integer) call(ribbon, "getTaskCount");
        Object yedek = null;
        for (int i = 0; i < n; i++) {
            Object task = call(ribbon, "getTask", i);
            String tt = String.valueOf(call(task, "getTitle")).trim();
            for (Object band : (List<?>) call(task, "getBands")) {
                if (method(band, "addCommandButton", 2) == null) continue;   // JRibbonBand değil
                String bt = String.valueOf(call(band, "getTitle")).trim();
                if (TASK.equals(tt) && BAND.equals(bt)) return band;
                if (yedek == null && hasButton(band, "İmzala")) yedek = band;
            }
        }
        return yedek;
    }

    static boolean hasButton(Object band, String text) throws Exception {
        Object cp = call(band, "getControlPanel");
        return cp instanceof Container && hasText((Container) cp, text);
    }

    private static boolean hasText(Container c, String text) {
        for (Component k : c.getComponents()) {
            Method g = method(k, "getText", 0);
            if (g != null && k.getClass().getName().startsWith("org.pushingpixels.flamingo")) {
                try { if (text.equals(String.valueOf(g.invoke(k)).trim())) return true; } catch (Exception e) { }
            }
            if (k instanceof Container && hasText((Container) k, text)) return true;
        }
        return false;
    }

    private static Component find(Component c, String simpleName) {
        if (c.getClass().getSimpleName().equals(simpleName)) return c;
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                Component r = find(k, simpleName);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Method method(Object o, String name, int argc) {
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        }
        return null;
    }

    private static Object call(Object o, String name, Object... args) throws Exception {
        Method m = method(o, name, args.length);
        if (m == null) throw new NoSuchMethodException(o.getClass().getName() + "." + name);
        return m.invoke(o, args);
    }

    // ------------------------------------------------------------------ vektör ikon

    /** Flamingo ResizableIcon'u derleme-zamanı görmeden uygular; her ölçekte keskin çizer.
     *  Glif: sayfa + imza çizgisi + iç içe geçen iki mühür (birleşen imzalar). */
    static final class Glyph implements InvocationHandler {
        private int w = 16, h = 16;

        @Override public Object invoke(Object proxy, Method m, Object[] a) {
            switch (m.getName()) {
                case "setDimension": { Dimension d = (Dimension) a[0]; w = d.width; h = d.height; return null; }
                case "getIconWidth": return w;
                case "getIconHeight": return h;
                case "paintIcon": paint((Component) a[0], (Graphics) a[1], (Integer) a[2], (Integer) a[3]); return null;
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                case "toString": return "ImzaBirlestir.Glyph[" + w + "x" + h + "]";
                default: return null;
            }
        }

        private void paint(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                float s = Math.min(w, h) / 16f;
                g.translate(x + (w - 16 * s) / 2f, y + (h - 16 * s) / 2f);
                g.scale(s, s);
                Color fg = UIManager.getColor("Label.foreground");
                if (fg == null) fg = c != null ? c.getForeground() : Color.DARK_GRAY;
                g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(fg);
                Path2D page = new Path2D.Float();                       // köşesi kıvrık sayfa
                page.moveTo(2.5, 1.5); page.lineTo(9.5, 1.5); page.lineTo(12.5, 4.5);
                page.lineTo(12.5, 14.5); page.lineTo(2.5, 14.5); page.closePath();
                g.draw(page);
                Path2D sig = new Path2D.Float();                        // imza çizgisi
                sig.moveTo(4.2, 9.2); sig.curveTo(5.2, 6.8, 5.8, 11.2, 6.8, 8.8); sig.curveTo(7.4, 7.6, 7.9, 9.6, 8.6, 8.9);
                g.draw(sig);
                g.draw(new RoundRectangle2D.Float(4f, 11.3f, 4.5f, 0.01f, 0, 0));
                g.setColor(new Color(0x2F6FDB));                        // mavi mühür
                g.fill(new Ellipse2D.Float(8.2f, 8.6f, 5.6f, 5.6f));
                g.setColor(new Color(0x1E9E57));                        // yeşil mühür (birleşen)
                g.fill(new Ellipse2D.Float(10.4f, 10.2f, 5.2f, 5.2f));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tik = new Path2D.Float();
                tik.moveTo(11.6, 12.9); tik.lineTo(12.7, 13.9); tik.lineTo(14.5, 11.8);
                g.draw(tik);
            } finally {
                g.dispose();
            }
        }
    }
}
