package com.scf.nyxguard.util

object ValidationUtils {
    
    fun isValidPhone(phone: String): Boolean {
        val phoneRegex = "^1[3-9]\\d{9}$".toRegex()
        return phone.matches(phoneRegex)
    }
    
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.matches(emailRegex)
    }
    
    fun isValidPassword(password: String): Boolean {
        if (password.length !in 6..20) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        return hasLetter && hasDigit
    }
    
    fun isValidNickname(nickname: String): Boolean {
        return nickname.trim().length in 2..20
    }
    
    fun getPasswordStrength(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.WEAK
        
        var score = 0
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        if (password.length >= 10) score++
        
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 3 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
}

enum class PasswordStrength {
    WEAK,
    MEDIUM,
    STRONG
}
