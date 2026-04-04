package com.scf.nyxguard.profile

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityGuardianBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.enqueue

class GuardianActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuardianBinding
    private val guardians = mutableListOf<Guardian>()
    private val adapter = GuardianAdapter { guardian -> deleteGuardian(guardian) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuardianBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.guardianRecycler.layoutManager = LinearLayoutManager(this)
        binding.guardianRecycler.adapter = adapter
        binding.fabAdd.setOnClickListener { showAddDialog() }

        loadGuardians()
    }

    private fun loadGuardians() {
        ApiClient.service.getGuardians().enqueue(
            onSuccess = { response ->
                guardians.clear()
                response.forEach { guardian ->
                    guardians.add(Guardian(
                        id = guardian.id,
                        name = guardian.nickname,
                        phone = guardian.phone,
                        relationship = guardian.relationship,
                    ))
                }
                getSharedPreferences("guardians", MODE_PRIVATE)
                    .edit()
                    .putInt("count", guardians.size)
                    .apply()
                adapter.submitList(guardians.toList())
                updateEmptyState()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                updateEmptyState()
            }
        )
    }

    private fun showAddDialog() {
        if (guardians.size >= 5) {
            Toast.makeText(this, getString(R.string.guardian_limit_reached), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_guardian, null)
        val inputName = dialogView.findViewById<TextInputEditText>(R.id.input_name)
        val inputPhone = dialogView.findViewById<TextInputEditText>(R.id.input_phone)
        val inputRelationship = dialogView.findViewById<AutoCompleteTextView>(R.id.input_relationship)

        inputRelationship.setAdapter(ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            resources.getStringArray(R.array.guardian_relationship_options)
        ))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guardian_add_title)
            .setView(dialogView)
            .setPositiveButton(R.string.guardian_add_action) { _, _ ->
                val name = inputName.text?.toString()?.trim() ?: ""
                val phone = inputPhone.text?.toString()?.trim() ?: ""
                val defaultRelationship = getString(R.string.guardian_default_relationship)
                val rel = inputRelationship.text?.toString()?.trim()?.ifEmpty { defaultRelationship }
                    ?: defaultRelationship

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, getString(R.string.guardian_fill_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                ApiClient.service.addGuardian(
                    mapOf("nickname" to name, "phone" to phone, "relationship" to rel)
                ).enqueue(
                    onSuccess = { _ -> loadGuardians() },
                    onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun deleteGuardian(guardian: Guardian) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guardian_delete_title)
            .setMessage(getString(R.string.guardian_delete_message, guardian.name))
            .setPositiveButton(R.string.guardian_delete_action) { _, _ ->
                ApiClient.service.deleteGuardian(guardian.id).enqueue(
                    onSuccess = { _ -> loadGuardians() },
                    onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateEmptyState() {
        binding.emptyText.visibility = if (guardians.isEmpty()) View.VISIBLE else View.GONE
        binding.guardianRecycler.visibility = if (guardians.isEmpty()) View.GONE else View.VISIBLE
    }
}
