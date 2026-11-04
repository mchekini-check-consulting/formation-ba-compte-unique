package com.example.compteunique.dossier;

import java.time.LocalDate;

import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Categories.TypePiece;

// Corps de PUT /dossiers/{reference}/identite : champs de l'étape « Mon identité » (CUB-8).
// Dates au format AAAA-MM-JJ.
public record IdentiteRequete(
		String civilite,
		String nomNaissance,
		String nomUsage,
		String prenoms,
		LocalDate dateNaissance,
		String communeNaissance,
		PaysNaissance paysNaissance,
		Nationalite nationalite,
		String voie,
		String complement,
		String codePostal,
		String ville,
		String paysAdresse,
		String email,
		String mobile,
		String fixe,
		TypePiece typePiece,
		String numeroPiece,
		LocalDate dateExpiration) {
}
