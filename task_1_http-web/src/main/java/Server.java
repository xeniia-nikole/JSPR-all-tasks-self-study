import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

public class Server {
    public static final int PORT = 9999;


    public static void start() {
        final var threadPool = Executors.newFixedThreadPool(64);
        try (final var serversocket = new ServerSocket(PORT)) {

            while (true) {
                final var socket = serversocket.accept();
                threadPool.execute(new ThreadServer(socket));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Server has stopped");
        }
    }
}

class ThreadServer implements Runnable {
    private static Socket socket;


    public ThreadServer(Socket client) {
        ThreadServer.socket = client;
    }

    @Override
    public void run() {
        final var validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
                "/resources.html", "/styles.css", "/app.js", "/links.html",
                "/forms.html", "/classic.html", "/events.html", "/events.js");

        try (final var input = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             final var output = new BufferedOutputStream(socket.getOutputStream())) {

            final var requestLine = input.readLine();
            System.out.println(requestLine);

            final var parts = requestLine.split(" ");
            if (parts.length != 3) {
                System.out.println("Error! Incorrect format");
                return;
            }

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                output.write((
                        """
                                HTTP/1.1 404 Not Found\r
                                Content-Length: 0\r
                                Connection: close\r
                                \r
                                """
                ).getBytes());
                output.flush();
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);
            final var length = Files.size(filePath);

            if (path.equalsIgnoreCase("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes(StandardCharsets.UTF_8);
                output.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                output.write(content);
                output.flush();
                return;
            }

            output.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());

            Files.copy(filePath, output);
            output.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}