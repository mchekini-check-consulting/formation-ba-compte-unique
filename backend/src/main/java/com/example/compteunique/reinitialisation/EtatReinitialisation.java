package com.example.compteunique.reinitialisation;

public enum EtatReinitialisation {
	/** Lien utilisable : la page « Définir mon mot de passe » s'affiche. */
	VALIDE,
	/** Lien inconnu, expiré (plus de 24 heures), déjà utilisé ou remplacé : « Lien expiré ». */
	EXPIRE,
	/** Nouveau mot de passe enregistré, lien utilisé, sessions fermées. */
	REINITIALISE
}
