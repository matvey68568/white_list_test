package com.matvey68568.whitelisttester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.matvey68568.whitelisttester.databinding.ActivityMainBinding
import com.matvey68568.whitelisttester.databinding.DialogAddEditSiteBinding
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

    // Список сайтов для тестирования (теперь изменяемый)
    private val testSites = mutableListOf(
        "https://yandex.ru",        // Поисковик
        "https://yandex.ru/maps",   // Карты
        "https://rutube.ru",        // Видеохостинг
        "https://gosuslugi.ru"      // Госуслуги
    ).map { SiteItem(it, true) }.toMutableList()

    // Контрольные сайты (должны работать только при полном интернете)
    private val externalSites = listOf(
        "https://google.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        siteAdapter = SiteAdapter(
            onEditClick = { site -> showEditDialog(site) },
            onDeleteClick = { site -> deleteSite(site) }
        )
        binding.sitesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sitesRecyclerView.adapter = siteAdapter

        binding.testButton.setOnClickListener {
            startTesting()
        }

        binding.addSiteFab.setOnClickListener {
            showAddDialog()
        }

        // Инициализируем адаптер начальными данными
        updateAdapterWithSites()
    }

    private fun updateAdapterWithSites() {
        val allSites = testSites.map { it.url } + externalSites
        siteAdapter.setSites(allSites.map { SiteDisplayItem(it, null, isDeletable = testSites.contains(SiteItem(it, true))) })
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddEditSiteBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Добавить сайт")
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.setOnClickListener {
            val url = dialogBinding.urlEditText.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                dialogBinding.urlInputLayout.error = "Введите URL сайта"
                return@setOnClickListener
            }

            // Добавляем https:// если нет протокола
            val normalizedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            // Проверяем дубликаты
            if (testSites.any { it.url == normalizedUrl } || externalSites.contains(normalizedUrl)) {
                dialogBinding.urlInputLayout.error = "Такой сайт уже есть в списке"
                return@setOnClickListener
            }

            testSites.add(SiteItem(normalizedUrl, true))
            updateAdapterWithSites()
            dialog.dismiss()
            Toast.makeText(this, "Сайт добавлен", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditDialog(site: SiteDisplayItem) {
        val dialogBinding = DialogAddEditSiteBinding.inflate(layoutInflater)
        dialogBinding.urlEditText.setText(site.url)
        dialogBinding.urlInputLayout.hint = "URL сайта"
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Редактировать сайт")
            .setView(dialogBinding.root)
            .create()

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.saveButton.setOnClickListener {
            val newUrl = dialogBinding.urlEditText.text?.toString()?.trim() ?: ""
            if (newUrl.isEmpty()) {
                dialogBinding.urlInputLayout.error = "Введите URL сайта"
                return@setOnClickListener
            }

            // Добавляем https:// если нет протокола
            val normalizedUrl = if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                "https://$newUrl"
            } else {
                newUrl
            }

            // Проверяем дубликаты (исключая текущий сайт)
            if (testSites.any { it.url == normalizedUrl && it.url != site.url } || 
                (externalSites.contains(normalizedUrl) && normalizedUrl != site.url)) {
                dialogBinding.urlInputLayout.error = "Такой сайт уже есть в списке"
                return@setOnClickListener
            }

            // Обновляем сайт в списке
            val index = testSites.indexOfFirst { it.url == site.url }
            if (index != -1) {
                testSites[index] = SiteItem(normalizedUrl, true)
            }
            
            updateAdapterWithSites()
            dialog.dismiss()
            Toast.makeText(this, "Сайт обновлен", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun deleteSite(site: SiteDisplayItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить сайт")
            .setMessage("Вы уверены, что хотите удалить ${site.url} из списка?")
            .setPositiveButton("Удалить") { _, _ ->
                testSites.removeAll { it.url == site.url }
                updateAdapterWithSites()
                Toast.makeText(this, "Сайт удален", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startTesting() {
        binding.testButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        binding.statusTextView.text = "Тестирование..."
        binding.detailsTextView.text = "Проверка доступности ресурсов"

        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // Параллельная проверка всех сайтов из списка тестирования
            val testSitesUrls = testSites.map { it.url }
            val testResults = withContext(Dispatchers.IO) {
                testSitesUrls.map { site ->
                    async { checkSite(site) }
                }.awaitAll()
            }

            val testAccessible = testResults.count { it }
            val testTotal = testSitesUrls.size

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
                updateUI(testAccessible, testTotal, externalAccessible, externalTotal, duration)
                displaySiteResults(testSitesUrls.zip(testResults).map { SiteDisplayItem(it.first, it.second, isDeletable = true) } + 
                                   externalSites.zip(externalResults).map { SiteDisplayItem(it.first, it.second, isDeletable = false) })
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

    private fun displaySiteResults(results: List<SiteDisplayItem>) {
        siteAdapter.setSites(results)
    }

    data class SiteItem(val url: String, val isEditable: Boolean = true)
    data class SiteDisplayItem(val url: String, val status: Boolean? = null, val isDeletable: Boolean = true)

    inner class SiteAdapter(
        private val onEditClick: (SiteDisplayItem) -> Unit,
        private val onDeleteClick: (SiteDisplayItem) -> Unit
    ) : RecyclerView.Adapter<SiteAdapter.SiteViewHolder>() {

        private var sites: List<SiteDisplayItem> = emptyList()

        fun setSites(newSites: List<SiteDisplayItem>) {
            sites = newSites
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_site, parent, false)
            return SiteViewHolder(view)
        }

        override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
            holder.bind(sites[position], onEditClick, onDeleteClick)
        }

        override fun getItemCount(): Int = sites.size

        inner class SiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val siteNameTextView: TextView = itemView.findViewById(R.id.siteNameTextView)
            private val siteStatusTextView: TextView = itemView.findViewById(R.id.siteStatusTextView)
            private val statusIconTextView: TextView = itemView.findViewById(R.id.statusIconTextView)
            private val editButton: ImageView = itemView.findViewById(R.id.editButton)
            private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

            fun bind(site: SiteDisplayItem, onEdit: (SiteDisplayItem) -> Unit, onDelete: (SiteDisplayItem) -> Unit) {
                siteNameTextView.text = site.url
                
                // Показываем статус только если он известен (после теста)
                if (site.status != null) {
                    siteStatusTextView.text = if (site.status) "Доступен" else "Недоступен"
                    if (site.status) {
                        statusIconTextView.text = "✓"
                        statusIconTextView.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.status_green)
                        )
                    } else {
                        statusIconTextView.text = "✗"
                        statusIconTextView.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.md_theme_error)
                        )
                    }
                } else {
                    siteStatusTextView.text = "Ожидание проверки"
                    statusIconTextView.text = ""
                }

                // Показываем кнопки редактирования/удаления только для сайтов из testSites
                editButton.visibility = if (site.isDeletable) View.VISIBLE else View.GONE
                deleteButton.visibility = if (site.isDeletable) View.VISIBLE else View.GONE

                editButton.setOnClickListener { onEdit(site) }
                deleteButton.setOnClickListener { onDelete(site) }
            }
        }
    }
}
