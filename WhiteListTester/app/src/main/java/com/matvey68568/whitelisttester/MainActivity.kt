package com.matvey68568.whitelisttester

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var siteAdapter: SiteAdapter

    private val prefs by lazy { getSharedPreferences("site_lists", 0) }
    
    // Списки сайтов по умолчанию
    private val defaultWhiteListSites = listOf(
        "https://yandex.ru",        // Поисковик
        "https://yandex.ru/maps",   // Карты
        "https://rutube.ru",        // Видеохостинг
        "https://gosuslugi.ru"      // Госуслуги
    )

    private val defaultExternalSites = listOf(
        "https://google.com"
    )

    // Получаем списки сайтов из SharedPreferences или используем значения по умолчанию
    private fun getWhiteListSites(): List<String> {
        val saved = prefs.getStringSet("white_list_sites", null)
        return saved?.toList() ?: defaultWhiteListSites
    }

    private fun getExternalSites(): List<String> {
        val saved = prefs.getStringSet("external_list_sites", null)
        return saved?.toList() ?: defaultExternalSites
    }

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

        // Добавляем кнопку настроек
        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startTesting() {
        binding.testButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        binding.statusTextView.text = "Тестирование..."
        binding.detailsTextView.text = "Проверка доступности ресурсов"

        val whiteListSites = getWhiteListSites()
        val externalSites = getExternalSites()

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
