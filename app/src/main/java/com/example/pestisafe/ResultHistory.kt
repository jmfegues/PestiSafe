package com.example.pestisafe

import java.io.Serializable

data class ResultHistory(
    val id: String = "",
    val title: String = "", // <-- Added field
    val predictionClass: String = "",
    val condition: String = "",
    val message: String = "",
    val imageBase64: String = "",
    val timestamp: Long = 0L
) : Serializable {
    fun getDisplayTitle(): String {
        return if (title.isNotBlank()) {
            title
        } else {
            val formattedDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
            "$predictionClass – $formattedDate"
        }
    }
}