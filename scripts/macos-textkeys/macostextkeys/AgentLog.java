package macostextkeys;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Agent kurulum izleri → ~/Library/Logs/ude-agent.txt
 *
 * Neden dosya: UDE System.err'i yutuyor (kendi konsol yönlendirmesi) → agent
 * hataları hiçbir yerde görünmüyordu. Kullanıcı yalnız "Cmd kısayolları
 * çalışmıyor" diyebiliyor, hangi parçanın düştüğü bilinemiyordu (issue #6).
 *
 * HATALAR HER ZAMAN yazılır (nadir ve tanı için kritik). Başarılı adımlar
 * yalnız UDE_AGENTLOG=1 ile yazılır (normalde dosyaya hiç dokunulmaz).
 */
final class AgentLog {

    private static final boolean VERBOSE = "1".equals(System.getenv("UDE_AGENTLOG"));

    private AgentLog() {}

    static void ok(String step) {
        if (VERBOSE) write("ok      " + step);
    }

    static void failed(String step, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        write("HATA    " + step + " → " + sw);
    }

    private static void write(String msg) {
        try {
            File f = new File(System.getProperty("user.home"), "Library/Logs/ude-agent.txt");
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            FileWriter w = new FileWriter(f, true);
            try {
                w.write(new java.util.Date() + "  " + msg + "\n");
            } finally {
                w.close();
            }
        } catch (Throwable ignore) {
            // Log asla uygulamayı etkilememeli.
        }
    }
}
