package com.example.compteunique.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.ordres.PositionsContrat;

// CU-314 / CU-338 contre un vrai PostgreSQL : la migration V36 crée le schéma gestion et ses paramètres, les entités
// lui correspondent (ddl-auto: validate), et l'encours par support se lit sur les positions de test.
@SpringBootTest
@Testcontainers
class GestionIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ModeDeGestion gestion;

	@Autowired
	private ParametresGestion parametres;

	@Autowired
	private ContratGestionRepository contrats;

	@Autowired
	private ChangementGestionRepository changements;

	@Autowired
	private ReequilibrageRepository reequilibrages;

	@Autowired
	private FraisMandatRepository frais;

	@Autowired
	private PositionsContrat positions;

	@Autowired
	private SignalementNonCoteRepository signalements;

	@Test
	void parametresInitiauxEtContratEnBase() {
		assertThat(gestion.mandat(Produit.PER).tauxToutComprisPct()).isEqualByComparingTo("1.5");
		assertThat(gestion.mandat(Produit.AV).montantMinimum()).isEqualByComparingTo("50000");
		assertThat(parametres.grille()).hasSize(12).allSatisfy(l -> assertThat(l.isAValider()).isTrue());
		assertThat(parametres.quotas()).extracting(q -> q.getPartMinNonCotePct().stripTrailingZeros().toPlainString())
				.containsExactly("0", "4", "8");

		UUID cliente = UUID.randomUUID();
		ContratGestion contrat = gestion.initialiser("PER-TEST-IT000001", cliente, Produit.PER, 2041, "DYNAMIQUE");
		assertThat(contrats.findByCustomerIdAndProduit(cliente, Produit.PER)).get().satisfies(c -> {
			assertThat(c.getMode()).isEqualTo(ModeGestion.PILOTEE);
			assertThat(c.getProfil()).isEqualTo(ProfilGestion.EQUILIBRE);
			assertThat(c.getAnneeDepartRetraite()).isEqualTo(2041);
		});
		assertThat(changements.findByNumeroContratOrderByIdDesc(contrat.getNumeroContrat())).singleElement()
				.satisfies(c -> assertThat(c.getFrais()).isEqualByComparingTo("0"));
		assertThat(gestion.contraintes(contrat).partMinNonCotePct()).isPositive();

		Reequilibrage reequilibrage = new Reequilibrage(contrat.getNumeroContrat(), OffsetDateTime.now());
		reequilibrage.ajouter(UUID.randomUUID(), "QZ0000000017", "QZ0000000025", new BigDecimal("12.34"));
		reequilibrages.save(reequilibrage);
		assertThat(reequilibrages.findByNumeroContratOrderByIdDesc(contrat.getNumeroContrat())).singleElement()
				.satisfies(r -> assertThat(r.getOrdres()).singleElement()
						.satisfies(o -> assertThat(o.getMontant()).isEqualByComparingTo("12.34")));
		frais.save(new FraisMandat(contrat.getNumeroContrat(), OffsetDateTime.now(), BigDecimal.TEN, BigDecimal.ONE,
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO));
		assertThat(frais.findTop50ByOrderByIdDesc()).hasSize(1);
		signalements.save(new SignalementNonCote(contrat.getNumeroContrat(), "QZ0000000033", new BigDecimal("1500.00"),
				6, ProfilGestion.PRUDENT, OffsetDateTime.now()));
		assertThat(gestion.signalements()).singleElement().satisfies(s -> {
			assertThat(s.getNiveauRisque()).isEqualTo(6);
			assertThat(s.getStatut()).isEqualTo("A_TRAITER");
		});

		positions.enregistrerPositionTest(contrat.getNumeroContrat(), "QZ0000000017", new BigDecimal("100.00"), null);
		positions.enregistrerPositionTest(contrat.getNumeroContrat(), "QZ0000000017", new BigDecimal("50.00"), null);
		assertThat(positions.encoursParSupport(contrat.getNumeroContrat())).containsEntry("QZ0000000017",
				new BigDecimal("150.00"));
	}

}
