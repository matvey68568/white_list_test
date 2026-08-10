package com.matvey68568.whitelisttester

import android.content.Intent
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
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

    // Список сайтов для тестирования (сохраняется в памяти)
    private var whiteListSites = mutableListOf(
        "https://yandex.ru",        // Поисковик
        "https://yandex.ru/maps",   // Карты
        "https://rutube.ru",        // Видеохостинг
        "https://gosuslugi.ru"      // Госуслуги
    ).toList()

    // Контрольные сайты (должны работать только при полном интернете)
    private var externalSites = mutableListOf(
        "https://google.com"
    ).toList()

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

        binding.settingsButton.setOnClickListener {
            openSettingsActivity()
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putStringArrayListExtra("whiteListSites", ArrayList(whiteListSites))
            putStringArrayListExtra("externalSites", ArrayList(externalSites))
        }
        startActivityForResult(intent, SETTINGS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Загружаем обновленные данные из SharedPreferences
            loadSettingsFromPrefs()
        }
    }

    private fun loadSettingsFromPrefs() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val savedWhiteList = prefs.getStringSet("whiteListSites", null)
        val savedExternal = prefs.getStringSet("externalSites", null)
        
        if (savedWhiteList != null && savedWhiteList.isNotEmpty()) {
            whiteListSites = savedWhiteList.toList()
        }
        if (savedExternal != null && savedExternal.isNotEmpty()) {
            externalSites = savedExternal.toList()
        }
    }

    companion object {
        private const val SETTINGS_REQUEST_CODE = 100
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
                displaySiteResults(whiteListSites.zip(whiteListResults), externalSites.zip(externalResults))
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

    private fun displaySiteResults(whiteListResults: List<Pair<String, Boolean>>, externalResults: List<Pair<String, Boolean>>) {
        val allResults = mutableListOf<Pair<String, Boolean>>()
        
        // Добавляем заголовок для белых списков
        if (whiteListResults.isNotEmpty()) {
            allResults.add(Pair("=== БЕЛЫЕ СПИСКИ ===", true))
            allResults.addAll(whiteListResults)
        }
        
        // Добавляем заголовок для внешних сайтов
        if (externalResults.isNotEmpty()) {
            allResults.add(Pair("=== ВНЕШНИЕ САЙТЫ ===", true))
            allResults.addAll(externalResults)
        }
        
        siteAdapter.setResults(allResults)
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
                
                // Проверяем, является ли элемент заголовком раздела
                if (url.startsWith("===")) {
                    siteNameTextView.text = url
                    siteNameTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.md_theme_primary))
                    siteNameTextView.textSize = 14f
                    statusIconTextView.visibility = View.GONE
                } else {
                    siteNameTextView.text = url
                    siteNameTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.md_theme_onSurface))
                    siteNameTextView.textSize = 15f
                    statusIconTextView.visibility = View.VISIBLE
                    
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
}
