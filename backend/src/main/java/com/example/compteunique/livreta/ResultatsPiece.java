package com.example.compteunique.livreta;

// CU-29 : résultats transmis par l'écran, après l'appel au bouchon Stripe Identity. SANS_REPONSE : pas de
// réponse en 5 secondes, ni après les 3 relances automatiques.
public final class ResultatsPiece {

	private ResultatsPiece() {
	}

	/** Analyse de la pièce. */
	public enum Document {
		VERIFIE, A_REPRENDRE, REJETE, ERREUR_TECHNIQUE, SANS_REPONSE
	}

	/** Vérification de présence (selfie comparé à la photo de la pièce). */
	public enum Selfie {
		CORRESPONDANCE, NON_CORRESPONDANCE, ECHEC_VERIFICATION_PRESENCE, SANS_REPONSE
	}

}
