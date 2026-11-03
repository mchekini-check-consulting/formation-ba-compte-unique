package com.example.bouchonsidentite.document;

import java.time.OffsetDateTime;

import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

// donnees et concordance sont renseignées seulement quand la pièce a pu être lue.
public record VerificationDocumentReponse(
		Long id,
		StatutVerificationDocument statut,
		String motif,
		DonneesExtraites donnees,
		Concordance concordance,
		int tentatives,
		Suite suite,
		StatutDossier statutDossier,
		String message,
		OffsetDateTime verifieLe) {
}
