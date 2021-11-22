import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ThreadServer implements Runnable {
    public static final String GET = "GET";
    public static final String POST = "POST";
    private static Socket socket;

    public ThreadServer(Socket client) {
        ThreadServer.socket = client;
    }

    @Override
    public void run() {
        final var validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
                "/resources.html", "/styles.css", "/app.js", "/links.html",
                "/forms.html", "/classic.html", "/events.html", "/events.js");
        final var allowedMethods = List.of(GET, POST);

        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            while (!socket.isClosed()){

// лимит на request line + заголовки
                final var limit = 4096;

                in.mark(limit); // устанавливаем для считывателя лимит


                final byte[] buffer = new byte[limit]; // считываем в буфер сами байты запроса
                final int read = in.read(buffer); // считаем их количество


// ищем request line
                final var requestLineDelimiter = new byte[]{'\r', '\n'}; // создаем массив из двух байтов разделителем
                final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read); // сравниваем
                // все полученные байты из буфера с байтами из разделителя, считаем кол-во до первого вхождения
                // получаем конец разделителя
                if (requestLineEnd == -1) { // проверяем, что запрос не пустой
                    badRequest(out);
                    continue;
                }

// читаем request line
                final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" "); //
                // считываем запрос через копирование байтов из буфера до конца разделителя, разбиваем по пробелам
                if (requestLine.length != 3) {  // проверяем, что в запросе есть все три элемента
                    badRequest(out);
                    continue;
                }

                final var method = requestLine[0]; // записываем метод в отдельную переменную
                if (!allowedMethods.contains(method)) { // проверяем его наличие среди разрешенных
                    badRequest(out);
                    continue;
                }
                System.out.println(method);


                final var path = requestLine[1]; // записываем путь в отдельную переменную
                if (!path.startsWith("/")&!validPaths.contains(path)) {// проверяем корректность пути
                    badRequest(out);
                    continue;
                }
                System.out.println(path);
// достаем query параметры
                Map<String, List<String>> map = new HashMap<>();
                if (method.equals("GET")) {
                    if (path.contains("?")) {
                        String queryString  = getQueryString(path);
                        map = getQueryParams(queryString);
                    }
                }
                printParamsMap(map);

// ищем заголовки
                final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'}; // создаем массив из четырех байтов
                final int headersStart = requestLineEnd + requestLineDelimiter.length; // ищем число, с которого начинаются заголовки
                final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read); // ищем число, где заканчиваются заголовки
                if (headersEnd == -1) { // проверяем, есть ли они
                    badRequest(out);
                    continue;
                }

// отматываем на начало буфера
                in.reset();
// пропускаем requestLine
                in.skip(headersStart);

                final var headersBytes = in.readNBytes(headersEnd - headersStart); // считываем
                // в множество байт все между числом начала заголовков и числом конца
                final var headers = Arrays.asList(new String(headersBytes).split("\r\n")); // копируем
                // все в список, разбивая по переносу строки
                System.out.println(headers);

// для GET тела нет
                if (!method.equals(GET)) {
                    in.skip(headersDelimiter.length);
// вычитываем Content-Length, чтобы прочитать body
                    final var contentLength = extractHeader(headers);
                    if (contentLength.isPresent()) {
                        final var length = Integer.parseInt(contentLength.get());
                        final var bodyBytes = in.readNBytes(length);

                        final var body = new String(bodyBytes);
                        System.out.println(body);
//                            printBody(body);
                    }
                }

                out.write((
                        """
                                HTTP/1.1 200 OK\r
                                Content-Length: 0\r
                                Connection: close\r
                                \r
                                """
                ).getBytes());
                out.flush();
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printParamsMap(Map<String, List<String>> map) {
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                System.out.println(entry.getKey() + " : ");
                for (String s : entry.getValue()) {
                    System.out.println(s);
                }
            }
    }

    private Map<String, List<String>> getQueryParams(String queryString) {
        Map<String, List<String>> params = new HashMap<>();
        var param = URLEncodedUtils.parse(queryString, StandardCharsets.UTF_8);
        for (NameValuePair nameValuePair : param) {
            String name = nameValuePair.getName();
            String value = nameValuePair.getValue();
            if (!params.containsKey(name)) params.put(name, new ArrayList<>());
            params.get(name).add(value);
        }
        return params;
    }

    private String getQueryString(String path) {
        int queryParamsStart = path.indexOf('?');
        return path.substring(queryParamsStart + 1);
    }

    private static Optional<String> extractHeader(List<String> headers) {
        return headers.stream()
                .filter(o -> o.startsWith("Content-Length"))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                """
                        HTTP/1.1 400 Bad Request\r
                        Content-Length: 0\r
                        Connection: close\r
                        \r
                        """
        ).getBytes());
        out.flush();
    }

    // from Google guava with modifications
    private static int indexOf(byte[] buffer, byte[] delimiter, int start, int max) {
        outer:
        for (int i = start; i < max - delimiter.length + 1; i++) {
            for (int j = 0; j < delimiter.length; j++) {
                if (buffer[i + j] != delimiter[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
