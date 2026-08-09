package com.matvey68568.whitelisttester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.matvey68568.whitelisttester.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var siteAdapter: SiteAdapter
    private val siteListFetcher = SiteListFetcher()
    
    // Счетчик нажатий для debug меню
    private var debugClickCount = 0
    private var debugClickTimer: Long = 0

    // Дефолтные списки на случай ошибки загрузки
    private val defaultWhitelistSites = listOf(
        "https://yandex.ru",
        "https://yandex.ru/maps",
        "https://rutube.ru",
        "https://gosuslugi.ru"
    )

    private val defaultExternalSites = listOf(
        "https://google.com"
    )

    // Текущие списки сайтов (загружаются из репозитория)
    private var currentWhitelistSites: List<String> = defaultWhitelistSites
    private var currentExternalSites: List<String> = defaultExternalSites
    
    // Debug информация
    private var lastFetchTime: Long = 0
    private var fetchSuccess: Boolean = false
    private var fetchError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        siteAdapter = SiteAdapter()
        binding.sitesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sitesRecyclerView.adapter = siteAdapter

        binding.testButton.setOnClickListener {
            startTesting()
        }
        
        // Обработчик нажатий на невидимую область для debug меню
        binding.debugTouchArea.setOnClickListener {
            handleDebugTouch()
        }

        // Загрузка актуальных списков сайтов при старте
        loadSiteLists()
    }
    
    /**
     * Обработка нажатий на debug область
     * Показывает debug меню после 4 нажатий в течение 2 секунд
     */
    private fun handleDebugTouch() {
        val currentTime = System.currentTimeMillis()
        
        // Если прошло больше 2 секунд с последнего нажатия, сбрасываем счетчик
        if (currentTime - debugClickTimer > 2000) {
            debugClickCount = 0
        }
        
        debugClickCount++
        debugClickTimer = currentTime
        
        if (debugClickCount >= 4) {
            showDebugMenu()
            debugClickCount = 0
        } else {
            Toast.makeText(this, "Ещё ${4 - debugClickCount} нажатий для debug меню", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Показывает диалог с debug информацией
     */
    private fun showDebugMenu() {
        val whitelistSize = currentWhitelistSites.size
        val externalSize = currentExternalSites.size
        val isDefaultWhitelist = currentWhitelistSites == defaultWhitelistSites
        val isDefaultExternal = currentExternalSites == defaultExternalSites
        
        val fetchStatus = when {
            fetchSuccess -> "Успешно"
            fetchError != null -> "Ошибка: $fetchError"
            else -> "Не загружалось"
        }
        
        val debugInfo = buildString {
            appendLine("=== Debug Информация ===")
            appendLine()
            appendLine("📊 Списки сайтов:")
            appendLine("  Белый список: $whitelistSize сайтов")
            appendLine("  Внешние сайты: $externalSize сайтов")
            appendLine()
            appendLine("🔄 Статус загрузки:")
            appendLine("  Последняя загрузка: ${if (lastFetchTime > 0) android.text.format.DateFormat.format(\"HH:mm:ss\", lastFetchTime) else \"Никогда\"}")
            appendLine("  Результат: $fetchStatus")
            appendLine()
            appendLine("📋 Используемые списки:")
            appendLine("  Белый список дефолтный: $isDefaultWhitelist")
            appendLine("  Внешний список дефолтный: $isDefaultExternal")
            appendLine()
            appendLine("🌐 URL для загрузки:")
            appendLine("  ${SiteListFetcher.SITES_JSON_URL}")
            appendLine()
            appendLine("📱 Текущие сайты:")
            appendLine("  Белый список: ${currentWhitelistSites.joinToString(\", \")}")
            appendLine("  Внешние: ${currentExternalSites.joinToString(\", \")}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Debug Информация")
            .setMessage(debugInfo)
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("Перезагрузить списки") { _, _ ->
                loadSiteLists()
                Toast.makeText(this@MainActivity, "Загрузка списков...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Сбросить на дефолтные") { _, _ ->
                currentWhitelistSites = defaultWhitelistSites
                currentExternalSites = defaultExternalSites
                fetchSuccess = false
                fetchError = "Сброшено на дефолтные значения"
                lastFetchTime = System.currentTimeMillis()
                Toast.makeText(this@MainActivity, "Списки сброшены на дефолтные", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    /**
     * Загружает списки сайтов из удаленного репозитория
     * При ошибке использует дефолтные списки
     */
    private fun loadSiteLists() {
        lifecycleScope.launch(Dispatchers.IO) {
            val defaultLists = SiteListFetcher.SiteLists(defaultWhitelistSites, defaultExternalSites)
            try {
                val siteLists = siteListFetcher.fetchSiteLists()
                withContext(Dispatchers.Main) {
                    currentWhitelistSites = siteLists.whitelist
                    currentExternalSites = siteLists.external
                    fetchSuccess = true
                    fetchError = null
                    lastFetchTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentWhitelistSites = defaultWhitelistSites
                    currentExternalSites = defaultExternalSites
                    fetchSuccess = false
                    fetchError = e.message ?: "Неизвестная ошибка"
                    lastFetchTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun startTesting() {
        binding.testButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        binding.statusTextView.text = "Тестирование..."
        binding.detailsTextView.text = "Проверка доступности ресурсов"

        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // Параллельная проверка белых списков
            val whiteListResults = withContext(Dispatchers.IO) {
                currentWhitelistSites.map { site ->
                    async { checkSite(site) }
                }.awaitAll()
            }

            val whiteListAccessible = whiteListResults.count { it }
            val whiteListTotal = currentWhitelistSites.size

            // Параллельная проверка внешних сайтов
            val externalResults = withContext(Dispatchers.IO) {
                currentExternalSites.map { site ->
                    async { checkSite(site) }
                }.awaitAll()
            }

            val externalAccessible = externalResults.count { it }
            val externalTotal = currentExternalSites.size

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            withContext(Dispatchers.Main) {
                updateUI(whiteListAccessible, whiteListTotal, externalAccessible, externalTotal, duration)
                displaySiteResults(currentWhitelistSites.zip(whiteListResults) + currentExternalSites.zip(externalResults))
                binding.testButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkSite(urlString: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            connection.useCaches = false
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            (responseCode >= 200 && responseCode < 400)
        } catch (e: IOException) {
            false
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun updateUI(whiteOk: Int, whiteTotal: Int, externalOk: Int, externalTotal: Int, duration: Long) {
        if (externalOk == externalTotal && whiteOk == whiteTotal) {
            // Все сайты работают
            binding.statusTextView.text = "Интернет работает нормально"
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.md_theme_primary))
            binding.detailsTextView.text = "Доступны все проверенные ресурсы.\nВремя теста: ${duration}мс"
        } else if (whiteOk > (whiteTotal / 2) && externalOk == 0) {
            // Работают только белые списки
            binding.statusTextView.text = "Белые списки"
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.status_orange))
            binding.detailsTextView.text = "Доступны только российские сервисы из белого списка.\nВнешние ресурсы заблокированы.\nВремя теста: ${duration}мс"
        } else if (whiteOk == 0) {
            // Ничего не работает
            binding.statusTextView.text = "Нет соединения"
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.md_theme_error))
            binding.detailsTextView.text = "Проверьте подключение к сети.\nВремя теста: ${duration}мс"
        } else {
            // Смешанный результат (нестабильное соединение)
            binding.statusTextView.text = "Нестабильное соединение"
            binding.statusTextView.setTextColor(ContextCompat.getColor(this, R.color.status_yellow))
            binding.detailsTextView.text = "Часть ресурсов недоступна.\nБелый список: $whiteOk/$whiteTotal\nВнешние: $externalOk/$externalTotal"
        }
    }

    private fun displaySiteResults(results: List<Pair<String, Boolean>>) {
        siteAdapter.setResults(results)
    }

    class SiteAdapter : RecyclerView.Adapter<SiteAdapter.SiteViewHolder>() {

        private var sites: List<Pair<String, Boolean>> = emptyList()

        fun setResults(results: List<Pair<String, Boolean>>) {
            sites = results
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_site, parent, false)
            return SiteViewHolder(view)
        }

        override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
            holder.bind(sites[position])
        }

        override fun getItemCount(): Int = sites.size

        class SiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val siteNameTextView: TextView = itemView.findViewById(R.id.siteNameTextView)
            private val statusIconTextView: TextView = itemView.findViewById(R.id.statusIconTextView)

            fun bind(siteResult: Pair<String, Boolean>) {
                val (url, isWorking) = siteResult
                siteNameTextView.text = url

                val context = itemView.context
                if (isWorking) {
                    statusIconTextView.text = "✓"
                    statusIconTextView.setTextColor(
                        ContextCompat.getColor(context, R.color.status_green)
                    )
                } else {
                    statusIconTextView.text = "✗"
                    statusIconTextView.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_error)
                    )
                }
            }
        }
    }
}
