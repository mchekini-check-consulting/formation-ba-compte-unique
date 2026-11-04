package com.example.compteunique.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;

class DossierControllerTest {

	private final DossierService dossiers = mock(DossierService.class);
	private final DossierController controller = new DossierController(dossiers);
	private final DossierReponse reponse = new DossierReponse("ref", StatutDossier.EN_COURS_DE_VERIFICATION, false,
			true, null, null, null);

	@Test
	void renseignerIdentiteDelegueAuService() {
		IdentiteRequete identite = ExempleIdentite.identite();
		when(dossiers.renseignerIdentite("ref", identite)).thenReturn(reponse);
		assertEquals(reponse, controller.renseignerIdentite("ref", identite));
	}

	@Test
	void enregistrerControlesDelegueAuService() {
		ControlesRequete controles = new ControlesRequete(ResultatControle.OK, ResultatControle.OK);
		when(dossiers.enregistrerControles("ref", controles)).thenReturn(reponse);
		assertEquals(reponse, controller.enregistrerControles("ref", controles));
	}

}
