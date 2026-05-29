import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;

/**
 * Минимальный фейковый SMTP-сервер для локального теста канала EMAIL без реальной почты.
 * Принимает любое письмо (без авторизации) и печатает его в консоль.
 * Зависимостей нет — только JDK. Запуск: java FakeSmtpServer [port]  (по умолчанию 2525).
 */
public class FakeSmtpServer {

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2525;
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Fake SMTP server listening on port " + port
                    + " (no auth). Письма будут печататься здесь. Ctrl+C — стоп.");
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> handle(socket)).start();
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            reply(out, "220 fake-smtp ready");
            StringBuilder data = new StringBuilder();
            boolean inData = false;
            String line;
            while ((line = in.readLine()) != null) {
                if (inData) {
                    if (line.equals(".")) {
                        inData = false;
                        printEmail(data.toString());
                        data.setLength(0);
                        reply(out, "250 OK: queued");
                    } else {
                        data.append(line).append('\n');
                    }
                    continue;
                }
                String cmd = line.length() >= 4 ? line.substring(0, 4).toUpperCase() : line.toUpperCase();
                switch (cmd) {
                    case "EHLO", "HELO" -> reply(out, "250 Hello");
                    case "MAIL", "RCPT", "RSET", "NOOP" -> reply(out, "250 OK");
                    case "DATA" -> {
                        reply(out, "354 End data with <CR><LF>.<CR><LF>");
                        inData = true;
                    }
                    case "QUIT" -> {
                        reply(out, "221 Bye");
                        return;
                    }
                    default -> reply(out, "250 OK");
                }
            }
        } catch (IOException e) {
            System.err.println("SMTP connection error: " + e.getMessage());
        }
    }

    private static void printEmail(String raw) {
        System.out.println("\n===== EMAIL RECEIVED @ " + LocalTime.now() + " =====");
        System.out.println(raw);
        System.out.println("==============================================\n");
    }

    private static void reply(BufferedWriter out, String text) throws IOException {
        out.write(text + "\r\n");
        out.flush();
    }
}
