package com.example.compteunique.connexion;

import java.time.OffsetDateTime;
import java.util.Optional;

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

	public ConnexionService(IdentifiantConnexionRepository identifiants, SessionClientRepository sessions,
			PasswordEncoder hacheur) {
		this.identifiants = identifiants;
		this.sessions = sessions;
		this.hacheur = hacheur;
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
		sessions.save(new SessionClient(compte.getId(), SecretsAleatoires.empreinte(jeton), OffsetDateTime.now()));
		return new ResultatConnexion(compte.getIdentifiant(), jeton);
	}

	@Transactional(readOnly = true)
	public Optional<String> identifiantDeLaSession(String jetonSession) {
		if (jetonSession == null) {
			return Optional.empty();
		}
		return sessions.findByJetonHache(SecretsAleatoires.empreinte(jetonSession))
				.flatMap(session -> identifiants.findById(session.getIdentifiantConnexionId()))
				.map(IdentifiantConnexion::getIdentifiant);
	}

	private void refuserApresComparaison(String motDePasse, boolean activationAttendue) {
		hacheur.matches(motDePasse, empreinteLeurre);
		throw new ConnexionRefuseeException(activationAttendue);
	}

}
