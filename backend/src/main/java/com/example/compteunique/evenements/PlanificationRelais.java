package com.example.compteunique.evenements;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// CU-382 : déclenche le relais toutes les 2 secondes (app.kafka.relais-ms). Classe séparée pour que l'appel passe par
// le relais géré par Spring, donc dans sa transaction (verrou et marquage « publié » enregistrés en base).
@Component
class PlanificationRelais {

	private final RelaisEvenements relais;

	PlanificationRelais(RelaisEvenements relais) {
		this.relais = relais;
	}

	@Scheduled(fixedDelayString = "${app.kafka.relais-ms:2000}", initialDelayString = "${app.kafka.relais-ms:2000}")
	void publier() {
		relais.publierEnAttente();
	}

}
