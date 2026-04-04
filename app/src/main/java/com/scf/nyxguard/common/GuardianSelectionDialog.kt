package com.scf.nyxguard.common

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scf.nyxguard.R
import com.scf.nyxguard.network.GuardianDto

object GuardianSelectionDialog {

    fun show(
        context: Context,
        guardians: List<GuardianDto>,
        selectedGuardianIds: Collection<Int>,
        onConfirmed: (List<Int>) -> Unit
    ) {
        val labels = guardians.map { guardian ->
            buildString {
                append(guardian.nickname)
                if (guardian.relationship.isNotBlank()) {
                    append(" · ")
                    append(guardian.relationship)
                }
                if (guardian.phone.isNotBlank()) {
                    append(" · ")
                    append(guardian.phone)
                }
            }
        }.toTypedArray()

        val checkedItems = BooleanArray(guardians.size) { index ->
            selectedGuardianIds.contains(guardians[index].id)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.guardian_trip_dialog_title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.guardian_trip_dialog_confirm) { _, _ ->
                val selectedIds = guardians.indices
                    .filter { checkedItems[it] }
                    .map { guardians[it].id }
                onConfirmed(selectedIds)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
