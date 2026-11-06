package com.example.compteunique.connexion;

import java.time.Duration;

// CU-16 et CU-48 : connexion refusée pendant un blocage (identifiant ou adresse IP).
// Le message est montré tel quel à la cliente ; CU-171 : la durée restante du blocage est
// aussi renvoyée (réponse 429), pour que l'écran affiche « Réessayez dans X minutes ».
public class TropDeTentativesException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** CU-48 critère 1 : texte exact du ticket, ne désigne aucun identifiant. */
	static final String MESSAGE_ADRESSE_IP = "Trop de tentatives depuis votre connexion. Réessayez plus tard.";

	private final transient Duration restant;

	private TropDeTentativesException(String message, Duration restant) {
		super(message);
		this.restant = restant.isNegative() ? Duration.ZERO : restant;
	}

	/** CU-16 critères 1 et 2 : « Trop de tentatives. Réessayez dans 5 minutes. », avec le temps restant. */
	static TropDeTentativesException identifiant(Duration restant) {
		long minutes = minutes(restant);
		return new TropDeTentativesException(
				"Trop de tentatives. Réessayez dans " + minutes + (minutes > 1 ? " minutes." : " minute."), restant);
	}

	/** CU-48 critère 1, CU-171 : le temps restant accompagne le message. */
	static TropDeTentativesException adresseIp(Duration restant) {
		return new TropDeTentativesException(MESSAGE_ADRESSE_IP, restant);
	}

	/** Temps restant avant la fin du blocage. */
	public Duration getRestant() {
		return restant;
	}

	/** Temps restant arrondi à la seconde supérieure, au moins 1 (en-tête Retry-After). */
	public long getRestantSecondes() {
		return Math.max(1, restant.getSeconds() + (restant.getNano() > 0 ? 1 : 0));
	}

	/** Minutes entières à attendre, arrondies au-dessus, au moins 1. */
	static long minutes(Duration restant) {
		return Math.max(1, (restant.getSeconds() + 59) / 60);
	}

}
