package macosimgfix;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.swing.JOptionPane;

/**
 * Okunamayan görsel dosyasında kullanıcıya Türkçe hata gösterir (IMGFIX).
 *
 * Kök neden (issue: "resim ekle dediğimde seçtiğim resmi eklemiyor"):
 * macOS native dosya paneli UDE'nin görsel filtresini uygulamadığı için
 * kullanıcı HEIC/WEBP gibi ImageIO'nun okuyamadığı bir dosya seçebiliyor.
 * {@code utils.h.a(File)} böyle bir dosyada İSTİSNA ATMADAN null dönüyor;
 * UDE null'ı geçerli sanıp {@code new ImageIcon(null)} ile NPE atıyor ve
 * "Resim Ekle/Düzenle" penceresi hiç açılmıyor. UDE'nin hata yolu yalnız
 * log'a yazdığı için kullanıcıya HİÇBİR ŞEY görünmüyordu.
 *
 * Bu sınıf hem null hem istisna durumunda mesaj gösterir ve okuma çağrısını
 * IOException ile sonlandırır — UDE'nin kendi "iptal" yolu (cz: dispose,
 * gR: catch IOException) devreye girer, NPE oluşmaz.
 */
public final class ImageLoad {
    private ImageLoad() {}

    /** Aynı hata için ikinci diyalog açılmasın diye işaretli istisna. */
    public static final class Reported extends IOException {
        private static final long serialVersionUID = 1L;
        public Reported(String m) { super(m); }
    }

    /** ImageIO'nun okuyamadığı, kullanıcının sık karşılaştığı biçimler. */
    private static final String[] UNSUPPORTED = { "heic", "heif", "webp", "avif", "svg", "pdf", "psd", "ico" };

    /** Dosya adının uzantısı (küçük harf); yoksa "". */
    static String ext(File f) {
        if (f == null) return "";
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        if (dot < 0 || dot == n.length() - 1) return "";
        return n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static boolean isKnownUnsupported(String ext) {
        for (String u : UNSUPPORTED) if (u.equals(ext)) return true;
        return false;
    }

    public static boolean alreadyReported(Throwable t) {
        while (t != null) {
            if (t instanceof Reported) return true;
            t = t.getCause();
        }
        return false;
    }

    /** Kullanıcıya gösterilecek mesaj (test edilebilir; GUI'siz). */
    static String message(File f, Throwable cause) {
        String name = (f == null) ? "Seçilen dosya" : f.getName();
        String e = ext(f);
        StringBuilder sb = new StringBuilder();
        if (isKnownUnsupported(e)) {
            sb.append("\"").append(name).append("\" eklenemedi.\n\n");
            sb.append("UDE ").append(e.toUpperCase(Locale.ROOT))
              .append(" biçimini desteklemiyor. Desteklenen biçimler: PNG, JPEG, GIF, TIFF, BMP.\n\n");
            sb.append("Çözüm: Dosyayı Önizleme (Preview) uygulamasında açın, ");
            sb.append("Dosya ▸ Dışa Aktar… ile PNG veya JPEG olarak kaydedin ve yeniden deneyin.");
        } else {
            sb.append("\"").append(name).append("\" okunamadı.\n\n");
            sb.append("Dosya bozuk olabilir, biçimi tanınmıyor olabilir ");
            sb.append("veya iCloud'dan bu Mac'e henüz indirilmemiş olabilir.\n\n");
            sb.append("Desteklenen biçimler: PNG, JPEG, GIF, TIFF, BMP.");
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isEmpty()) {
                sb.append("\n\nAyrıntı: ").append(cause.getMessage());
            }
        }
        return sb.toString();
    }

    /** Görsel okuma null döndü: mesajı göster, okumayı IOException ile sonlandır. */
    public static void reportNull(File f) throws IOException {
        show(message(f, null));
        throw new Reported("gorsel okunamadi: " + (f == null ? "null" : f.getName()));
    }

    /** Görsel okuma istisna attı: mesajı göster (istisnayı çağıran yeniden fırlatır). */
    public static void reportError(File f, Throwable cause) {
        if (alreadyReported(cause)) return;   // reportNull zaten gösterdi
        show(message(f, cause));
    }

    private static void show(String msg) {
        try {
            Window owner = javax.swing.FocusManager.getCurrentManager().getActiveWindow();
            JOptionPane.showMessageDialog(owner, msg, "Resim Ekle", JOptionPane.WARNING_MESSAGE);
        } catch (Throwable ignore) {
            // Başsız/erken aşama: diyalog gösterilemiyorsa sessizce geç (akış yine iptal olur).
        }
    }
}
