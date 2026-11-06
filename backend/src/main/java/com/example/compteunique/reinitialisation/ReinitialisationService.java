package com.example.compteunique.reinitialisation;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.activation.ActivationService;
import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.connexion.Jeton;
import com.example.compteunique.connexion.JetonInvalideException;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.SessionClientRepository;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.securite.JournalSecurite;

// CU-46 (US-18) : mot de passe oublié. La cliente saisit son identifiant et reçoit par SMS un
// lien (jeton haché, valable 24 h, à usage unique : JetonService) vers la page « Définir mon
// mot de passe ». La réponse ne révèle jamais si un compte existe. L'envoi du SMS est fait par
// EnvoiLienReinitialisation.
@Service
@Loggable
public class ReinitialisationService {

	private static final Logger log = LoggerFactory.getLogger(ReinitialisationService.class);

	private final IdentifiantConnexionService identifiantService;
	private final JetonService jetons;
	private final SessionClientRepository sessions;
	private final ActivationService activation;
	private final EnvoiLienReinitialisation envoiLien;
	private final JournalSecurite journal;
	private final Executor arrierePlan;
	private Clock horloge = Clock.systemDefaultZone();

	public ReinitialisationService(IdentifiantConnexionService identifiantService, JetonService jetons,
			SessionClientRepository sessions, ActivationService activation, EnvoiLienReinitialisation envoiLien,
			JournalSecurite journal, @Qualifier("applicationTaskExecutor") Executor arrierePlan) {
		this.identifiantService = identifiantService;
		this.jetons = jetons;
		this.sessions = sessions;
		this.activation = activation;
		this.envoiLien = envoiLien;
		this.journal = journal;
		this.arrierePlan = arrierePlan;
	}

	/** Tests : horloge simulée (l'horloge système est utilisée sinon). */
	ReinitialisationService avecHorloge(Clock horlogeSimulee) {
		this.horloge = horlogeSimulee;
		return this;
	}

	/**
	 * Critères 1 à 3 et 6 : la demande est traitée en arrière-plan, la réponse part donc dans le
	 * même délai, qu'un compte existe ou non.
	 */
	public void demander(String identifiant) {
		// CU-48 : la demande est journalisée telle que saisie, sans révéler si le compte existe.
		journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.DEMANDE_REINITIALISATION,
				identifiant, null, null, OffsetDateTime.now(horloge)));
		arrierePlan.execute(() -> {
			try {
				envoiLien.envoyer(identifiant);
			} catch (RuntimeException e) {
				// Ni identifiant ni destinataire dans le log.
				log.warn("Demande de réinitialisation non traitée : {}", e.getClass().getSimpleName());
			}
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
		// CU-48 : utilisation du lien de réinitialisation journalisée.
		journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.REINITIALISATION,
				identifiantService.identifiantDe(identifiantId).orElse(null), identifiantId, null,
				OffsetDateTime.now(horloge)));
		return EtatReinitialisation.REINITIALISE;
	}

}
