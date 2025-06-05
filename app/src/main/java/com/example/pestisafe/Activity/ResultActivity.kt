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

    /* ───────────────────────────────────────────── */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        /* Toolbar */
        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { finish() }

        /* View refs */
        val resultImage     = findViewById<ImageView>(R.id.resultImageView)
        val resultClassTxt  = findViewById<TextView>(R.id.resultClassText)
        val resultCondTxt   = findViewById<TextView>(R.id.resultConditionText)
        val resultRangeTxt  = findViewById<TextView>(R.id.resultResidueRangeText)
        val resultPestTxt   = findViewById<TextView>(R.id.resultPesticideText)
        val resultMsgTxt    = findViewById<TextView>(R.id.resultMessageText)
        val timestampTxt    = findViewById<TextView>(R.id.resultTimestampText)
        val saveButton      = findViewById<Button>(R.id.saveButton)
        val deleteButton    = findViewById<Button>(R.id.deleteButton)

        /* ── Pull extras from intent ───────────────────── */
        val predictionClass = intent.getStringExtra("class")      ?: "N/A"
        val condition       = intent.getStringExtra("condition")  ?: "N/A"
        val residueRange    = intent.getStringExtra("residueRange") ?: "N/A"
        val pesticideName   = intent.getStringExtra("pesticide")  ?: "N/A"
        val message         = intent.getStringExtra("message")    ?: "—"
        val imageUri        = intent.getStringExtra("imageUri")?.let { Uri.parse(it) }

        /* ── Timestamp label ───────────────────────────── */
        val timestamp   = System.currentTimeMillis()
        val formatted   = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            .format(Date(timestamp))
        timestampTxt.text = "Scanned on: $formatted"

        /* ── Bind to UI ────────────────────────────────── */
        resultClassTxt.text = "Classification: $predictionClass"
        resultCondTxt.text  = "Condition: $condition"
        resultRangeTxt.text = "Residue Range: $residueRange"
        resultPestTxt.text  = "Pesticide: $pesticideName"
        resultMsgTxt.text   = message
        imageUri?.let(resultImage::setImageURI)

        /* ── Save / Delete buttons ─────────────────────── */
        saveButton.setOnClickListener {
            if (!isSaved) {
                confirmSave {
                    showLoading(true)
                    saveResultToFirebase(
                        predictionClass, condition, residueRange, pesticideName,
                        message, imageUri, timestamp
                    ) {
                        showLoading(false)
                        isSaved = true
                        showPostSaveOptions()
                    }
                }
            } else Toast.makeText(this, "Already saved.", Toast.LENGTH_SHORT).show()
        }

        deleteButton.setOnClickListener {
            confirmDelete {
                if (isSaved && firebaseDocId != null) {
                    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                        FirebaseDatabase.getInstance().reference
                            .child("users").child(uid)
                            .child("results").child(firebaseDocId!!)
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

    /* ───────────────── helpers ─────────────────────── */

    private fun showLoading(show: Boolean) {
        if (show) {
            progressDialog = ProgressDialog(this).apply {
                setMessage("Saving result, please wait…")
                setCancelable(false)
                show()
            }
        } else progressDialog?.dismiss()
    }

    private fun confirmSave(onYes: () -> Unit) =
        AlertDialog.Builder(this)
            .setTitle("Save Result")
            .setMessage("Do you want to save this?")
            .setPositiveButton("Yes") { d, _ -> onYes(); d.dismiss() }
            .setNegativeButton("No") { d, _ -> d.dismiss() }
            .show()

    private fun confirmDelete(onYes: () -> Unit) =
        AlertDialog.Builder(this)
            .setTitle("Delete Result")
            .setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Yes") { d, _ -> onYes(); d.dismiss() }
            .setNegativeButton("No") { d, _ -> d.dismiss() }
            .show()

    private fun showPostSaveOptions() =
        AlertDialog.Builder(this)
            .setTitle("Result Saved")
            .setMessage("The result was saved successfully.")
            .setPositiveButton("Home") { d, _ ->
                d.dismiss()
                val i = Intent(this, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i); finish()
            }
            .setNegativeButton("Detection") { d, _ ->
                d.dismiss()
                val i = Intent(this, DetectionActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i); finish()
            }
            .show()

    /* ── Firebase persistence ───────────────────────── */

    private fun saveResultToFirebase(
        predictionClass: String, condition: String,
        residueRange: String, pesticide: String,
        message: String, imageUri: Uri?, timestamp: Long,
        onDone: () -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            showLoading(false); return
        }

        val uid         = user.uid
        val dbRef       = FirebaseDatabase.getInstance().reference
        val resultId    = dbRef.child("users").child(uid).child("results").push().key ?: return

        /* Optional: encode image for history */
        var imageBase64: String? = null
        var tmpFileToDelete: java.io.File? = null

        if (imageUri != null) try {
            contentResolver.openInputStream(imageUri)?.use { stream ->
                val bmp  = BitmapFactory.decodeStream(stream)
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
            if (imageUri.scheme == "file") {
                val f = java.io.File(imageUri.path!!)
                if (f.exists() && f.parent?.contains(cacheDir.absolutePath) == true) tmpFileToDelete = f
            }
        } catch (e: Exception) {
            Log.e("ResultActivity", "Image encode failed: ${e.message}")
        }

        /* Build payload */
        val data = mapOf(
            "id"             to resultId,
            "predictionClass" to predictionClass,
            "condition"      to condition,
            "residueRange"   to residueRange,
            "pesticide"      to pesticide,
            "message"        to message,
            "imageBase64"    to imageBase64,
            "timestamp"      to timestamp
        )

        dbRef.child("users").child(uid).child("results").child(resultId)
            .setValue(data)
            .addOnSuccessListener {
                firebaseDocId = resultId
                tmpFileToDelete?.delete()
                onDone()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save result", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }
}