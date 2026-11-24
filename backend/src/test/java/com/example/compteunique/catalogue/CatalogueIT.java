package com.example.compteunique.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.JournalBatch.Declenchement;
import com.example.compteunique.catalogue.JournalBatch.Statut;
import com.example.compteunique.datemetier.DateMetier;

// CU-381 contre un vrai PostgreSQL : la migration V27 crée le schéma catalogue, les entités lui correspondent
// (ddl-auto: validate), le verrou du batch fonctionne, et les critères 1 à 3 tiennent avec les vrais fichiers.
// Batch automatique désactivé pendant le test (vérification toutes les 1 000 000 s).
@SpringBootTest(properties = { "app.test.date-metier-modifiable=true", "app.catalogue.verification-ms=1000000000" })
@Testcontainers
class CatalogueIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ChargementCatalogue chargement;

	@Autowired
	private CatalogueService catalogue;

	@Autowired
	private SupportRepository supports;

	@Autowired
	private HistoriqueVlRepository historique;

	@Autowired
	private DateMetier dateMetier;

	@Test
	void leFichierEstChargeRejoueEtSesAnomaliesRejetees() {
		dateMetier.changer(LocalDateTime.of(2026, 11, 23, 2, 0), "Test CU-381");
		chargement.deposer("catalogue_uc_2026-11-23.csv", FichiersCatalogue.principal());

		JournalBatch premier = chargement.lancer(Declenchement.AUTOMATIQUE);
		assertThat(premier.getStatut()).isEqualTo(Statut.TERMINE);
		assertThat(premier.getAjoutes()).isEqualTo(102);
		assertThat(supports.count()).isEqualTo(102);
		assertThat(historique.count()).isEqualTo(102);
		assertThat(catalogue.catalogueMisAJour(LocalDate.of(2026, 11, 23))).isTrue();
		assertThat(catalogue.supportsProposes(Produit.PER)).isNotEmpty().noneMatch(CatalogueService.SupportUc::retire);

		JournalBatch rejoue = chargement.lancer(Declenchement.MANUEL);
		assertThat(rejoue.getAjoutes()).isZero();
		assertThat(rejoue.getModifies()).isZero();
		assertThat(supports.count()).isEqualTo(102);
		assertThat(historique.count()).isEqualTo(102);

		chargement.deposer("catalogue_uc_2026-11-23.csv", FichiersCatalogue.avecAnomalies());
		JournalBatch anomalies = chargement.lancer(Declenchement.MANUEL);
		assertThat(anomalies.getRejetees()).isEqualTo(7);
		assertThat(chargement.journal().getFirst().getLignesRejetees()).hasSize(7);
		assertThat(chargement.lancer(Declenchement.AUTOMATIQUE)).isNull();

		dateMetier.revenirALaDateReelle("Test CU-381");
	}

}
