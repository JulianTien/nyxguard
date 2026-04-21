package com.scf.nyxguard.model

data class Guardian(
    val id: Int,
    val nickname: String,
    val phone: String,
    val relationship: String,
    val createdAt: String? = null
)

data class AddGuardianRequest(
    val nickname: String,
    val phone: String,
    val relationship: String
)
