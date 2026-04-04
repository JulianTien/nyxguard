package com.scf.nyxguard.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.scf.nyxguard.LocaleManager
import com.scf.nyxguard.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scf.nyxguard.common.ThemePreference
import com.scf.nyxguard.common.ThemePreferenceStore
import com.scf.nyxguard.databinding.DialogEditProfileBinding
import com.scf.nyxguard.databinding.FragmentProfileBinding
import com.scf.nyxguard.fakecall.FakeCallSettingActivity
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.ProfileSummaryDto
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.network.UpdateProfileRequest
import com.scf.nyxguard.network.UserDto
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.profile.GuardianActivity
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class ProfileFragment : Fragment() {

    private companion object {
        const val PROFILE_CACHE_PREFS = "profile_cache"
        const val KEY_EMERGENCY_PHONE = "emergency_phone"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentProfile: UserDto? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.menuGuardians.setOnClickListener {
            startActivity(Intent(requireContext(), GuardianActivity::class.java))
        }
        binding.menuFakeCall.setOnClickListener {
            startActivity(Intent(requireContext(), FakeCallSettingActivity::class.java))
        }
        binding.menuTheme.setOnClickListener { showThemeDialog() }
        binding.btnEditProfile.setOnClickListener { showEditProfileDialog() }
        binding.menuLanguage.setOnClickListener { showLanguageDialog() }
        binding.menuAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_about_title)
                .setMessage(R.string.profile_about_message)
                .setPositiveButton(R.string.profile_about_positive, null)
                .show()
        }
        binding.menuLogout.setOnClickListener { showLogoutDialog() }

        // 显示本地缓存的昵称
        binding.userName.text = TokenManager.getNickname(requireContext())
            .ifEmpty { getString(R.string.profile_default_name) }
        binding.userPhone.text = getString(R.string.profile_phone_not_set)
        binding.userEmergencyPhone.text = getString(R.string.profile_emergency_phone_not_set)
        binding.userAccountMeta.text = getString(R.string.profile_account_meta_default)
        binding.userAvatar.setImageResource(R.drawable.ic_nav_profile)
        updateLanguageSummary()
        updateThemeSummary()
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        loadProfileSummary()
        loadGuardianCount()
        updateLanguageSummary()
        updateThemeSummary()
    }

    private fun loadProfile() {
        ApiClient.service.getProfile().enqueue(
            onSuccess = { profile ->
                if (_binding == null) return@enqueue
                currentProfile = profile
                renderProfile(profile)
            },
            onError = {
                if (_binding == null) return@enqueue
                renderLocalFallback()
            }
        )
    }

    private fun loadGuardianCount() {
        ApiClient.service.getGuardians().enqueue(
            onSuccess = { guardians ->
                if (_binding == null) return@enqueue
                requireContext().getSharedPreferences("guardians", 0)
                    .edit()
                    .putInt("count", guardians.size)
                    .apply()
                binding.guardianCount.text = formatGuardianCount(guardians.size)
            },
            onError = {
                if (_binding == null) return@enqueue
                val prefs = requireContext().getSharedPreferences("guardians", 0)
                val count = prefs.getInt("count", 0)
                binding.guardianCount.text = formatGuardianCount(count)
            }
        )
    }

    private fun loadProfileSummary() {
        ApiClient.service.getProfileSummary().enqueue(
            onSuccess = { summary ->
                if (_binding == null) return@enqueue
                renderProfileSummary(summary)
            },
            onError = {
                if (_binding == null) return@enqueue
                binding.routesValue.setText(R.string.edge_profile_stats_placeholder_routes)
                binding.hoursValue.setText(R.string.edge_profile_stats_placeholder_hours)
            }
        )
    }

    private fun showLanguageDialog() {
        val languageTags = arrayOf(
            LocaleManager.LANGUAGE_ENGLISH,
            LocaleManager.LANGUAGE_SIMPLIFIED_CHINESE
        )
        val labels = arrayOf(
            getString(R.string.language_option_english),
            getString(R.string.language_option_chinese)
        )
        val currentTag = LocaleManager.currentLanguageTag(requireContext())
        val checkedIndex = languageTags.indexOf(currentTag).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language_dialog_title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                LocaleManager.setLanguage(requireContext(), languageTags[which])
                updateLanguageSummary()
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateLanguageSummary() {
        binding.languageValue.text = when (LocaleManager.currentLanguageTag(requireContext())) {
            LocaleManager.LANGUAGE_SIMPLIFIED_CHINESE -> getString(R.string.language_option_chinese)
            else -> getString(R.string.language_option_english)
        }
    }

    private fun updateThemeSummary() {
        binding.themeValue.text = when (ThemePreferenceStore.getThemePreference(requireContext())) {
            ThemePreference.SYSTEM -> getString(R.string.edge_theme_system)
            ThemePreference.LIGHT -> getString(R.string.edge_theme_light)
            ThemePreference.DARK -> getString(R.string.edge_theme_dark)
        }
    }

    private fun showThemeDialog() {
        val options = arrayOf(
            getString(R.string.edge_theme_system),
            getString(R.string.edge_theme_light),
            getString(R.string.edge_theme_dark),
        )
        val preferences = arrayOf(
            ThemePreference.SYSTEM,
            ThemePreference.LIGHT,
            ThemePreference.DARK,
        )
        val current = ThemePreferenceStore.getThemePreference(requireContext())
        val checked = preferences.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edge_profile_theme_dialog_title)
            .setSingleChoiceItems(options, checked) { dialog, which ->
                ThemePreferenceStore.setThemePreference(requireContext(), preferences[which])
                updateThemeSummary()
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun renderProfile(profile: UserDto) {
        val emergencyPhone = profile.emergency_phone?.takeIf { it.isNotBlank() }
            ?: profileCache().getString(KEY_EMERGENCY_PHONE, null)

        binding.userName.text =
            profile.nickname.ifEmpty { getString(R.string.profile_default_name) }
        binding.userPhone.text =
            profile.phone ?: getString(R.string.profile_phone_not_set)
        binding.userEmergencyPhone.text =
            emergencyPhone ?: getString(R.string.profile_emergency_phone_not_set)
        binding.userAccountMeta.text = buildAccountMeta(profile.created_at, profile.updated_at)
        binding.profileBadge.text = getString(R.string.edge_profile_badge, profileStreakDays(profile.created_at))

        val avatar = profile.avatar_url?.takeIf { it.isNotBlank() }
        if (avatar != null) {
            Glide.with(this)
                .load(avatar)
                .placeholder(R.drawable.ic_nav_profile)
                .error(R.drawable.ic_nav_profile)
                .circleCrop()
                .into(binding.userAvatar)
        } else {
            binding.userAvatar.setImageResource(R.drawable.ic_nav_profile)
        }
    }

    private fun renderProfileSummary(summary: ProfileSummaryDto) {
        binding.profileBadge.text = getString(R.string.edge_profile_badge, summary.badge_days)
        binding.guardianCount.text = formatGuardianCount(summary.guardian_count)
        binding.routesValue.text = getString(R.string.profile_summary_routes_count, summary.frequent_routes_count)
        binding.hoursValue.text = getString(
            R.string.profile_summary_hours_count,
            summary.guard_minutes_total / 60,
            summary.guard_minutes_total % 60
        )
        if (binding.userName.text.isNullOrBlank()) {
            binding.userName.text = summary.nickname
        }
    }

    private fun renderLocalFallback() {
        val nickname = TokenManager.getNickname(requireContext())
            .ifEmpty { getString(R.string.profile_default_name) }
        binding.userName.text = nickname
        binding.userPhone.text = getString(R.string.profile_phone_not_set)
        binding.userEmergencyPhone.text =
            profileCache().getString(KEY_EMERGENCY_PHONE, null)
                ?: getString(R.string.profile_emergency_phone_not_set)
        binding.userAccountMeta.text = getString(R.string.profile_account_meta_default)
        binding.userAvatar.setImageResource(R.drawable.ic_nav_profile)
        binding.profileBadge.setText(R.string.edge_profile_badge_default)
    }

    private fun buildAccountMeta(createdAt: String?, updatedAt: String?): String {
        val createdLabel = createdAt?.let { formatDateTime(it) } ?: "--"
        val updatedLabel = updatedAt?.let { formatDateTime(it) } ?: "--"
        return getString(R.string.profile_account_meta, createdLabel, updatedLabel)
    }

    private fun formatDateTime(raw: String): String {
        return runCatching {
            val parsed = OffsetDateTime.parse(raw)
            parsed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()))
        }.getOrElse { raw }
    }

    private fun profileStreakDays(createdAt: String?): Int {
        if (createdAt.isNullOrBlank()) return 1
        return runCatching {
            val created = OffsetDateTime.parse(createdAt)
            ChronoUnit.DAYS.between(created.toLocalDate(), OffsetDateTime.now().toLocalDate())
                .toInt()
                .coerceAtLeast(1)
        }.getOrDefault(1)
    }

    private fun showEditProfileDialog() {
        val profile = currentProfile
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialogBinding.inputNickname.setText(
            profile?.nickname ?: TokenManager.getNickname(requireContext())
        )
        dialogBinding.inputEmergency.setText(profile?.emergency_phone.orEmpty())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.profile_edit_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nickname = dialogBinding.inputNickname.text?.toString()?.trim().orEmpty()
                val emergencyPhone = dialogBinding.inputEmergency.text?.toString()?.trim().orEmpty()

                if (nickname.isBlank()) {
                    dialogBinding.layoutNickname.error = getString(R.string.profile_edit_nickname_required)
                    return@setOnClickListener
                }

                dialogBinding.layoutNickname.error = null
                dialogBinding.layoutEmergency.error = null
                saveProfile(nickname, emergencyPhone, dialog)
            }
        }

        dialog.show()
    }

    private fun saveProfile(nickname: String, emergencyPhone: String, dialog: androidx.appcompat.app.AlertDialog) {
        val payload = UpdateProfileRequest(
            nickname = nickname,
            emergency_phone = emergencyPhone
        )

        ApiClient.service.updateProfile(payload).enqueue(
            onSuccess = { updated ->
                if (_binding == null) return@enqueue
                currentProfile = updated
                renderProfile(updated)
                profileCache().edit().putString(KEY_EMERGENCY_PHONE, emergencyPhone).apply()
                val token = TokenManager.getToken(requireContext())
                val userId = TokenManager.getUserId(requireContext())
                if (!token.isNullOrBlank() && userId > 0) {
                    TokenManager.saveLogin(requireContext(), token, userId, updated.nickname)
                }
                dialog.dismiss()
                Toast.makeText(requireContext(), getString(R.string.profile_edit_success), Toast.LENGTH_SHORT).show()
            },
            onError = { msg ->
                if (_binding == null) return@enqueue
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun formatGuardianCount(count: Int): String {
        return if (count > 0) {
            getString(R.string.profile_guardian_count, count)
        } else {
            getString(R.string.profile_not_set)
        }
    }

    private fun profileCache() =
        requireContext().getSharedPreferences(PROFILE_CACHE_PREFS, 0)

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_logout_title)
            .setMessage(R.string.profile_logout_message)
            .setPositiveButton(R.string.profile_logout_confirm) { _, _ ->
                // 清除登录状态
                TokenManager.logout(requireContext())
                // 跳转到登录页
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
