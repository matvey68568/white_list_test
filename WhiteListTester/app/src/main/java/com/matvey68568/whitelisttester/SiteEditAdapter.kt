package com.matvey68568.whitelisttester

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.matvey68568.whitelisttester.databinding.ItemSiteEditableBinding

class SiteEditAdapter(
    private val sites: MutableList<String>,
    private val onSiteChanged: (Int, String) -> Unit,
    private val onSiteDeleted: (Int) -> Unit
) : RecyclerView.Adapter<SiteEditAdapter.SiteEditViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteEditViewHolder {
        val binding = ItemSiteEditableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SiteEditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SiteEditViewHolder, position: Int) {
        holder.bind(sites[position], position)
    }

    override fun getItemCount(): Int = sites.size

    inner class SiteEditViewHolder(private val binding: ItemSiteEditableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(site: String, position: Int) {
            binding.siteUrlEditText.setText(site)
            
            binding.siteUrlEditText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val url = binding.siteUrlEditText.text.toString().trim()
                    onSiteChanged(adapterPosition, url)
                }
            }

            binding.deleteButton.setOnClickListener {
                onSiteDeleted(adapterPosition)
            }
        }
    }
}
