package com.matvey68568.whitelisttester

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.matvey68568.whitelisttester.databinding.ActivitySettingsBinding
import com.matvey68568.whitelisttester.databinding.ItemSiteSettingBinding
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var whiteListAdapter: SettingsAdapter
    private lateinit var externalAdapter: SettingsAdapter

    private var whiteListSites = mutableListOf<String>()
    private var externalSites = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем данные из Intent
        whiteListSites = intent.getStringArrayListExtra("whiteListSites")?.toMutableList() ?: mutableListOf(
            "https://yandex.ru",
            "https://yandex.ru/maps",
            "https://rutube.ru",
            "https://gosuslugi.ru"
        )
        externalSites = intent.getStringArrayListExtra("externalSites")?.toMutableList() ?: mutableListOf(
            "https://google.com"
        )

        // Настраиваем Toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Настраиваем адаптеры для двух списков
        whiteListAdapter = SettingsAdapter(whiteListSites, isExternal = false) { sites ->
            whiteListSites = sites.toMutableList()
            saveData()
        }
        externalAdapter = SettingsAdapter(externalSites, isExternal = true) { sites ->
            externalSites = sites.toMutableList()
            saveData()
        }

        binding.whiteListRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.whiteListRecyclerView.adapter = whiteListAdapter

        binding.externalRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.externalRecyclerView.adapter = externalAdapter

        // Кнопка добавления сайта
        binding.addSiteButton.setOnClickListener {
            val urlText = binding.siteInputEditText.text?.toString()?.trim() ?: ""
            if (isValidUrl(urlText)) {
                val addToExternal = binding.externalRadioButton.isChecked
                val targetList = if (addToExternal) externalSites else whiteListSites
                
                if (!targetList.contains(urlText)) {
                    targetList.add(urlText)
                    if (addToExternal) {
                        externalAdapter.setSites(externalSites)
                    } else {
                        whiteListAdapter.setSites(whiteListSites)
                    }
                    binding.siteInputEditText.text?.clear()
                }
                saveData()
            } else {
                binding.siteInputLayout.error = getString(R.string.invalid_url)
            }
        }

        binding.siteInputEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.siteInputLayout.error = null
            }
        }
    }

    private fun saveData() {
        // Сохраняем данные в SharedPreferences или передаем обратно в MainActivity
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putStringSet("whiteListSites", whiteListSites.toSet())
            putStringSet("externalSites", externalSites.toSet())
            apply()
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    override fun onBackPressed() {
        saveData()
        setResult(RESULT_OK)
        finish()
    }

    override fun onNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class SettingsAdapter(
        private var sites: MutableList<String>,
        private val isExternal: Boolean,
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
            if (position < sites.size) {
                holder.bind(sites[position], position)
            }
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
                if (position < 0 || position >= sites.size) {
                    return
                }
                
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
                            // Проверка: google.com только во внешних
                            if (newUrl == "https://google.com" && !isExternal) {
                                // Нельзя добавить google.com в белый список
                                return@setPositiveButton
                            }
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
                    return
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
