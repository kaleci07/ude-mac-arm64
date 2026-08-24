package macosimgfix;

/*
 * Sayfaya sığdırma matematiği testi (IMGFIX).
 *
 * Çalıştırma (elle, repo kökünden):
 *   javac -encoding UTF-8 -d /tmp/imgfix-test \
 *     scripts/macos-imagefix/macosimgfix/ImageFit.java \
 *     tests/ImageFitTest.java
 *   java -cp /tmp/imgfix-test macosimgfix.ImageFitTest
 */
public final class ImageFitTest {

    private static int fails = 0;
    private static final double A4_W = 510.3;   // canlı UDE ölçümü (A4, imageable)
    private static final double A4_H = 756.945;

    private static void check(String name, float[] got, float w, float h) {
        if (Math.abs(got[0] - w) > 0.05f || Math.abs(got[1] - h) > 0.05f) {
            fails++;
            System.out.println("FAIL " + name + ": beklenen " + w + "x" + h
                + ", gelen " + got[0] + "x" + got[1]);
        }
    }

    public static void main(String[] args) {
        check("sayfaya sigan gorsel dokunulmaz",
            ImageFit.fitted(400, 300, A4_W, A4_H), 400, 300);

        check("tam sinirdaki gorsel dokunulmaz",
            ImageFit.fitted(510, 756, A4_W, A4_H), 510, 756);

        // 1200x800 -> olcek 510.3/1200 = 0.42525 -> 510.3 x 340.2
        check("genis gorsel genislige sigdirilir",
            ImageFit.fitted(1200, 800, A4_W, A4_H), 510.3f, 340.2f);

        // 3024x4032 -> min(510.3/3024=0.16875, 756.945/4032=0.18773) = 0.16875 -> 510.3 x 680.4
        check("telefon fotografi (dikey) genislige sigdirilir",
            ImageFit.fitted(3024, 4032, A4_W, A4_H), 510.3f, 680.4f);

        // 600x2000 -> min(0.8505, 0.3785) = 0.3785 -> 227.08 x 756.945
        check("uzun gorsel yuksekige sigdirilir",
            ImageFit.fitted(600, 2000, A4_W, A4_H), 227.083f, 756.945f);

        check("oran korunur (kare)",
            ImageFit.fitted(2000, 2000, A4_W, A4_H), 510.3f, 510.3f);

        // Savunmacı: geçersiz girdilerde dokunma
        check("sifir genislik dokunulmaz", ImageFit.fitted(0, 300, A4_W, A4_H), 0, 300);
        check("negatif yukseklik dokunulmaz", ImageFit.fitted(400, -5, A4_W, A4_H), 400, -5);
        check("sayfa olcusu yoksa dokunulmaz", ImageFit.fitted(1200, 800, 0, 0), 1200, 800);
        check("sayfa olcusu NaN ise dokunulmaz",
            ImageFit.fitted(1200, 800, Double.NaN, A4_H), 1200, 800);

        System.out.println(fails == 0 ? "ImageFitTest: TAMAM" : "ImageFitTest: " + fails + " HATA");
        if (fails > 0) System.exit(1);
    }
}
