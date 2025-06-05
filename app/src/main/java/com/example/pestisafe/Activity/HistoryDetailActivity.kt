package com.example.pestisafe.Activity

import android.Manifest
import android.app.AlertDialog
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.*
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
    private lateinit var resultResidueRangeText: TextView
    private lateinit var resultPesticideText: TextView
    private lateinit var resultMessageText: TextView
    private lateinit var resultTimestampText: TextView
    private lateinit var btnExportPdf: Button
    private lateinit var btnDelete: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) exportResultAsPdf()
            else Toast.makeText(this, "Storage permission is required to save PDF", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { finish() }

        resultImageView       = findViewById(R.id.resultImageView)
        resultClassText       = findViewById(R.id.resultClassText)
        resultConditionText   = findViewById(R.id.resultConditionText)
        resultResidueRangeText= findViewById(R.id.resultResidueRangeText)
        resultPesticideText   = findViewById(R.id.resultPesticideText)
        resultMessageText     = findViewById(R.id.resultMessageText)
        resultTimestampText   = findViewById(R.id.resultTimestampText)
        btnExportPdf          = findViewById(R.id.buttonExportPdf)
        btnDelete             = findViewById(R.id.buttonDelete)

        result = intent.getSerializableExtra("result") as ResultHistory

        displayResultDetails()

        btnExportPdf.setOnClickListener { checkPermissionsAndExportPdf() }
        btnDelete.setOnClickListener   { confirmDelete() }
    }


    private fun displayResultDetails() {
        // image
        if (result.imageBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(result.imageBase64, Base64.DEFAULT)
                val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                resultImageView.setImageBitmap(bmp)
            } catch (_: Exception) {
                resultImageView.setImageResource(android.R.color.darker_gray)
            }
        } else resultImageView.setImageResource(android.R.color.darker_gray)

        // text fields
        resultClassText.text        = "Classification: ${result.predictionClass}"
        resultConditionText.text    = "Condition: ${result.condition}"
        resultResidueRangeText.text = "Residue Range: ${result.residueRange}"
        resultPesticideText.text    = "Pesticide: ${result.pesticide}"
        resultMessageText.text      = result.message
        resultTimestampText.text    = "Scanned on: " +
                SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(result.timestamp))
    }

    private fun checkPermissionsAndExportPdf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            exportResultAsPdf(); return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED -> exportResultAsPdf()
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
                    Toast.makeText(this, "Storage permission needed to save PDF", Toast.LENGTH_SHORT).show()
                        .also { requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                else -> requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else exportResultAsPdf()
    }

    private fun exportResultAsPdf() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            color = Color.BLACK
        }
        val pageW = 595
        val pageH = 842
        val margin = 72
        val usableW = pageW - 2 * margin

        val pdf = PdfDocument()
        var pageNo = 1
        var y = margin.toFloat()
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var canvas = page.canvas

        fun newPage() {
            pdf.finishPage(page)
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page.canvas
            y = margin.toFloat()
        }

        fun ensureSpace(h: Float) {
            if (y + h > pageH - margin) newPage()
        }

        fun drawLbl(lbl: String) {
            paint.isFakeBoldText = true
            canvas.drawText(lbl, margin.toFloat(), y, paint)
            paint.isFakeBoldText = false
            y += paint.textSize
        }

        fun drawText(text: String) {
            val sl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                StaticLayout.Builder.obtain(text, 0, text.length, paint, usableW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            else
                StaticLayout(text, paint, usableW, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)

            ensureSpace(sl.height.toFloat())
            canvas.save()
            canvas.translate(margin.toFloat(), y)
            sl.draw(canvas)
            canvas.restore()

            y += sl.height
            y += 20f
        }

        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Detection Result", margin.toFloat(), y, paint)
        paint.isFakeBoldText = false
        paint.textSize = 16f
        y += 40f

        // Image
        if (result.imageBase64.isNotEmpty()) {
            try {
                val decodedBytes = Base64.decode(result.imageBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                val img = Bitmap.createScaledBitmap(bmp, 150, 150, true)
                ensureSpace(170f)
                canvas.drawBitmap(img, margin.toFloat(), y, null)
                y += 170f
            } catch (_: Exception) {
                drawText("Image unavailable.")
            }
        }

        drawLbl("Date:")
        drawText(SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(result.timestamp)))

        drawLbl("Classification:")
        drawText(result.predictionClass)

        drawLbl("Condition:")
        drawText(result.condition)

        drawLbl("Residue Range:")
        drawText(result.residueRange)

        drawLbl("Pesticide:")
        drawText(result.pesticide)

        drawLbl("Message:")
        drawText(result.message)

        pdf.finishPage(page)

        val fname = if (result.title.isNotBlank()) {
            result.title.replace("\\W+".toRegex(), "_") + ".pdf"
        } else {
            "PestiSafe_${result.predictionClass.replace("\\W+".toRegex(), "_")}_${System.currentTimeMillis()}.pdf"
        }
        val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outFile = File(outDir, fname)

        try {
            pdf.writeTo(FileOutputStream(outFile))
            Toast.makeText(this, "PDF saved to Downloads/$fname", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdf.close()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Result").setMessage("Are you sure you want to delete this?")
            .setPositiveButton("Yes") { d,_ -> deleteResult(); d.dismiss() }
            .setNegativeButton("No")  { d,_ -> d.dismiss() }.show()
    }

    private fun deleteResult() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("results").child(result.id)
            .removeValue()
            .addOnSuccessListener { Toast.makeText(this,"Deleted",Toast.LENGTH_SHORT).show(); finish() }
            .addOnFailureListener { Toast.makeText(this,"Failed to delete",Toast.LENGTH_SHORT).show() }
    }
}