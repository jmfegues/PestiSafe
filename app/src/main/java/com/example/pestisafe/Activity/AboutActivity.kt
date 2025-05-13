package com.example.pestisafe.Activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pestisafe.R
import com.example.pestisafe.databinding.ActivityAboutBinding
import com.google.android.material.appbar.MaterialToolbar


class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}