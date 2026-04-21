package com.scf.nyxguard.ui.guardian

import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.DashboardResponseDto
import com.scf.nyxguard.network.GuardianDashboardDto
import com.scf.nyxguard.network.GuardianSosDetailDto
import com.scf.nyxguard.network.GuardianTripDetailDto
import com.scf.nyxguard.network.ProfileSummaryDto
import com.scf.nyxguard.network.enqueue

data class GuardianOverviewUiState(
    val nickname: String,
    val greeting: String,
    val guardianCount: Int,
    val activeTrip: DashboardResponseDto? = null,
    val guardianDashboard: GuardianDashboardDto? = null,
    val profileSummary: ProfileSummaryDto? = null,
)

data class GuardianTripUiState(
    val trip: GuardianTripDetailDto? = null,
    val errorMessage: String? = null,
)

data class GuardianSosUiState(
    val sos: GuardianSosDetailDto? = null,
    val errorMessage: String? = null,
)

object GuardianModeRepository {

    fun loadOverview(
        onSuccess: (GuardianOverviewUiState) -> Unit,
        onError: (String) -> Unit,
    ) {
        ApiClient.service.getGuardianDashboard().enqueue(
            onSuccess = { guardianDashboard ->
                ApiClient.service.getProfileSummary().enqueue(
                    onSuccess = { summary ->
                        onSuccess(
                            GuardianOverviewUiState(
                                nickname = guardianDashboard.guardian_nickname,
                                greeting = guardianDashboard.greeting,
                                guardianCount = guardianDashboard.protected_users.size,
                                activeTrip = null,
                                guardianDashboard = guardianDashboard,
                                profileSummary = summary
                            )
                        )
                    },
                    onError = {
                        onSuccess(
                            GuardianOverviewUiState(
                                nickname = guardianDashboard.guardian_nickname,
                                greeting = guardianDashboard.greeting,
                                guardianCount = guardianDashboard.protected_users.size,
                                activeTrip = null,
                                guardianDashboard = guardianDashboard,
                                profileSummary = null
                            )
                        )
                    }
                )
            },
            onError = {
                ApiClient.service.getDashboard().enqueue(
                    onSuccess = { dashboard ->
                        ApiClient.service.getProfileSummary().enqueue(
                            onSuccess = { summary ->
                                onSuccess(
                                    GuardianOverviewUiState(
                                        nickname = dashboard.nickname,
                                        greeting = dashboard.greeting,
                                        guardianCount = dashboard.guardian_count,
                                        activeTrip = dashboard,
                                        guardianDashboard = null,
                                        profileSummary = summary
                                    )
                                )
                            },
                            onError = {
                                onSuccess(
                                    GuardianOverviewUiState(
                                        nickname = dashboard.nickname,
                                        greeting = dashboard.greeting,
                                        guardianCount = dashboard.guardian_count,
                                        activeTrip = dashboard,
                                        guardianDashboard = null,
                                        profileSummary = null
                                    )
                                )
                            }
                        )
                    },
                    onError = onError
                )
            }
        )
    }

    fun loadCurrentTrip(
        tripId: Int?,
        onSuccess: (GuardianTripUiState) -> Unit,
        onError: (String) -> Unit,
    ) {
        val resolvedTripId = tripId
        if (resolvedTripId != null) {
            ApiClient.service.getGuardianTripDetail(resolvedTripId).enqueue(
                onSuccess = { detail ->
                    onSuccess(GuardianTripUiState(trip = detail))
                },
                onError = { message ->
                    onError(message)
                }
            )
            return
        }

        ApiClient.service.getGuardianDashboard().enqueue(
            onSuccess = { dashboard ->
                val firstTripId = dashboard.protected_users.firstNotNullOfOrNull { it.active_trip?.id }
                if (firstTripId == null) {
                    onSuccess(GuardianTripUiState(trip = null))
                    return@enqueue
                }
                ApiClient.service.getGuardianTripDetail(firstTripId).enqueue(
                    onSuccess = { detail ->
                        onSuccess(GuardianTripUiState(trip = detail))
                    },
                    onError = onError,
                )
            },
            onError = onError,
        )
    }

    fun loadSosDetail(
        sosId: Int?,
        onSuccess: (GuardianSosUiState) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (sosId == null) {
            onSuccess(GuardianSosUiState(sos = null))
            return
        }

        ApiClient.service.getGuardianSosDetail(sosId).enqueue(
            onSuccess = { detail ->
                onSuccess(GuardianSosUiState(sos = detail))
            },
            onError = { message ->
                onError(message)
            }
        )
    }
}
