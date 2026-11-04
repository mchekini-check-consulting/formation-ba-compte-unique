package com.example.compteunique.connexion;

public record ResultatConnexion(String identifiant, String jetonSession) {

	// Le jeton de session ne doit jamais apparaître dans un log.
	@Override
	public String toString() {
		return "ResultatConnexion[identifiant=" + identifiant + "]";
	}

}
