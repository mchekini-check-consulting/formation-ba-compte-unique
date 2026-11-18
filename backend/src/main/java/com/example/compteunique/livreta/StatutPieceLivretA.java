package com.example.compteunique.livreta;

// CU-29 : statut de la vérification d'une nouvelle pièce d'identité à l'ouverture d'un Livret A.
public enum StatutPieceLivretA {
	/** Pièce saisie, analyse Stripe Identity attendue (ou à reprendre). */
	PIECE_A_VERIFIER,
	/** Pièce vérifiée, vérification de présence (selfie) attendue. */
	SELFIE_A_FAIRE,
	/** Pièce et selfie vérifiés : la pièce remplace celle du dossier, l'éligibilité peut commencer. */
	VALIDE,
	/** Pièce rejetée, selfie non concordant ou photo d'écran : la demande de Livret A est arrêtée. */
	BLOQUE,
	/** Pas de réponse après les relances : démarche en attente, conseiller alerté. */
	EN_ATTENTE
}
