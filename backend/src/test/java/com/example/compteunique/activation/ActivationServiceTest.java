package com.example.compteunique.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.activation.ActivationController.DemandeActivation;
import com.example.compteunique.activation.ActivationController.Lien;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.connexion.HachageConfig;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.connexion.Jeton;
import com.example.compteunique.connexion.JetonInvalideException;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.MotDePasseNonConformeException;
import com.example.compteunique.connexion.PolitiqueMotDePasse;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.dossier.ExempleDossier;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.ouverture.EnvoisIdentifiants;

// CU-14 : un cas de test par critère d'acceptation.
class ActivationServiceTest {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final Instant CREE = Instant.parse("2026-11-04T10:00:00Z");
	private static final Clock HORLOGE = Clock.fixed(CREE.plus(Duration.ofHours(1)), PARIS);
	private static final String JETON = "jeton-secret";
	private static final String CONFORME = "Abcdefghij1!";

	private final JetonService jetons = mock(JetonService.class);
	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final PasswordEncoder hacheur = new HachageConfig().hacheurMotDePasse();
	private final IdentifiantConnexionService identifiantService = new IdentifiantConnexionService(identifiants, hacheur);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final EnvoisIdentifiants envois = mock(EnvoisIdentifiants.class);
	private final ActivationService service = new ActivationService(jetons, identifiants, identifiantService, comptes,
			dossiers, envois, HORLOGE);

	private final IdentifiantConnexion identifiant = new IdentifiantConnexion("12345678");

	private static Jeton jeton(Instant creeLe) {
		OffsetDateTime cree = OffsetDateTime.ofInstant(creeLe, PARIS);
		return new Jeton(7L, TypeJeton.ACTIVATION, "empreinte", cree, cree.plusHours(24));
	}

	@BeforeEach
	void lienRecuParSms() {
		ReflectionTestUtils.setField(identifiant, "id", 7L);
		when(identifiants.findById(7L)).thenReturn(Optional.of(identifiant));
		when(jetons.trouver(JETON, TypeJeton.ACTIVATION)).thenReturn(Optional.of(jeton(CREE)));
		Compte compte = new Compte(ExempleDossier.ID, UUID.randomUUID(), Iban.de("12345678901"), 7L,
				OffsetDateTime.now(HORLOGE));
		when(comptes.findByIdentifiantConnexionId(7L)).thenReturn(Optional.of(compte));
		when(dossiers.findById(ExempleDossier.ID)).thenReturn(Optional.of(ExempleDossier.conventionSignee("ref")));
	}

	@Test
	void lienValideAfficheLaPageSansLeConsommer() {
		assertThat(service.etat(JETON)).isEqualTo(EtatActivation.VALIDE);
		verify(jetons, never()).utiliser(anyString(), any());
	}

	@Test
	void motDePasseConformeEnregistreHacheIdentifiantActifEtLienUtilise() {
		assertThat(service.activer(JETON, CONFORME, CONFORME)).isEqualTo(EtatActivation.ACTIVE);
		assertThat(identifiant.getMotDePasseHache()).startsWith("$argon2id$").doesNotContain(CONFORME);
		assertThat(identifiant.getStatut()).isEqualTo(StatutIdentifiant.ACTIF);
		verify(jetons).utiliser(JETON, TypeJeton.ACTIVATION);
	}

	private void refuse(String motDePasse, String message) {
		MotDePasseNonConformeException refus = assertThrows(MotDePasseNonConformeException.class,
				() -> service.activer(JETON, motDePasse, motDePasse));
		assertThat(refus.getRegles()).first().isEqualTo(message);
		ActivationController controleur = new ActivationController(service);
		assertThat(controleur.motDePasseRefuse(refus).getBody().message()).isEqualTo(message);
		// Le lien reste valide.
		verify(jetons, never()).utiliser(anyString(), any());
		assertThat(identifiant.getStatut()).isEqualTo(StatutIdentifiant.EN_ATTENTE_ACTIVATION);
	}

	@Test
	void moinsDe12CaracteresRefuseEtLienToujoursValide() {
		refuse("Court1!", "Votre mot de passe doit contenir au moins 12 caractères");
	}

	@Test
	void motCourantRefuseEtLienToujoursValide() {
		refuse("Motdepasse123!", "Ce mot de passe est trop courant");
	}

	@Test
	void emailOuNomRefuseEtLienToujoursValide() {
		refuse("Xx-Dupont-2026", "Votre mot de passe ne doit pas contenir votre e-mail ni votre nom");
		refuse("Xx-marie.dupont-26", "Votre mot de passe ne doit pas contenir votre e-mail ni votre nom");
	}

	// Complément de l'US-09 : confirmation différente ou vide refusée par le serveur, même si
	// l'écran a été contourné ; le lien reste valable pour corriger la saisie.
	@Test
	void confirmationDifferenteRefuseeEtLienToujoursValable() {
		MotDePasseNonConformeException refus = assertThrows(MotDePasseNonConformeException.class,
				() -> service.activer(JETON, CONFORME, "Abcdefghij1?"));
		assertThat(refus.getRegles()).containsExactly("Les deux mots de passe ne correspondent pas");
		assertThrows(MotDePasseNonConformeException.class, () -> service.activer(JETON, CONFORME, ""));
		verify(jetons, never()).utiliser(anyString(), any());
		assertThat(identifiant.getMotDePasseHache()).isNull();
		assertThat(service.etat(JETON)).isEqualTo(EtatActivation.VALIDE);
		assertThat(service.activer(JETON, CONFORME, CONFORME)).isEqualTo(EtatActivation.ACTIVE);
		verify(jetons).utiliser(JETON, TypeJeton.ACTIVATION);
	}

	@Test
	void lienExpireOuDejaUtiliseAfficheLienExpire() {
		when(jetons.trouver(JETON, TypeJeton.ACTIVATION)).thenReturn(Optional.of(jeton(CREE.minus(Duration.ofHours(24)))));
		assertThat(service.etat(JETON)).isEqualTo(EtatActivation.EXPIRE);
		assertThrows(JetonInvalideException.class, () -> service.activer(JETON, CONFORME, CONFORME));
		assertThat(new ActivationController(service).lienExpire().getBody().message()).isEqualTo("Lien expiré");
	}

	@Test
	void lienInconnuAfficheLienExpire() {
		assertThat(service.etat("inconnu")).isEqualTo(EtatActivation.EXPIRE);
		assertThrows(JetonInvalideException.class, () -> service.activer("inconnu", CONFORME, CONFORME));
	}

	@Test
	void nouveauLienEnvoyeParSmsEtAncienInvalide() {
		service.nouveauLien(JETON);
		verify(envois).renvoyer(ExempleDossier.ID, Canal.SMS);
	}

	@Test
	void nouveauLienSansEnvoiPourUnLienInconnuOuUnEspaceActive() {
		service.nouveauLien("inconnu");
		identifiant.definirMotDePasse("hache", OffsetDateTime.now(HORLOGE));
		service.nouveauLien(JETON);
		verify(envois, never()).renvoyer(any(), any());
	}

	@Test
	void identifiantDejaActifAfficheEspaceDejaActive() {
		identifiant.definirMotDePasse("hache", OffsetDateTime.now(HORLOGE));
		assertThat(service.etat(JETON)).isEqualTo(EtatActivation.DEJA_ACTIVE);
		EspaceDejaActiveException refus = assertThrows(EspaceDejaActiveException.class,
				() -> service.activer(JETON, CONFORME, CONFORME));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(new ActivationController(service).dejaActive().getBody().message())
				.isEqualTo("Votre espace est déjà activé");
	}

	@Test
	void controleurDelegueAuServiceSansJamaisMontrerLeJeton() {
		ActivationController controleur = new ActivationController(service);
		assertThat(controleur.etat(new Lien(JETON)).etat()).isEqualTo(EtatActivation.VALIDE);
		assertThat(controleur.activer(new DemandeActivation(JETON, CONFORME, CONFORME)).etat()).isEqualTo(EtatActivation.ACTIVE);
		assertThat(controleur.nouveauLien(new Lien("inconnu")).message()).contains("nouveau lien");
		assertThat(new Lien(JETON).toString()).doesNotContain(JETON);
		assertThat(new DemandeActivation(JETON, CONFORME, CONFORME).toString()).doesNotContain(JETON, CONFORME);
		assertThat(PolitiqueMotDePasse.interditsDe("a@b.fr")).isEqualTo(List.of("a@b.fr", "a"));
	}

}
