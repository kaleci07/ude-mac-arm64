package macosimgfix;

import java.io.File;
import java.io.IOException;

/*
 * Okunamayan görsel mesajı testi (IMGFIX).
 *
 * Çalıştırma (elle, repo kökünden):
 *   javac -encoding UTF-8 -d /tmp/imgfix-test \
 *     scripts/macos-imagefix/macosimgfix/ImageLoad.java \
 *     tests/ImageLoadMessageTest.java
 *   java -cp /tmp/imgfix-test macosimgfix.ImageLoadMessageTest
 */
public final class ImageLoadMessageTest {

    private static int fails = 0;

    private static void contains(String name, String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            fails++;
            System.out.println("FAIL " + name + ": \"" + needle + "\" beklendi, gelen:\n" + haystack);
        }
    }

    private static void absent(String name, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            fails++;
            System.out.println("FAIL " + name + ": \"" + needle + "\" olmamalıydı, gelen:\n" + haystack);
        }
    }

    public static void main(String[] args) {
        // 1) HEIC: biçim desteklenmiyor, dosya adı ve çözüm önerisi mesajda
        String heic = ImageLoad.message(new File("/Users/x/IMG_1234.HEIC"), null);
        contains("heic dosya adi", heic, "IMG_1234.HEIC");
        contains("heic bicim adi", heic, "HEIC");
        contains("heic desteklenmiyor", heic, "desteklemiyor");
        contains("heic cozum onerisi", heic, "PNG");

        // 2) WEBP: aynı desteklenmeyen-biçim yolu
        String webp = ImageLoad.message(new File("/tmp/gorsel.webp"), null);
        contains("webp bicim adi", webp, "WEBP");
        contains("webp desteklenmiyor", webp, "desteklemiyor");

        // 3) Desteklenen uzantı + okuma hatası: "bozuk/okunamıyor" yolu, biçim suçlanmaz
        String broken = ImageLoad.message(new File("/tmp/foto.png"), new IOException("bozuk başlık"));
        contains("png dosya adi", broken, "foto.png");
        contains("png okunamadi", broken, "okunamadı");
        absent("png bicim suclanmamali", broken, "desteklemiyor");

        // 4) Desteklenen uzantı ama sessiz null (ör. iCloud'dan inmemiş dosya)
        String silent = ImageLoad.message(new File("/tmp/foto.jpg"), null);
        contains("jpg dosya adi", silent, "foto.jpg");
        contains("jpg okunamadi", silent, "okunamadı");

        // 5) Uzantısız dosya çökmemeli
        String noext = ImageLoad.message(new File("/tmp/dosya"), null);
        contains("uzantisiz dosya adi", noext, "dosya");

        // 6) null dosya çökmemeli
        String nullFile = ImageLoad.message(null, null);
        if (nullFile == null || nullFile.isEmpty()) {
            fails++;
            System.out.println("FAIL null dosya: boş mesaj");
        }

        // 7) Zaten raporlanmış hata ikinci kez diyalog açmamalı
        if (!ImageLoad.alreadyReported(new ImageLoad.Reported("x"))) {
            fails++;
            System.out.println("FAIL Reported tanınmadı");
        }
        if (ImageLoad.alreadyReported(new IOException("x"))) {
            fails++;
            System.out.println("FAIL sıradan IOException yanlışlıkla raporlanmış sayıldı");
        }

        System.out.println(fails == 0 ? "ImageLoadMessageTest: TAMAM"
            : "ImageLoadMessageTest: " + fails + " HATA");
        if (fails > 0) System.exit(1);
    }
}
