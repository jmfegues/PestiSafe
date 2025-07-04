package com.example.pestisafe.Activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pestisafe.databinding.ActivitySignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var databaseReference: DatabaseReference
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference

        binding.signupBtn.setOnClickListener {
            val fname = binding.firstname.text.toString()
            val lname = binding.lastname.text.toString()
            val email = binding.emailadd.text.toString()
            val password = binding.password.text.toString()
            val confirmPassword = binding.confpassword.text.toString()

            if (fname.isNotEmpty() && lname.isNotEmpty() &&
                email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()) {

                if (password.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (password != confirmPassword) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = mAuth.currentUser?.uid
                            if (uid != null) {
                                val userMap = mapOf(
                                    "firstname" to fname,
                                    "lastname" to lname,
                                    "emailaddress" to email
                                )
                                databaseReference.child("users").child(uid).setValue(userMap)
                                    .addOnCompleteListener {
                                        Toast.makeText(applicationContext, "User registered successfully", Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(applicationContext, LogInActivity::class.java))
                                        finish()
                                    }
                            }
                        } else {
                            Toast.makeText(applicationContext, "Registration Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

            } else {
                Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.alreadyhaveanacc.setOnClickListener {
            val intent = Intent(this@SignUpActivity, LogInActivity::class.java)
            startActivity(intent)
        }
    }
}