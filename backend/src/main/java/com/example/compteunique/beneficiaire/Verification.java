package com.example.compteunique.beneficiaire;

/** CU-145 : résultat d'une vérification ; nomVerifie : nom du titulaire, seulement si le nom correspond. */
public record Verification(ResultatVerification resultat, String nomVerifie) {

	static Verification sans(ResultatVerification resultat) {
		return new Verification(resultat, null);
	}

}
