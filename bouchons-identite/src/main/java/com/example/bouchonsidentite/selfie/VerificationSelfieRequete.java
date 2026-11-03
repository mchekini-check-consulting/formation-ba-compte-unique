package com.example.bouchonsidentite.selfie;

// Corps de POST /stripe-identity/verifications-selfie.
// selfie : code d'un selfie de test, pas d'image (voir VerificationSelfieService).
public record VerificationSelfieRequete(String dossierId, String selfie) {
}
