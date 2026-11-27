package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.souscription.SouscriptionController.DemandeSignature;
import com.example.compteunique.souscription.SouscriptionController.DemandeVersement;
import com.example.compteunique.souscription.SouscriptionPer.Etat;

// CU-312 : routes /souscriptions/per (session obligatoire) et forme des refus.
class SouscriptionControllerTest {

	private final SouscriptionPer service = mock(SouscriptionPer.class);
	private final SouscriptionController routes = new SouscriptionController(service);
	private final Etat etat = mock(Etat.class);

	@Test
	@DisplayName("Chaque route transmet la demande au service et renvoie l'état du parcours")
	void routes() {
		when(service.etat("j")).thenReturn(etat);
		when(service.choisirVersement("j", BigDecimal.TEN, "AUTRE", "Prime")).thenReturn(etat);
		when(service.envoyerCode("j")).thenReturn(etat);
		when(service.signer("j", "123456")).thenReturn(etat);

		assertThat(routes.etat("j")).isSameAs(etat);
		assertThat(routes.versement("j", new DemandeVersement(BigDecimal.TEN, "AUTRE", "Prime"))).isSameAs(etat);
		assertThat(routes.code("j")).isSameAs(etat);
		assertThat(routes.signature("j", new DemandeSignature("123456"))).isSameAs(etat);
	}

	@Test
	@DisplayName("Corps absent : le service reçoit des valeurs vides et refuse lui-même")
	void corpsAbsent() {
		routes.versement("j", null);
		routes.signature("j", null);

		verify(service).choisirVersement("j", null, null, null);
		verify(service).signer("j", null);
	}

	@Test
	@DisplayName("Refus : code HTTP et message du ticket ; support inadapté (CU-349) : 422")
	void refus() {
		var refus = routes.refus(new DemandeRefusee(HttpStatus.LOCKED, "bloquée"));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
		assertThat(refus.getBody().message()).isEqualTo("bloquée");

		var inadapte = routes.inadapte(new SupportInadapte());
		assertThat(inadapte.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(routes.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(routes.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
