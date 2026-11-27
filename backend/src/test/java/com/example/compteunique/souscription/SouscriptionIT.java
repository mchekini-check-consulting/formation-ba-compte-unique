package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.catalogue.CatalogueService.Produit;

// CU-312 contre un vrai PostgreSQL : la migration V38 crée la table souscription (l'entité lui correspond,
// ddl-auto: validate) et autorise le mouvement « versement épargne » du compte courant.
@SpringBootTest
@Testcontainers
class SouscriptionIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private SouscriptionRepository souscriptions;

	@Autowired
	private JdbcTemplate base;

	@Test
	void souscriptionEnregistreeEtRelue() {
		UUID cliente = UUID.randomUUID();
		OffsetDateTime le = OffsetDateTime.now();
		Souscription souscription = new Souscription(cliente, Produit.PER, le);
		souscription.versement(new BigDecimal("500.00"), "AUTRE", "Prime");
		souscription.codeEnvoye("sel$empreinte", le);
		souscription.bloquer(le, SouscriptionPer.BLOCAGE);
		souscription.signer("PER-1A2B3C4D", le);
		souscriptions.saveAndFlush(souscription);

		assertThat(souscriptions.findByCustomerIdAndProduit(cliente, Produit.PER)).get().satisfies(s -> {
			assertThat(s.estSignee()).isTrue();
			assertThat(s.getMontant()).isEqualByComparingTo("500.00");
			assertThat(s.getOriginePrecision()).isEqualTo("Prime");
			assertThat(s.getCompartiment()).isEqualTo((short) 1);
			assertThat(s.getCodesFaux()).isEqualTo(1);
			assertThat(s.getNumeroContrat()).isEqualTo("PER-1A2B3C4D");
		});
		assertThat(base.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
				+ "where conname = 'mouvement_contrepartie'", String.class)).contains("VERSEMENT_EPARGNE");
	}

}
