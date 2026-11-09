package com.example.compteunique.connexion;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.logging.LogMetier;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.securite.JournalSecurite;

@Service
@Loggable
public class ConnexionService {

	/** CU-16 : en dessous, la tentative est incomplète (ni comptée, ni journalisée). */
	static final int LONGUEUR_IDENTIFIANT = 8;
	/** Taille de la colonne identifiant : au-delà, aucun identifiant ne peut exister. */
	private static final int IDENTIFIANT_MAX = 32;

	private final IdentifiantConnexionRepository identifiants;
	private final SessionClientRepository sessions;
	private final PasswordEncoder hacheur;
	private final ProtectionConnexion protection;
	private final JournalSecurite journal;
	// Comparé quand il n'y a pas de mot de passe à vérifier : la durée de la réponse ne
	// révèle pas si l'identifiant existe.
	private final String empreinteLeurre;
	private final Clock horloge;

	/** CU-17 : session en cours, sans le jeton ; secondes restantes avant fermeture faute d'activité. */
	public record EtatSession(String identifiant, long expireDansSecondes) {
	}

	@Autowired
	public ConnexionService(IdentifiantConnexionRepository identifiants, SessionClientRepository sessions,
			PasswordEncoder hacheur, ProtectionConnexion protection, JournalSecurite journal) {
		this(identifiants, sessions, hacheur, protection, journal, Clock.systemDefaultZone());
	}

	ConnexionService(IdentifiantConnexionRepository identifiants, SessionClientRepository sessions,
			PasswordEncoder hacheur, ProtectionConnexion protection, JournalSecurite journal, Clock horloge) {
		this.identifiants = identifiants;
		this.sessions = sessions;
		this.hacheur = hacheur;
		this.protection = protection;
		this.journal = journal;
		this.horloge = horloge;
		this.empreinteLeurre = hacheur.encode(SecretsAleatoires.nouveauJeton());
	}

	/**
	 * Connexion, dans cet ordre : tentative incomplète (refusée sans rien compter, CU-16 critère 7),
	 * adresse IP bloquée (CU-48), identifiant bloqué (CU-16, sans vérifier le mot de passe), puis
	 * identifiant et mot de passe. Chaque tentative complète est journalisée (CU-48) ; si le journal
	 * ne peut pas être écrit, la connexion est refusée (JournalIndisponibleException). Les refus
	 * n'annulent pas la transaction : compteurs et journal sont gardés.
	 */
	@Transactional(noRollbackFor = { ConnexionRefuseeException.class, TropDeTentativesException.class })
	public ResultatConnexion connecter(String identifiantSaisi, String motDePasse, String adresseIp) {
		if (incomplete(identifiantSaisi, motDePasse)) {
			throw new ConnexionRefuseeException(false);
		}
		String identifiant = identifiantSaisi.length() > IDENTIFIANT_MAX
				? identifiantSaisi.substring(0, IDENTIFIANT_MAX)
				: identifiantSaisi;
		OffsetDateTime maintenant = maintenant();
		protection.verifierAdresse(adresseIp, identifiant, maintenant);
		// CU-212 : cherché avant la vérification du blocage, pour que le log d'une tentative pendant un
		// blocage porte le numéro technique de la cliente. Le mot de passe n'est toujours pas vérifié.
		Optional<IdentifiantConnexion> trouve = identifiants.findByIdentifiant(identifiant);
		Long identifiantConnexionId = trouve.map(IdentifiantConnexion::getId).orElse(null);
		BlocageIdentifiant blocage = protection.verifierIdentifiant(identifiant, identifiantConnexionId, maintenant);

		String motif = motifDeRefus(trouve, motDePasse);
		if (motif != null) {
			journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.CONNEXION_ECHOUEE, identifiant,
					identifiantConnexionId, motif, maintenant));
			protection.erreur(blocage, identifiantConnexionId, maintenant);
			throw new ConnexionRefuseeException(ProtectionConnexion.COMPTE_NON_ACTIVE.equals(motif));
		}
		IdentifiantConnexion compte = trouve.orElseThrow();
		journal.enregistrer(EvenementSecurite.authentification(EvenementSecurite.CONNEXION_REUSSIE, identifiant,
				identifiantConnexionId, null, maintenant));
		protection.reussite(blocage);
		compte.connexionReussie();
		String jeton = SecretsAleatoires.nouveauJeton();
		sessions.save(new SessionClient(compte.getId(), SecretsAleatoires.empreinte(jeton), maintenant));
		return new ResultatConnexion(compte.getIdentifiant(), jeton);
	}

	private static boolean incomplete(String identifiant, String motDePasse) {
		return identifiant == null || identifiant.length() < LONGUEUR_IDENTIFIANT || motDePasse == null
				|| motDePasse.isEmpty();
	}

	/** Motif du refus, vide si identifiant et mot de passe sont bons. Toujours une comparaison. */
	private String motifDeRefus(Optional<IdentifiantConnexion> trouve, String motDePasse) {
		if (trouve.isEmpty()) {
			hacheur.matches(motDePasse, empreinteLeurre);
			return ProtectionConnexion.IDENTIFIANT_INCONNU;
		}
		if (trouve.get().getStatut() != StatutIdentifiant.ACTIF) {
			hacheur.matches(motDePasse, empreinteLeurre);
			return ProtectionConnexion.COMPTE_NON_ACTIVE;
		}
		return hacheur.matches(motDePasse, trouve.get().getMotDePasseHache()) ? null
				: ProtectionConnexion.MOT_DE_PASSE_INCORRECT;
	}

	@Transactional
	public Optional<String> identifiantDeLaSession(String jetonSession) {
		return etatSession(jetonSession).map(EtatSession::identifiant);
	}

	/**
	 * CU-17 : session en cours, sans prolonger le délai (lecture, rafraîchissement automatique).
	 * Vide si la session est absente, inconnue, révoquée ou expirée.
	 */
	@Transactional
	public Optional<EtatSession> etatSession(String jetonSession) {
		return sessionActive(jetonSession).flatMap(this::etat);
	}

	/** CU-17 : une action de la cliente ; le délai de 10 minutes repart de zéro. */
	@Transactional
	public Optional<EtatSession> activite(String jetonSession) {
		return sessionActive(jetonSession).flatMap(session -> {
			session.activite(maintenant());
			return etat(session);
		});
	}

	/** CU-17 : déconnexion volontaire ; sans effet si la session est absente ou déjà fermée. */
	@Transactional
	@LogMetier(message = "déconnexion volontaire")
	public void deconnecter(String jetonSession) {
		session(jetonSession).filter(session -> !session.estRevoquee()).ifPresent(session -> {
			OffsetDateTime maintenant = maintenant();
			session.revoquer(maintenant);
			journaliserSession(EvenementSecurite.DECONNEXION, session, maintenant);
		});
	}

	/** Identifiant technique de la cliente connectée, vide si la session n'est pas active. */
	@Transactional
	public Optional<Long> idDeLaSession(String jetonSession) {
		return sessionActive(jetonSession).map(SessionClient::getIdentifiantConnexionId);
	}

	// CU-17 : toute demande est vérifiée par le serveur, même si l'écran est resté ouvert. Une
	// session trouvée expirée est révoquée, et son expiration journalisée une seule fois (CU-48).
	private Optional<SessionClient> sessionActive(String jetonSession) {
		OffsetDateTime maintenant = maintenant();
		return session(jetonSession).filter(session -> {
			if (session.estActive(maintenant)) {
				return true;
			}
			if (!session.estRevoquee()) {
				session.revoquer(maintenant);
				journaliserSession(EvenementSecurite.EXPIRATION_SESSION, session, maintenant);
			}
			return false;
		});
	}

	private void journaliserSession(String type, SessionClient session, OffsetDateTime maintenant) {
		String identifiant = identifiants.findById(session.getIdentifiantConnexionId())
				.map(IdentifiantConnexion::getIdentifiant)
				.orElse(null);
		journal.enregistrer(EvenementSecurite.authentification(type, identifiant, session.getIdentifiantConnexionId(),
				null, maintenant));
	}

	private Optional<SessionClient> session(String jetonSession) {
		if (jetonSession == null || jetonSession.isEmpty()) {
			return Optional.empty();
		}
		return sessions.findByJetonHache(SecretsAleatoires.empreinte(jetonSession));
	}

	private Optional<EtatSession> etat(SessionClient session) {
		return identifiants.findById(session.getIdentifiantConnexionId())
				.map(compte -> new EtatSession(compte.getIdentifiant(),
						Math.max(0, Duration.between(maintenant(), session.expireLe()).toSeconds())));
	}

	private OffsetDateTime maintenant() {
		return OffsetDateTime.now(horloge);
	}

}
