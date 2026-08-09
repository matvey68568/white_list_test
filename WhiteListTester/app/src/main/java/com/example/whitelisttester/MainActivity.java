package com.example.whitelisttester;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView statusTextView;
    private TextView detailsTextView;
    private ProgressBar progressBar;
    private Button testButton;
    
    // Список сайтов из белых списков
    private final List<String> whiteListSites = new ArrayList<String>() {{
        // Государственные компании и сервисы
        add("https://www.gosuslugi.ru");
        add("https://rosseti.ru");
        add("https://www.moex.com");
        add("https://duma.gov.ru");
        add("https://government.ru");
        add("https://genproc.gov.ru");
        
        // Маркетплейсы
        add("https://www.ozon.ru");
        add("https://www.wildberries.ru");
        
        // Кафе и рестораны
        add("https://dodopizza.ru");
        add("https://vkusno-i-tochka.ru");
        
        // Связь
        add("https://www.beeline.ru");
        add("https://www.megafon.ru");
        add("https://mts.ru");
        add("https://www.rostelecom.ru");
        add("https://t2.ru");
        
        // Сервисы для путешествий
        add("https://www.aeroflot.ru");
        add("https://www.rzd.ru");
        add("https://www.pobeda.aero");
        
        // Банки
        add("https://www.gazprombank.ru");
        add("https://www.alfabank.ru");
        add("https://mironline.ru");
        
        // Гипермаркеты и магазины
        add("https://lenta.com");
        add("https://www.okey.ru");
        add("https://www.vkusvil.ru");
        add("https://auchan.ru");
        add("https://www.metro-cc.ru");
        
        // Каршеринг и такси
        add("https://go.yandex.ru");
        add("https://delimobil.ru");
        add("https://citydrive.ru");
        
        // IT-компании
        add("https://www.1c.ru");
        add("https://www.trueconf.ru");
        
        // Медиа и социальные сети
        add("https://aif.ru");
        add("https://www.1tv.ru");
        add("https://vk.com");
        add("https://ok.ru");
        add("https://mail.ru");
        add("https://dzen.ru");
        add("https://rutube.ru");
    }};
    
    // Дополнительные сайты для проверки (не из белого списка)
    private final List<String> externalSites = new ArrayList<String>() {{
        add("https://www.google.com");
        add("https://www.youtube.com");
        add("https://www.instagram.com");
        add("https://www.facebook.com");
        add("https://www.twitter.com");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        detailsTextView = findViewById(R.id.detailsTextView);
        progressBar = findViewById(R.id.progressBar);
        testButton = findViewById(R.id.testButton);

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTesting();
            }
        });
    }

    private void startTesting() {
        testButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText("Тестирование...");
        detailsTextView.setText("");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        final int[] whiteListSuccess = {0};
        final int[] whiteListTotal = {whiteListSites.size()};
        final int[] externalSuccess = {0};
        final int[] externalTotal = {externalSites.size()};
        
        Handler handler = new Handler(Looper.getMainLooper());
        
        // Проверяем сайты из белого списка
        for (String site : whiteListSites) {
            executor.execute(() -> {
                boolean isSuccess = checkWebsite(site);
                if (isSuccess) {
                    whiteListSuccess[0]++;
                }
                
                handler.post(() -> {
                    int totalTested = whiteListSuccess[0] + (whiteListTotal[0] - whiteListSuccess[0]) + 
                                     externalSuccess[0] + (externalTotal[0] - externalSuccess[0]);
                    int maxProgress = whiteListTotal[0] + externalTotal[0];
                    progressBar.setProgress((int) ((totalTested / (double) maxProgress) * 100));
                });
            });
        }
        
        // Проверяем внешние сайты
        for (String site : externalSites) {
            executor.execute(() -> {
                boolean isSuccess = checkWebsite(site);
                if (isSuccess) {
                    externalSuccess[0]++;
                }
                
                handler.post(() -> {
                    int totalTested = whiteListSuccess[0] + (whiteListTotal[0] - whiteListSuccess[0]) + 
                                     externalSuccess[0] + (externalTotal[0] - externalSuccess[0]);
                    int maxProgress = whiteListTotal[0] + externalTotal[0];
                    progressBar.setProgress((int) ((totalTested / (double) maxProgress) * 100));
                });
            });
        }
        
        executor.execute(() -> {
            try {
                Thread.sleep(3000); // Ждем завершения всех проверок
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            handler.post(() -> {
                displayResults(whiteListSuccess[0], whiteListTotal[0], externalSuccess[0], externalTotal[0]);
                testButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
                progressBar.setProgress(0);
            });
        });
        
        executor.shutdown();
    }

    private boolean checkWebsite(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            connection.connect();
            int responseCode = connection.getResponseCode();
            return (responseCode >= 200 && responseCode < 400);
        } catch (Exception e) {
            return false;
        }
    }

    private void displayResults(int whiteSuccess, int whiteTotal, int externalSuccess, int externalTotal) {
        StringBuilder details = new StringBuilder();
        details.append("Сайты из белого списка: ").append(whiteSuccess).append("/").append(whiteTotal).append(" работают\n");
        details.append("Внешние сайты: ").append(externalSuccess).append("/").append(externalTotal).append(" работают");
        detailsTextView.setText(details.toString());
        
        if (externalSuccess > 0) {
            // Если работают внешние сайты - интернет работает нормально
            statusTextView.setText("✅ Интернет работает нормально");
            statusTextView.setTextColor(getColor(android.R.color.holo_green_dark));
        } else if (whiteSuccess > 0) {
            // Если работают только сайты из белого списка
            statusTextView.setText("⚠️ Белые списки");
            statusTextView.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            // Если ничего не работает
            statusTextView.setText("❌ Нет соединения");
            statusTextView.setTextColor(getColor(android.R.color.holo_red_dark));
        }
    }
}
