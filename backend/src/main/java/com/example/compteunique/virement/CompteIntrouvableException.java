package com.example.compteunique.virement;

// CU-143 : aucun compte ne correspond à l'IBAN du virement -> « Compte introuvable ».
public class CompteIntrouvableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
