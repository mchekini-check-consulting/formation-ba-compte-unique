package com.example.bouchonsidentite.sanctions;

import java.time.LocalDate;

// Corps de POST /sanctions-pep/controles : identité saisie à l'étape « Mon identité » (US-01).
// casDeTest (facultatif) : cas choisi dans le panneau de test de l'écran ; il remplace la
// recherche par nom (« Test Sanctionné »...).
public record ControleRequete(String nomNaissance, String prenoms, LocalDate dateNaissance, String nationalite,
		CasDeTest casDeTest) {

	public ControleRequete(String nomNaissance, String prenoms, LocalDate dateNaissance, String nationalite) {
		this(nomNaissance, prenoms, dateNaissance, nationalite, null);
	}

}
