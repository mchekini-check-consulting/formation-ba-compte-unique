package com.example.compteunique.connexion;

public class ConnexionRefuseeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final boolean activationAttendue;

	public ConnexionRefuseeException(boolean activationAttendue) {
		super("Connexion refusée");
		this.activationAttendue = activationAttendue;
	}

	public boolean isActivationAttendue() {
		return activationAttendue;
	}

}
