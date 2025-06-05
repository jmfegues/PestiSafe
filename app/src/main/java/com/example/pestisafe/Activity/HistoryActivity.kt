package com.example.pestisafe.Activity

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pestisafe.HistoryAdapter
import com.example.pestisafe.R
import com.example.pestisafe.ResultHistory
import com.example.pestisafe.databinding.ActivityHistoryBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.File
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var fullList: List<ResultHistory> = emptyList()
    private lateinit var databaseRef: DatabaseReference
    private var isDescending = true
    private var selectedDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            databaseRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("results")
        }

        adapter = HistoryAdapter(emptyList(),
            onDeleteClick = { item -> confirmDelete(item) },
            onEditTitleClicked = { item -> promptRenameTitle(item) }
        )

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewHistory.adapter = adapter

        if (uid != null) loadHistory()

        binding.editSearchInp.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterList(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val buttonSort = findViewById<MaterialButton>(R.id.buttonSort)
        buttonSort.setOnClickListener {
            isDescending = !isDescending
            val sortedList = if (isDescending) {
                fullList.sortedByDescending { it.timestamp }
            } else {
                fullList.sortedBy { it.timestamp }
            }
            adapter.updateList(applyFilters(sortedList, binding.editSearchInp.text.toString()))
            buttonSort.text = if (isDescending) "Sort: Newest" else "Sort: Oldest"
        }

        findViewById<MaterialButton>(R.id.buttonFilterDate).setOnClickListener {
            showDatePicker()
        }

        findViewById<MaterialButton>(R.id.buttonClearDate).visibility = android.view.View.GONE

        findViewById<MaterialButton>(R.id.buttonClearDate).setOnClickListener {
            selectedDate = null
            findViewById<MaterialButton>(R.id.buttonClearDate).visibility = android.view.View.GONE
            adapter.updateList(applyFilters(fullList, binding.editSearchInp.text.toString()))
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            selectedDate = calendar.timeInMillis
            findViewById<MaterialButton>(R.id.buttonClearDate).visibility = android.view.View.VISIBLE
            adapter.updateList(applyFilters(fullList, binding.editSearchInp.text.toString()))
        },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun confirmDelete(item: ResultHistory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Result")
            .setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Yes") { dialog, _ ->
                deleteHistoryItem(item)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun deleteHistoryItem(item: ResultHistory) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users").child(uid).child("results").child(item.id)
        ref.removeValue().addOnSuccessListener {
            Toast.makeText(this, "Deleted history item", Toast.LENGTH_SHORT).show()
            deleteLocalPdfFile(item.getDisplayTitle())
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to delete item", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHistory() {
        databaseRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val tempList = mutableListOf<ResultHistory>()
                    for (child in snapshot.children) {
                        val result = child.getValue(ResultHistory::class.java)
                        if (result != null) {
                            tempList.add(result.copy(id = child.key ?: ""))
                        }
                    }
                    fullList = tempList.sortedByDescending { it.timestamp }
                    adapter.updateList(applyFilters(fullList, binding.editSearchInp.text.toString()))
                }

                override fun onCancelled(error: DatabaseError) {
                }
            })
    }

    private fun filterList(query: String) {
        adapter.updateList(applyFilters(fullList, query))
    }

    private fun applyFilters(list: List<ResultHistory>, query: String): List<ResultHistory> {
        val queryFiltered = list.filter {
            it.getDisplayTitle().contains(query, ignoreCase = true) ||
                    it.predictionClass.contains(query, ignoreCase = true) ||
                    it.condition.contains(query, ignoreCase = true) ||
                    it.message.contains(query, ignoreCase = true)
        }
        return selectedDate?.let { date ->
            val startOfDay = date
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000
            queryFiltered.filter { it.timestamp in startOfDay until endOfDay }
        } ?: queryFiltered
    }

    private fun promptRenameTitle(item: ResultHistory) {
        val scale = resources.displayMetrics.density
        val paddingDp = (20 * scale + 0.5f).toInt()  // 16dp

        val editText = EditText(this).apply {
            setText(item.title)
            hint = "Enter new Title"
        }

        val container = FrameLayout(this).apply {
            setPadding(paddingDp, 0, paddingDp, 0)
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(container)
            .setPositiveButton("Save") { dialog, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != item.title) {
                    renameTitleInDatabase(item, newTitle)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun renameTitleInDatabase(item: ResultHistory, newTitle: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users").child(uid).child("results").child(item.id)
        ref.child("title").setValue(newTitle).addOnSuccessListener {
            Toast.makeText(this, "Renamed Successfully.", Toast.LENGTH_SHORT).show()
            renameLocalPdfFile(item.getDisplayTitle(), newTitle)
            val updatedList = fullList.map {
                if (it.id == item.id) it.copy(title = newTitle) else it
            }
            fullList = updatedList
            adapter.updateList(applyFilters(updatedList, binding.editSearchInp.text.toString()))
        }.addOnFailureListener {
            Toast.makeText(this, "Rename Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitizeFileName(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
    }

    private fun renameLocalPdfFile(oldTitle: String, newTitle: String) {
        val pdfDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (pdfDir == null) {
            Toast.makeText(this, "PDF directory not found", Toast.LENGTH_SHORT).show()
            return
        }

        val oldFile = File(pdfDir, "${sanitizeFileName(oldTitle)}.pdf")
        val newFile = File(pdfDir, "${sanitizeFileName(newTitle)}.pdf")

        if (oldFile.exists()) {
            val success = oldFile.renameTo(newFile)
            if (success) {
                Toast.makeText(this, "PDF file renamed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to rename PDF file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteLocalPdfFile(title: String) {
        val pdfDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        val file = File(pdfDir, "${sanitizeFileName(title)}.pdf")
        if (file.exists()) {
            file.delete()
        }
    }
}