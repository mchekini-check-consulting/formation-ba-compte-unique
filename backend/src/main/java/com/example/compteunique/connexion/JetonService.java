package com.example.compteunique.connexion;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.logging.Loggable;

// Jetons d'activation et de réinitialisation (appelés par CU-13, CU-14 et CU-46).
@Service
@Loggable
public class JetonService {

	private static final Duration VALIDITE = Duration.ofHours(24);

	private final JetonRepository jetons;
	private final String adresseBase;

	public JetonService(JetonRepository jetons, @Value("${app.liens.adresse-base}") String adresseBase) {
		this.jetons = jetons;
		this.adresseBase = adresseBase;
	}

	// Renvoie le jeton en clair, à mettre dans le lien : il n'est enregistré nulle part.
	@Transactional
	public String generer(Long identifiantConnexionId, TypeJeton type) {
		OffsetDateTime maintenant = OffsetDateTime.now();
		jetons.invaliderLesJetonsActifs(identifiantConnexionId, type, maintenant);
		String jeton = SecretsAleatoires.nouveauJeton();
		jetons.save(new Jeton(identifiantConnexionId, type, SecretsAleatoires.empreinte(jeton), maintenant,
				maintenant.plus(VALIDITE)));
		return jeton;
	}

	// Renvoie le client du jeton, qui ne pourra plus resservir.
	@Transactional
	public Long utiliser(String jeton, TypeJeton type) {
		OffsetDateTime maintenant = OffsetDateTime.now();
		Jeton trouve = jetons.findByJetonHacheAndType(SecretsAleatoires.empreinte(jeton), type)
				.filter(candidat -> candidat.estUtilisable(maintenant))
				.orElseThrow(JetonInvalideException::new);
		trouve.marquerUtilise(maintenant);
		return trouve.getIdentifiantConnexionId();
	}

	/** Jeton tel qu'enregistré, même expiré ou déjà utilisé, sans le consommer (CU-14). */
	@Transactional(readOnly = true)
	public Optional<Jeton> trouver(String jeton, TypeJeton type) {
		if (jeton == null || jeton.isBlank()) {
			return Optional.empty();
		}
		return jetons.findByJetonHacheAndType(SecretsAleatoires.empreinte(jeton), type);
	}

	public String lien(TypeJeton type, String jeton) {
		return adresseBase + type.getChemin() + "?jeton=" + jeton;
	}

}
