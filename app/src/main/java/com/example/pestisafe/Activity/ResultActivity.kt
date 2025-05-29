package com.example.pestisafe.Activity

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.pestisafe.MainActivity
import com.example.pestisafe.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {

    private var isSaved = false
    private var firebaseDocId: String? = null
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { finish() }

        val resultImage = findViewById<ImageView>(R.id.resultImageView)
        val resultClass = findViewById<TextView>(R.id.resultClassText)
        val resultCondition = findViewById<TextView>(R.id.resultConditionText)
        val resultMessage = findViewById<TextView>(R.id.resultMessageText)
        val resultTimestamp = findViewById<TextView>(R.id.resultTimestampText)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val deleteButton = findViewById<Button>(R.id.deleteButton)

        val predictionClass = intent.getStringExtra("class")
        val condition = intent.getStringExtra("condition")
        val message = intent.getStringExtra("message")
        val imageUri = intent.getStringExtra("imageUri")?.let { Uri.parse(it) }
        val timestamp = System.currentTimeMillis()

        val formattedTime = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(timestamp))
        resultTimestamp.text = "Scanned on: $formattedTime"

        resultClass.text = "Classification: $predictionClass"
        resultCondition.text = "Condition: $condition"
        resultMessage.text = message
        imageUri?.let { resultImage.setImageURI(it) }

        saveButton.setOnClickListener {
            if (!isSaved) {
                showSaveConfirmation {
                    showLoading(true)
                    saveResultToFirebase(predictionClass, condition, message, imageUri, timestamp) {
                        showLoading(false)
                        isSaved = true
                        showPostSaveOptions()
                    }
                }
            } else {
                Toast.makeText(this, "Already saved.", Toast.LENGTH_SHORT).show()
            }
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmation {
                if (isSaved && firebaseDocId != null) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseDatabase.getInstance().reference
                            .child("users")
                            .child(uid)
                            .child("results")
                            .child(firebaseDocId!!)
                            .removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Deleted from history", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Discarding result.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressDialog = ProgressDialog(this).apply {
                setMessage("Saving result, please wait...")
                setCancelable(false)
                show()
            }
        } else {
            progressDialog?.dismiss()
        }
    }

    private fun showSaveConfirmation(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Save Result")
            .setMessage("Do you want to save this?")
            .setPositiveButton("Yes") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showDeleteConfirmation(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Result")
            .setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Yes") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPostSaveOptions() {
        AlertDialog.Builder(this)
            .setTitle("Result Saved")
            .setMessage("The result was saved successfully.")
            .setPositiveButton("Home") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Detection") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, DetectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun saveResultToFirebase(
        predictionClass: String?, condition: String?, message: String?,
        imageUri: Uri?, timestamp: Long, onComplete: () -> Unit
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        val uid = currentUser.uid
        val databaseRef = FirebaseDatabase.getInstance().reference
        val resultId = databaseRef.child("users").child(uid).child("results").push().key ?: return

        var imageBase64: String? = null
        var fileToDelete: java.io.File? = null

        if (imageUri != null) {
            try {
                Log.d("ResultActivity", "Trying to encode image from URI: $imageUri")

                // Open input stream safely
                contentResolver.openInputStream(imageUri)?.use { inputStream ->

                    // Decode bitmap
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap == null) {
                        Log.e("ResultActivity", "Bitmap decode failed from input stream")
                        throw Exception("Bitmap decode failed")
                    }

                    // Compress bitmap to PNG format
                    val baos = ByteArrayOutputStream()
                    val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    if (!compressed) {
                        Log.e("ResultActivity", "Bitmap compression failed")
                        throw Exception("Bitmap compression failed")
                    }

                    val bytes = baos.toByteArray()
                    imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    Log.d("ResultActivity", "Image encoded successfully, size: ${bytes.size} bytes")

                    // Check if this is a temp file in cache to delete later
                    if (imageUri.scheme == "file") {
                        val file = java.io.File(imageUri.path!!)
                        if (file.exists() && file.parent?.contains(cacheDir.absolutePath) == true) {
                            fileToDelete = file
                        }
                    }
                } ?: run {
                    Log.e("ResultActivity", "InputStream is null for URI: $imageUri")
                    throw Exception("InputStream null")
                }
            } catch (e: Exception) {
                Log.e("ResultActivity", "Failed to encode image: ${e.message}", e)
            }
        }

        if (imageUri != null && imageBase64 == null) {
            Toast.makeText(this, "Failed to encode image", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        val resultData = mapOf(
            "id" to resultId,
            "predictionClass" to predictionClass,
            "condition" to condition,
            "message" to message,
            "imageBase64" to imageBase64,
            "timestamp" to timestamp
        )

        databaseRef.child("users").child(uid).child("results").child(resultId)
            .setValue(resultData)
            .addOnSuccessListener {
                firebaseDocId = resultId
                fileToDelete?.delete()
                Log.d("ResultActivity", "Temp file deleted: ${fileToDelete?.absolutePath}")
                onComplete()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save result", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }
}