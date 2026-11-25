package com.example.compteunique.livreta;

import java.time.OffsetDateTime;

import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;

/** CU-32 : réponses au questionnaire, telles que la cliente les a données pour cette ouverture. */
public record QuestionnaireVue(String reference, ObjetEpargne objetEpargne, OrigineFonds origineFonds,
		String precisionOrigine, OffsetDateTime reponduLe) {

	static QuestionnaireVue de(QuestionnaireLivretA questionnaire) {
		return questionnaire == null ? null
				: new QuestionnaireVue(questionnaire.getReference(), questionnaire.getObjetEpargne(),
						questionnaire.getOrigineFonds(), questionnaire.getPrecisionOrigine(),
						questionnaire.getReponduLe());
	}

}
