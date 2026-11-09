package com.example.compteunique.compte;

// Versement refusé : le compte n'est pas au statut ACTIF (par exemple clôturé).
public class CompteNonActifException extends RuntimeException {

	private static final long serialVersionUID = 1L;

}
