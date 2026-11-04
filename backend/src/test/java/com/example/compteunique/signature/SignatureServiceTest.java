package com.example.compteunique.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.dossier.ExempleDossier;

// CU-12 : un cas de test par critère d'acceptation.
class SignatureServiceTest {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final Instant DEBUT = Instant.parse("2026-11-04T10:00:00Z");
	private static final String REF = "ref-1";

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final SignatureConventionRepository signatures = mock(SignatureConventionRepository.class);
	private final ApplicationEventPublisher evenements = mock(ApplicationEventPublisher.class);
	private Dossier dossier;
	private SignatureConvention enregistree;

	@BeforeEach
	void dossierPretASigner() {
		dossier = ExempleDossier.pretASigner(REF, ResultatControle.OK);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		when(signatures.findByDossierId(dossier.getId())).thenAnswer(invocation -> Optional.ofNullable(enregistree));
		when(signatures.save(any(SignatureConvention.class))).thenAnswer(invocation -> {
			enregistree = invocation.getArgument(0);
			return enregistree;
		});
	}

	private SignatureService service(Duration ecoule) {
		return new SignatureService(dossiers, signatures, evenements, Clock.fixed(DEBUT.plus(ecoule), PARIS));
	}

	private SignatureService maintenant() {
		return service(Duration.ZERO);
	}

	private SignatureReponse saisir(ResultatSignature resultat) {
		return maintenant().enregistrerResultat(REF, resultat);
	}

	@Test
	void conventionAffichéeAvecSonEmpreinteAvantToutCode() {
		SignatureReponse reponse = maintenant().etat(REF);
		assertThat(reponse.etat()).isEqualTo("A_SIGNER");
		assertThat(reponse.convention()).isEqualTo(Convention.TEXTE);
		assertThat(reponse.empreinteDocument()).hasSize(64).isEqualTo(Convention.empreinte(Convention.TEXTE));
	}

	@Test
	void codeSaisiDansLes5MinutesSigneEtHorodateLaConvention() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		SignatureReponse reponse = service(Duration.ofMinutes(4)).enregistrerResultat(REF,
				ResultatSignature.SIGNATURE_VALIDE);
		assertThat(reponse.etat()).isEqualTo("SIGNEE");
		assertThat(reponse.signeeLe()).isEqualTo(OffsetDateTime.ofInstant(DEBUT.plus(Duration.ofMinutes(4)), PARIS));
		assertThat(reponse.statutDossier()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
		assertThat(enregistree.getEmpreinteDocument()).isEqualTo(Convention.EMPREINTE);
		verify(evenements).publishEvent(new ConventionSigneeEvent(dossier.getId()));
	}

	@Test
	void conventionSigneeNeCreeNiCompteNiIbanNiIdentifiant() {
		// La signature ne fait que publier l'événement : aucun dépôt de compte ou d'identifiant n'est appelé ici.
		maintenant().enveloppeEnvoyee(REF, "env-1");
		saisir(ResultatSignature.SIGNATURE_VALIDE);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
		assertThat(dossier.getCustomerId()).isNotNull();
	}

	@Test
	void codeExpireNouveauCodeDemandeEtDossierToujoursEnCours() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		SignatureReponse expire = saisir(ResultatSignature.CODE_EXPIRE);
		assertThat(expire.etat()).isEqualTo("EXPIREE");
		assertThat(expire.nouveauCodePossible()).isTrue();
		SignatureReponse relance = maintenant().enveloppeEnvoyee(REF, "env-2");
		assertThat(relance.etat()).isEqualTo("ENVOYEE");
		assertThat(relance.enveloppeId()).isEqualTo("env-2");
		assertThat(enregistree.getRelances()).isEqualTo(1);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.EN_COURS_DE_VERIFICATION);
	}

	@Test
	void codeSaisiApres5MinutesEstExpireMemeSiDocusignLeValide() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		SignatureReponse reponse = service(Duration.ofMinutes(5).plusSeconds(1)).enregistrerResultat(REF,
				ResultatSignature.SIGNATURE_VALIDE);
		assertThat(reponse.etat()).isEqualTo("EXPIREE");
		verify(evenements, never()).publishEvent(any());
	}

	@Test
	void codeDeLaRelanceExpireAnnuleLaSignature() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		saisir(ResultatSignature.CODE_EXPIRE);
		maintenant().enveloppeEnvoyee(REF, "env-2");
		SignatureReponse reponse = saisir(ResultatSignature.CODE_EXPIRE);
		assertThat(reponse.etat()).isEqualTo("ANNULEE");
		assertThat(reponse.motifAnnulation()).isEqualTo("Code expiré");
		assertThat(reponse.statutDossier()).isEqualTo(StatutDossier.ANNULE);
	}

	@Test
	void uneSeuleRelancePossible() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> maintenant().enveloppeEnvoyee(REF, "env-2"));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void troisCodesFauxPuisQuatriemeTentativeAnnuleLaSignature() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		assertThat(saisir(ResultatSignature.CODE_FAUX).essaisRestants()).isEqualTo(2);
		saisir(ResultatSignature.CODE_FAUX);
		SignatureReponse troisieme = saisir(ResultatSignature.CODE_FAUX);
		assertThat(troisieme.etat()).isEqualTo("ENVOYEE");
		assertThat(troisieme.codesFaux()).isEqualTo(3);
		SignatureReponse quatrieme = saisir(ResultatSignature.SIGNATURE_VALIDE);
		assertThat(quatrieme.etat()).isEqualTo("ANNULEE");
		assertThat(quatrieme.motifAnnulation()).isEqualTo("3 tentatives dépassées");
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.ANNULE);
		verify(evenements, never()).publishEvent(any());
	}

	@Test
	void retourApresFermetureReprendAuMemeEndroitAvecLesCodesFauxComptes() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		saisir(ResultatSignature.CODE_FAUX);
		SignatureReponse reprise = maintenant().etat(REF);
		assertThat(reprise.etat()).isEqualTo("ENVOYEE");
		assertThat(reprise.enveloppeId()).isEqualTo("env-1");
		assertThat(reprise.codesFaux()).isEqualTo(1);
		assertThat(reprise.statutDossier()).isEqualTo(StatutDossier.EN_COURS_DE_VERIFICATION);
	}

	@Test
	void annulationDemandeeAnnuleLaSignatureEtLeDossier() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		SignatureReponse reponse = saisir(ResultatSignature.ANNULATION_DEMANDEE);
		assertThat(reponse.etat()).isEqualTo("ANNULEE");
		assertThat(reponse.motifAnnulation()).isEqualTo("Annulation demandée");
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.ANNULE);
	}

	@Test
	void annulationPossibleAvecUnCodeExpire() {
		maintenant().enveloppeEnvoyee(REF, "env-1");
		saisir(ResultatSignature.CODE_EXPIRE);
		assertThat(saisir(ResultatSignature.ANNULATION_DEMANDEE).etat()).isEqualTo("ANNULEE");
	}

	@Test
	void conventionNonPreteASignerSiUnControleEstEnEchec() {
		dossier = ExempleDossier.pretASigner(REF, ResultatControle.NOK);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> maintenant().enveloppeEnvoyee(REF, "env-1"));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void requetesIncompletesOuSansSignatureEnCoursRefusees() {
		SignatureService service = maintenant();
		assertThat(assertThrows(ResponseStatusException.class, () -> service.enveloppeEnvoyee(REF, " "))
				.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(assertThrows(ResponseStatusException.class, () -> service.enregistrerResultat(REF, null))
				.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(assertThrows(ResponseStatusException.class,
				() -> service.enregistrerResultat(REF, ResultatSignature.CODE_FAUX)).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
		assertThat(assertThrows(ResponseStatusException.class, () -> service.etat("inconnu")).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void controleurDelegueAuService() {
		SignatureService mockService = mock(SignatureService.class);
		SignatureReponse reponse = SignatureReponse.aSigner(StatutDossier.EN_COURS_DE_VERIFICATION);
		when(mockService.etat(REF)).thenReturn(reponse);
		when(mockService.enveloppeEnvoyee(REF, "e")).thenReturn(reponse);
		when(mockService.enregistrerResultat(REF, ResultatSignature.CODE_FAUX)).thenReturn(reponse);
		SignatureController controleur = new SignatureController(mockService);
		assertThat(controleur.etat(REF)).isSameAs(reponse);
		assertThat(controleur.enveloppe(REF, new SignatureController.EnveloppeEnvoyee("e"))).isSameAs(reponse);
		assertThat(controleur.resultat(REF, new SignatureController.ResultatSaisie(ResultatSignature.CODE_FAUX)))
				.isSameAs(reponse);
	}

}
