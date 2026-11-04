package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class IdentifiantConnexionServiceTest {

	private static final String CONFORME = "Abcdefghij1!";

	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final PasswordEncoder hacheur = new HachageConfig().hacheurMotDePasse();
	private final IdentifiantConnexionService service = new IdentifiantConnexionService(identifiants, hacheur);

	@Test
	void creerGenereUnIdentifiantEnAttenteSansMotDePasse() {
		when(identifiants.save(any(IdentifiantConnexion.class))).thenAnswer(invocation -> invocation.getArgument(0));
		IdentifiantConnexion cree = service.creer();
		assertThat(cree.getIdentifiant()).matches("\\d{8}");
		assertThat(cree.getStatut()).isEqualTo(StatutIdentifiant.EN_ATTENTE_ACTIVATION);
		assertThat(cree.getMotDePasseHache()).isNull();
	}

	@Test
	void creerRetireUnAutreIdentifiantSiLePremierExisteDeja() {
		when(identifiants.existsByIdentifiant(anyString())).thenReturn(true, false);
		when(identifiants.save(any(IdentifiantConnexion.class))).thenAnswer(invocation -> invocation.getArgument(0));
		service.creer();
		verify(identifiants, times(2)).existsByIdentifiant(anyString());
	}

	// CU-13 : au-delà de 5 identifiants déjà en base, la création échoue.
	@Test
	void creerEchoueApres5IdentifiantsDejaPris() {
		when(identifiants.existsByIdentifiant(anyString())).thenReturn(true);
		assertThrows(IdentifiantIndisponibleException.class, service::creer);
		verify(identifiants, times(IdentifiantConnexionService.TENTATIVES_MAX)).existsByIdentifiant(anyString());
		verify(identifiants, never()).save(any());
	}

	// CU-14 : le mot de passe ne doit pas contenir l'e-mail ni le nom de la cliente.
	@Test
	void definirMotDePasseRefuseLeNomDeLaCliente() {
		MotDePasseNonConformeException refus = assertThrows(MotDePasseNonConformeException.class,
				() -> service.definirMotDePasse(1L, "Dupont-2026!x", PolitiqueMotDePasse.interditsDe("a@b.fr", "Dupont")));
		assertThat(refus.getRegles()).containsExactly(PolitiqueMotDePasse.CONTIENT_EMAIL_OU_NOM);
		verify(identifiants, never()).findById(any());
	}

	@Test
	void definirMotDePasseEnregistreUneEmpreinteArgon2idJamaisLeClair() {
		IdentifiantConnexion compte = new IdentifiantConnexion("ABC");
		when(identifiants.findById(1L)).thenReturn(Optional.of(compte));
		service.definirMotDePasse(1L, CONFORME);
		assertThat(compte.getMotDePasseHache()).startsWith("$argon2id$").doesNotContain(CONFORME);
		assertThat(hacheur.matches(CONFORME, compte.getMotDePasseHache())).isTrue();
		assertThat(compte.getMotDePasseDefiniLe()).isNotNull();
		assertThat(compte.getStatut()).isEqualTo(StatutIdentifiant.ACTIF);
	}

	@Test
	void definirMotDePasseRefuseUnMotDePasseNonConforme() {
		MotDePasseNonConformeException refus = assertThrows(MotDePasseNonConformeException.class,
				() -> service.definirMotDePasse(1L, "court"));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(refus.getRegles()).contains(PolitiqueMotDePasse.TROP_COURT);
		verify(identifiants, never()).findById(any());
	}

	@Test
	void definirMotDePasseRefuseUnCompteInconnu() {
		when(identifiants.findById(1L)).thenReturn(Optional.empty());
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> service.definirMotDePasse(1L, CONFORME));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
