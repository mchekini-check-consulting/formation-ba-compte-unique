package com.example.compteunique.connexion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MotDePasseNonConformeException extends ResponseStatusException {

	private static final long serialVersionUID = 1L;

	private final transient List<String> regles;

	public MotDePasseNonConformeException(List<String> regles) {
		super(HttpStatus.BAD_REQUEST, "Mot de passe non conforme");
		this.regles = List.copyOf(regles);
	}

	public List<String> getRegles() {
		return regles;
	}

}
