package com.example.compteunique.connexion;

import java.time.Duration;

// CU-16 et CU-48 : connexion refusée pendant un blocage (identifiant ou adresse IP).
// Le message est montré tel quel à la cliente.
public class TropDeTentativesException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** CU-48 critère 1 : texte exact du ticket, ne désigne aucun identifiant. */
	static final String MESSAGE_ADRESSE_IP = "Trop de tentatives depuis votre connexion. Réessayez plus tard.";

	private TropDeTentativesException(String message) {
		super(message);
	}

	/** CU-16 critères 1 et 2 : « Trop de tentatives. Réessayez dans 5 minutes. », avec le temps restant. */
	static TropDeTentativesException identifiant(Duration restant) {
		long minutes = Math.max(1, (restant.getSeconds() + 59) / 60);
		return new TropDeTentativesException(
				"Trop de tentatives. Réessayez dans " + minutes + (minutes > 1 ? " minutes." : " minute."));
	}

	static TropDeTentativesException adresseIp() {
		return new TropDeTentativesException(MESSAGE_ADRESSE_IP);
	}

}
