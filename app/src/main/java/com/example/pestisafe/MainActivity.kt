package com.example.pestisafe

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import com.example.pestisafe.Activity.AboutActivity
import com.example.pestisafe.Activity.DetectionActivity
import com.example.pestisafe.databinding.ActivityMainBinding
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        binding.aboutBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, AboutActivity::class.java)
            startActivity(intent)
        }

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

    }

}