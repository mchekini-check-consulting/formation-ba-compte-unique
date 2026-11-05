package com.example.compteunique.ouverture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.GenerateurIban;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.connexion.IdentifiantIndisponibleException;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.dossier.ExempleDossier;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.messagerie.Messagerie;
import com.example.compteunique.signature.ConventionSigneeEvent;

// CU-13 : un cas de test par critère d'acceptation.
class OuvertureTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneId.of("Europe/Paris"));
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.now(HORLOGE);
	private static final String REF = "ref-1";
	private static final long ID = ExempleDossier.ID;

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final GenerateurIban generateur = mock(GenerateurIban.class);
	private final IdentifiantConnexionService identifiantService = mock(IdentifiantConnexionService.class);
	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final JetonService jetons = mock(JetonService.class);
	private final Messagerie messagerie = mock(Messagerie.class);
	private final EnvoiMessageRepository envois = mock(EnvoiMessageRepository.class);
	private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

	private final CreationCompte creation = new CreationCompte(dossiers, comptes, generateur, identifiantService,
			HORLOGE);
	private final EnvoisIdentifiants envoisIdentifiants = new EnvoisIdentifiants(dossiers, comptes, identifiants,
			jetons, messagerie, envois, transactions, HORLOGE);
	private final OuvertureService ouverture = new OuvertureService(dossiers, comptes, envois, creation,
			envoisIdentifiants, HORLOGE);

	private Dossier dossier;
	private Compte compte;
	private final IdentifiantConnexion identifiant = new IdentifiantConnexion("12345678");
	private EnvoiMessage envoiEmail;
	private EnvoiMessage envoiSms;

	@BeforeEach
	void conventionSignee() {
		dossier = ExempleDossier.conventionSignee(REF);
		ReflectionTestUtils.setField(identifiant, "id", 7L);
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(dossiers.findById(ID)).thenReturn(Optional.of(dossier));
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		when(generateur.generer()).thenReturn(Iban.de("12345678901"));
		when(identifiantService.creer()).thenReturn(identifiant);
		when(comptes.findByDossierId(ID)).thenAnswer(invocation -> Optional.ofNullable(compte));
		when(comptes.save(any(Compte.class))).thenAnswer(invocation -> {
			compte = invocation.getArgument(0);
			return compte;
		});
		when(identifiants.findById(7L)).thenReturn(Optional.of(identifiant));
		when(jetons.generer(7L, TypeJeton.ACTIVATION)).thenReturn("jeton-secret");
		when(jetons.lien(TypeJeton.ACTIVATION, "jeton-secret"))
				.thenReturn("https://frontend.formation-ba-compte-unique.dev.check-consulting.net/activation?jeton=jeton-secret");
		when(envois.findByDossierIdAndCanal(ID, Canal.EMAIL)).thenAnswer(invocation -> Optional.ofNullable(envoiEmail));
		when(envois.findByDossierIdAndCanal(ID, Canal.SMS)).thenAnswer(invocation -> Optional.ofNullable(envoiSms));
		when(envois.save(any(EnvoiMessage.class))).thenAnswer(invocation -> {
			EnvoiMessage envoi = invocation.getArgument(0);
			if (envoi.getCanal() == Canal.EMAIL) {
				envoiEmail = envoi;
			} else {
				envoiSms = envoi;
			}
			return envoi;
		});
		when(messagerie.envoyerEmail(anyString(), anyString(), anyString())).thenReturn(true);
		when(messagerie.envoyerSms(anyString(), anyString())).thenReturn(true);
	}

	@Test
	void conventionSigneeCreeLeCompteEtLIdentifiantPuisEnvoieLesDeuxMessages() {
		ouverture.conventionSignee(new ConventionSigneeEvent(ID));
		assertThat(compte.getIban()).isEqualTo(Iban.de("12345678901").valeur());
		assertThat(compte.getSoldeCentimes()).isZero();
		assertThat(compte.getStatut()).isEqualTo("ACTIF");
		assertThat(compte.getCustomerId()).isEqualTo(dossier.getCustomerId());
		assertThat(compte.getIdentifiantConnexionId()).isEqualTo(7L);
		assertThat(compte.getOuvertLe()).isEqualTo(MAINTENANT);
		assertThat(identifiant.getStatut()).isEqualTo(StatutIdentifiant.EN_ATTENTE_ACTIVATION);
		assertThat(identifiant.getMotDePasseHache()).isNull();
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(envoiEmail.getResultat()).isEqualTo(ResultatEnvoi.ENVOYE);
		assertThat(envoiEmail.getTentatives()).isEqualTo(1);
		assertThat(envoiEmail.getDernierEssaiLe()).isEqualTo(MAINTENANT);
		assertThat(envoiSms.getResultat()).isEqualTo(ResultatEnvoi.ENVOYE);
	}

	@Test
	void emailContientLIdentifiantSansLienNiDonneeDIdentite() {
		ouverture.ouvrir(ID);
		ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
		verify(messagerie).envoyerEmail(eq(dossier.getEmail()), eq(EnvoisIdentifiants.OBJET_EMAIL), texte.capture());
		assertThat(texte.getValue()).contains("12345678", "Identifiant").doesNotContain("http", "Dupont", "Marie",
				"1990", "Lilas", "Lyon", "2000000000123");
	}

	@Test
	void smsContientLeLienValable24HeuresSansLIdentifiantNiDonneeDIdentite() {
		ouverture.ouvrir(ID);
		ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
		verify(messagerie).envoyerSms(eq(dossier.getMobile()), texte.capture());
		assertThat(texte.getValue())
				.contains("https://frontend.formation-ba-compte-unique.dev.check-consulting.net/activation?jeton=",
						"24 h")
				.doesNotContain("12345678", "Dupont", "Marie");
	}

	@Test
	void echecDeLEmailNEmpechePasLeSms() {
		when(messagerie.envoyerEmail(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException());
		ouverture.ouvrir(ID);
		assertThat(envoiEmail.getResultat()).isEqualTo(ResultatEnvoi.EN_ECHEC);
		assertThat(envoiEmail.getProchaineTentativeLe()).isEqualTo(MAINTENANT.plusMinutes(1));
		assertThat(envoiSms.getResultat()).isEqualTo(ResultatEnvoi.ENVOYE);
	}

	@Test
	void envoiNonRemisRelanceTroisFoisPuisEchec() {
		when(messagerie.envoyerSms(anyString(), anyString())).thenReturn(false);
		ouverture.ouvrir(ID);
		envoisIdentifiants.envoyer(ID, Canal.SMS);
		envoisIdentifiants.envoyer(ID, Canal.SMS);
		assertThat(envoiSms.getResultat()).isEqualTo(ResultatEnvoi.EN_ECHEC);
		envoisIdentifiants.envoyer(ID, Canal.SMS);
		assertThat(envoiSms.getTentatives()).isEqualTo(4);
		assertThat(envoiSms.getResultat()).isEqualTo(ResultatEnvoi.ECHEC);
		assertThat(envoiSms.getProchaineTentativeLe()).isNull();
	}

	@Test
	void erreurPendantLaCreationEnregistreLEchecSansRienEnvoyer() {
		when(generateur.generer()).thenThrow(new IllegalStateException("panne"));
		ouverture.ouvrir(ID);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
		assertThat(dossier.getCreationTentatives()).isEqualTo(1);
		assertThat(dossier.getCreationProchaineLe()).isEqualTo(MAINTENANT.plusMinutes(1));
		verify(messagerie, never()).envoyerEmail(anyString(), anyString(), anyString());
	}

	@Test
	void identifiantsIndisponiblesPassentLeDossierACreationARelancer() {
		when(identifiantService.creer()).thenThrow(new IdentifiantIndisponibleException());
		ouverture.ouvrir(ID);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CREATION_A_RELANCER);
		verify(comptes, never()).save(any());
	}

	@Test
	void creationRelanceeSansDoublonSiLeCompteExisteDeja() {
		ouverture.ouvrir(ID);
		ouverture.ouvrir(ID);
		verify(comptes, org.mockito.Mockito.times(1)).save(any());
		assertThat(envoiEmail.getTentatives()).isEqualTo(1);
	}

	@Test
	void creationRefuseeSiLaConventionNEstPasSignee() {
		dossier.annuler();
		ouverture.ouvrir(ID);
		verify(comptes, never()).save(any());
	}

	@Test
	void relanceManuelleReprendAvecLeMemeCustomerId() {
		when(identifiantService.creer()).thenThrow(new IdentifiantIndisponibleException()).thenReturn(identifiant);
		ouverture.ouvrir(ID);
		var customerId = dossier.getCustomerId();
		OuvertureReponse reponse = ouverture.relancer(REF);
		assertThat(reponse.statutDossier()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(compte.getCustomerId()).isEqualTo(customerId);
		assertThat(assertThrows(ResponseStatusException.class, () -> ouverture.relancer(REF)).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void ecranDeConfirmationAvecAdresseEtNumeroMasques() {
		ouverture.ouvrir(ID);
		OuvertureReponse reponse = ouverture.etat(REF);
		assertThat(reponse.statutDossier()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(reponse.emailMasque()).isEqualTo("m•••@exemple.fr");
		assertThat(reponse.mobileMasque()).isEqualTo("06 •• •• •• 00");
		assertThat(reponse.ibanMasque()).startsWith("FR").contains("••••").endsWith(compte.getIban().substring(23));
		assertThat(reponse.envoiEmail()).isEqualTo(ResultatEnvoi.ENVOYE);
		assertThat(reponse.envoiSms()).isEqualTo(ResultatEnvoi.ENVOYE);
	}

	@Test
	void renvoiPossibleApres60SecondesSeulement() {
		when(messagerie.envoyerEmail(anyString(), anyString(), anyString())).thenReturn(false, true);
		ouverture.ouvrir(ID);
		assertThat(assertThrows(ResponseStatusException.class, () -> ouverture.renvoyer(REF, Canal.EMAIL))
				.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		OuvertureService plusTard = new OuvertureService(dossiers, comptes, envois, creation, envoisIdentifiants,
				Clock.offset(HORLOGE, java.time.Duration.ofSeconds(61)));
		assertThat(plusTard.renvoyer(REF, Canal.EMAIL).envoiEmail()).isEqualTo(ResultatEnvoi.ENVOYE);
		assertThat(envoiEmail.getTentatives()).isEqualTo(1);
		assertThat(assertThrows(ResponseStatusException.class, () -> plusTard.renvoyer("inconnu", Canal.SMS))
				.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void renvoiSansEnvoiPrealableRefuse() {
		assertThat(assertThrows(ResponseStatusException.class, () -> ouverture.renvoyer(REF, Canal.SMS))
				.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void relancesAutomatiquesReprennentCreationsEtEnvoisEnEchec() {
		EnvoiMessage enEchec = new EnvoiMessage(ID, Canal.SMS);
		when(dossiers.findByStatutAndCreationProchaineLeLessThanEqual(StatutDossier.CONVENTION_SIGNEE, MAINTENANT))
				.thenReturn(List.of(dossier));
		when(envois.findByResultatAndProchaineTentativeLeLessThanEqual(ResultatEnvoi.EN_ECHEC, MAINTENANT))
				.thenReturn(List.of(enEchec));
		new RelancesAutomatiques(dossiers, envois, ouverture, envoisIdentifiants, HORLOGE).relancer();
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(envoiSms.getTentatives()).isEqualTo(2);
	}

	@Test
	void masquageDesCoordonnees() {
		assertThat(Masquage.email("marie.dupont@example.com")).isEqualTo("m•••@example.com");
		assertThat(Masquage.email("sansarobase")).isEqualTo("•••");
		assertThat(Masquage.email(null)).isNull();
		assertThat(Masquage.mobile("+33 6 12 34 56 78")).isEqualTo("33 •• •• •• 78");
		assertThat(Masquage.mobile("123")).isEqualTo("•••");
		assertThat(Masquage.mobile(null)).isNull();
	}

	@Test
	void controleurDelegueAuService() {
		OuvertureService mockService = mock(OuvertureService.class);
		OuvertureReponse reponse = new OuvertureReponse(StatutDossier.COMPTE_OUVERT, null, null, null, null, null);
		when(mockService.etat(REF)).thenReturn(reponse);
		when(mockService.relancer(REF)).thenReturn(reponse);
		when(mockService.renvoyer(REF, Canal.SMS)).thenReturn(reponse);
		OuvertureController controleur = new OuvertureController(mockService);
		assertThat(controleur.etat(REF)).isSameAs(reponse);
		assertThat(controleur.relancer(REF)).isSameAs(reponse);
		assertThat(controleur.renvoyer(REF, Canal.SMS)).isSameAs(reponse);
	}

}
