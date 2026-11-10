package com.example.compteunique.connexion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.compteunique.logging.ContexteMdc;

/**
 * CU-212 (US-LOG-03), lignes L1 à L7 : traces de la connexion, de la session et de la déconnexion, écrites par
 * ConnexionService. Couche service ; champs : evenementConnexion (sert aux indicateurs I1 à I6 de Grafana), motif,
 * identifiantConnexionId (identifiant technique, « aucun » si l'identifiant saisi est inconnu) et secondes.
 * Jamais d'identifiant saisi, de mot de passe, de jeton ni d'adresse IP.
 */
final class TracesConnexion {

	static final String AUCUN = "aucun";
	private static final String CONNECTER = "connecter";

	static final String CONNEXION_REUSSIE = "CONNEXION_REUSSIE";
	static final String CONNEXION_REFUSEE = "CONNEXION_REFUSEE";
	static final String BLOCAGE_IDENTIFIANT = "BLOCAGE_IDENTIFIANT";
	static final String TENTATIVE_PENDANT_BLOCAGE = "TENTATIVE_PENDANT_BLOCAGE";
	static final String ADRESSE_IP_BLOQUEE = "ADRESSE_IP_BLOQUEE";
	static final String SESSION_INACTIVE = "SESSION_INACTIVE";
	static final String DECONNEXION = "DECONNEXION";

	private static final Logger log = LoggerFactory.getLogger(TracesConnexion.class);

	private TracesConnexion() {
	}

	/** L1 : connexion réussie. */
	static void connexionReussie(Long identifiantConnexionId) {
		ecrire(false, CONNECTER, CONNEXION_REUSSIE, identifiantConnexionId, null, null, "connexion réussie");
	}

	/** L2 : identifiant inconnu, compte non activé ou mot de passe faux. */
	static void connexionRefusee(String motif, Long identifiantConnexionId) {
		ecrire(true, CONNECTER, CONNEXION_REFUSEE, identifiantConnexionId, motif, null, "connexion refusée");
	}

	/** L3 : la troisième erreur bloque l'identifiant. */
	static void blocageIdentifiant(Long identifiantConnexionId, long secondes) {
		ecrire(true, CONNECTER, BLOCAGE_IDENTIFIANT, identifiantConnexionId, ProtectionConnexion.IDENTIFIANT_BLOQUE,
				secondes, "blocage de " + secondes + " secondes");
	}

	/** L4 : tentative pendant un blocage de l'identifiant, avec les secondes restantes. */
	static void tentativePendantBlocage(Long identifiantConnexionId, long secondesRestantes) {
		ecrire(true, CONNECTER, TENTATIVE_PENDANT_BLOCAGE, identifiantConnexionId,
				ProtectionConnexion.IDENTIFIANT_BLOQUE, secondesRestantes, "tentative pendant un blocage");
	}

	/** L5 : adresse IP bloquée, avec les secondes restantes ; ni adresse ni identifiant. */
	static void adresseIpBloquee(long secondesRestantes) {
		ecrire(true, CONNECTER, ADRESSE_IP_BLOQUEE, null, ProtectionConnexion.ADRESSE_IP_BLOQUEE,
				secondesRestantes, "adresse IP bloquée");
	}

	/** L6 : session fermée après 10 minutes sans activité. */
	static void sessionInactive(Long identifiantConnexionId) {
		ecrire(false, "session", SESSION_INACTIVE, identifiantConnexionId, null, null,
				"inactivité " + SessionClient.DELAI_INACTIVITE.toMinutes() + " minutes");
	}

	/** L7 : déconnexion volontaire. */
	static void deconnexionVolontaire(Long identifiantConnexionId) {
		ecrire(false, "deconnecter", DECONNEXION, identifiantConnexionId, null, null, "déconnexion volontaire");
	}

	private static void ecrire(boolean alerte, String methode, String evenement, Long identifiantConnexionId,
			String motif, Long secondes, String message) {
		ContexteMdc contexte = ContexteMdc.ouvrir()
				.avec("layer", "service")
				.avec("method", "ConnexionService." + methode)
				.avec("evenementConnexion", evenement);
		if (!ADRESSE_IP_BLOQUEE.equals(evenement)) {
			contexte.avec("identifiantConnexionId",
					identifiantConnexionId == null ? AUCUN : String.valueOf(identifiantConnexionId));
		}
		if (motif != null) {
			contexte.avec("motif", motif);
		}
		if (secondes != null) {
			contexte.avec("secondes", String.valueOf(secondes));
		}
		try (contexte) {
			if (alerte) {
				log.warn("{}", message);
			} else {
				log.info("{}", message);
			}
		}
	}

}
