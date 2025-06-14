import com.sun.net.httpserver.HttpServer; // Серверная библиотека позволяет создать HTTP-сервер без внешних библиотек
import com.sun.net.httpserver.HttpHandler; // Интерфейс для обработки входящих HTTP-запросов
import com.sun.net.httpserver.HttpExchange; // Объект, представляющий HTTP-запрос и ответ
import java.io.*; // Универсальный импорт всех классов ввода/вывода: InputStream, OutputStream, BufferedReader, File
import java.net.InetSocketAddress; // Работа с сетью и HTTP-запросами, представляет адрес (IP + порт)
import java.net.HttpURLConnection; // Используется для создания HTTP-запросов на клиенте (например, GET/POST)
import java.net.URL; // Представляет веб-адрес
import java.nio.charset.StandardCharsets; // Позволяет задавать стандартизированные кодировки, StandardCharsets.UTF_8
import java.util.*; // Импортирует классы из пакета java.util:List, Map, ArrayList, HashMap, Scanner, Collections
import org.json.JSONObject; // Внешний класс библиотеки org.json для создания, парсинга и обработки JSON-объектов

public class Server {
    private static final int PORT = 8080;
    private static final String API_KEY = "bd8a4ddcd6b838addc3b6d52"; // Получите бесплатный ключ на exchangerate-api.com
    private static final String BASE_CURRENCY = "USD";
    private static Map<String, Double> exchangeRates = new HashMap<>(); // статическая переменная хранит курсы валют

    public static void main(String[] args) throws IOException {
        // Загрузка курсов валют при старте
        updateExchangeRates();

        // Периодическое обновление курсов (каждые 30 минут)
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateExchangeRates();
            }
        }, 0, 30 * 60 * 1000);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/convert", new ConversionHandler());
        server.createContext("/currencies", new CurrenciesHandler());
        server.createContext("/manual-convert", new ManualConversionHandler()); // <--- новый путь
        server.setExecutor(null);
        server.start();
        System.out.println("Сервер запущен на порту " + PORT);
    }

    private static void updateExchangeRates() {
        try {
            URL url = new URL("https://v6.exchangerate-api.com/v6/" + API_KEY + "/latest/" + BASE_CURRENCY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONObject rates = jsonResponse.getJSONObject("conversion_rates");

            synchronized (exchangeRates) {
                exchangeRates.clear();
                for (String currency : rates.keySet()) {
                    exchangeRates.put(currency, rates.getDouble(currency));
                }
            }

            System.out.println("Курсы валют обновлены: " + new Date());
        } catch (Exception e) {
            System.err.println("Ошибка при обновлении курсов: " + e.getMessage());
        }
    }

    static class CurrenciesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Метод не поддерживается");
                return;
            }

            try {
                List<String> currencies = new ArrayList<>(exchangeRates.keySet());
                Collections.sort(currencies);
                String response = String.join(",", currencies);
                sendResponse(exchange, 200, response);
            } catch (Exception e) {
                sendResponse(exchange, 500, "Ошибка сервера");
            }
        }
    }

    static class ConversionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Метод не поддерживается");
                return;
            }

            try {
                InputStream requestBody = exchange.getRequestBody();
                String requestData = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestData);

                String amountStr = params.get("amount");
                String fromCurrency = params.get("from");
                String toCurrency = params.get("to");

                if (amountStr == null || amountStr.isEmpty() || fromCurrency == null || toCurrency == null) {
                    sendResponse(exchange, 400, "Неполные данные");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                double fromRate, toRate;

                synchronized (exchangeRates) {
                    fromRate = exchangeRates.getOrDefault(fromCurrency, -1.0);
                    toRate = exchangeRates.getOrDefault(toCurrency, -1.0);
                }

                if (fromRate == -1 || toRate == -1) {
                    sendResponse(exchange, 400, "Неверная валюта");
                    return;
                }

                double result = amount * (toRate / fromRate);
                System.out.printf("Конвертация: %.2f %s = %.2f %s%n", amount, fromCurrency, result, toCurrency);

                String response = String.format("%.2f", result);
                sendResponse(exchange, 200, response);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "Неверный формат числа");
            } catch (Exception e) {
                sendResponse(exchange, 500, "Ошибка сервера");
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> params = new HashMap<>();
            String[] pairs = formData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
            return params;
        }
    }

    // 💡 Новый обработчик для ручного курса
    static class ManualConversionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Метод не поддерживается");
                return;
            }

            try {
                InputStream requestBody = exchange.getRequestBody();
                String requestData = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(requestData);

                String amountStr = params.get("amount");
                String rateStr = params.get("rate");

                if (amountStr == null || rateStr == null) {
                    sendResponse(exchange, 400, "Неполные данные");
                    return;
                }

                double amount = Double.parseDouble(amountStr);
                double rate = Double.parseDouble(rateStr);

                double result = amount * rate;
                String response = String.format("%.2f * %.4f = %.2f", amount, rate, result);

                System.out.println("Ручная конвертация: " + response);
                sendResponse(exchange, 200, response);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "Неверный формат числа");
            } catch (Exception e) {
                sendResponse(exchange, 500, "Ошибка сервера");
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> params = new HashMap<>();
            String[] pairs = formData.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
            return params;
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
}
