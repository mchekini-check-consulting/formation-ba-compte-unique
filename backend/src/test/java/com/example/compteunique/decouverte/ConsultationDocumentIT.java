package com.example.compteunique.decouverte;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.decouverte.DocumentsProduits.Document;
import com.example.compteunique.decouverte.DocumentsProduits.Produit;

// CU-311 / CU-331 contre un vrai PostgreSQL : la migration V34 crée le schéma decouverte, l'entité lui correspond
// (ddl-auto: validate), chaque ouverture est une ligne et la case cochée complète la dernière.
@SpringBootTest
@Testcontainers
class ConsultationDocumentIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	@Autowired
	private ConsultationDocumentRepository consultations;

	@Test
	void lesOuverturesEtLaCaseCocheeSontConserveesEnBase() {
		UUID cliente = UUID.randomUUID();
		OffsetDateTime le = OffsetDateTime.parse("2026-11-26T15:30:00+01:00");
		consultations.save(new ConsultationDocument(cliente, Produit.PER, Document.DIC, "DIC-PER-2026-11", le));
		ConsultationDocument derniere = consultations
				.save(new ConsultationDocument(cliente, Produit.PER, Document.DIC, "DIC-PER-2026-11", le.plusMinutes(1)));

		assertThat(consultations.existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(cliente,
				Produit.PER, Document.DIC, "DIC-PER-2026-11")).isFalse();

		derniere.cocher(le.plusMinutes(2));
		consultations.save(derniere);

		assertThat(consultations.findFirstByCustomerIdAndProduitAndDocumentAndVersionDocumentOrderByIdDesc(cliente,
				Produit.PER, Document.DIC, "DIC-PER-2026-11")).get().satisfies(c -> {
					assertThat(c.getOuvertLe().toInstant()).isEqualTo(le.plusMinutes(1).toInstant());
					assertThat(c.getCocheLe().toInstant()).isEqualTo(le.plusMinutes(2).toInstant());
				});
		assertThat(consultations.existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(cliente,
				Produit.PER, Document.DIC, "DIC-PER-2026-11")).isTrue();
		// Une autre version du document n'est pas lue.
		assertThat(consultations.existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(cliente,
				Produit.PER, Document.DIC, "DIC-PER-2027-01")).isFalse();
	}

}
