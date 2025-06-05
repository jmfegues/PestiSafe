package com.example.pestisafe

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.pestisafe.Activity.HistoryDetailActivity
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private var historyList: List<ResultHistory>,
    private val onDeleteClick: (ResultHistory) -> Unit,
    private val onEditTitleClicked: (ResultHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgResult: ImageView = itemView.findViewById(R.id.imageView)
        val txtTitle: TextView = itemView.findViewById(R.id.textTitle)
        val btnRename: ImageView = itemView.findViewById(R.id.btnRename)
        val txtCondition: TextView = itemView.findViewById(R.id.textCondition)
        val txtResidueRange: TextView = itemView.findViewById(R.id.textResidueRange)
        val txtDate: TextView = itemView.findViewById(R.id.textDate)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]

        // Decode base64 image
        if (!item.imageBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(item.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.imgResult.setImageBitmap(bitmap)
            } catch (e: IllegalArgumentException) {
                holder.imgResult.setImageResource(android.R.color.darker_gray)
            }
        } else {
            holder.imgResult.setImageResource(android.R.color.darker_gray)
        }

        // Bind data
        holder.txtTitle.text = item.getDisplayTitle()
        holder.txtCondition.text = item.condition
        holder.txtResidueRange.text = "Residue: ${item.residueRange}"
        holder.txtDate.text = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            .format(Date(item.timestamp))

        // Buttons
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        holder.btnRename.setOnClickListener { onEditTitleClicked(item) }
        holder.txtTitle.setOnClickListener { onEditTitleClicked(item) }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, HistoryDetailActivity::class.java).apply {
                putExtra("result", item)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = historyList.size

    fun updateList(newList: List<ResultHistory>) {
        historyList = newList
        notifyDataSetChanged()
    }
}