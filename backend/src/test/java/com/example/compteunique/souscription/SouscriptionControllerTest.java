package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.souscription.SouscriptionController.DemandeSignature;
import com.example.compteunique.souscription.SouscriptionService.DemandeVersement;
import com.example.compteunique.souscription.SouscriptionService.Etat;

// CU-312 et CU-330 : routes /souscriptions/{per|av} (session obligatoire) et forme des refus.
class SouscriptionControllerTest {

	private final SouscriptionService service = mock(SouscriptionService.class);
	private final SouscriptionController routes = new SouscriptionController(service);
	private final Etat etat = mock(Etat.class);

	@Test
	@DisplayName("Chaque route transmet la demande au service avec son produit et renvoie l'état du parcours")
	void routes() {
		DemandeVersement demande = new DemandeVersement(BigDecimal.TEN, "AUTRE", "Prime", List.of());
		when(service.etat("j", Produit.AV)).thenReturn(etat);
		when(service.choisirVersement("j", Produit.PER, demande)).thenReturn(etat);
		when(service.envoyerCode("j", Produit.AV)).thenReturn(etat);
		when(service.signer("j", Produit.PER, "123456")).thenReturn(etat);

		assertThat(routes.etat("j", "av")).isSameAs(etat);
		assertThat(routes.versement("j", "per", demande)).isSameAs(etat);
		assertThat(routes.code("j", "av")).isSameAs(etat);
		assertThat(routes.signature("j", "per", new DemandeSignature("123456"))).isSameAs(etat);
	}

	@Test
	@DisplayName("Corps absent : le service reçoit des valeurs vides et refuse lui-même ; produit inconnu : 404")
	void corpsAbsentEtProduitInconnu() {
		routes.versement("j", "av", null);
		routes.signature("j", "per", null);

		verify(service).choisirVersement("j", Produit.AV, new DemandeVersement(null, null, null, null));
		verify(service).signer("j", Produit.PER, null);
		try {
			routes.etat("j", "livret");
		} catch (DemandeRefusee refus) {
			assertThat(refus.getStatut()).isEqualTo(HttpStatus.NOT_FOUND);
			return;
		}
		throw new AssertionError("refus attendu");
	}

	@Test
	@DisplayName("Refus : code HTTP et message du ticket ; support inadapté (CU-349) : 422")
	void refus() {
		var refus = routes.refus(new DemandeRefusee(HttpStatus.LOCKED, "bloquée"));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
		assertThat(refus.getBody().message()).isEqualTo("bloquée");

		assertThat(routes.inadapte(new SupportInadapte()).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(routes.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(routes.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
