package com.example.compteunique.ordres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;

import tools.jackson.databind.json.JsonMapper;

import com.example.compteunique.evenements.EvenementADistribuer;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.ordres.OrdresService.Annulation;
import com.example.compteunique.ordres.OrdresService.AnnulationImpossible;
import com.example.compteunique.ordres.OrdresService.DemandeInvalide;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.OrdresService.Enregistrement;
import com.example.compteunique.ordres.OrdresService.MontantInsuffisant;
import com.example.compteunique.ordres.OrdresService.OrdreIntrouvable;
import com.example.compteunique.ordres.OrdresTestController.PositionDemandee;

// CU-376 : page de test /test/ordres (routes du backend).
class OrdresTestControllerTest {

	private static final OffsetDateTime LE = OffsetDateTime.of(2026, 12, 1, 9, 0, 0, 0, ZoneOffset.UTC);

	private final OrdresService service = mock(OrdresService.class);
	private final PositionsContrat positions = mock(PositionsContrat.class);
	private final Evenements evenements = mock(Evenements.class);
	private final OrdresTestController routes = new OrdresTestController(service, positions, evenements);

	private static Ordre ordre(UUID evenementId) {
		Ordre ordre = new Ordre(new DemandeOrdre(UUID.randomUUID(), "AV-TEST-0001", TypeOrdre.RACHAT, "QZ0000000017",
				null, new BigDecimal("1000.00")), LE, LocalDate.of(2026, 12, 1));
		ordre.evenementEnregistre(evenementId);
		return ordre;
	}

	@Test
	@DisplayName("Passer un ordre : 201 au premier envoi, 200 pour un doublon ; l'ordre est affiché en heure de Paris "
			+ "avec son statut, sa date d'exécution prévue, le montant disponible et l'événement (en attente)")
	void passerUnOrdre() {
		UUID evenementId = UUID.randomUUID();
		Ordre ordre = ordre(evenementId);
		when(service.passer(any())).thenReturn(new Enregistrement(ordre, true), new Enregistrement(ordre, false));
		when(service.montantDisponible("AV-TEST-0001", "QZ0000000017")).thenReturn(new BigDecimal("2000.00"));
		EvenementADistribuer evenement = mock(EvenementADistribuer.class);
		when(evenement.getPublieLe()).thenReturn(null);
		when(evenement.getDerniereErreur()).thenReturn("Kafka indisponible : TimeoutException");
		when(evenements.aDistribuer(evenementId)).thenReturn(Optional.of(evenement));

		var cree = routes.passer(new DemandeOrdre(ordre.getId(), "AV-TEST-0001", TypeOrdre.RACHAT, "QZ0000000017",
				null, new BigDecimal("1000")));
		var doublon = routes.passer(new DemandeOrdre(ordre.getId(), "AV-TEST-0001", TypeOrdre.RACHAT,
				"QZ0000000017", null, new BigDecimal("1000")));

		assertThat(cree.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(doublon.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cree.getBody()).satisfies(vue -> {
			assertThat(vue.statut()).isEqualTo("DEMANDE");
			assertThat(vue.demandeLe()).isEqualTo("2026-12-01T10:00:00+01:00");
			assertThat(vue.dateExecutionPrevue()).isEqualTo(LocalDate.of(2026, 12, 1));
			assertThat(vue.montantDisponible()).isEqualByComparingTo("2000");
			assertThat(vue.evenement().statut()).isEqualTo("EN_ATTENTE");
			assertThat(vue.evenement().derniereErreur()).contains("Kafka indisponible");
		});
	}

	@Test
	@DisplayName("Événement publié : la page l'indique avec l'heure de publication ; liste des derniers ordres")
	void evenementPublie() {
		UUID evenementId = UUID.randomUUID();
		Ordre ordre = ordre(evenementId);
		EvenementADistribuer evenement = mock(EvenementADistribuer.class);
		when(evenement.getPublieLe()).thenReturn(LE);
		when(evenements.aDistribuer(evenementId)).thenReturn(Optional.of(evenement));
		when(service.derniers()).thenReturn(List.of(ordre));
		when(service.montantDisponible(any(), any())).thenReturn(BigDecimal.ZERO);

		assertThat(routes.ordres()).singleElement().satisfies(vue -> {
			assertThat(vue.evenement().statut()).isEqualTo("PUBLIE");
			assertThat(vue.evenement().publieLe()).isEqualTo("2026-12-01T10:00:00+01:00");
		});
	}

	@Test
	@DisplayName("Refus clairs : 400 demande invalide, 422 montant insuffisant avec le montant disponible")
	void refus() {
		assertThat(routes.invalide(new DemandeInvalide("Le montant doit être positif.")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		var insuffisant = routes.insuffisant(new MontantInsuffisant(new BigDecimal("2000.00")));
		assertThat(insuffisant.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(insuffisant.getBody().disponible()).isEqualByComparingTo("2000");
		assertThat(insuffisant.getBody().message()).contains("2 000,00 €");
	}

	@Test
	@DisplayName("Critère 9 : une position de test de 3 000 € est enregistrée puis le montant disponible affiché "
			+ "pour cette UC est de 3 000 €")
	void critere9Position() {
		when(service.montantDisponible("AV-TEST-0001", "QZ0000000017")).thenReturn(new BigDecimal("3000"));

		var reponse = routes.enregistrerPosition(
				new PositionDemandee("AV-TEST-0001", "QZ0000000017", new BigDecimal("3000"), null));

		verify(positions).enregistrerPositionTest("AV-TEST-0001", "QZ0000000017", new BigDecimal("3000"), null);
		assertThat(reponse.disponible()).isEqualByComparingTo("3000");
		assertThat(routes.disponible("AV-TEST-0001", "QZ0000000017").disponible()).isEqualByComparingTo("3000");
	}

	@Test
	@DisplayName("Ordre en parts : la demande JSON de l'écran porte le nombre de parts, affiché ensuite sur la page")
	void ordreEnParts() {
		DemandeOrdre demande = JsonMapper.builder().build().readValue("""
				{"id":"8d2c3f8e-1a8b-4a5e-9a51-0e7f3b9c1d11","numeroContrat":"AV-TEST-0001","type":"RACHAT",
				 "supportDepart":"QZ0000000017","supportArrivee":null,"montant":null,"nombreParts":12.5}
				""", DemandeOrdre.class);
		assertThat(demande.nombreParts()).isEqualByComparingTo("12.5");
		assertThat(demande.montant()).isNull();
		Ordre ordre = new Ordre(demande, LE, LocalDate.of(2026, 12, 1));
		when(service.passer(demande)).thenReturn(new Enregistrement(ordre, true));
		when(service.montantDisponible(any(), any())).thenReturn(BigDecimal.ZERO);

		var vue = routes.passer(demande).getBody();

		assertThat(vue.nombreParts()).isEqualByComparingTo("12.5");
		assertThat(vue.montant()).isNull();
		assertThat(vue.annuleLe()).isNull();
	}

	@Test
	@DisplayName("Annuler un ordre : 200 avec l'ordre annulé et l'heure d'annulation (Paris) ; 404 ordre inconnu, 409 "
			+ "annulation refusée")
	void annulerUnOrdre() {
		Ordre ordre = ordre(null);
		ordre.annuler(LE.plusHours(1));
		when(service.annuler(ordre.getId())).thenReturn(new Annulation(ordre, true));
		when(service.montantDisponible(any(), any())).thenReturn(new BigDecimal("3000"));

		var vue = routes.annuler(ordre.getId());

		assertThat(vue.statut()).isEqualTo("ANNULE");
		assertThat(vue.annuleLe()).isEqualTo("2026-12-01T11:00:00+01:00");
		assertThat(vue.evenement()).isNull();
		assertThat(routes.introuvable(new OrdreIntrouvable()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		var refus = routes.annulationImpossible(new AnnulationImpossible("L'heure limite est passée."));
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(refus.getBody().message()).isEqualTo("L'heure limite est passée.");
	}

	@Test
	@DisplayName("Vider les ordres et positions de test")
	void vider() {
		routes.vider();

		verify(service).viderOrdresEtPositionsDeTest();
	}

	@Test
	@DisplayName("Critère 10 : en preprod et prod (ORDRES_SIMULES=false), la page /test/ordres n'existe pas")
	void critere10AbsenteEnProduction() {
		ConditionalOnProperty condition = OrdresTestController.class.getAnnotation(ConditionalOnProperty.class);

		assertThat(condition.name()).containsExactly("app.test.ordres-simules");
		assertThat(condition.havingValue()).isEqualTo("true");
	}

}
