package com.example.compteunique.connexion;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Même réponse que le jeton soit inconnu, expiré, déjà utilisé ou invalidé :
// rien ne révèle si le compte existe.
public class JetonInvalideException extends ResponseStatusException {

	private static final long serialVersionUID = 1L;

	public JetonInvalideException() {
		super(HttpStatus.GONE, "Lien expiré");
	}

}
