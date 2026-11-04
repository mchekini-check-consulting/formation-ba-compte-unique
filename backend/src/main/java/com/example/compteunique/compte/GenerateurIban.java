package com.example.compteunique.compte;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.compteunique.logging.Loggable;

// CU-88 (TECH-02) : générateur d'IBAN fictif, pour créer des comptes sans partenaire bancaire.
// Le numéro de compte est tiré au hasard ; s'il existe déjà en base, il est tiré à nouveau.
@Service
@Loggable
public class GenerateurIban {

	/** Au-delà, la création du compte échoue et sera relancée (CU-13). */
	static final int TIRAGES_MAX = 10;
	private static final long NUMEROS_POSSIBLES = 100_000_000_000L;

	private final CompteRepository comptes;
	private final RandomGenerator aleatoire;

	@Autowired
	public GenerateurIban(CompteRepository comptes) {
		this(comptes, new SecureRandom());
	}

	GenerateurIban(CompteRepository comptes, RandomGenerator aleatoire) {
		this.comptes = comptes;
		this.aleatoire = aleatoire;
	}

	public Iban generer() {
		for (int tirage = 0; tirage < TIRAGES_MAX; tirage++) {
			String numero = String.format("%011d", aleatoire.nextLong(NUMEROS_POSSIBLES));
			if (!comptes.existsByNumeroCompte(numero)) {
				return Iban.de(numero);
			}
		}
		throw new IllegalStateException("aucun numéro de compte libre après " + TIRAGES_MAX + " tirages");
	}

}
