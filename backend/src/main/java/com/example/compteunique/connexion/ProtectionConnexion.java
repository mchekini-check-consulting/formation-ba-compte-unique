package com.example.compteunique.connexion;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.securite.JournalSecurite;

// CU-16 (US-11) et CU-48 : limitation des tentatives de connexion, par identifiant saisi
// (3 erreurs consécutives → 5 minutes) et par adresse IP (20 échecs en 15 minutes → 15 minutes).
// Appelé par ConnexionService, dans la transaction de la tentative.
@Service
@Loggable
public class ProtectionConnexion {

	/** Échec au sens de US-11 : tentative complète refusée pour l'un de ces motifs. */
	public static final String IDENTIFIANT_INCONNU = "IDENTIFIANT_INCONNU";
	public static final String COMPTE_NON_ACTIVE = "COMPTE_NON_ACTIVE";
	public static final String MOT_DE_PASSE_INCORRECT = "MOT_DE_PASSE_INCORRECT";
	/** Refus pendant un blocage : journalisés, mais pas comptés comme des échecs. */
	public static final String IDENTIFIANT_BLOQUE = "IDENTIFIANT_BLOQUE";
	public static final String ADRESSE_IP_BLOQUEE = "ADRESSE_IP_BLOQUEE";

	static final List<String> MOTIFS_ECHEC = List.of(IDENTIFIANT_INCONNU, COMPTE_NON_ACTIVE, MOT_DE_PASSE_INCORRECT);

	static final int ECHECS_MAX_ADRESSE_IP = 20;
	static final Duration FENETRE_ADRESSE_IP = Duration.ofMinutes(15);
	static final Duration DUREE_BLOCAGE_ADRESSE_IP = Duration.ofMinutes(15);

	private final BlocageIdentifiantRepository blocagesIdentifiant;
	private final BlocageAdresseIpRepository blocagesAdresse;
	private final JournalSecuriteRepository evenements;
	private final JournalSecurite journal;

	public ProtectionConnexion(BlocageIdentifiantRepository blocagesIdentifiant,
			BlocageAdresseIpRepository blocagesAdresse, JournalSecuriteRepository evenements,
			JournalSecurite journal) {
		this.blocagesIdentifiant = blocagesIdentifiant;
		this.blocagesAdresse = blocagesAdresse;
		this.evenements = evenements;
		this.journal = journal;
	}

	/**
	 * CU-48 critères 1 à 4 : refuse la tentative si l'adresse est bloquée, ou si elle a déjà 20
	 * échecs dans les 15 dernières minutes (la tentative déclenche alors le blocage). Sans adresse
	 * connue (hors demande HTTP), pas de limitation par adresse.
	 */
	public void verifierAdresse(String adresseIp, String identifiantSaisi, OffsetDateTime maintenant) {
		if (adresseIp == null) {
			return;
		}
		BlocageAdresseIp blocage = blocagesAdresse.findById(adresseIp).orElse(null);
		if (blocage != null && blocage.estBloquee(maintenant)) {
			refuserAdresse(identifiantSaisi, maintenant);
		}
		long echecs = evenements.countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(adresseIp,
				EvenementSecurite.CONNEXION_ECHOUEE, MOTIFS_ECHEC, maintenant.minus(FENETRE_ADRESSE_IP));
		if (echecs >= ECHECS_MAX_ADRESSE_IP) {
			OffsetDateTime fin = maintenant.plus(DUREE_BLOCAGE_ADRESSE_IP);
			if (blocage == null) {
				blocagesAdresse.save(new BlocageAdresseIp(adresseIp, fin));
			} else {
				blocage.bloquerJusquAu(fin);
			}
			journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.BLOCAGE_ADRESSE_IP,
					identifiantSaisi, null, ADRESSE_IP_BLOQUEE, maintenant));
			refuserAdresse(identifiantSaisi, maintenant);
		}
	}

	private void refuserAdresse(String identifiantSaisi, OffsetDateTime maintenant) {
		journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.CONNEXION_ECHOUEE, identifiantSaisi,
				null, ADRESSE_IP_BLOQUEE, maintenant));
		throw TropDeTentativesException.adresseIp();
	}

	/**
	 * CU-16 critère 2 : refuse la tentative, sans vérifier le mot de passe, si l'identifiant est
	 * bloqué. Sinon renvoie son compteur, remis à zéro si un blocage vient de se terminer (critère 6).
	 */
	public BlocageIdentifiant verifierIdentifiant(String identifiant, OffsetDateTime maintenant) {
		BlocageIdentifiant blocage = blocagesIdentifiant.findWithLockByIdentifiant(identifiant)
				.orElseGet(() -> new BlocageIdentifiant(identifiant));
		if (blocage.estBloque(maintenant)) {
			journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.CONNEXION_ECHOUEE, identifiant,
					null, IDENTIFIANT_BLOQUE, maintenant));
			throw TropDeTentativesException.identifiant(blocage.restant(maintenant));
		}
		blocage.leverSiTermine(maintenant);
		return blocage;
	}

	/** CU-16 critères 1 et 3 : une erreur de plus ; la troisième bloque l'identifiant 5 minutes. */
	public void erreur(BlocageIdentifiant blocage, Long identifiantConnexionId, OffsetDateTime maintenant) {
		boolean bloque = blocage.erreur(maintenant);
		blocagesIdentifiant.save(blocage);
		if (bloque) {
			journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.BLOCAGE_IDENTIFIANT,
					blocage.getIdentifiant(), identifiantConnexionId, IDENTIFIANT_BLOQUE, maintenant));
			throw TropDeTentativesException.identifiant(BlocageIdentifiant.DUREE_BLOCAGE);
		}
	}

	/** CU-16 critères 4 et 5 : connexion réussie, le compteur est remis à zéro. */
	public void reussite(BlocageIdentifiant blocage) {
		if (blocagesIdentifiant.existsById(blocage.getIdentifiant())) {
			blocagesIdentifiant.delete(blocage);
		}
	}

}
