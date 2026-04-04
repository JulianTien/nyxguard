package com.scf.nyxguard

import com.scf.nyxguard.util.PasswordStrength
import com.scf.nyxguard.util.ValidationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun validationUtils_accepts_expectedPhoneFormats() {
        assertTrue(ValidationUtils.isValidPhone("13812345678"))
        assertFalse(ValidationUtils.isValidPhone("12812345678"))
        assertFalse(ValidationUtils.isValidPhone("1381234"))
    }

    @Test
    fun validationUtils_enforcesPasswordRules() {
        assertTrue(ValidationUtils.isValidPassword("abc123"))
        assertFalse(ValidationUtils.isValidPassword("abcdef"))
        assertFalse(ValidationUtils.isValidPassword("123456"))
    }

    @Test
    fun validationUtils_reportsPasswordStrength() {
        assertEquals(PasswordStrength.WEAK, ValidationUtils.getPasswordStrength("abc123"))
        assertEquals(PasswordStrength.STRONG, ValidationUtils.getPasswordStrength("Abc123!xyz"))
    }

    @Test
    fun validationUtils_checksNicknameLength() {
        assertTrue(ValidationUtils.isValidNickname("Nyx"))
        assertFalse(ValidationUtils.isValidNickname("A"))
    }
}
