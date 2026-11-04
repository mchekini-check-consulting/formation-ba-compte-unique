package com.example.compteunique.connexion;

// Le chemin est celui de la page du frontend qui reçoit le lien (activation : envoyé par SMS, CU-13).
public enum TypeJeton {
	ACTIVATION("/activation"),
	REINITIALISATION("/reinitialisation");

	private final String chemin;

	TypeJeton(String chemin) {
		this.chemin = chemin;
	}

	public String getChemin() {
		return chemin;
	}
}
