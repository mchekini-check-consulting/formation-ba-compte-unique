package com.example.bouchonsidentite.sanctions;

import com.example.bouchonsidentite.commun.Messages;
import com.example.bouchonsidentite.commun.StatutDossier;
import com.example.bouchonsidentite.commun.Suite;

public record ControleReponse(ResultatControle resultat, Suite suite, StatutDossier statutDossier, String message) {

	static ControleReponse pour(ResultatControle resultat) {
		if (resultat == ResultatControle.AUCUNE_CORRESPONDANCE) {
			return new ControleReponse(resultat, Suite.POURSUIVRE, StatutDossier.EN_COURS_DE_VERIFICATION, null);
		}
		return new ControleReponse(resultat, Suite.ANNULER, StatutDossier.ANNULE, Messages.OUVERTURE_ANNULEE);
	}

}
