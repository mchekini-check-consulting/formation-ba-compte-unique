package com.example.compteunique.dossier;

import java.time.OffsetDateTime;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;

// Ce que l'écran a besoin de savoir du dossier : aucune donnée d'identité n'est renvoyée.
public record DossierReponse(
		String reference,
		StatutDossier statut,
		boolean indiceAmericain,
		boolean residenceFrancaise,
		ResultatControle controleSanctions,
		ResultatControle controlePep,
		OffsetDateTime controleLe) {

	static DossierReponse de(Dossier dossier) {
		return new DossierReponse(dossier.getReference(), dossier.getStatut(), dossier.isIndiceAmericain(),
				dossier.isResidenceFrancaise(), dossier.getControleSanctions(), dossier.getControlePep(),
				dossier.getControleLe());
	}

}
