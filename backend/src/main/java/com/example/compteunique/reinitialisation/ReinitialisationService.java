package com.example.compteunique.reinitialisation;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.activation.ActivationService;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.connexion.Jeton;
import com.example.compteunique.connexion.JetonInvalideException;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.SessionClientRepository;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Messagerie;

// CU-46 (US-18) : mot de passe oublié. La cliente saisit son identifiant et reçoit par SMS un
// lien (jeton haché, valable 24 h, à usage unique : JetonService) vers la page « Définir mon
// mot de passe ». La réponse ne révèle jamais si un compte existe.
@Service
@Loggable
public class ReinitialisationService {

	/** Critère 6 : au-delà, aucun SMS (seuil à valider avec le RSSI). */
	static final int DEMANDES_MAX_PAR_HEURE = 3;

	private static final Logger log = LoggerFactory.getLogger(ReinitialisationService.class);

	private final IdentifiantConnexionRepository identifiants;
	private final IdentifiantConnexionService identifiantService;
	private final JetonService jetons;
	private final SessionClientRepository sessions;
	private final CompteRepository comptes;
	private final DossierRepository dossiers;
	private final ActivationService activation;
	private final Messagerie messagerie;
	private final Executor arrierePlan;
	private final Clock horloge;

	@Autowired
	@SuppressWarnings("PMD.ExcessiveParameterList")
	public ReinitialisationService(IdentifiantConnexionRepository identifiants,
			IdentifiantConnexionService identifiantService, JetonService jetons, SessionClientRepository sessions,
			CompteRepository comptes, DossierRepository dossiers, ActivationService activation, Messagerie messagerie,
			@Qualifier("applicationTaskExecutor") Executor arrierePlan) {
		this(identifiants, identifiantService, jetons, sessions, comptes, dossiers, activation, messagerie, arrierePlan,
				Clock.systemDefaultZone());
	}

	@SuppressWarnings("PMD.ExcessiveParameterList")
	ReinitialisationService(IdentifiantConnexionRepository identifiants,
			IdentifiantConnexionService identifiantService, JetonService jetons, SessionClientRepository sessions,
			CompteRepository comptes, DossierRepository dossiers, ActivationService activation, Messagerie messagerie,
			Executor arrierePlan, Clock horloge) {
		this.identifiants = identifiants;
		this.identifiantService = identifiantService;
		this.jetons = jetons;
		this.sessions = sessions;
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.activation = activation;
		this.messagerie = messagerie;
		this.arrierePlan = arrierePlan;
		this.horloge = horloge;
	}

	/**
	 * Critères 1 à 3 et 6 : la demande est traitée en arrière-plan, la réponse part donc dans le
	 * même délai, qu'un compte existe ou non.
	 */
	public void demander(String identifiant) {
		arrierePlan.execute(() -> {
			try {
				envoyerLien(identifiant);
			} catch (RuntimeException e) {
				// Ni identifiant ni destinataire dans le log.
				log.warn("Demande de réinitialisation non traitée : {}", e.getClass().getSimpleName());
			}
		});
	}

	/** Lien envoyé par SMS seulement pour un compte activé, dans la limite de 3 demandes par heure. */
	void envoyerLien(String identifiant) {
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

	/** Ouverture du lien : sans le consommer. */
	@Transactional(readOnly = true)
	public EtatReinitialisation etat(String jeton) {
		return jetons.trouver(jeton, TypeJeton.REINITIALISATION)
				.filter(trouve -> trouve.estUtilisable(OffsetDateTime.now(horloge)))
				.map(trouve -> EtatReinitialisation.VALIDE)
				.orElse(EtatReinitialisation.EXPIRE);
	}

	/**
	 * Critère 4 : deux saisies identiques et conformes → nouveau mot de passe haché, lien utilisé,
	 * toutes les sessions fermées. Sinon rien n'est enregistré et le lien reste valable.
	 */
	@Transactional
	public EtatReinitialisation reinitialiser(String jeton, String motDePasse, String confirmation) {
		Jeton trouve = jetons.trouver(jeton, TypeJeton.REINITIALISATION)
				.filter(candidat -> candidat.estUtilisable(OffsetDateTime.now(horloge)))
				.orElseThrow(JetonInvalideException::new);
		Long identifiantId = trouve.getIdentifiantConnexionId();
		// Contrôle des deux saisies d'abord : si elles sont refusées, le lien n'est pas consommé.
		identifiantService.definirMotDePasse(identifiantId, motDePasse, confirmation,
				activation.interdits(identifiantId));
		jetons.utiliser(jeton, TypeJeton.REINITIALISATION);
		sessions.fermerToutesLesSessions(identifiantId);
		return EtatReinitialisation.REINITIALISE;
	}

	static String texteSms(String lien) {
		return "Hi-Tech Bank : définissez votre nouveau mot de passe : " + lien
				+ " (lien valable 24 h, à usage unique). Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.";
	}

}
