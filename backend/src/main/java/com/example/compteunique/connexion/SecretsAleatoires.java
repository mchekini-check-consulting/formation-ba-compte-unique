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
	private static final int LONGUEUR_IDENTIFIANT = 12;
	// Sans 0, O, 1, I ni L, qui se confondent à la lecture.
	private static final String ALPHABET_IDENTIFIANT = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

	private SecretsAleatoires() {
	}

	static String nouveauJeton() {
		byte[] octets = new byte[OCTETS_JETON];
		ALEATOIRE.nextBytes(octets);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
	}

	static String nouvelIdentifiant() {
		StringBuilder identifiant = new StringBuilder(LONGUEUR_IDENTIFIANT);
		for (int i = 0; i < LONGUEUR_IDENTIFIANT; i++) {
			identifiant.append(ALPHABET_IDENTIFIANT.charAt(ALEATOIRE.nextInt(ALPHABET_IDENTIFIANT.length())));
		}
		return identifiant.toString();
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
