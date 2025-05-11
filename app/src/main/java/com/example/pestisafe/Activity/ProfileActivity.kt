package com.example.pestisafe.Activity

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pestisafe.R
import com.example.pestisafe.databinding.ActivityProfileBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference

        // Load the user data
        val userId = mAuth.currentUser?.uid
        if (userId != null) {
            // Fetch the user data from Firebase
            databaseReference.child("users").child(userId).get().addOnSuccessListener {
                if (it.exists()) {
                    val user = it.value as Map<String, Any>
                    binding.fullname.setText(user["firstname"].toString() + " " + user["lastname"].toString())
                    binding.emailaddp.setText(user["emailaddress"].toString())
                    binding.mobileno.setText(user["mobileno"].toString())
                    binding.country.setText(user["country"].toString())
                }
            }
        }

        // Update user data
        binding.updateBtn.setOnClickListener {
            val fullName = binding.fullname.text.toString().split(" ")
            val firstName = fullName[0]
            val lastName = fullName.getOrElse(1) { "" }
            val email = binding.emailaddp.text.toString()
            val mobileNo = binding.mobileno.text.toString()
            val country = binding.country.text.toString()

            if (firstName.isNotEmpty() && lastName.isNotEmpty() && email.isNotEmpty()) {
                val userId = mAuth.currentUser?.uid
                if (userId != null) {
                    val userMap = mapOf(
                        "firstname" to firstName,
                        "lastname" to lastName,
                        "emailaddress" to email,
                        "mobileno" to mobileNo,
                        "country" to country
                    )

                    // Save updated data to Firebase
                    databaseReference.child("users").child(userId).updateChildren(userMap)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                Toast.makeText(applicationContext, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(applicationContext, "Update failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
            } else {
                Toast.makeText(applicationContext, "Please complete all fields", Toast.LENGTH_SHORT).show()
            }
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

    }
}
