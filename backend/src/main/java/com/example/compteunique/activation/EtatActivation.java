package com.example.compteunique.activation;

public enum EtatActivation {
	/** Lien utilisable : la page « Activer mon espace » s'affiche. */
	VALIDE,
	/** Lien inconnu, expiré (plus de 24 heures), déjà utilisé ou remplacé : « Lien expiré ». */
	EXPIRE,
	/** Identifiant déjà ACTIF : « Votre espace est déjà activé ». */
	DEJA_ACTIVE,
	/** Mot de passe enregistré, identifiant passé à ACTIF. */
	ACTIVE
}
