package com.example.iot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class RecordRow(val id: String, val title: String, val subtitle: String,
    val detail: String, val dialog: String = "")

class RecordAdapter(private val onClick: (RecordRow) -> Unit = {}) :
    ListAdapter<RecordRow, RecordAdapter.Holder>(object : DiffUtil.ItemCallback<RecordRow>() {
        override fun areItemsTheSame(a: RecordRow, b: RecordRow) = a.id == b.id
        override fun areContentsTheSame(a: RecordRow, b: RecordRow) = a == b
    }) {
    class Holder(view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.row_record, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.itemView.apply {
            findViewById<TextView>(R.id.recordTitle).text = row.title
            findViewById<TextView>(R.id.recordSubtitle).text = row.subtitle
            findViewById<TextView>(R.id.recordDetail).text = row.detail
            isClickable = row.dialog.isNotEmpty()
            setOnClickListener { if (row.dialog.isNotEmpty()) onClick(row) }
        }
    }
}
