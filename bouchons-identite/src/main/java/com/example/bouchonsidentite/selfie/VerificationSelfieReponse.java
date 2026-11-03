package com.example.bouchonsidentite.selfie;

import java.time.OffsetDateTime;

import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

// score : pourcentage de ressemblance avec la pièce, absent si la comparaison n'a pas eu lieu.
public record VerificationSelfieReponse(
		Long id,
		ResultatSelfie resultat,
		Integer score,
		int tentatives,
		Suite suite,
		StatutDossier statutDossier,
		String message,
		OffsetDateTime verifieLe) {
}
