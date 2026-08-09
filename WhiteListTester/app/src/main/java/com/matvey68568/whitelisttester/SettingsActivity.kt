package com.matvey68568.whitelisttester

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.matvey68568.whitelisttester.databinding.ActivitySettingsBinding
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var siteAdapter: SiteEditableAdapter
    private val sitesList = mutableListOf<String>()

    companion object {
        private const val SHARED_PREFS_NAME = "whitelist_tester_prefs"
        private const val SITES_KEY = "tested_sites"
        private const val REQUEST_CODE_IMPORT = 1001
        private const val REQUEST_CODE_EXPORT = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved sites
        loadSites()

        siteAdapter = SiteEditableAdapter(sitesList, 
            onEditClick = { position -> showEditDialog(position) },
            onDeleteClick = { position -> deleteSite(position) }
        )
        binding.sitesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sitesRecyclerView.adapter = siteAdapter

        // Back button - close without saving
        binding.backButton.setOnClickListener {
            finish()
        }

        // Save button - save and close
        binding.saveButton.setOnClickListener {
            saveSitesAndClose()
        }

        // Add site button
        binding.addSiteButton.setOnClickListener {
            showAddSiteDialog()
        }

        // Import button
        binding.importButton.setOnClickListener {
            importSites()
        }

        // Export button
        binding.exportButton.setOnClickListener {
            exportSites()
        }

        updateEmptyState()
    }

    private fun loadSites() {
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val sitesJson = prefs.getString(SITES_KEY, null)
        
        if (sitesJson != null) {
            try {
                val jsonArray = JSONArray(sitesJson)
                sitesList.clear()
                for (i in 0 until jsonArray.length()) {
                    sitesList.add(jsonArray.getString(i))
                }
            } catch (e: JSONException) {
                sitesList.clear()
            }
        }
        
        // If no saved sites, use default list from MainActivity
        if (sitesList.isEmpty()) {
            sitesList.addAll(listOf(
                "https://yandex.ru",
                "https://yandex.ru/maps",
                "https://rutube.ru",
                "https://gosuslugi.ru",
                "https://google.com"
            ))
        }
    }

    private fun saveSitesAndClose() {
        // Clean up sites: remove empty strings and trim whitespace
        val cleanedSites = sitesList
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        val prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        val jsonArray = JSONArray()
        cleanedSites.forEach { site ->
            jsonArray.put(site)
        }
        
        editor.putString(SITES_KEY, jsonArray.toString())
        editor.apply()
        
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateEmptyState() {
        if (sitesList.isEmpty()) {
            binding.emptyStateTextView.visibility = View.VISIBLE
            binding.sitesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateTextView.visibility = View.GONE
            binding.sitesRecyclerView.visibility = View.VISIBLE
        }
        siteAdapter.notifyDataSetChanged()
    }

    private fun showAddSiteDialog() {
        val editText = TextInputEditText(this).apply {
            hint = getString(R.string.settings_site_hint)
            setSingleLine()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_add_site))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = editText.text?.toString()?.trim() ?: ""
                if (url.isNotEmpty()) {
                    sitesList.add(url)
                    siteAdapter.notifyItemInserted(sitesList.size - 1)
                    updateEmptyState()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(position: Int) {
        val editText = TextInputEditText(this).apply {
            setText(sitesList[position])
            hint = getString(R.string.settings_site_hint)
            setSingleLine()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_edit))
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = editText.text?.toString()?.trim() ?: ""
                if (url.isNotEmpty()) {
                    sitesList[position] = url
                    siteAdapter.notifyItemChanged(position)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSite(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_delete))
            .setMessage("Удалить этот сайт из списка?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                sitesList.removeAt(position)
                siteAdapter.notifyItemRemoved(position)
                updateEmptyState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importSites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/*"))
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    private fun exportSites() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "sites.json")
        }
        startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_CODE_IMPORT -> handleImport(data.data)
                REQUEST_CODE_EXPORT -> handleExport(data.data)
            }
        }
    }

    private fun handleImport(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(this, getString(R.string.settings_import_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val content = reader.readText()
                    
                    val importedSites = try {
                        // Try to parse as JSON array
                        val jsonArray = JSONArray(content)
                        val sites = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            val site = jsonArray.getString(i).trim()
                            if (site.isNotEmpty()) {
                                sites.add(site)
                            }
                        }
                        sites
                    } catch (e: JSONException) {
                        // Fallback: parse as text file with one site per line
                        content.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && it.startsWith("http") }
                    }

                    if (importedSites.isNotEmpty()) {
                        sitesList.clear()
                        sitesList.addAll(importedSites)
                        siteAdapter.notifyDataSetChanged()
                        updateEmptyState()
                        Toast.makeText(this, getString(R.string.settings_import_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.settings_import_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.settings_import_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleExport(uri: Uri?) {
        if (uri == null) {
            Toast.makeText(this, getString(R.string.settings_export_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Clean up sites before export
            val cleanedSites = sitesList
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val jsonArray = JSONArray()
            cleanedSites.forEach { site ->
                jsonArray.put(site)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonArray.toString(2)) // Pretty print with 2-space indent
                }
            }

            Toast.makeText(this, getString(R.string.settings_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.settings_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    class SiteEditableAdapter(
        private val sites: MutableList<String>,
        private val onEditClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SiteEditableAdapter.SiteViewHolder>() {

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
            private val siteUrlTextView: TextView = itemView.findViewById(R.id.siteUrlTextView)
            private val editButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.editButton)
            private val deleteButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.deleteButton)

            fun bind(site: String, position: Int) {
                siteUrlTextView.text = site
                
                editButton.setOnClickListener {
                    onEditClick(position)
                }
                
                deleteButton.setOnClickListener {
                    onDeleteClick(position)
                }
            }
        }
    }
}
