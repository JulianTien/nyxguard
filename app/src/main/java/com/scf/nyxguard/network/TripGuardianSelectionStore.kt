package com.scf.nyxguard.network

/**
 * Holds the guardian ids selected for the next trip creation.
 *
 * The trip setup flow sets this before launching the tracking screen so
 * CreateTripRequest can pick it up without touching tracking activities.
 */
object TripGuardianSelectionStore {

    private val selectedGuardianIds = linkedSetOf<Int>()

    @Synchronized
    fun setSelectedGuardianIds(ids: Collection<Int>) {
        selectedGuardianIds.clear()
        selectedGuardianIds.addAll(ids)
    }

    @Synchronized
    fun getSelectedGuardianIds(): List<Int> = selectedGuardianIds.toList()

    @Synchronized
    fun clear() {
        selectedGuardianIds.clear()
    }
}
