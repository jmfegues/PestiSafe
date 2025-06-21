package com.example.pestisafe

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.pestisafe.Activity.AboutActivity
import com.example.pestisafe.Activity.DetectionActivity
import com.example.pestisafe.Activity.HistoryActivity
import com.example.pestisafe.Activity.LogInActivity
import com.example.pestisafe.Activity.ProfileActivity
import com.example.pestisafe.databinding.ActivityMainBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()

        binding.cvdetection.setOnClickListener {
            val intent = Intent(this@MainActivity, DetectionActivity::class.java)
            startActivity(intent)
        }

        binding.cvhistory.setOnClickListener {
            val intent = Intent(this@MainActivity, HistoryActivity::class.java)
            startActivity(intent)
        }

        binding.cvregspes.setOnClickListener {
            val urlregpes = "https://fpa.da.gov.ph/wp-content/uploads/2024/05/Updated-List-of-Registered-pesticide-final-as-of-May-2-2024.pdf"
            val intent = Intent(Intent.ACTION_VIEW, urlregpes.toUri())
            startActivity(intent)
        }

        binding.cvbanned.setOnClickListener {
            val urlbanned = "https://fpa.da.gov.ph/resources/reports/list-of-banned-and-restricted-pesticides/"
            val intent = Intent(Intent.ACTION_VIEW, urlbanned.toUri())
            startActivity(intent)
        }

        binding.cvmrl.setOnClickListener {
            val urlmrl = "https://ppssd.buplant.da.gov.ph/storage/app/public/LegalReference/PNS_BAFS%20161_2015%20Banana%20MRL.pdf"
            val intent = Intent(Intent.ACTION_VIEW, urlmrl.toUri())
            startActivity(intent)
        }

        binding.cvphilgapmanual.setOnClickListener {
            val urlphilgap = "https://bucketeer-3eb16243-2c1c-43d2-be4e-1c2b3664d293.s3.amazonaws.com/2023/05/02-PhilGAP-Manual.pdf"
            val intent = Intent(Intent.ACTION_VIEW, urlphilgap.toUri())
            startActivity(intent)
        }

        binding.cvgapcode.setOnClickListener {
            val urlgapcode = "https://ppssd.buplant.da.gov.ph/storage/app/public/LegalReference/PNS_BAFS%2049_2021%20Code%20of%20GAP%20for%20Fruits%20and%20Vegetable%20Farming.pdf"
            val intent = Intent(Intent.ACTION_VIEW, urlgapcode.toUri())
            startActivity(intent)
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_about -> {
                val aboutIntent = Intent(this, AboutActivity::class.java)
                startActivity(aboutIntent)
                true
            }
            R.id.nav_profile -> {
                val profileIntent = Intent(this, ProfileActivity::class.java)
                startActivity(profileIntent)
                true
            }
            R.id.nav_logout -> {
                showLogoutConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                mAuth.signOut()
                val intent = Intent(this, LogInActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }
}
