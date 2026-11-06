package com.example.compteunique;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.ControlesRequete;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.dossier.DossierService;
import com.example.compteunique.dossier.ExempleIdentite;

// Démarrage complet, connecté à un PostgreSQL 16 jetable (migrations Flyway comprises).
// make test-integration ; Docker requis.
@SpringBootTest
@Testcontainers
class CompteUniqueApplicationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private DossierService dossierService;

	@Autowired
	private DossierRepository dossiers;

	// CUB-8 : l'entité Dossier correspond à la migration V3 (ddl-auto: validate) et se relit.
	@Test
	void dossierEnregistreEtRelu() {
		dossierService.renseignerIdentite("it-1", ExempleIdentite.identite());
		dossierService.enregistrerControles("it-1", new ControlesRequete(ResultatControle.OK, ResultatControle.NOK));
		assertThat(dossiers.findByReference("it-1")).hasValueSatisfying(dossier -> {
			assertThat(dossier.getNomNaissance()).isEqualTo("Dupont");
			assertThat(dossier.getSaisiLe()).isNotNull();
			assertThat(dossier.getControlePep()).isEqualTo(ResultatControle.NOK);
			assertThat(dossier.getControleLe()).isNotNull();
			assertThat(dossier.getStatut()).isEqualTo(StatutDossier.ANNULE);
		});
	}

	@Test
	void contextLoads() {
	}

}
