package com.example.compteunique.connexion;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.logging.Loggable;

// Création des identifiants et définition du mot de passe (appelés par CU-13 et CU-14).
@Service
@Loggable
public class IdentifiantConnexionService {

	private final IdentifiantConnexionRepository identifiants;
	private final PasswordEncoder hacheur;

	public IdentifiantConnexionService(IdentifiantConnexionRepository identifiants, PasswordEncoder hacheur) {
		this.identifiants = identifiants;
		this.hacheur = hacheur;
	}

	public IdentifiantConnexion creer() {
		String identifiant = SecretsAleatoires.nouvelIdentifiant();
		while (identifiants.existsByIdentifiant(identifiant)) {
			identifiant = SecretsAleatoires.nouvelIdentifiant();
		}
		return identifiants.save(new IdentifiantConnexion(identifiant));
	}

	@Transactional
	public void definirMotDePasse(Long identifiantConnexionId, String motDePasse) {
		List<String> regles = PolitiqueMotDePasse.reglesNonRespectees(motDePasse);
		if (!regles.isEmpty()) {
			throw new MotDePasseNonConformeException(regles);
		}
		IdentifiantConnexion compte = identifiants.findById(identifiantConnexionId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		compte.definirMotDePasse(hacheur.encode(motDePasse), OffsetDateTime.now());
	}

}
