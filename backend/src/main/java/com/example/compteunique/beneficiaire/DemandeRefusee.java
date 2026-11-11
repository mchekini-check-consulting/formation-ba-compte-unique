package com.example.compteunique.beneficiaire;

import org.springframework.http.HttpStatus;

/**
 * CU-146 et CU-147 : demande refusée, avec le message exact du ticket affiché à la cliente et le code HTTP.
 * termine : le parcours en cours est arrêté (ajout ou virement annulé, code expiré), l'écran revient au début.
 */
public class DemandeRefusee extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus statut;
	private final boolean termine;

	public DemandeRefusee(HttpStatus statut, String message) {
		this(statut, message, false);
	}

	public DemandeRefusee(HttpStatus statut, String message, boolean termine) {
		super(message);
		this.statut = statut;
		this.termine = termine;
	}

	public HttpStatus getStatut() {
		return statut;
	}

	public boolean isTermine() {
		return termine;
	}

}
