package com.example.compteunique.virement;

import java.util.Locale;

/**
 * CU-11 : virement instantané reçu pour un IBAN. simule : envoyé par le bouchon de test (CU-143), aucun virement
 * réel. L'IBAN est ramené à sa forme enregistrée (sans espace, en majuscules) ; émetteur et libellé sans espaces
 * superflus.
 */
public record VirementEntrant(String iban, long montantCentimes, String emetteur, String libelle, boolean simule) {

	public static final int EMETTEUR_MAX = 70;
	public static final int LIBELLE_MAX = 140;

	public VirementEntrant {
		iban = iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
		emetteur = emetteur == null ? "" : emetteur.strip();
		libelle = libelle == null ? "" : libelle.strip();
	}

	/** Émetteur et libellé obligatoires, dans la taille des champs de la base. */
	boolean aDesInformationsCompletes() {
		return !emetteur.isEmpty() && emetteur.length() <= EMETTEUR_MAX && !libelle.isEmpty()
				&& libelle.length() <= LIBELLE_MAX;
	}

}
