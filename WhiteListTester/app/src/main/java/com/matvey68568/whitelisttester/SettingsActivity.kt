package com.matvey68568.whitelisttester

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.matvey68568.whitelisttester.databinding.ActivitySettingsBinding
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var siteAdapter: SiteAdapter
    private val sitesList = mutableListOf<String>()
    
    // Исходные сайты для восстановления при отмене
    private var originalSitesList = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Загружаем текущий список сайтов из MainActivity
        val savedSites = intent.getStringArrayListExtra(EXTRA_SITES)?.toList() ?: emptyList()
        sitesList.addAll(savedSites)
        originalSitesList = savedSites

        setupRecyclerView()
        setupToolbar()
        setupButtons()
    }

    private fun setupRecyclerView() {
        siteAdapter = SiteAdapter(
            sites = sitesList,
            onEditClick = { position -> showEditDialog(position) },
            onDeleteClick = { position -> deleteSite(position) }
        )
        binding.sitesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sitesRecyclerView.adapter = siteAdapter
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.saveButton.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupButtons() {
        binding.addSiteButton.setOnClickListener {
            showAddDialog()
        }

        binding.importButton.setOnClickListener {
            importSites()
        }

        binding.exportButton.setOnClickListener {
            exportSites()
        }
    }

    private fun showAddDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.site_url_hint)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_site))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidUrl(url)) {
                        sitesList.add(url)
                        siteAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(position: Int) {
        val currentUrl = sitesList[position]
        val editText = EditText(this).apply {
            setText(currentUrl)
            hint = getString(R.string.site_url_hint)
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    if (isValidUrl(url)) {
                        sitesList[position] = url
                        siteAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSite(position: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage("Удалить сайт ${sitesList[position]}?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                sitesList.removeAt(position)
                siteAdapter.notifyDataSetChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun saveAndFinish() {
        val resultIntent = Intent().apply {
            putStringArrayListExtra(EXTRA_SITES, ArrayList(sitesList))
        }
        setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    // Import/Export с использованием Storage Access Framework
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromUri(it) }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { exportToUri(it) }
    }

    private fun importSites() {
        importLauncher.launch("application/json")
    }

    private fun exportSites() {
        exportLauncher.launch("whitelist_sites.json")
    }

    private fun importFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        jsonBuilder.append(line)
                    }
                    
                    val jsonString = jsonBuilder.toString()
                    if (jsonString.isBlank()) {
                        Toast.makeText(this, R.string.empty_file, Toast.LENGTH_SHORT).show()
                        return@use
                    }

                    try {
                        val jsonArray = JSONArray(jsonString)
                        val newSites = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            val url = jsonArray.getString(i)
                            if (isValidUrl(url)) {
                                newSites.add(url)
                            }
                        }
                        
                        if (newSites.isEmpty()) {
                            Toast.makeText(this, R.string.empty_file, Toast.LENGTH_SHORT).show()
                        } else {
                            sitesList.clear()
                            sitesList.addAll(newSites)
                            siteAdapter.notifyDataSetChanged()
                            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: JSONException) {
                        // Пытаемся прочитать как простой текстовый файл (по одному URL на строку)
                        val textSites = jsonString.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && isValidUrl(it) }
                        
                        if (textSites.isEmpty()) {
                            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
                        } else {
                            sitesList.clear()
                            sitesList.addAll(textSites)
                            siteAdapter.notifyDataSetChanged()
                            Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToUri(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    val jsonArray = JSONArray()
                    sitesList.forEach { url ->
                        jsonArray.put(url)
                    }
                    writer.write(jsonArray.toString(2))
                }
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show()
        }
    }

    class SiteAdapter(
        private val sites: List<String>,
        private val onEditClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SiteAdapter.SiteViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_site_editable, parent, false)
            return SiteViewHolder(view)
        }

        override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
            holder.bind(sites[position], position)
        }

        override fun getItemCount(): Int = sites.size

        inner class SiteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val siteNameTextView: TextView = itemView.findViewById(R.id.siteNameTextView)
            private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

            fun bind(url: String, position: Int) {
                siteNameTextView.text = url
                
                editButton.setOnClickListener {
                    onEditClick(position)
                }
                
                deleteButton.setOnClickListener {
                    onDeleteClick(position)
                }
            }
        }
    }

    companion object {
        const val EXTRA_SITES = "extra_sites"
    }
}
