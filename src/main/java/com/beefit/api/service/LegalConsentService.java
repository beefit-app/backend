package com.beefit.api.service;

import com.beefit.api.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LegalConsentService {
    private final String termsVersion;
    private final String privacyVersion;

    public LegalConsentService(
            @Value("${beefit.legal.terms-version}") String termsVersion,
            @Value("${beefit.legal.privacy-version}") String privacyVersion) {
        this.termsVersion = requireVersion(termsVersion, "Termos de Uso");
        this.privacyVersion = requireVersion(privacyVersion, "Política de Privacidade");
    }

    public String termsVersion() { return termsVersion; }
    public String privacyVersion() { return privacyVersion; }

    public void recordConsent(User user, String acceptedTermsVersion, String acceptedPrivacyVersion) {
        if (!termsVersion.equals(acceptedTermsVersion) || !privacyVersion.equals(acceptedPrivacyVersion)) {
            throw new IllegalArgumentException("Os documentos legais foram atualizados. Revise e aceite as versões vigentes.");
        }
        LocalDateTime acceptedAt = LocalDateTime.now();
        user.setAcceptedTermsVersion(termsVersion);
        user.setTermsAcceptedAt(acceptedAt);
        user.setAcceptedPrivacyVersion(privacyVersion);
        user.setPrivacyAcceptedAt(acceptedAt);
    }

    private String requireVersion(String value, String document) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalStateException("Configure uma versão válida para " + document + ".");
        }
        return value;
    }
}
