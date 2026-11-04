package com.example.compteunique.ouverture;

import com.example.compteunique.dossier.Categories.StatutDossier;

// Écran de confirmation de l'ouverture (CU-13) : aucune donnée en clair, seulement masquée.
// envoiEmail, envoiSms : null tant que l'envoi n'a pas eu lieu.
public record OuvertureReponse(
		StatutDossier statutDossier,
		String ibanMasque,
		String emailMasque,
		String mobileMasque,
		ResultatEnvoi envoiEmail,
		ResultatEnvoi envoiSms) {
}
