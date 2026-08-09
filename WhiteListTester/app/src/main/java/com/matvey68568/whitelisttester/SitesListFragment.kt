package com.matvey68568.whitelisttester

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.matvey68568.whitelisttester.databinding.FragmentSitesListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SitesListFragment : Fragment() {

    private var _binding: FragmentSitesListBinding? = null
    private val binding get() = _binding!!

    private lateinit var siteAdapter: SiteEditAdapter
    private val sites = mutableListOf<String>()
    
    var onSitesChanged: ((List<String>) -> Unit)? = null
    var listType: String = "white" // "white" или "external"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSitesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Загружаем сайты из SharedPreferences
        loadSites()

        siteAdapter = SiteEditAdapter(
            sites,
            onSiteChanged = { position, newUrl ->
                if (newUrl.isNotEmpty()) {
                    sites[position] = newUrl
                    saveSites()
                } else {
                    sites.removeAt(position)
                    siteAdapter.notifyItemRemoved(position)
                    saveSites()
                }
            },
            onSiteDeleted = { position ->
                showDeleteConfirmation(position)
            }
        )

        binding.sitesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.sitesRecyclerView.adapter = siteAdapter

        // Добавляем поддержку перетаскивания
        setupItemTouchHelper()

        binding.addSiteFab.setOnClickListener {
            addNewSite()
        }
    }

    private fun loadSites() {
        val prefs = requireContext().getSharedPreferences("site_lists", 0)
        val key = if (listType == "white") "white_list_sites" else "external_list_sites"
        val saved = prefs.getStringSet(key, null)
        
        // Если список пустой или не сохранен, загружаем значения по умолчанию
        if (saved == null || saved.isEmpty()) {
            if (listType == "white") {
                sites.addAll(listOf(
                    "https://yandex.ru",
                    "https://yandex.ru/maps",
                    "https://rutube.ru",
                    "https://gosuslugi.ru"
                ))
            } else {
                sites.add("https://google.com")
            }
            saveSites()
        } else {
            // Конвертируем в список для сохранения порядка
            sites.clear()
            sites.addAll(saved.toList())
        }
    }

    private fun saveSites() {
        val prefs = requireContext().getSharedPreferences("site_lists", 0)
        val key = if (listType == "white") "white_list_sites" else "external_list_sites"
        prefs.edit().putStringSet(key, sites.toSet()).apply()
        onSitesChanged?.invoke(sites.toList())
    }

    private fun addNewSite() {
        sites.add("https://")
        siteAdapter.notifyItemInserted(sites.size - 1)
        binding.sitesRecyclerView.scrollToPosition(sites.size - 1)
        saveSites()
    }

    private fun showDeleteConfirmation(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить сайт?")
            .setMessage("Вы уверены, что хотите удалить этот сайт из списка?")
            .setPositiveButton("Удалить") { _, _ ->
                sites.removeAt(position)
                siteAdapter.notifyItemRemoved(position)
                saveSites()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupItemTouchHelper() {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                sites.swap(fromPosition, toPosition)
                siteAdapter.notifyItemMoved(fromPosition, toPosition)
                saveSites()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                showDeleteConfirmation(position)
            }
        }

        val itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper.attachToRecyclerView(binding.sitesRecyclerView)
    }

    private fun MutableList<String>.swap(fromPosition: Int, toPosition: Int) {
        val temp = this[fromPosition]
        this[fromPosition] = this[toPosition]
        this[toPosition] = temp
    }

    fun getSites(): List<String> = sites.toList()

    fun setSites(newSites: List<String>) {
        sites.clear()
        sites.addAll(newSites)
        siteAdapter.notifyDataSetChanged()
        saveSites()
    }

    fun forceSave() {
        // Сохраняем текущее состояние списка
        saveSites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
