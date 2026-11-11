package com.example.compteunique.beneficiaire;

/** CU-145 : résultat de la vérification du nom du titulaire d'un IBAN. */
public enum ResultatVerification {
	/** Le nom saisi est celui du titulaire. */
	CORRESPOND,
	/** L'IBAN existe mais le titulaire a un autre nom. */
	NE_CORRESPOND_PAS,
	/** Aucun compte pour cet IBAN. */
	INTROUVABLE,
	/** Service de vérification indisponible (sélecteur du bouchon, ou aucun partenaire en production). */
	INDISPONIBLE
}
