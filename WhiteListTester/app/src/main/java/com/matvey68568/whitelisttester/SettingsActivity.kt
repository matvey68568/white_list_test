package com.matvey68568.whitelisttester

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.matvey68568.whitelisttester.databinding.ActivitySettingsBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var whiteListFragment: SitesListFragment
    private lateinit var externalListFragment: SitesListFragment

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val whiteSites = whiteListFragment.getSites()
                        val externalSites = externalListFragment.getSites()
                        
                        val writer = OutputStreamWriter(outputStream)
                        writer.write("# White List Sites\n")
                        whiteSites.forEach { writer.write("$it\n") }
                        writer.write("\n# External Sites\n")
                        externalSites.forEach { writer.write("$it\n") }
                        writer.flush()
                    }
                    Toast.makeText(this, "Списки сайтов экспортированы", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val whiteSites = mutableListOf<String>()
                        val externalSites = mutableListOf<String>()
                        var isWhiteList = true

                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                                if (trimmed.contains("External", ignoreCase = true)) {
                                    isWhiteList = false
                                }
                                return@forEach
                            }
                            if (isWhiteList) {
                                whiteSites.add(trimmed)
                            } else {
                                externalSites.add(trimmed)
                            }
                        }

                        if (whiteSites.isNotEmpty()) {
                            whiteListFragment.setSites(whiteSites)
                        }
                        if (externalSites.isNotEmpty()) {
                            externalListFragment.setSites(externalSites)
                        }

                        Toast.makeText(this, "Списки сайтов импортированы", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка импорта: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Создаем фрагменты для списков
        whiteListFragment = SitesListFragment().apply { listType = "white" }
        externalListFragment = SitesListFragment().apply { listType = "external" }

        // Настраиваем ViewPager2 с табами
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int) = when (position) {
                0 -> whiteListFragment
                1 -> externalListFragment
                else -> whiteListFragment
            }
        }

        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Белый список"
                1 -> "Внешние сайты"
                else -> ""
            }
        }.attach()

        binding.exportButton.setOnClickListener {
            exportSites()
        }

        binding.importButton.setOnClickListener {
            importSites()
        }
    }

    private fun exportSites() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "whitelist_sites.txt")
        }
        exportLauncher.launch(intent)
    }

    private fun importSites() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        importLauncher.launch(intent)
    }

    override fun onBackPressed() {
        // Сохраняем текущие списки перед выходом
        whiteListFragment.getSites()
        externalListFragment.getSites()
        super.onBackPressed()
    }
}
