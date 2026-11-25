package com.example.compteunique.livreta;

// CU-32 : choix proposés dans le questionnaire de l'ouverture d'un Livret A (décision de la BA du 25/11/2026).
public final class ReponsesQuestionnaire {

	private ReponsesQuestionnaire() {
	}

	/** Objet de l'épargne. */
	public enum ObjetEpargne {
		PRECAUTION, PROJET, AUTRE
	}

	/** Origine des fonds ; AUTRE demande une précision. */
	public enum OrigineFonds {
		SALAIRE_REVENUS, EPARGNE_EXISTANTE, HERITAGE_DONATION, VENTE_BIEN, AUTRE
	}

}
