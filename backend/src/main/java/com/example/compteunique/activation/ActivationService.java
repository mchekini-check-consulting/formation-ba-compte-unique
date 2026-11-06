package com.example.compteunique.activation;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.connexion.Jeton;
import com.example.compteunique.connexion.JetonInvalideException;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.PolitiqueMotDePasse;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.ouverture.EnvoisIdentifiants;
import com.example.compteunique.securite.JournalSecurite;

// CU-14 (US-09) : définition du mot de passe à partir du lien d'activation reçu par SMS.
// Le lien sert uniquement à définir le mot de passe, pas à se connecter. Il est valable
// 24 heures, à usage unique, stocké haché (règles de jeton de CU-15 : JetonService).
@Service
@Loggable
public class ActivationService {

	static final String MESSAGE_DEJA_ACTIVE = "Votre espace est déjà activé";

	private final JetonService jetons;
	private final IdentifiantConnexionRepository identifiants;
	private final IdentifiantConnexionService identifiantService;
	private final CompteRepository comptes;
	private final DossierRepository dossiers;
	private final EnvoisIdentifiants envois;
	private final JournalSecurite journal;
	private final Clock horloge;

	@Autowired
	public ActivationService(JetonService jetons, IdentifiantConnexionRepository identifiants,
			IdentifiantConnexionService identifiantService, CompteRepository comptes, DossierRepository dossiers,
			EnvoisIdentifiants envois, JournalSecurite journal) {
		this(jetons, identifiants, identifiantService, comptes, dossiers, envois, journal, Clock.systemDefaultZone());
	}

	@SuppressWarnings("PMD.ExcessiveParameterList")
	ActivationService(JetonService jetons, IdentifiantConnexionRepository identifiants,
			IdentifiantConnexionService identifiantService, CompteRepository comptes, DossierRepository dossiers,
			EnvoisIdentifiants envois, JournalSecurite journal, Clock horloge) {
		this.jetons = jetons;
		this.identifiants = identifiants;
		this.identifiantService = identifiantService;
		this.comptes = comptes;
		this.dossiers = dossiers;
		this.envois = envois;
		this.journal = journal;
		this.horloge = horloge;
	}

	/** Ouverture du lien : sans le consommer. */
	@Transactional(readOnly = true)
	public EtatActivation etat(String jeton) {
		Optional<Jeton> trouve = jetons.trouver(jeton, TypeJeton.ACTIVATION);
		if (trouve.isEmpty()) {
			return EtatActivation.EXPIRE;
		}
		if (estActif(trouve.get())) {
			return EtatActivation.DEJA_ACTIVE;
		}
		return trouve.get().estUtilisable(OffsetDateTime.now(horloge)) ? EtatActivation.VALIDE : EtatActivation.EXPIRE;
	}

	/**
	 * Mot de passe conforme : enregistré haché (Argon2id), identifiant ACTIF, lien marqué utilisé.
	 * Mot de passe refusé ou confirmation différente : le lien reste valide.
	 */
	@Transactional
	public EtatActivation activer(String jeton, String motDePasse, String confirmation) {
		Jeton trouve = jetons.trouver(jeton, TypeJeton.ACTIVATION).orElseThrow(JetonInvalideException::new);
		if (estActif(trouve)) {
			throw new EspaceDejaActiveException();
		}
		if (!trouve.estUtilisable(OffsetDateTime.now(horloge))) {
			throw new JetonInvalideException();
		}
		Long identifiantId = trouve.getIdentifiantConnexionId();
		// Contrôle du mot de passe d'abord : s'il est refusé, le lien n'est pas consommé.
		identifiantService.definirMotDePasse(identifiantId, motDePasse, confirmation, interdits(identifiantId));
		jetons.utiliser(jeton, TypeJeton.ACTIVATION);
		journaliser(EvenementSecurite.ACTIVATION, identifiantId);
		return EtatActivation.ACTIVE;
	}

	/**
	 * « Recevoir un nouveau lien » : nouveau lien valable 24 heures envoyé par SMS, l'ancien est
	 * invalidé. Rien n'est envoyé pour un lien inconnu ou un espace déjà activé, sans le révéler.
	 */
	public void nouveauLien(String jeton) {
		jetons.trouver(jeton, TypeJeton.ACTIVATION)
				.filter(trouve -> !estActif(trouve))
				.flatMap(trouve -> comptes.findByIdentifiantConnexionId(trouve.getIdentifiantConnexionId()))
				.ifPresent(compte -> envois.renvoyer(compte.getDossierId(), Canal.SMS));
	}

	/** CU-48 : activation journalisée avec l'identifiant de la cliente. */
	private void journaliser(String type, Long identifiantId) {
		String identifiant = identifiants.findById(identifiantId).map(IdentifiantConnexion::getIdentifiant).orElse(null);
		journal.enregistrer(EvenementSecurite.authentification(type, identifiant, identifiantId, null,
				OffsetDateTime.now(horloge)));
	}

	private boolean estActif(Jeton jeton) {
		return identifiants.findById(jeton.getIdentifiantConnexionId())
				.map(IdentifiantConnexion::getStatut)
				.filter(StatutIdentifiant.ACTIF::equals)
				.isPresent();
	}

	/** Adresse e-mail et noms de la cliente, que le mot de passe ne doit pas contenir (aussi pour CU-46). */
	public List<String> interdits(Long identifiantId) {
		return comptes.findByIdentifiantConnexionId(identifiantId)
				.map(Compte::getDossierId)
				.flatMap(dossiers::findById)
				.map(ActivationService::interditsDe)
				.orElse(List.of());
	}

	private static List<String> interditsDe(Dossier dossier) {
		return PolitiqueMotDePasse.interditsDe(dossier.getEmail(), dossier.getNomNaissance(), dossier.getNomUsage());
	}

}
