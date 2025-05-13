package com.example.pestisafe.Activity

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.pestisafe.R
import com.example.pestisafe.databinding.ActivityDetectionBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DetectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetectionBinding
    private lateinit var photoURI: Uri
    private var currentPhotoPath: String = ""
    private var lastSavedImageUri: Uri? = null

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_FILE_PICK = 2
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_STORAGE_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar setup
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Bottom Navigation setup
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_upload -> {
                    checkStoragePermissionAndPickFile()
                    true
                }
                R.id.nav_camera -> {
                    checkCameraPermissionAndOpenCamera()
                    true
                }
                else -> false
            }
        }

        // Image preview
        binding.capturedimage.setOnClickListener {
            lastSavedImageUri?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, contentResolver.getType(uri))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun checkStoragePermissionAndPickFile() {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, storagePermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(storagePermission), REQUEST_STORAGE_PERMISSION)
        } else {
            openFilePicker()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                ex.printStackTrace()
                null
            }

            photoFile?.also {
                photoURI = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    it
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir!!).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(Intent.createChooser(intent, "Select Image"), REQUEST_FILE_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val imageUri = Uri.fromFile(File(currentPhotoPath))
                    startCrop(imageUri)
                }

                REQUEST_FILE_PICK -> {
                    data?.data?.let { uri -> startCrop(uri) }
                }

                UCrop.REQUEST_CROP -> {
                    val resultUri = UCrop.getOutput(data!!)
                    resultUri?.let {
                        binding.capturedimage.setImageURI(it)
                        saveImageToGallery(it)
                        lastSavedImageUri = it
                    }
                }
            }
        } else if (requestCode == UCrop.RESULT_ERROR) {
            val cropError = data?.let { UCrop.getError(it) }
            cropError?.printStackTrace()
            Toast.makeText(this, "Crop failed: ${cropError?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
            setCompressionQuality(80)
        }

        UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .withAspectRatio(0f, 0f)
            .withMaxResultSize(1080, 1080)
            .start(this)
    }

    private fun saveImageToGallery(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri) ?: return

        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PestiSafe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val galleryUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        galleryUri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }

        inputStream.close()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_CAMERA_PERMISSION -> dispatchTakePictureIntent()
                REQUEST_STORAGE_PERMISSION -> openFilePicker()
            }
        } else {
            Toast.makeText(this, "Permission is required", Toast.LENGTH_SHORT).show()
        }
    }
}
