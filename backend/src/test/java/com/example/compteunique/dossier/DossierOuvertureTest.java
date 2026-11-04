package com.example.compteunique.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;

// CU-12 et CU-13 : évolution du statut du dossier après les contrôles.
class DossierOuvertureTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-11-04T10:00:00+01:00");

	@Test
	void pretASignerSeulementSiLesDeuxControlesSontOk() {
		assertThat(ExempleDossier.pretASigner("a", ResultatControle.OK).estPretASigner()).isTrue();
		assertThat(ExempleDossier.pretASigner("b", ResultatControle.NOK).estPretASigner()).isFalse();
		assertThat(new Dossier("c").estPretASigner()).isFalse();
	}

	@Test
	void signatureAttribueUnCustomerIdEtPrevoitUnFiletDeSecurite() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
		assertThat(dossier.getCustomerId()).isNotNull();
		assertThat(dossier.getCreationProchaineLe()).isNotNull();
	}

	@Test
	void troisRelancesPuisCreationARelancer() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		for (int echec = 1; echec < Dossier.CREATION_ESSAIS_MAX; echec++) {
			dossier.creationEchouee(MAINTENANT, false);
			assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
			assertThat(dossier.getCreationProchaineLe()).isEqualTo(MAINTENANT.plus(Dossier.DELAI_RELANCE));
		}
		dossier.creationEchouee(MAINTENANT, false);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CREATION_A_RELANCER);
		assertThat(dossier.getCreationTentatives()).isEqualTo(4);
		assertThat(dossier.getCreationProchaineLe()).isNull();
	}

	@Test
	void echecDefinitifPasseDirectementACreationARelancer() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		dossier.creationEchouee(MAINTENANT, true);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CREATION_A_RELANCER);
	}

	@Test
	void relanceManuelleGardeLeMemeCustomerId() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		UUID customerId = dossier.getCustomerId();
		dossier.creationEchouee(MAINTENANT, true);
		dossier.relancerCreation(MAINTENANT);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.CONVENTION_SIGNEE);
		assertThat(dossier.getCreationTentatives()).isZero();
		dossier.conventionSignee(MAINTENANT);
		assertThat(dossier.getCustomerId()).isEqualTo(customerId);
	}

	@Test
	void echecIgnoreUneFoisLeCompteOuvert() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		dossier.compteOuvert();
		dossier.creationEchouee(MAINTENANT, true);
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.COMPTE_OUVERT);
		assertThat(dossier.getCreationProchaineLe()).isNull();
	}

	@Test
	void identiteNonModifiableApresLaSignature() {
		DossierRepository dossiers = mock(DossierRepository.class);
		when(dossiers.findByReference("a")).thenReturn(Optional.of(ExempleDossier.conventionSignee("a")));
		ResponseStatusException refus = assertThrows(ResponseStatusException.class,
				() -> new DossierService(dossiers).renseignerIdentite("a", ExempleIdentite.identite()));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void annulationEffaceLaRelancePrevue() {
		Dossier dossier = ExempleDossier.conventionSignee("a");
		dossier.annuler();
		assertThat(dossier.getStatut()).isEqualTo(StatutDossier.ANNULE);
		assertThat(dossier.getCreationProchaineLe()).isNull();
	}

}
