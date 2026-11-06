package com.example.compteunique.reinitialisation;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Messagerie;

// CU-46 (US-18) critères 2, 3 et 6 : envoi par SMS du lien « Définir mon mot de passe », seulement
// pour un compte activé et dans la limite de 3 demandes par heure. Appelé en arrière-plan par
// ReinitialisationService, pour que la réponse ne révèle jamais si un compte existe.
@Service
@Loggable
public class EnvoiLienReinitialisation {

	/** Critère 6 : au-delà, aucun SMS (seuil à valider avec le RSSI). */
	static final int DEMANDES_MAX_PAR_HEURE = 3;

	private final IdentifiantConnexionRepository identifiants;
	private final JetonService jetons;
	private final CompteRepository comptes;
	private final DossierRepository dossiers;
	private final Messagerie messagerie;
	private Clock horloge = Clock.systemDefaultZone();

	public EnvoiLienReinitialisation(IdentifiantConnexionRepository identifiants, JetonService jetons,
			CompteRepository comptes, DossierRepository dossiers, Messagerie messagerie) {
		this.identifiants = identifiants;
		this.jetons = jetons;
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.messagerie = messagerie;
	}

	/** Tests : horloge simulée (l'horloge système est utilisée sinon). */
	EnvoiLienReinitialisation avecHorloge(Clock horlogeSimulee) {
		this.horloge = horlogeSimulee;
		return this;
	}

	/** Lien envoyé par SMS seulement pour un compte activé, dans la limite de 3 demandes par heure. */
	public void envoyer(String identifiant) {
		if (identifiant == null || identifiant.isBlank()) {
			return;
		}
		identifiants.findByIdentifiant(identifiant.trim())
				.filter(compte -> compte.getStatut() == StatutIdentifiant.ACTIF)
				.filter(this::sousLaLimite)
				.ifPresent(this::envoyerSms);
	}

	private boolean sousLaLimite(IdentifiantConnexion compte) {
		OffsetDateTime ilYaUneHeure = OffsetDateTime.now(horloge).minus(Duration.ofHours(1));
		return jetons.nombreCreesDepuis(compte.getId(), TypeJeton.REINITIALISATION, ilYaUneHeure)
				< DEMANDES_MAX_PAR_HEURE;
	}

	private void envoyerSms(IdentifiantConnexion compte) {
		comptes.findByIdentifiantConnexionId(compte.getId())
				.map(Compte::getDossierId)
				.flatMap(dossiers::findById)
				.map(Dossier::getMobile)
				.ifPresent(mobile -> {
					String jeton = jetons.generer(compte.getId(), TypeJeton.REINITIALISATION);
					messagerie.envoyerSms(mobile, texteSms(jetons.lien(TypeJeton.REINITIALISATION, jeton)));
				});
	}

	static String texteSms(String lien) {
		return "Hi-Tech Bank : définissez votre nouveau mot de passe : " + lien
				+ " (lien valable 24 h, à usage unique). Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.";
	}

}
