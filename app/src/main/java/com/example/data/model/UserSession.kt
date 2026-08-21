package com.baoverung.app.data.model

data class UserSession(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val unit: String = "",
    val department: String = "",
    val registrationKey: String = "",
    val expiryDate: String = "",
    val permissions: String = "FULL", // FULL, VIEW_ONLY, SURVEY_ONLY
    val loginTimestamp: Long = 0L,
    val photoUrl: String = "",
    val autoGpx: Boolean = false,
    val canSync: Boolean = true, // Requirement IV: Managed from Web Admin (Activation Key)
    val isLoggedIn: Boolean = false,
    val isOfflineMode: Boolean = false
) {
    fun isComplete(): Boolean {
        if (isOfflineMode) return true
        return email.isNotEmpty() && displayName.isNotEmpty() && phoneNumber.isNotEmpty() && unit.isNotEmpty()
    }
}
