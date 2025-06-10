import javax.swing.*; // библиотека для создания графического интерфейса пользователя (GUI) для окон, кнопок, форм
import java.awt.*; // Библиотека абстрактного графического интерфейса (AWT) для задания цветов, шрифтов, компоновки
import java.awt.event.ActionEvent; // представляет событие, которое возникает, когда пользователь выполняет действие, нажимает кнопку
import java.io.*; // Пакет для работы с вводом и выводом (файлы, потоки, консоль, сеть)
import java.net.HttpURLConnection; // для установления HTTP-соединения с сервером
import java.net.URL; // открыть соединение с сайтом/сервером по адресу
import java.nio.charset.StandardCharsets; // для преобразования строки в байты с нужной кодировкой

public class Client {

    public static void main(String[] args) { // Создание окна
        JFrame frame = new JFrame("Валютный калькулятор");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // программа завершится, когда пользователь нажмёт крестик
        frame.setSize(500, 350); // Устанавливает размер окна: 500 пикселей в ширину и 350 в высоту
        frame.setLayout(new GridLayout(6, 2, 10, 10)); // Задаёт сетку 6 строк и 2 колонки с отступами 10 пикселей

        JLabel amountLabel = new JLabel("Сумма:");
        JTextField amountField = new JTextField(); // поле сумма для конвертации

        JLabel fromLabel = new JLabel("Из валюты:");
        DefaultComboBoxModel<String> fromModel = new DefaultComboBoxModel<>(); // для динамического наполнения списка (загрузка валют с сервера)
        JComboBox<String> fromCurrency = new JComboBox<>(fromModel);

        JLabel toLabel = new JLabel("В валюту:");
        DefaultComboBoxModel<String> toModel = new DefaultComboBoxModel<>();
        JComboBox<String> toCurrency = new JComboBox<>(toModel);

        JLabel resultLabel = new JLabel("Результат:");
        JTextField resultField = new JTextField();
        resultField.setEditable(false); // поле только для чтения

        JButton convertButton = new JButton("Конвертировать");
        JLabel statusLabel = new JLabel("");

        frame.add(amountLabel); // Добавление компонентов в окно
        frame.add(amountField);
        frame.add(fromLabel);
        frame.add(fromCurrency);
        frame.add(toLabel);
        frame.add(toCurrency);
        frame.add(resultLabel);
        frame.add(resultField);
        frame.add(new JLabel()); // заполнитель пустой ячейки (чтобы выровнять кнопку и статус)
        frame.add(convertButton);
        frame.add(statusLabel);

        // Загрузка валют
        loadCurrencies(statusLabel, fromModel, toModel); // вызов функции, которая загружает список валют с сервера

        convertButton.addActionListener((ActionEvent e) -> { // обработчик события когда нажимаем кнопку "Конвертировать"
            String amountStr = amountField.getText(); // Чтение данных из формы
            String from = (String) fromCurrency.getSelectedItem();
            String to = (String) toCurrency.getSelectedItem();

            if (amountStr.isEmpty()) {
                statusLabel.setText("Введите сумму");
                return;
            }
            if (from == null || to == null) {
                statusLabel.setText("Выберите валюты");
                return;
            }
            if (from.equals(to)) {
                statusLabel.setText("Выберите разные валюты");
                return;
            }

            try { // конструкция try-with-resources автоматически закроет поток os, когда блок завершится, даже если произойдёт ошибка
                URL url = new URL("http://localhost:8080/convert");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection(); // Создаётся соединение с локальным сервером
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String postData = String.format("amount=%s&from=%s&to=%s", // Создаётся строка данных в формате ключ=значение&ключ=значение
                        amountStr, from, to);

                try (OutputStream os = conn.getOutputStream()) { // Отправка данных на сервер в теле POST-запроса
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8); // преобразует строку в массив байт в кодировке UTF-8, потому что HTTP передаёт данные в байтах
                    os.write(input, 0, input.length); // записывает байты в поток, в тело POST-запроса (начальная позиция массива, сколько байтов записать)
                }

                int responseCode = conn.getResponseCode(); // Обработка ответа от сервера
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String response = br.readLine();
                        resultField.setText(response);
                        statusLabel.setText(String.format("Конвертация %s %s → %s %s",
                                amountStr, from, response, to));
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        String errorResponse = br.readLine();
                        statusLabel.setText("Ошибка: " + errorResponse);
                    }
                }
            } catch (Exception ex) {
                statusLabel.setText("Ошибка соединения: " + ex.getMessage());
            }
        });

        JLabel manualRateLabel = new JLabel("Свой курс:");
        JTextField manualRateField = new JTextField();

        JButton manualConvertButton = new JButton("<html><center>Конвертация<br>по своему курсу</center></html>");

// Добавляем в интерфейс
        frame.add(manualRateLabel);
        frame.add(manualRateField);
        manualConvertButton.setPreferredSize(new Dimension(200, 50)); // увеличиваем кнопку по ширине и высоте
        frame.add(manualConvertButton);

// Обработчик кнопки "Конвертация по своему курсу"
        manualConvertButton.addActionListener((ActionEvent e) -> {
            String amountStr = amountField.getText();
            String manualRateStr = manualRateField.getText();
            String from = (String) fromCurrency.getSelectedItem();
            String to = (String) toCurrency.getSelectedItem();

            if (amountStr.isEmpty() || manualRateStr.isEmpty()) {
                statusLabel.setText("Введите сумму и свой курс");
                return;
            }

            if (from == null || to == null || from.equals(to)) {
                statusLabel.setText("Выберите разные валюты");
                return;
            }

            try {
                URL url = new URL("http://localhost:8080/manual-convert");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String postData = String.format("amount=%s&rate=%s", amountStr, manualRateStr);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String response = br.readLine();
                        resultField.setText(response); // Показываем только результат
                        statusLabel.setText("Готово");
                    }
                } else {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        String errorResponse = br.readLine();
                        statusLabel.setText("Ошибка: " + errorResponse);
                    }
                }
            } catch (Exception ex) {
                statusLabel.setText("Ошибка соединения: " + ex.getMessage());
            }
        });



        frame.setVisible(true); // Сделать окно видимым на экране
    }

    private static void loadCurrencies(JLabel statusLabel, // метод принадлежит классу, а не объекту, не доступен извне
                                       DefaultComboBoxModel<String> fromModel, // метка, в которую будет выведен результат загрузки ("Доступно 30 валют")
                                       DefaultComboBoxModel<String> toModel) {
        try {
            URL url = new URL("http://localhost:8080/currencies");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(); // Создается подключение к локальному серверу по адресу
            conn.setRequestMethod("GET"); // Отправляется HTTP GET-запрос

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String response = br.readLine(); // Читает строку с валютами, разделенными запятыми
                String[] currencies = response.split(","); // Разбивает строку по запятой в массив валют

                SwingUtilities.invokeLater(() -> { // обновление GUI произойдет в потоке интерфейса
                    fromModel.removeAllElements();
                    toModel.removeAllElements(); // Удаляет старые элементы и добавляет новые валюты в оба выпадающих списка
                    for (String currency : currencies) {
                        fromModel.addElement(currency);
                        toModel.addElement(currency);
                    }
                    statusLabel.setText("Доступно " + currencies.length + " валют"); // Показывает сколько валют доступно
                });
            }
        } catch (Exception e) {
            statusLabel.setText("Ошибка загрузки валют: " + e.getMessage());

            String[] backupCurrencies = {
                    "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY",
                    "SEK", "NZD", "MXN", "SGD", "HKD", "NOK", "KRW", "TRY",
                    "RUB", "INR", "BRL", "ZAR"
            };

            SwingUtilities.invokeLater(() -> { // ставит задачу в очередь на выполнение в потоке пользовательского интерфейса EDT — Event Dispatch Thread
                fromModel.removeAllElements(); // сокращённый способ записать анонимный Runnable (код, который нужно выполнить позже)
                toModel.removeAllElements(); // Удаляются все предыдущие валюты из выпадающих списков
                for (String currency : backupCurrencies) { // Перебирается массив валют и добавляется каждая в оба выпадающих списка
                    fromModel.addElement(currency);
                    toModel.addElement(currency);
                }
            });
        }
    }
}
