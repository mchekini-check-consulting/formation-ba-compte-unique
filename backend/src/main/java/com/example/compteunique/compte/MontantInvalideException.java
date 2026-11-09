package com.example.compteunique.compte;

// CU-11 et CU-143 : montant nul, négatif, non entier en centimes ou trop grand -> « Montant invalide ».
public class MontantInvalideException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
