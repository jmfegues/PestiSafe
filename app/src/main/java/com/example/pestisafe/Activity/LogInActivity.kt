package com.example.pestisafe.Activity

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pestisafe.MainActivity
import com.example.pestisafe.databinding.ActivityLogInBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class LogInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogInBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseReference = Firebase.database.reference
        mAuth = Firebase.auth

        // Check if the user is already logged in
        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            // If the user is authenticated, skip login and go directly to MainActivity
            startActivity(Intent(this@LogInActivity, MainActivity::class.java))
            finish() // Finish this activity so the user can't go back to it
        }

        // Handle login button click
        binding.loginBtn.setOnClickListener {
            val email = binding.emailadd.text.toString()
            val password = binding.password.text.toString()

            var valid = true

            if (email.isEmpty()) {
                binding.emailadd.error = "Email is required!"
                valid = false
            }

            if (password.isEmpty()) {
                binding.password.error = "Password is required!"
                valid = false
            }

            if (valid) {
                mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(applicationContext, "Logged In Successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(applicationContext, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(applicationContext, "Log in Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        // Handle "Don't have an account? Sign up" click
        binding.donthaveanaccsignuptxt.setOnClickListener {
            val intent = Intent(this@LogInActivity, SignUpActivity::class.java)
            startActivity(intent)
        }

        // Handle password field action on "Done" or "Enter" key press
        binding.password.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                binding.loginBtn.performClick()
                true
            } else {
                false
            }
        }
    }
}