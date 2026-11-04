package com.example.compteunique.connexion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Génération des valeurs aléatoires : jetons de 256 bits et identifiants de connexion.
final class SecretsAleatoires {

	private static final SecureRandom ALEATOIRE = new SecureRandom();
	private static final int OCTETS_JETON = 32;
	/** CU-13 (US-08) : identifiant de connexion = nombre de 8 chiffres. */
	private static final int IDENTIFIANTS_POSSIBLES = 100_000_000;

	private SecretsAleatoires() {
	}

	static String nouveauJeton() {
		byte[] octets = new byte[OCTETS_JETON];
		ALEATOIRE.nextBytes(octets);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
	}

	static String nouvelIdentifiant() {
		return String.format("%08d", ALEATOIRE.nextInt(IDENTIFIANTS_POSSIBLES));
	}

	static String empreinte(String jeton) {
		try {
			byte[] octets = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(octets);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}

}
