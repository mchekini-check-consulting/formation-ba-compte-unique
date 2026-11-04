package com.example.compteunique.connexion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class HachageConfig {

	private static final int LONGUEUR_SEL = 16;
	private static final int LONGUEUR_EMPREINTE = 32;
	private static final int PARALLELISME = 1;
	private static final int MEMOIRE_KO = 19456;
	private static final int ITERATIONS = 2;

	// Argon2id, paramètres minimaux recommandés par l'OWASP.
	@Bean
	public PasswordEncoder hacheurMotDePasse() {
		return new Argon2PasswordEncoder(LONGUEUR_SEL, LONGUEUR_EMPREINTE, PARALLELISME, MEMOIRE_KO, ITERATIONS);
	}

}
