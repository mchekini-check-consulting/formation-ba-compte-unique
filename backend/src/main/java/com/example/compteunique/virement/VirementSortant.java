package com.example.compteunique.virement;

import java.util.Locale;

/**
 * CU-144 : virement sortant depuis le compte d'IBAN iban, vers un bénéficiaire. simule : envoyé par le bouchon de
 * test, aucun virement réel. Les IBAN sont ramenés à leur forme enregistrée (sans espace, en majuscules) ;
 * bénéficiaire et libellé sans espaces superflus.
 */
public record VirementSortant(String iban, long montantCentimes, String beneficiaire, String ibanBeneficiaire,
		String libelle, boolean simule) {

	public static final int BENEFICIAIRE_MAX = 70;
	public static final int LIBELLE_MAX = 140;

	public VirementSortant {
		iban = normaliser(iban);
		ibanBeneficiaire = normaliser(ibanBeneficiaire);
		beneficiaire = beneficiaire == null ? "" : beneficiaire.strip();
		libelle = libelle == null ? "" : libelle.strip();
	}

	private static String normaliser(String iban) {
		return iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
	}

	/** Nom du bénéficiaire et libellé obligatoires, dans la taille des champs de la base. */
	boolean aDesInformationsCompletes() {
		return !beneficiaire.isEmpty() && beneficiaire.length() <= BENEFICIAIRE_MAX && !libelle.isEmpty()
				&& libelle.length() <= LIBELLE_MAX;
	}

}
