package com.matvey68568.whitelisttester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var siteAdapter: SiteAdapter

    // URL для загрузки списков сайтов из репозитория
    private val sitesJsonUrl = "https://raw.githubusercontent.com/matvey68568/white_list_test_lists/refs/heads/main/sites.json"

    // Списки сайтов (загружаются из репозитория)
    private var whiteListSites = listOf<String>()
    private var externalSites = listOf<String>()

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

        // Загрузка списков сайтов при запуске
        loadSitesFromRepository()
    }

    private fun loadSitesFromRepository() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonContent = fetchJsonFromUrl(sitesJsonUrl)
                val sites = parseSitesJson(jsonContent)
                
                withContext(Dispatchers.Main) {
                    whiteListSites = sites.first
                    externalSites = sites.second
                    binding.detailsTextView.text = "Загружено сайтов: белый список (${whiteListSites.size}), внешние (${externalSites.size})"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // При ошибке загрузки используем значения по умолчанию
                    whiteListSites = listOf(
                        "https://yandex.ru",
                        "https://yandex.ru/maps",
                        "https://rutube.ru",
                        "https://gosuslugi.ru"
                    )
                    externalSites = listOf("https://google.com")
                    binding.detailsTextView.text = "Используются списки по умолчанию (ошибка загрузки)"
                }
            }
        }
    }

    private fun fetchJsonFromUrl(urlString: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode >= 200 && responseCode < 400) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw IOException("HTTP error: $responseCode")
            }
        } catch (e: Exception) {
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseSitesJson(jsonString: String): Pair<List<String>, List<String>> {
        val json = JSONObject(jsonString)
        
        val whitelist = json.getJSONArray("whitelist")
        val external = json.getJSONArray("external")
        
        val whitelistSites = mutableListOf<String>()
        for (i in 0 until whitelist.length()) {
            val site = whitelist.getString(i)
            // Добавляем https:// если нет протокола
            whitelistSites.add(if (site.startsWith("http")) site else "https://$site")
        }
        
        val externalSites = mutableListOf<String>()
        for (i in 0 until external.length()) {
            val site = external.getString(i)
            externalSites.add(if (site.startsWith("http")) site else "https://$site")
        }
        
        return Pair(whitelistSites, externalSites)
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
                displaySiteResults(whiteListSites.zip(whiteListResults) + externalSites.zip(externalResults))
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
