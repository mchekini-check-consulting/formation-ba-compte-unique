package com.example.compteunique.connexion;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.logging.Loggable;

@Service
@Loggable
public class ConnexionService {

	private final IdentifiantConnexionRepository identifiants;
	private final SessionClientRepository sessions;
	private final PasswordEncoder hacheur;
	// Comparé quand il n'y a pas de mot de passe à vérifier : la durée de la réponse ne
	// révèle pas si l'identifiant existe.
	private final String empreinteLeurre;
	private final Clock horloge;

	/** CU-17 : session en cours, sans le jeton ; secondes restantes avant fermeture faute d'activité. */
	public record EtatSession(String identifiant, long expireDansSecondes) {
	}

	@Autowired
	public ConnexionService(IdentifiantConnexionRepository identifiants, SessionClientRepository sessions,
			PasswordEncoder hacheur) {
		this(identifiants, sessions, hacheur, Clock.systemDefaultZone());
	}

	ConnexionService(IdentifiantConnexionRepository identifiants, SessionClientRepository sessions,
			PasswordEncoder hacheur, Clock horloge) {
		this.identifiants = identifiants;
		this.sessions = sessions;
		this.hacheur = hacheur;
		this.horloge = horloge;
		this.empreinteLeurre = hacheur.encode(SecretsAleatoires.nouveauJeton());
	}

	@Transactional
	public ResultatConnexion connecter(String identifiant, String motDePasse) {
		Optional<IdentifiantConnexion> trouve = identifiants.findByIdentifiant(identifiant);
		if (trouve.isEmpty()) {
			refuserApresComparaison(motDePasse, false);
		}
		IdentifiantConnexion compte = trouve.orElseThrow();
		if (compte.getStatut() != StatutIdentifiant.ACTIF) {
			refuserApresComparaison(motDePasse, true);
		}
		if (!hacheur.matches(motDePasse, compte.getMotDePasseHache())) {
			throw new ConnexionRefuseeException(false);
		}
		compte.connexionReussie();
		String jeton = SecretsAleatoires.nouveauJeton();
		sessions.save(new SessionClient(compte.getId(), SecretsAleatoires.empreinte(jeton), maintenant()));
		return new ResultatConnexion(compte.getIdentifiant(), jeton);
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
	public void deconnecter(String jetonSession) {
		session(jetonSession).ifPresent(session -> session.revoquer(maintenant()));
	}

	/** Identifiant technique de la cliente connectée, vide si la session n'est pas active. */
	@Transactional
	public Optional<Long> idDeLaSession(String jetonSession) {
		return sessionActive(jetonSession).map(SessionClient::getIdentifiantConnexionId);
	}

	// CU-17 : toute demande est vérifiée par le serveur, même si l'écran est resté ouvert. Une
	// session trouvée expirée est révoquée.
	private Optional<SessionClient> sessionActive(String jetonSession) {
		OffsetDateTime maintenant = maintenant();
		return session(jetonSession).filter(session -> {
			if (session.estActive(maintenant)) {
				return true;
			}
			session.revoquer(maintenant);
			return false;
		});
	}

	private Optional<SessionClient> session(String jetonSession) {
		if (jetonSession == null) {
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

	private void refuserApresComparaison(String motDePasse, boolean activationAttendue) {
		hacheur.matches(motDePasse, empreinteLeurre);
		throw new ConnexionRefuseeException(activationAttendue);
	}

}
