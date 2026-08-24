package macosimgfix;

import java.awt.print.PageFormat;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eklenen görselin GÖRÜNEN boyutunu sayfaya sığdırır (IMGFIX).
 *
 * Neden: IMGFULL yaması satıcının {@code utils.h.a(hj,BufferedImage)} sığdırma
 * adımını kaldırır (bitmap tam çözünürlükte gömülsün diye). Ama "Resim
 * Ekle/Düzenle" penceresi görünen boyutu bitmap'in PİKSEL ölçüsünden aldığı
 * için 1200x800 bir görsel 1200x800 PUNTO ekleniyor; A4'ün yazılabilir eni
 * 510 punto → görsel sayfayı taşıyor.
 *
 * Bu sınıf yalnız görünen ölçüyü küçültür; bitmap'e DOKUNMAZ (baskı/PDF
 * keskinliği korunur, IMGFULL kazanımı yerinde kalır). Görsel zaten sığıyorsa
 * hiçbir şey değişmez, yani panodan yapıştırma gibi kendi sığdırmasını yapan
 * yollar için no-op'tur.
 */
public final class ImageFit {
    private ImageFit() {}

    /** Sınıf başına PageFormat getter önbelleği (obfuscate ad: hj.a()). */
    private static final Map<Class<?>, Method> PAGE_GETTER = new ConcurrentHashMap<Class<?>, Method>();
    private static final Method NONE;
    static {
        Method m = null;
        try { m = ImageFit.class.getDeclaredMethod("noPageFormat"); } catch (Throwable ignore) {}
        NONE = m;
    }
    private static void noPageFormat() {}

    /**
     * Saf matematik: w×h görünen ölçüsünü pageW×pageH sınırına oranı koruyarak
     * sığdırır. Yalnız küçültür. Geçersiz girdide (≤0, NaN) girdi aynen döner.
     */
    public static float[] fitted(float w, float h, double pageW, double pageH) {
        float[] same = { w, h };
        if (!(w > 0) || !(h > 0)) return same;
        if (!(pageW > 0) || !(pageH > 0)) return same;
        if (Double.isNaN(pageW) || Double.isNaN(pageH)) return same;
        double scale = Math.min(pageW / w, pageH / h);
        if (!(scale < 1.0) || Double.isNaN(scale)) return same;
        return new float[] { (float) (w * scale), (float) (h * scale) };
    }

    /**
     * Yama giriş noktası: {@code hj.a(BufferedImage,float,float)} başına
     * enjekte edilir. editor'ün canlı PageFormat'ını yansımayla okur; okunamazsa
     * girdiyi aynen döndürür (asla patlamaz).
     */
    public static float[] fit(Object editor, float w, float h) {
        try {
            PageFormat pf = pageFormat(editor);
            if (pf == null) return new float[] { w, h };
            return fitted(w, h, pf.getImageableWidth(), pf.getImageableHeight());
        } catch (Throwable t) {
            return new float[] { w, h };
        }
    }

    /** editor sınıfındaki argümansız, PageFormat dönen metodu bulur (hj.a()). */
    private static PageFormat pageFormat(Object editor) throws Exception {
        if (editor == null) return null;
        Class<?> c = editor.getClass();
        Method m = PAGE_GETTER.get(c);
        if (m == null) {
            m = NONE;
            for (Method cand : c.getMethods()) {
                if (cand.getParameterCount() == 0 && cand.getReturnType() == PageFormat.class) {
                    cand.setAccessible(true);
                    m = cand;
                    break;
                }
            }
            PAGE_GETTER.put(c, m);
        }
        if (m == NONE) return null;
        return (PageFormat) m.invoke(editor);
    }
}
