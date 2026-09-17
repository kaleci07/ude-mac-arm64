import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;

/**
 * IMGFIX build-zamanı yamaları — "Resim Ekle" iki kusuru:
 *
 *  1) SESSİZ BAŞARISIZLIK (asıl şikâyet): macOS native dosya paneli UDE'nin
 *     görsel filtresini uygulamadığı için kullanıcı HEIC/WEBP gibi ImageIO'nun
 *     okuyamadığı bir dosya seçebiliyor. {@code utils.h.a(File)} böyle bir
 *     dosyada İSTİSNA ATMADAN null dönüyor → UDE null'ı geçerli sanıp
 *     {@code new ImageIcon(null)} ile NPE atıyor (cz: "Resim Ekle/Düzenle"
 *     penceresi hiç açılmıyor; gR: arka plan resmi seçilmiyor) ve hata yolu
 *     yalnız log'a yazdığı için kullanıcıya hiçbir şey görünmüyor.
 *     Yama: h.a(File) dönüşü null ise ImageLoad.reportNull → Türkçe uyarı +
 *     IOException (UDE'nin KENDİ iptal yolu devreye girer); istisna atarsa
 *     ImageLoad.reportError ile uyarı gösterilip istisna yeniden fırlatılır.
 *
 *  2) SAYFAYI TAŞAN GÖRSEL: IMGFULL satıcının sığdırma adımını kaldırdığı için
 *     görünen boyut bitmap'in PİKSEL ölçüsü oluyor (1200x800 görsel → 1200x800
 *     punto; A4 yazılabilir en 510 punto). Yama: ekleme primitifi
 *     {@code hj.a(BufferedImage,float,float)} başında görünen ölçü sayfaya
 *     sığdırılır (ImageFit). Bitmap'e DOKUNULMAZ → IMGFULL keskinliği durur;
 *     görsel zaten sığıyorsa no-op (pano/rich-paste yolları etkilenmez).
 *
 * ÖN KOŞUL: macosimgfix/*.class bu patcher'dan ÖNCE jar'a enjekte edilmiş
 * olmalı (build.sh sırası; Javassist köprü ifadeleri jar classpath'inden çözer).
 *
 * Argümanlar: <editor-app.jar> <out-dir>
 */
public class ImageFixPatch {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Kullanım: ImageFixPatch <editor-app.jar> <out-dir>");
            System.exit(2);
        }
        String jar = args[0];
        File outDir = new File(args[1]);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(jar);

        // --- 1) utils.h.a(File): null/istisna → kullanıcıya Türkçe uyarı ---
        CtClass h = pool.get("tr.com.havelsan.uyap.system.editor.utils.h");
        if (references(h, "macosimgfix.ImageLoad")) {
            System.out.println("[ImageFixPatch] utils.h zaten yamalı, atlandı.");
        } else {
            CtMethod read = h.getMethod("a", "(Ljava/io/File;)Ljava/awt/image/BufferedImage;");
            read.addCatch(
                "{ macosimgfix.ImageLoad.reportError($1, $e); throw $e; }",
                pool.get("java.lang.Throwable"));
            read.insertAfter("{ if ($_ == null) macosimgfix.ImageLoad.reportNull($1); }");
            writeClass(h, outDir);
            System.out.println("[ImageFixPatch] utils.h.a(File) yamalandı (okunamayan görselde uyarı).");
        }

        // --- 2) hj.a(BufferedImage,float,float): görünen ölçüyü sayfaya sığdır ---
        CtClass hj = pool.get("tr.com.havelsan.uyap.system.editor.common.text.hj");
        if (references(hj, "macosimgfix.ImageFit")) {
            System.out.println("[ImageFixPatch] text.hj zaten yamalı, atlandı.");
        } else {
            CtMethod ins = hj.getDeclaredMethod("a",
                new CtClass[] {
                    pool.get("java.awt.image.BufferedImage"),
                    CtClass.floatType,
                    CtClass.floatType
                });
            ins.insertBefore(
                "{ float[] imgfixWh = macosimgfix.ImageFit.fit($0, $2, $3);"
              + "  $2 = imgfixWh[0]; $3 = imgfixWh[1]; }");
            writeClass(hj, outDir);
            System.out.println("[ImageFixPatch] text.hj.a(BufferedImage,float,float) yamalandı (sayfaya sığdırma).");
        }
    }

    /** cc, verilen sınıfa atıf yapıyor mu (idempotans kontrolü)? */
    private static boolean references(CtClass cc, String className) {
        Collection<?> refs = cc.getRefClasses();
        return refs != null && refs.contains(className);
    }

    private static void writeClass(CtClass cc, File outDir) throws Exception {
        byte[] code = cc.toBytecode();
        File f = new File(outDir, cc.getName().replace('.', '/') + ".class");
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(code);
        }
    }
}
