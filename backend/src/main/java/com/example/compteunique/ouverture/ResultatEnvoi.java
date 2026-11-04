package com.example.compteunique.ouverture;

public enum ResultatEnvoi {
	ENVOYE,
	/** Non remis : relance automatique prévue. */
	EN_ECHEC,
	/** Non remis après les 3 relances : « Échec ». */
	ECHEC
}
