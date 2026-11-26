package com.example.compteunique.livreta;

import com.example.compteunique.livreta.QuestionsLivretAController.Reponses;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;

// CU-32 : questionnaire rempli et texte d'information accepté, préalable à l'éligibilité dans les tests
// d'intégration du Livret A.
final class QuestionnaireExemple {

	private QuestionnaireExemple() {
	}

	static QuestionnaireVue rempli(QuestionsLivretAController questions, String jeton) {
		return questions.repondre(jeton,
				new Reponses(ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, null, true)).getBody();
	}

}
