package com.example.pestisafe.Activity

import android.Manifest
import android.app.AlertDialog
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.pestisafe.R
import com.example.pestisafe.ResultHistory
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var result: ResultHistory

    private lateinit var resultImageView: ImageView
    private lateinit var resultClassText: TextView
    private lateinit var resultConditionText: TextView
    private lateinit var resultMessageText: TextView
    private lateinit var resultTimestampText: TextView
    private lateinit var btnExportPdf: Button
    private lateinit var btnDelete: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                exportResultAsPdf()
            } else {
                Toast.makeText(this, "Storage permission is required to save PDF", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        resultImageView = findViewById(R.id.resultImageView)
        resultClassText = findViewById(R.id.resultClassText)
        resultConditionText = findViewById(R.id.resultConditionText)
        resultMessageText = findViewById(R.id.resultMessageText)
        resultTimestampText = findViewById(R.id.resultTimestampText)
        btnExportPdf = findViewById(R.id.buttonExportPdf)
        btnDelete = findViewById(R.id.buttonDelete)

        result = intent.getSerializableExtra("result") as ResultHistory

        displayResultDetails()

        btnExportPdf.setOnClickListener {
            checkPermissionsAndExportPdf()
        }

        btnDelete.setOnClickListener {
            confirmDelete()
        }
    }

    private fun displayResultDetails() {
        if (!result.imageBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(result.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                resultImageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                resultImageView.setImageResource(android.R.color.darker_gray)
            }
        } else {
            resultImageView.setImageResource(android.R.color.darker_gray)
        }

        resultClassText.text = "Classification: ${result.predictionClass}"
        resultConditionText.text = "Condition: ${result.condition}"
        resultMessageText.text = result.message

        val formattedDate = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(result.timestamp))
        resultTimestampText.text = "Scanned on: $formattedDate"
    }

    private fun checkPermissionsAndExportPdf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            exportResultAsPdf()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    exportResultAsPdf()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    Toast.makeText(this, "Storage permission needed to save PDF", Toast.LENGTH_SHORT).show()
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            exportResultAsPdf()
        }
    }

    private fun exportResultAsPdf() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            color = Color.BLACK
        }

        val pageWidth = 595  // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 72      // 1 inch margin (72 points = 1 inch)
        val usableWidth = pageWidth - 2 * margin

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var yPosition = margin.toFloat()

        fun startNewPage(): Pair<PdfDocument.Page, Canvas> {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val page = pdfDocument.startPage(pageInfo)
            return Pair(page, page.canvas)
        }

        var (page, canvas) = startNewPage()

        fun checkForNewPage(height: Float): Boolean {
            if (yPosition + height > pageHeight - margin) {
                pdfDocument.finishPage(page)
                val newPage = startNewPage()
                page = newPage.first
                canvas = newPage.second
                yPosition = margin.toFloat()
                return true
            }
            return false
        }

        fun drawLabel(label: String) {
            paint.isFakeBoldText = true
            canvas.drawText(label, margin.toFloat(), yPosition, paint)
            paint.isFakeBoldText = false
            yPosition += 20f
        }

        fun drawWrappedText(text: String) {
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, usableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
            } else {
                StaticLayout(text, paint, usableWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
            }

            checkForNewPage(layout.height.toFloat())
            canvas.save()
            canvas.translate(margin.toFloat(), yPosition)
            layout.draw(canvas)
            canvas.restore()
            yPosition += layout.height + 20f
        }

        // Draw Title: "Detection Result"
        paint.textSize = 24f
        paint.isFakeBoldText = true
        checkForNewPage(30f)
        canvas.drawText("Detection Result", margin.toFloat(), yPosition, paint)
        paint.isFakeBoldText = false
        paint.textSize = 16f
        yPosition += 40f

        // Draw image (150x150)
        if (!result.imageBase64.isNullOrEmpty()) {
            try {
                val imageBytes = Base64.decode(result.imageBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                val targetWidth = 150
                val targetHeight = 150

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

                if (checkForNewPage(targetHeight + 20f)) {
                    // Page changed, yPosition reset
                }
                canvas.drawBitmap(scaledBitmap, margin.toFloat(), yPosition, null)
                yPosition += targetHeight + 20f
            } catch (e: Exception) {
                drawWrappedText("Failed to load image.")
            }
        } else {
            drawWrappedText("No image available.")
        }

        // Draw date below image
        drawLabel("Date:")
        val formattedDate = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(result.timestamp))
        drawWrappedText(formattedDate)

        // Draw detection details
        drawLabel("Classification:")
        drawWrappedText(result.predictionClass)

        drawLabel("Condition:")
        drawWrappedText(result.condition)

        drawLabel("Message:")
        drawWrappedText(result.message)

        pdfDocument.finishPage(page)

        val sanitizedClass = result.predictionClass.replace("\\W+".toRegex(), "_")
        val fileName = "PestiSafe_${sanitizedClass}_${System.currentTimeMillis()}.pdf"
        val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsPath, fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            Toast.makeText(this, "PDF saved to Downloads/$fileName", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdfDocument.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun drawMultilineText(text: String, canvas: Canvas, x: Float, y: Float, paint: TextPaint, maxWidth: Int) {
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            StaticLayout(text, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Result")
            .setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Yes") { dialog, _ ->
                deleteResult()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteResult() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val ref = FirebaseDatabase.getInstance().reference
            .child("users")
            .child(uid)
            .child("results")
            .child(result.id)

        ref.removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
            }
    }
}