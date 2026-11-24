package com.example.compteunique.profil;

import jakarta.persistence.Embeddable;

/**
 * CU-10 : les réponses codées du questionnaire, telles qu'envoyées par l'écran et enregistrées dans chaque version du
 * profil (colonnes de même nom en snake_case). Codes et contrôles : {@link ProfilClientService}.
 */
@Embeddable
public record Reponses(String statutMatrimonial, String situationProfessionnelle, String patrimoine,
		String produitsDetenus, String objectif, String horizon, String reactionBaisse, String perteAcceptable,
		String financeDurable, Long plafondRetraiteEuros, Integer plafondRetraiteAnnee, Integer tmi,
		Integer anneeDepartRetraite) {
}
