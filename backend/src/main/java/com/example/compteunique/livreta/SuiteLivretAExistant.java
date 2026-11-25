package com.example.compteunique.livreta;

// CU-296 : choix de la cliente quand un Livret A est déjà détenu dans une autre banque.
public enum SuiteLivretAExistant {
	/** « Je clôture moi-même mon ancien livret » : certificat de clôture à déposer sous 3 mois. */
	CLOTURE_PAR_CLIENTE,
	/** « Je renonce à l'ouverture » : la demande est annulée. */
	RENONCIATION
}
