package macostextkeys;

import java.io.File;
import java.nio.file.Files;

/**
 * ImzaBirlestir: UDE ham başlığından belge yolu + "kullanılabilir belge" süzgeci + yardımcı yolu.
 * Canlı keşif (5.4.21): "Doküman Editörü v5.4.21 - ad.udf (/tam/yol/ad.udf)", başta ~100 boşluk;
 * kaydedilmemiş yeni belge ~/.uki/isimsiz.UDF.
 *
 * Elle çalıştırma:
 *   JDK=~/Library/Java/JavaVirtualMachines/zulu-11-arm64.jdk/Contents/Home
 *   $JDK/bin/javac -encoding UTF-8 -d /tmp/tk $(find scripts/macos-textkeys -name '*.java')
 *   $JDK/bin/javac -encoding UTF-8 -d /tmp/tkt -cp /tmp/tk tests/ImzaBirlestirTest.java
 *   $JDK/bin/java -Djava.awt.headless=true -cp /tmp/tk:/tmp/tkt macostextkeys.ImzaBirlestirTest
 */
public class ImzaBirlestirTest {
    static int fail = 0;

    public static void main(String[] args) throws Exception {
        String pad = " ".repeat(100);
        eq("boşluklu ham başlık", "/Users/x/Belgeler/Tutanak.udf",
            ImzaBirlestir.pathFromTitle(pad + "Doküman Editörü v5.4.21 - Tutanak.udf (/Users/x/Belgeler/Tutanak.udf)"));
        eq("parantezli klasör + ad", "/Users/x/Dava (2026)/Son Tutanak (imzalı).udf",
            ImzaBirlestir.pathFromTitle("Doküman Editörü v5.4.21 - Son Tutanak (imzalı).udf (/Users/x/Dava (2026)/Son Tutanak (imzalı).udf)"));
        eq("temiz başlık (yol yok)", null, ImzaBirlestir.pathFromTitle("Tutanak.udf"));
        eq("adda parantez, yol yok", null, ImzaBirlestir.pathFromTitle("Tutanak (imzalı).udf"));
        eq("null", null, ImzaBirlestir.pathFromTitle(null));

        File dir = Files.createTempDirectory("imzab").toFile();
        File udf = new File(dir, "Nüsha (1).udf");  Files.write(udf.toPath(), new byte[] { 1 });
        File buyuk = new File(dir, "NUSHA.UDF");     Files.write(buyuk.toPath(), new byte[] { 1 });
        File pdf = new File(dir, "a.pdf");           Files.write(pdf.toPath(), new byte[] { 1 });
        File uki = new File(dir, ".uki");            uki.mkdirs();
        File gecici = new File(uki, "isimsiz.UDF");  Files.write(gecici.toPath(), new byte[] { 1 });
        check("kaydedilmiş .udf kullanılır", ImzaBirlestir.usable(udf.getPath()));
        check("büyük harf .UDF kullanılır", ImzaBirlestir.usable(buyuk.getPath()));
        check(".pdf reddedilir", !ImzaBirlestir.usable(pdf.getPath()));
        check("~/.uki geçici belge reddedilir", !ImzaBirlestir.usable(gecici.getPath()));
        check("diskte olmayan reddedilir", !ImzaBirlestir.usable(new File(dir, "yok.udf").getPath()));
        check("null reddedilir", !ImzaBirlestir.usable(null));

        System.setProperty("imzabirlestir.helper", new File(dir, "yok.app").getPath());
        check("yardımcı yoksa null (düğme eklenmez)", ImzaBirlestir.helper() == null);
        File app = new File(dir, "UDF Imza Birlestirici.app"); app.mkdirs();
        System.setProperty("imzabirlestir.helper", app.getPath());
        check("yardımcı varsa bulunur", app.equals(ImzaBirlestir.helper()));

        System.out.println(fail == 0 ? "ImzaBirlestirTest: HEPSİ GEÇTİ" : "ImzaBirlestirTest: " + fail + " HATA");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void eq(String ad, String beklenen, String gercek) {
        boolean ok = beklenen == null ? gercek == null : beklenen.equals(gercek);
        if (!ok) { fail++; System.out.println("  HATA " + ad + ": beklenen=" + beklenen + " gerçek=" + gercek); }
        else System.out.println("  ok   " + ad);
    }

    static void check(String ad, boolean ok) {
        if (!ok) { fail++; System.out.println("  HATA " + ad); } else System.out.println("  ok   " + ad);
    }
}
