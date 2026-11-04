package com.example.compteunique.connexion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Politique de mot de passe, contrôlée côté serveur même si le contrôle de l'écran a été
// contourné :
// - CU-14 (US-09) : 12 caractères minimum, refus des mots de la liste des mots courants
//   (mots-de-passe-courants.txt, à remplacer par la liste fournie par le RSSI) et de ceux qui
//   contiennent l'adresse e-mail ou le nom de la cliente ;
// - CU-15 (maquette de l'étape 3) : majuscule, minuscule, chiffre et caractère spécial.
public final class PolitiqueMotDePasse {

	public static final String TROP_COURT = "Votre mot de passe doit contenir au moins 12 caractères";
	public static final String TROP_COURANT = "Ce mot de passe est trop courant";
	public static final String CONTIENT_EMAIL_OU_NOM = "Votre mot de passe ne doit pas contenir votre e-mail ni votre nom";

	private static final int LONGUEUR_MINIMALE = 12;
	/** Un nom ou un début d'e-mail plus court n'est pas recherché (« Li » refuserait trop de mots). */
	private static final int LONGUEUR_MINIMALE_INTERDIT = 3;
	private static final Set<String> MOTS_COURANTS = chargerMotsCourants();

	private PolitiqueMotDePasse() {
	}

	static List<String> reglesNonRespectees(String motDePasse) {
		return reglesNonRespectees(motDePasse, List.of());
	}

	/**
	 * @param interdits adresse e-mail et noms de la cliente, que le mot de passe ne doit pas
	 *                  contenir (sans tenir compte des majuscules ni des accents)
	 */
	static List<String> reglesNonRespectees(String motDePasse, Collection<String> interdits) {
		String valeur = motDePasse == null ? "" : motDePasse;
		List<String> regles = new ArrayList<>();
		if (valeur.length() < LONGUEUR_MINIMALE) {
			regles.add(TROP_COURT);
		}
		if (MOTS_COURANTS.contains(normaliser(valeur))) {
			regles.add(TROP_COURANT);
		}
		if (contientUnInterdit(valeur, interdits)) {
			regles.add(CONTIENT_EMAIL_OU_NOM);
		}
		if (valeur.chars().noneMatch(Character::isUpperCase)) {
			regles.add("Au moins une majuscule");
		}
		if (valeur.chars().noneMatch(Character::isLowerCase)) {
			regles.add("Au moins une minuscule");
		}
		if (valeur.chars().noneMatch(Character::isDigit)) {
			regles.add("Au moins un chiffre");
		}
		if (valeur.chars().allMatch(Character::isLetterOrDigit)) {
			regles.add("Au moins un caractère spécial");
		}
		return regles;
	}

	/** E-mail complet, partie avant le « @ », nom de naissance et nom d'usage. */
	public static List<String> interditsDe(String email, String... noms) {
		List<String> interdits = new ArrayList<>();
		if (email != null) {
			interdits.add(email);
			int arobase = email.indexOf('@');
			if (arobase > 0) {
				interdits.add(email.substring(0, arobase));
			}
		}
		for (String nom : noms) {
			if (nom != null) {
				interdits.add(nom);
			}
		}
		return interdits;
	}

	private static boolean contientUnInterdit(String motDePasse, Collection<String> interdits) {
		String normalise = normaliser(motDePasse);
		return interdits.stream()
				.map(PolitiqueMotDePasse::normaliser)
				.filter(interdit -> interdit.length() >= LONGUEUR_MINIMALE_INTERDIT)
				.anyMatch(normalise::contains);
	}

	private static String normaliser(String texte) {
		String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return sansAccents.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private static Set<String> chargerMotsCourants() {
		InputStream fichier = PolitiqueMotDePasse.class.getResourceAsStream("/mots-de-passe-courants.txt");
		if (fichier == null) {
			throw new IllegalStateException("mots-de-passe-courants.txt introuvable");
		}
		try (BufferedReader lecture = new BufferedReader(new InputStreamReader(fichier, StandardCharsets.UTF_8))) {
			return lecture.lines()
					.map(String::trim)
					.filter(ligne -> !ligne.isEmpty() && !ligne.startsWith("#"))
					.map(PolitiqueMotDePasse::normaliser)
					.collect(Collectors.toUnmodifiableSet());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
