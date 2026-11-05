package com.example.compteunique.connexion;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.logging.Loggable;

// Création des identifiants et définition du mot de passe (appelés par CU-13, CU-14 et CU-46).
@Service
@Loggable
public class IdentifiantConnexionService {

	/** CU-13 : un identifiant déjà en base est régénéré, jusqu'à 5 tentatives. */
	static final int TENTATIVES_MAX = 5;

	private final IdentifiantConnexionRepository identifiants;
	private final PasswordEncoder hacheur;

	public IdentifiantConnexionService(IdentifiantConnexionRepository identifiants, PasswordEncoder hacheur) {
		this.identifiants = identifiants;
		this.hacheur = hacheur;
	}

	/** Identifiant de 8 chiffres, EN_ATTENTE_ACTIVATION, sans mot de passe. */
	public IdentifiantConnexion creer() {
		for (int tentative = 0; tentative < TENTATIVES_MAX; tentative++) {
			String identifiant = SecretsAleatoires.nouvelIdentifiant();
			if (!identifiants.existsByIdentifiant(identifiant)) {
				return identifiants.save(new IdentifiantConnexion(identifiant));
			}
		}
		throw new IdentifiantIndisponibleException();
	}

	@Transactional
	public void definirMotDePasse(Long identifiantConnexionId, String motDePasse) {
		definirMotDePasse(identifiantConnexionId, motDePasse, List.of());
	}

	/** @param interdits adresse e-mail et noms de la cliente (CU-14) */
	@Transactional
	public void definirMotDePasse(Long identifiantConnexionId, String motDePasse, Collection<String> interdits) {
		enregistrer(identifiantConnexionId, motDePasse, PolitiqueMotDePasse.reglesNonRespectees(motDePasse, interdits));
	}

	/**
	 * Saisie en deux fois (complément de l'US-09) : rien n'est enregistré tant que les deux saisies
	 * ne sont pas identiques et conformes, même si le contrôle de l'écran a été contourné.
	 */
	@Transactional
	public void definirMotDePasse(Long identifiantConnexionId, String motDePasse, String confirmation,
			Collection<String> interdits) {
		enregistrer(identifiantConnexionId, motDePasse,
				PolitiqueMotDePasse.reglesNonRespectees(motDePasse, confirmation, interdits));
	}

	private void enregistrer(Long identifiantConnexionId, String motDePasse, List<String> regles) {
		if (!regles.isEmpty()) {
			throw new MotDePasseNonConformeException(regles);
		}
		IdentifiantConnexion compte = identifiants.findById(identifiantConnexionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		compte.definirMotDePasse(hacheur.encode(motDePasse), OffsetDateTime.now());
	}

}
