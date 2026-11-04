package com.example.bouchonsidentite.sanctions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

// CUB-70 : un cas de test par critère d'acceptation.
class ControleSanctionsServiceTest {

	private final ControleSanctionsService service = new ControleSanctionsService(Duration.ofMillis(50));

	private static ControleRequete personne(String prenoms, String nom) {
		return new ControleRequete(nom, prenoms, LocalDate.of(1980, 1, 1), "Française");
	}

	@Test
	void personneSanctionneeAnnuleLOuvertureEnMoinsDUneSeconde() {
		ControleReponse reponse = assertTimeoutPreemptively(Duration.ofSeconds(1),
				() -> service.controler(personne("Test", "Sanctionné")));
		assertEquals(ResultatControle.CORRESPONDANCE_SANCTIONS, reponse.resultat());
		assertEquals(Suite.ANNULER, reponse.suite());
		assertEquals(StatutDossier.ANNULE, reponse.statutDossier());
		assertEquals(Messages.OUVERTURE_ANNULEE, reponse.message());
	}

	@Test
	void personnePepAnnuleLOuvertureEnMoinsDUneSeconde() {
		ControleReponse reponse = assertTimeoutPreemptively(Duration.ofSeconds(1),
				() -> service.controler(personne("Test", "PEP")));
		assertEquals(ResultatControle.CORRESPONDANCE_PEP, reponse.resultat());
		assertEquals(Suite.ANNULER, reponse.suite());
	}

	@Test
	void personneConformePoursuitLeParcours() {
		ControleReponse reponse = service.controler(personne("Test", "Conforme"));
		assertEquals(ResultatControle.AUCUNE_CORRESPONDANCE, reponse.resultat());
		assertEquals(Suite.POURSUIVRE, reponse.suite());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statutDossier());
		assertNull(reponse.message());
	}

	@ParameterizedTest
	@CsvSource({ "Test, Sanctionnée", "Tes, Sanctionné", "Test Marie, PEP", "Test, PEPS" })
	void nomProcheSansEtreIdentiqueNeCorrespondPas(String prenoms, String nom) {
		assertEquals(ResultatControle.AUCUNE_CORRESPONDANCE, service.controler(personne(prenoms, nom)).resultat());
	}

	@Test
	void ignoreMajusculesAccentsEtEspaces() {
		assertEquals(ResultatControle.CORRESPONDANCE_SANCTIONS,
				service.controler(personne("  TEST ", " sanctionne ")).resultat());
	}

	@Test
	void modeIndisponibleNeRepondPasPuisEchoue() {
		long debut = System.nanoTime();
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class,
				() -> service.controler(personne("Test", "Indisponible")));
		assertTrue(Duration.ofNanos(System.nanoTime() - debut).toMillis() >= 50);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, erreur.getStatusCode());
		assertEquals(Messages.VERIFICATION_IMPOSSIBLE, erreur.getReason());
	}

	@Test
	void modeIndisponibleAttendPlusDeCinqSecondesParDefaut() throws Exception {
		var valeur = ControleSanctionsService.class.getConstructor(Duration.class).getParameters()[0]
				.getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
		String parDefaut = valeur.substring(valeur.indexOf(':') + 1, valeur.indexOf('}'));
		assertTrue(org.springframework.boot.convert.DurationStyle.detectAndParse(parDefaut)
				.compareTo(Duration.ofSeconds(5)) > 0);
	}

	@Test
	void modeIndisponibleRespecteUneInterruption() {
		Thread.currentThread().interrupt();
		try {
			assertThrows(ResponseStatusException.class, () -> service.controler(personne("Test", "Indisponible")));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@ParameterizedTest
	@CsvSource({ "CONFORME, AUCUNE_CORRESPONDANCE", "SANCTIONNE, CORRESPONDANCE_SANCTIONS",
			"PEP, CORRESPONDANCE_PEP" })
	void casDeTestRemplaceLaRechercheParNom(CasDeTest cas, ResultatControle attendu) {
		ControleRequete requete = new ControleRequete("Sanctionné", "Test", LocalDate.of(1980, 1, 1), "FRANCE", cas);
		assertEquals(attendu, service.controler(requete).resultat());
	}

	@Test
	void casDeTestIndisponibleNeRepondPasPuisEchoue() {
		ControleRequete requete = new ControleRequete("Dupont", "Marie", LocalDate.of(1980, 1, 1), "FRANCE",
				CasDeTest.INDISPONIBLE);
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class, () -> service.controler(requete));
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, erreur.getStatusCode());
	}

	@Test
	void nomEtPrenomsObligatoires() {
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
				() -> service.controler(personne("Test", " "))).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
				() -> service.controler(personne(null, "PEP"))).getStatusCode());
	}

}
