package macostextkeys;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * AgentLog davranışı (issue #6 teşhis kanalı):
 *   - HATA satırı HER ZAMAN yazılır (UDE_AGENTLOG olmasa da),
 *   - ok satırı YALNIZ UDE_AGENTLOG=1 ile yazılır,
 *   - ~/Library/Logs yoksa oluşturulur (yoksa log sessizce kaybolurdu).
 *
 * Elle çalıştırma (paket macostextkeys → sınıf yolunda derlenmiş agent gerekir):
 *   JDK=~/Library/Java/JavaVirtualMachines/zulu-11-arm64.jdk/Contents/Home
 *   $JDK/bin/javac -encoding UTF-8 -d /tmp/tk $(find scripts/macos-textkeys -name '*.java')
 *   $JDK/bin/javac -encoding UTF-8 -d /tmp/tkt -cp /tmp/tk tests/AgentLogTest.java
 *   $JDK/bin/java -cp /tmp/tk:/tmp/tkt macostextkeys.AgentLogTest
 * (UDE_AGENTLOG=1 kolu ayrı bir JVM'de koşar; test onu kendisi başlatır.)
 */
public class AgentLogTest {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "verbose-child".equals(args[0])) { emit(); return; }

        File home = Files.createTempDirectory("aglog").toFile();
        System.setProperty("user.home", home.getAbsolutePath());
        emit();

        File log = new File(home, "Library/Logs/ude-agent.txt");
        check("Library/Logs oluşturuldu", log.isFile());
        String txt = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        check("HATA satırı yazıldı", txt.contains("HATA") && txt.contains("test-adimi"));
        check("yığın izi yazıldı", txt.contains("IllegalStateException"));
        check("UDE_AGENTLOG yokken ok satırı yazılmadı", !txt.contains("ok      test-adimi"));

        // UDE_AGENTLOG=1 kolu: ayrı JVM (ortam değişkeni sınıf yüklenirken okunur)
        File home2 = Files.createTempDirectory("aglog2").toFile();
        ProcessBuilder pb = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-Duser.home=" + home2.getAbsolutePath(),
                "-cp", System.getProperty("java.class.path"),
                "macostextkeys.AgentLogTest", "verbose-child");
        pb.environment().put("UDE_AGENTLOG", "1");
        pb.inheritIO();
        check("alt JVM koştu", pb.start().waitFor() == 0);
        String txt2 = new String(Files.readAllBytes(
                new File(home2, "Library/Logs/ude-agent.txt").toPath()), StandardCharsets.UTF_8);
        check("UDE_AGENTLOG=1 ile ok satırı yazıldı", txt2.contains("ok      test-adimi"));

        System.out.println(fail == 0 ? "TÜM TESTLER GEÇTİ" : "BAŞARISIZ");
        if (fail != 0) System.exit(1);
    }

    private static void emit() {
        AgentLog.ok("test-adimi");
        AgentLog.failed("test-adimi", new IllegalStateException("kasıtlı hata"));
    }

    private static int fail = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "✓ " : "✗ ") + name);
        if (!ok) fail = 1;
    }
}
