package com.example.whitelisttester

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var detailsText: TextView
    private lateinit var testButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var glassCard: CardView

    // Оптимизированный список: по 1-2 ключевых сайта из каждой категории для скорости
    private val whiteListSites = listOf(
        "https://gosuslugi.ru",       // Госуслуги
        "https://ozon.ru",            // Маркетплейсы
        "https://dodopizza.ru",       // Кафе
        "https://mts.ru",             // Связь
        "https://rzd.ru",             // Путешествия
        "https://gazprombank.ru",     // Банки
        "https://lentamega.ru",       // Магазины
        "https://taxi.yandex.ru",     // Такси
        "https://1c.ru",              // IT
        "https://vk.com",             // Соцсети
        "https://rutube.ru"           // Медиа
    )

    // Контрольные сайты (должны работать только при полном интернете)
    private val externalSites = listOf(
        "https://google.com",
        "https://youtube.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        detailsText = findViewById(R.id.detailsText)
        testButton = findViewById(R.id.testButton)
        progressBar = findViewById(R.id.progressBar)
        glassCard = findViewById(R.id.glassCard)

        // Настройка эффекта стекла для карточки
        setupLiquidGlassEffect()

        testButton.setOnClickListener {
            startTesting()
        }
    }

    private fun setupLiquidGlassEffect() {
        // Визуальная настройка карточки (прозрачность и тени уже в XML)
        glassCard.alpha = 0.95f
    }

    private fun startTesting() {
        testButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Тестирование..."
        detailsText.text = "Проверка доступности ресурсов"

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()

            // Параллельная проверка белых списков
            val whiteListResults = withContext(Dispatchers.IO) {
                whiteListSites.map { site ->
                    async { checkSite(site) }
                }.awaitAll()
            }

            val whiteListAccessible = whiteListResults.count { it }
            val whiteListTotal = whiteListSites.size

            // Параллельная проверка внешних сайтов
            val externalResults = withContext(Dispatchers.IO) {
                externalSites.map { site ->
                    async { checkSite(site) }
                }.awaitAll()
            }

            val externalAccessible = externalResults.count { it }
            val externalTotal = externalSites.size

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            withContext(Dispatchers.Main) {
                updateUI(whiteListAccessible, whiteListTotal, externalAccessible, externalTotal, duration)
                testButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkSite(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000 // Уменьшено до 3 сек для скорости
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            (responseCode >= 200 && responseCode < 400)
        } catch (e: IOException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateUI(whiteOk: Int, whiteTotal: Int, externalOk: Int, externalTotal: Int, duration: Long) {
        if (externalOk == externalTotal && whiteOk == whiteTotal) {
            // Все сайты работают
            statusText.text = "Интернет работает нормально"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            detailsText.text = "Доступны все проверенные ресурсы.\nВремя теста: ${duration}мс"
        } else if (whiteOk > (whiteTotal / 2) && externalOk == 0) {
            // Работают только белые списки
            statusText.text = "Белые списки"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_orange))
            detailsText.text = "Доступны только российские сервисы из белого списка.\nВнешние ресурсы заблокированы.\nВремя теста: ${duration}мс"
        } else if (whiteOk == 0) {
            // Ничего не работает
            statusText.text = "Нет соединения"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            detailsText.text = "Проверьте подключение к сети.\nВремя теста: ${duration}мс"
        } else {
            // Смешанный результат (нестабильное соединение)
            statusText.text = "Нестабильное соединение"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.status_yellow))
            detailsText.text = "Часть ресурсов недоступна.\nБелый список: $whiteOk/$whiteTotal\nВнешние: $externalOk/$externalTotal"
        }
    }
}
