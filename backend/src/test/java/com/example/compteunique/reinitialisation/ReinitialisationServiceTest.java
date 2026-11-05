package com.example.compteunique.reinitialisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.activation.ActivationService;
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
import com.example.compteunique.connexion.SessionClientRepository;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.dossier.ExempleDossier;
import com.example.compteunique.messagerie.Messagerie;
import com.example.compteunique.reinitialisation.ReinitialisationController.DemandeLien;
import com.example.compteunique.reinitialisation.ReinitialisationController.Lien;
import com.example.compteunique.reinitialisation.ReinitialisationController.NouveauMotDePasse;

// CU-46 (US-18) et complément de l'US-09 : un cas de test par critère d'acceptation.
class ReinitialisationServiceTest {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final Instant CREE = Instant.parse("2026-11-04T10:00:00Z");
	private static final Clock HORLOGE = Clock.fixed(CREE.plus(Duration.ofHours(1)), PARIS);
	private static final String IDENTIFIANT = "12345678";
	private static final String JETON = "jeton-secret";
	private static final String ANCIEN = "hache-ancien";
	private static final String CONFORME = "Abcdefghij1!";
	private static final String LIEN = "https://frontend/reinitialisation?jeton=" + JETON;

	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final PasswordEncoder hacheur = new HachageConfig().hacheurMotDePasse();
	private final IdentifiantConnexionService identifiantService = new IdentifiantConnexionService(identifiants, hacheur);
	private final JetonService jetons = mock(JetonService.class);
	private final SessionClientRepository sessions = mock(SessionClientRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ActivationService activation = mock(ActivationService.class);
	private final Messagerie messagerie = mock(Messagerie.class);
	// Exécute la tâche d'arrière-plan tout de suite, pour pouvoir vérifier son effet.
	private final Executor immediat = Runnable::run;
	private final ReinitialisationService service = new ReinitialisationService(identifiants, identifiantService,
			jetons, sessions, comptes, dossiers, activation, messagerie, immediat, HORLOGE);

	private final IdentifiantConnexion compte = new IdentifiantConnexion(IDENTIFIANT);
	private final Dossier dossier = ExempleDossier.conventionSignee("ref");

	private static Jeton jeton(Instant creeLe) {
		OffsetDateTime cree = OffsetDateTime.ofInstant(creeLe, PARIS);
		return new Jeton(7L, TypeJeton.REINITIALISATION, "empreinte", cree, cree.plusHours(24));
	}

	@BeforeEach
	void compteActive() {
		ReflectionTestUtils.setField(compte, "id", 7L);
		compte.definirMotDePasse(ANCIEN, OffsetDateTime.now(HORLOGE));
		when(identifiants.findByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(compte));
		when(identifiants.findById(7L)).thenReturn(Optional.of(compte));
		Compte bancaire = new Compte(ExempleDossier.ID, UUID.randomUUID(), Iban.de("12345678901"), 7L,
				OffsetDateTime.now(HORLOGE));
		when(comptes.findByIdentifiantConnexionId(7L)).thenReturn(Optional.of(bancaire));
		when(dossiers.findById(ExempleDossier.ID)).thenReturn(Optional.of(dossier));
		when(jetons.generer(7L, TypeJeton.REINITIALISATION)).thenReturn(JETON);
		when(jetons.lien(TypeJeton.REINITIALISATION, JETON)).thenReturn(LIEN);
		when(jetons.trouver(JETON, TypeJeton.REINITIALISATION)).thenReturn(Optional.of(jeton(CREE)));
		when(activation.interdits(7L)).thenReturn(List.of());
	}

	// Critère 1 : même message, qu'un compte existe ou non.
	@Test
	void memeMessageQuUnCompteExisteOuNon() {
		ReinitialisationController controleur = new ReinitialisationController(service);
		assertThat(controleur.demander(new DemandeLien(IDENTIFIANT)).message())
				.isEqualTo("Si un compte correspond, un lien vient d’être envoyé par SMS");
		assertThat(controleur.demander(new DemandeLien("inconnu")).message())
				.isEqualTo(controleur.demander(new DemandeLien(IDENTIFIANT)).message());
	}

	// Critère 1 : la recherche du compte et l'envoi se font en arrière-plan, après la réponse.
	@Test
	void demandeTraiteeEnArrierePlan() {
		List<Runnable> enAttente = new ArrayList<>();
		ReinitialisationService differe = new ReinitialisationService(identifiants, identifiantService, jetons,
				sessions, comptes, dossiers, activation, messagerie, enAttente::add, HORLOGE);
		differe.demander(IDENTIFIANT);
		verify(identifiants, never()).findByIdentifiant(anyString());
		assertThat(enAttente).hasSize(1);
		enAttente.get(0).run();
		verify(messagerie).envoyerSms(dossier.getMobile(), ReinitialisationService.texteSms(LIEN));
	}

	// Critère 2 : compte activé → jeton créé et SMS avec le lien envoyé au mobile enregistré.
	@Test
	void compteActiveRecoitUnLienParSms() {
		service.demander(IDENTIFIANT);
		verify(jetons).generer(7L, TypeJeton.REINITIALISATION);
		verify(messagerie).envoyerSms(dossier.getMobile(), ReinitialisationService.texteSms(LIEN));
		assertThat(ReinitialisationService.texteSms(LIEN)).contains(LIEN).doesNotContain(IDENTIFIANT);
	}

	// Critère 3 : identifiant inconnu ou compte non activé → aucun SMS.
	@Test
	void aucunSmsPourUnIdentifiantInconnuOuUnCompteNonActive() {
		service.demander("inconnu");
		service.demander(" ");
		service.demander(null);
		when(identifiants.findByIdentifiant("87654321"))
				.thenReturn(Optional.of(new IdentifiantConnexion("87654321")));
		service.demander("87654321");
		verify(jetons, never()).generer(anyLong(), any());
		verify(messagerie, never()).envoyerSms(anyString(), anyString());
	}

	// Critère 6 : plus de 3 demandes en 1 heure → aucun SMS.
	@Test
	void auDelaDe3DemandesParHeureAucunSms() {
		when(jetons.nombreCreesDepuis(eq(7L), eq(TypeJeton.REINITIALISATION), any()))
				.thenReturn((long) ReinitialisationService.DEMANDES_MAX_PAR_HEURE);
		service.demander(IDENTIFIANT);
		verify(jetons).nombreCreesDepuis(7L, TypeJeton.REINITIALISATION,
				OffsetDateTime.now(HORLOGE).minus(Duration.ofHours(1)));
		verify(messagerie, never()).envoyerSms(anyString(), anyString());
	}

	@Test
	void uneErreurDEnvoiNeRemontePasALaCliente() {
		when(jetons.generer(7L, TypeJeton.REINITIALISATION)).thenThrow(new IllegalStateException("base"));
		service.demander(IDENTIFIANT);
		verify(messagerie, never()).envoyerSms(anyString(), anyString());
	}

	// Critère 4 : lien valide → page affichée sans consommer le lien.
	@Test
	void lienValideAfficheLaPageSansLeConsommer() {
		assertThat(service.etat(JETON)).isEqualTo(EtatReinitialisation.VALIDE);
		verify(jetons, never()).utiliser(anyString(), any());
	}

	// Critère 4 : mot de passe remplacé (haché), jeton utilisé, toutes les sessions fermées.
	@Test
	void nouveauMotDePasseRemplaceLAncienEtFermeLesSessions() {
		assertThat(service.reinitialiser(JETON, CONFORME, CONFORME)).isEqualTo(EtatReinitialisation.REINITIALISE);
		assertThat(compte.getMotDePasseHache()).isNotEqualTo(ANCIEN).startsWith("$argon2id$").doesNotContain(CONFORME);
		verify(jetons).utiliser(JETON, TypeJeton.REINITIALISATION);
		verify(sessions).fermerToutesLesSessions(7L);
	}

	// Complément de l'US-09 : confirmation différente ou vide, ou règles non respectées → rien
	// n'est enregistré et le lien reste valable.
	@Test
	void saisiesRefuseesSansConsommerLeLien() {
		refuse(CONFORME, "Abcdefghij1?", "Les deux mots de passe ne correspondent pas");
		refuse(CONFORME, "", "Veuillez confirmer votre mot de passe");
		refuse("Court1!", "Autre1!", "Votre mot de passe doit contenir au moins 12 caractères");
		assertThat(service.etat(JETON)).isEqualTo(EtatReinitialisation.VALIDE);
		assertThat(service.reinitialiser(JETON, CONFORME, CONFORME)).isEqualTo(EtatReinitialisation.REINITIALISE);
	}

	private void refuse(String motDePasse, String confirmation, String message) {
		MotDePasseNonConformeException refus = assertThrows(MotDePasseNonConformeException.class,
				() -> service.reinitialiser(JETON, motDePasse, confirmation));
		assertThat(new ReinitialisationController(service).motDePasseRefuse(refus).getBody().message())
				.isEqualTo(message);
		assertThat(compte.getMotDePasseHache()).isEqualTo(ANCIEN);
		verify(jetons, never()).utiliser(anyString(), any());
		verify(sessions, never()).fermerToutesLesSessions(anyLong());
	}

	// Critère 5 : lien expiré, déjà utilisé ou inconnu → « Lien expiré ».
	@Test
	void lienExpireDejaUtiliseOuInconnuAfficheLienExpire() {
		when(jetons.trouver(JETON, TypeJeton.REINITIALISATION))
				.thenReturn(Optional.of(jeton(CREE.minus(Duration.ofHours(24)))));
		assertThat(service.etat(JETON)).isEqualTo(EtatReinitialisation.EXPIRE);
		assertThrows(JetonInvalideException.class, () -> service.reinitialiser(JETON, CONFORME, CONFORME));
		assertThat(service.etat("inconnu")).isEqualTo(EtatReinitialisation.EXPIRE);
		Jeton utilise = jeton(CREE);
		utilise.marquerUtilise(OffsetDateTime.now(HORLOGE));
		when(jetons.trouver(JETON, TypeJeton.REINITIALISATION)).thenReturn(Optional.of(utilise));
		assertThat(service.etat(JETON)).isEqualTo(EtatReinitialisation.EXPIRE);
		assertThat(new ReinitialisationController(service).lienExpire().getBody().message()).isEqualTo("Lien expiré");
		assertThat(compte.getMotDePasseHache()).isEqualTo(ANCIEN);
	}

	@Test
	void controleurDelegueAuServiceSansJamaisMontrerDeSecret() {
		ReinitialisationController controleur = new ReinitialisationController(service);
		assertThat(controleur.etat(new Lien(JETON)).etat()).isEqualTo(EtatReinitialisation.VALIDE);
		assertThat(controleur.reinitialiser(new NouveauMotDePasse(JETON, CONFORME, CONFORME)).etat())
				.isEqualTo(EtatReinitialisation.REINITIALISE);
		assertThat(new Lien(JETON).toString()).doesNotContain(JETON);
		assertThat(new DemandeLien(IDENTIFIANT).toString()).doesNotContain(IDENTIFIANT);
		assertThat(new NouveauMotDePasse(JETON, CONFORME, CONFORME).toString()).doesNotContain(JETON, CONFORME);
	}

}
