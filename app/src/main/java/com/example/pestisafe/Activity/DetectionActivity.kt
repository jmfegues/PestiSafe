package com.example.pestisafe.Activity

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.pestisafe.R
import com.example.pestisafe.databinding.ActivityDetectionBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.yalantis.ucrop.UCrop
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DetectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetectionBinding
    private lateinit var photoURI: Uri
    private var currentPhotoPath: String = ""
    private var lastSavedImageUri: Uri? = null

    private lateinit var progressDialog: ProgressDialog
    private lateinit var buttonDetect: MaterialButton

    private val REQUEST_IMAGE_CAPTURE   = 1
    private val REQUEST_FILE_PICK      = 2
    private val REQUEST_CAMERA_PERMISSION   = 100
    private val REQUEST_STORAGE_PERMISSION  = 200

    /* ─────────────────────────────────────────── */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* Toolbar */
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        /* Bottom nav: choose camera or gallery */
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_upload -> { checkStoragePermissionAndPickFile(); true }
                R.id.nav_camera -> { checkCameraPermissionAndOpenCamera(); true }
                else -> false
            }
        }

        /* Detect button – disabled until image saved */
        buttonDetect = findViewById(R.id.buttonDetect)
        buttonDetect.isEnabled = false
        buttonDetect.visibility = View.GONE
        buttonDetect.setOnClickListener {
            lastSavedImageUri?.let { uri ->
                val file = File(getRealPathFromURI(uri) ?: "")
                if (file.exists()) uploadImage(file)
                else Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "No image to detect.", Toast.LENGTH_SHORT).show()
        }

        /* Tap preview image to open it */
        binding.capturedimage.setOnClickListener {
            lastSavedImageUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, contentResolver.getType(uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        /* Simple blocking progress dialog */
        progressDialog = ProgressDialog(this).apply {
            setTitle("Uploading Image")
            setMessage("Please wait...")
            setCancelable(false)
        }
    }

    /* ─────────────── Permission helpers ─────────────── */

    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else dispatchTakePictureIntent()
    }

    private fun checkStoragePermissionAndPickFile() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, storagePermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(storagePermission), REQUEST_STORAGE_PERMISSION)
        } else openFilePicker()
    }

    /* ─────────────── Camera & gallery ─────────────── */

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile = try { createImageFile() } catch (ex: IOException) { null }
            photoFile?.also {
                photoURI = FileProvider.getUriForFile(
                    this, "${applicationContext.packageName}.fileprovider", it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        } else Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(Intent.createChooser(intent, "Select Image"), REQUEST_FILE_PICK)
    }

    /* ─────────────── UCrop ─────────────── */

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setCompressionQuality(80)
        }
        UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .withAspectRatio(0f, 0f)        // free aspect ratio
            .withMaxResultSize(1080, 1080)
            .start(this)
    }

    /* ─────────────── onActivityResult ─────────────── */

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> startCrop(Uri.fromFile(File(currentPhotoPath)))
                REQUEST_FILE_PICK    -> data?.data?.let { startCrop(it) }
                UCrop.REQUEST_CROP   -> {
                    val resultUri = UCrop.getOutput(data!!)
                    resultUri?.let {
                        binding.capturedimage.setImageURI(it)
                        saveImageToGallery(it)
                    }
                }
            }
        } else if (requestCode == UCrop.RESULT_ERROR) {
            val err = data?.let { UCrop.getError(it) }
            Toast.makeText(this, "Crop failed: ${err?.message}", Toast.LENGTH_LONG).show()
        }
    }

    /* ─────────────── File helpers ─────────────── */

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun saveImageToGallery(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val filename = "PestiSafe_${System.currentTimeMillis()}.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/PestiSafe")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val savedUri = resolver.insert(imageCollection, contentValues)

            savedUri?.let { outUri ->
                resolver.openOutputStream(outUri)?.use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(outUri, contentValues, null, null)
                }
                lastSavedImageUri = outUri
                buttonDetect.visibility = View.VISIBLE
                buttonDetect.isEnabled = true
                Toast.makeText(this, "Image saved. Tap Detect to upload.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            if (cursor.moveToFirst()) return cursor.getString(columnIdx)
        }
        return uri.path
    }

    /* ─────────────── Networking ─────────────── */

    private fun uploadImage(imageFile: File) {
        progressDialog.show()

        val mediaType = when (imageFile.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg".toMediaTypeOrNull()
            "png"         -> "image/png".toMediaTypeOrNull()
            else -> {
                Toast.makeText(this, "Unsupported file type", Toast.LENGTH_LONG).show()
                progressDialog.dismiss()
                return
            }
        }

        val fileBody = imageFile.asRequestBody(mediaType)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", imageFile.name, fileBody)
            .build()

        val request = Request.Builder()
            .url("https://pestisafe-api-539738895234.us-central1.run.app/predict/")
            .post(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressDialog.dismiss()
                    showRetryDialog("Upload failed: ${e.message}", imageFile)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                runOnUiThread {
                    progressDialog.dismiss()

                    if (responseBody.isNullOrEmpty()) {
                        showRetryDialog("No response from server.", imageFile)
                        return@runOnUiThread
                    }

                    try {
                        val json = JSONObject(responseBody)

                        /* Handle invalid strip */
                        if (json.optString("recognized_class") == "invalid") {
                            val err = json.optString("error",
                                "Invalid image. Please upload a clear test strip.")

                            AlertDialog.Builder(this@DetectionActivity)
                                .setTitle("Invalid Image")
                                .setMessage(err)
                                .setPositiveButton("Upload") { dialog, _ ->
                                    dialog.dismiss()
                                    checkStoragePermissionAndPickFile()
                                }
                                .setNegativeButton("Camera") { dialog, _ ->
                                    dialog.dismiss()
                                    checkCameraPermissionAndOpenCamera()
                                }
                                .setCancelable(false)
                                .show()

                            return@runOnUiThread
                        }

                        /* Extract prediction fields */
                        val predictionClass = json.optString("class")
                        val condition       = json.optString("condition")
                        val message         = json.optString("message")
                        val residueRange    = json.optString("residue_range_mg_per_kg", "N/A")
                        val pesticide       = json.optString("pesticide", "N/A")

                        if (predictionClass.isEmpty() || condition.isEmpty()) {
                            showRetryDialog("Incomplete prediction result.", imageFile)
                            return@runOnUiThread
                        }

                        /* Launch ResultActivity */
                        val intent = Intent(this@DetectionActivity, ResultActivity::class.java).apply {
                            putExtra("class",          predictionClass)
                            putExtra("condition",      condition)
                            putExtra("message",        message)
                            putExtra("residueRange",   residueRange)
                            putExtra("pesticide",      pesticide)
                            lastSavedImageUri?.let { putExtra("imageUri", it.toString()) }
                        }
                        startActivity(intent)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        showRetryDialog("Error parsing server response.", imageFile)
                    }
                }
            }
        })
    }

    /* ─────────────── Dialog & permission callbacks ─────────────── */

    private fun showRetryDialog(msg: String, imageFile: File) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage("$msg\nWould you like to retry?")
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss(); uploadImage(imageFile)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_CAMERA_PERMISSION  -> dispatchTakePictureIntent()
                REQUEST_STORAGE_PERMISSION -> openFilePicker()
            }
        } else Snackbar.make(binding.root, "Permission is required.", Snackbar.LENGTH_LONG).show()
    }
}