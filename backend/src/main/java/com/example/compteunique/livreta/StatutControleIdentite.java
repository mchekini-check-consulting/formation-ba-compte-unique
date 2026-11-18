package com.example.compteunique.livreta;

// CU-28 : statut du contrôle d'identité d'une ouverture de Livret A.
public enum StatutControleIdentite {
	/** Identité confirmée, contrôle sanctions/PPE envoyé, résultat attendu. */
	EN_COURS,
	/** Aucune correspondance, ou PPE validée par un responsable : la cliente passe à l'éligibilité. */
	VALIDE,
	/** Registre national des gels des avoirs : démarche bloquée, conformité alertée. */
	BLOQUE,
	/** Personne politiquement exposée : la démarche attend la validation d'un responsable. */
	ATTENTE_RESPONSABLE,
	/** Pas de réponse après les relances : démarche en attente, conseiller alerté. */
	EN_ATTENTE
}
