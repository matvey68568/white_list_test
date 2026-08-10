package com.matvey68568.whitelisttester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.matvey68568.whitelisttester.databinding.ActivityMainBinding
import com.matvey68568.whitelisttester.databinding.DialogSettingsBinding
import com.matvey68568.whitelisttester.databinding.ItemSiteSettingBinding
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
    private lateinit var settingsAdapter: SettingsAdapter

    // Список сайтов для тестирования (сохраняется в памяти)
    private var whiteListSites = mutableListOf(
        "https://yandex.ru",        // Поисковик
        "https://yandex.ru/maps",   // Карты
        "https://rutube.ru",        // Видеохостинг
        "https://gosuslugi.ru"      // Госуслуги
    ).toList()

    // Контрольные сайты (должны работать только при полном интернете)
    private val externalSites = listOf(
        "https://google.com"
    )

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
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(LayoutInflater.from(this))
        
        settingsAdapter = SettingsAdapter(whiteListSites.toMutableList()) { sites ->
            whiteListSites = sites.toList()
        }
        
        dialogBinding.settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.settingsRecyclerView.adapter = settingsAdapter

        dialogBinding.addSiteButton.setOnClickListener {
            val urlText = dialogBinding.siteInputEditText.text?.toString()?.trim() ?: ""
            if (isValidUrl(urlText)) {
                val currentSites = settingsAdapter.getSites().toMutableList()
                if (!currentSites.contains(urlText)) {
                    currentSites.add(urlText)
                    settingsAdapter.setSites(currentSites)
                    whiteListSites = currentSites.toList()
                    dialogBinding.siteInputEditText.text?.clear()
                }
            } else {
                dialogBinding.siteInputLayout.error = getString(R.string.invalid_url)
            }
        }

        dialogBinding.siteInputEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                dialogBinding.siteInputLayout.error = null
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setTitle(R.string.settings_title)
            .setPositiveButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
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

    class SettingsAdapter(
        private var sites: MutableList<String>,
        private val onSaveCallback: (List<String>) -> Unit
    ) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

        fun getSites(): List<String> = sites.toList()

        fun setSites(newSites: List<String>) {
            sites = newSites.toMutableList()
            notifyDataSetChanged()
            onSaveCallback(sites.toList())
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
            val view = ItemSiteSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return SettingsViewHolder(view)
        }

        override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
            holder.bind(sites[position], position)
        }

        override fun getItemCount(): Int = sites.size

        inner class SettingsViewHolder(
            private val binding: ItemSiteSettingBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(url: String, position: Int) {
                binding.siteUrlTextView.text = url

                binding.editSiteButton.setOnClickListener {
                    showEditDialog(url, position)
                }

                binding.deleteSiteButton.setOnClickListener {
                    showDeleteDialog(url, position)
                }
            }

            private fun showEditDialog(url: String, position: Int) {
                val editText = TextInputEditText(itemView.context).apply {
                    setText(url)
                    hint = itemView.context.getString(R.string.add_site_hint)
                    setPadding(48, 32, 48, 32)
                }

                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(R.string.edit_site)
                    .setView(editText)
                    .setPositiveButton(R.string.save) { dialog, _ ->
                        val newUrl = editText.text?.toString()?.trim() ?: ""
                        if (isValidUrl(newUrl)) {
                            sites[position] = newUrl
                            notifyItemChanged(position)
                            onSaveCallback(sites.toList())
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            private fun showDeleteDialog(url: String, position: Int) {
                if (position < 0 || position >= sites.size) {
                    return // Защита от выхода за границы списка
                }
                
                MaterialAlertDialogBuilder(itemView.context)
                    .setTitle(R.string.delete_site)
                    .setMessage("Удалить сайт \"$url\" из списка?")
                    .setPositiveButton(R.string.delete_site) { dialog, _ ->
                        val currentSites = sites.toMutableList()
                        if (position < currentSites.size) {
                            currentSites.removeAt(position)
                            sites = currentSites
                            notifyItemRemoved(position)
                            onSaveCallback(sites.toList())
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            private fun isValidUrl(url: String): Boolean {
                return url.startsWith("http://") || url.startsWith("https://")
            }
        }
    }
}
