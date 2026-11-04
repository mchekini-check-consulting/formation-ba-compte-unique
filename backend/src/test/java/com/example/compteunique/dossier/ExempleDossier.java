package com.example.compteunique.dossier;

import java.time.OffsetDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.dossier.Categories.ResultatControle;

// Dossiers de test (fictifs) aux différentes étapes du parcours.
public final class ExempleDossier {

	public static final long ID = 42L;

	private ExempleDossier() {
	}

	/** Identité enregistrée, contrôle sanctions OK, contrôle PEP au résultat donné. */
	public static Dossier pretASigner(String reference, ResultatControle pep) {
		Dossier dossier = new Dossier(reference);
		ReflectionTestUtils.setField(dossier, "id", ID);
		dossier.renseignerIdentite(ExempleIdentite.identite(), OffsetDateTime.now());
		dossier.enregistrerControles(ResultatControle.OK, pep, OffsetDateTime.now());
		return dossier;
	}

	public static Dossier conventionSignee(String reference) {
		Dossier dossier = pretASigner(reference, ResultatControle.OK);
		dossier.conventionSignee(OffsetDateTime.now());
		return dossier;
	}

}
