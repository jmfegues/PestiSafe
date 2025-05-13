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

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    // Handle menu item clicks
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_about -> {
                // Go to AboutActivity
                val aboutIntent = Intent(this, AboutActivity::class.java)
                startActivity(aboutIntent)
                true
            }
            R.id.nav_profile -> {
                // Go to ProfileActivity
                val profileIntent = Intent(this, ProfileActivity::class.java)
                startActivity(profileIntent)
                true
            }
            R.id.nav_logout -> {
                // Show log out confirmation dialog
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
                // Perform logout action
                mAuth.signOut()
                val intent = Intent(this, LogInActivity::class.java)
                startActivity(intent)
                finish() // Close the MainActivity after logout
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog if "No" is clicked
            }
            .create()

        dialog.show()
    }
}
