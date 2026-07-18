package com.cactus.bitacora.biometric.technical

import com.cactus.bitacora.biometric.local.LocalFaceTemplateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceEnrollmentReviewPolicyTest {
    private val samples = LocalFaceTemplateRepository.REQUIRED_ENROLLMENT_CAPTURES

    @Test
    fun savesOnlyAfterExplicitSaveActionWithEnoughSamples() {
        assertTrue(shouldPersistEnrollment(samples, FaceEnrollmentReviewAction.SAVE))
        assertFalse(shouldPersistEnrollment(samples - 1, FaceEnrollmentReviewAction.SAVE))
    }

    @Test
    fun cancelDoesNotCreateTemplate() {
        assertFalse(shouldPersistEnrollment(samples, FaceEnrollmentReviewAction.CANCEL))
    }

    @Test
    fun repeatDoesNotCreateTemplate() {
        assertFalse(shouldPersistEnrollment(samples, FaceEnrollmentReviewAction.REPEAT))
    }
}
