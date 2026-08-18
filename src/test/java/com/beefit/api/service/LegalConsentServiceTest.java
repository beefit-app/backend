package com.beefit.api.service;

import com.beefit.api.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegalConsentServiceTest {
    private final LegalConsentService service = new LegalConsentService("terms-v2", "privacy-v3");

    @Test
    void recordsCurrentVersionsAndTimestamp() {
        User user = new User();
        service.recordConsent(user, "terms-v2", "privacy-v3");
        assertEquals("terms-v2", user.getAcceptedTermsVersion());
        assertEquals("privacy-v3", user.getAcceptedPrivacyVersion());
        assertNotNull(user.getTermsAcceptedAt());
        assertEquals(user.getTermsAcceptedAt(), user.getPrivacyAcceptedAt());
    }

    @Test
    void rejectsStaleDocumentVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordConsent(new User(), "terms-v1", "privacy-v3"));
    }
}
