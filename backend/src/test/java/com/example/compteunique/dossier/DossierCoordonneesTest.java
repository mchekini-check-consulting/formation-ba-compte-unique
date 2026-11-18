package com.example.compteunique.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Dossier.Coordonnees;

// CU-28 : coordonnées corrigées à l'étape identité de l'ouverture d'un Livret A, enregistrées dans le dossier.
class DossierCoordonneesTest {

	private static final OffsetDateTime PLUS_TARD = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

	private static Coordonnees de(Dossier dossier) {
		return new Coordonnees(dossier.getVoie(), dossier.getComplement(), dossier.getCodePostal(), dossier.getVille(),
				dossier.getPaysAdresse(), dossier.getEmail(), dossier.getMobile());
	}

	@Test
	void coordonneesModifieesEnregistreesAvecLaDate() {
		Dossier dossier = ExempleDossier.pretASigner("ref-coord", ResultatControle.OK);
		String nom = dossier.getNomNaissance();
		Coordonnees nouvelles = new Coordonnees("3 avenue Foch", "Bâtiment B", "69006", "Lyon", "France",
				"nouvelle@exemple.fr", "07 00 00 00 01");
		assertTrue(dossier.corrigerCoordonnees(nouvelles, PLUS_TARD));
		assertEquals(nouvelles, de(dossier));
		assertEquals(PLUS_TARD, dossier.getDerniereActionLe());
		assertEquals(nom, dossier.getNomNaissance());
	}

	@Test
	void coordonneesIdentiquesRienNeChange() {
		Dossier dossier = ExempleDossier.pretASigner("ref-coord-2", ResultatControle.OK);
		OffsetDateTime derniereAction = dossier.getDerniereActionLe();
		assertFalse(dossier.corrigerCoordonnees(de(dossier), PLUS_TARD));
		assertEquals(derniereAction, dossier.getDerniereActionLe());
	}

}
