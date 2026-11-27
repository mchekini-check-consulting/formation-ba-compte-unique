package com.example.compteunique.souscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.gestion.ModeDeGestion;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilKyc;
import com.example.compteunique.profil.Reponses;
import com.example.compteunique.souscription.SouscriptionService.Beneficiaire;
import com.example.compteunique.souscription.SouscriptionService.DemandeVersement;
import com.example.compteunique.souscription.SouscriptionService.Etape;

// CU-312 et CU-330 contre un vrai PostgreSQL : le service enregistre la souscription (CU-521 : montant et origine
// présents dès l'insertion), la clause bénéficiaire et l'âge de la titulaire ; migrations V38 et V39 conformes aux
// entités (ddl-auto: validate). Compte, profil, dossier, SMS et gestion sont simulés.
@SpringBootTest
@Testcontainers
class SouscriptionIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

	private static final String JETON = "jeton-it";

	@Autowired
	private SouscriptionService service;

	@Autowired
	private SouscriptionRepository souscriptions;

	@Autowired
	private JdbcTemplate base;

	@MockitoBean
	private CompteService comptes;

	@MockitoBean
	private CompteRepository comptesVerrouilles;

	@MockitoBean
	private MouvementRepository mouvements;

	@MockitoBean
	private DossierRepository dossiers;

	@MockitoBean
	private ProfilClientService profils;

	@MockitoBean
	private ModeDeGestion gestion;

	@MockitoBean
	private CodesSms codes;

	@Test
	void souscriptionsPerEtAssuranceVieEnregistreesEnBase() {
		UUID cliente = UUID.randomUUID();
		Compte compte = mock(Compte.class);
		when(compte.getCustomerId()).thenReturn(cliente);
		when(compte.getId()).thenReturn(1L);
		when(compte.getDossierId()).thenReturn(1L);
		when(compte.isActif()).thenReturn(true);
		when(compte.getSoldeCentimes()).thenReturn(500_000L);
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(comptesVerrouilles.verrouiller(1L)).thenReturn(Optional.of(compte));
		Dossier dossier = mock(Dossier.class);
		when(dossier.getDateNaissance()).thenReturn(LocalDate.of(1980, 1, 15));
		when(dossiers.findById(1L)).thenReturn(Optional.of(dossier));
		ProfilKyc kyc = mock(ProfilKyc.class);
		Reponses reponses = mock(Reponses.class);
		when(kyc.getReponses()).thenReturn(reponses);
		when(kyc.getProfilInvestisseur()).thenReturn("EQUILIBRE");
		when(profils.profilValide(cliente)).thenReturn(Optional.of(kyc));
		when(gestion.numeroContrat(any(), any())).thenReturn(Optional.empty());
		when(codes.envoyer(eq(compte), any())).thenAnswer(appel -> CodesSms.hacher("123456"));

		// CU-521 : la première saisie du PER (montant 500 €, salaire) est enregistrée sans erreur.
		assertThat(service.choisirVersement(JETON, Produit.PER,
				new DemandeVersement(new BigDecimal("500"), "REVENUS", null, null)).etape())
				.isEqualTo(Etape.SIGNATURE);
		assertThat(souscriptions.findByCustomerIdAndProduit(cliente, Produit.PER)).get()
				.satisfies(s -> assertThat(s.getCompartiment()).isEqualTo((short) 1));

		service.choisirVersement(JETON, Produit.AV, new DemandeVersement(new BigDecimal("1000"), "EPARGNE", null,
				List.of(new Beneficiaire("Martin", "Léa", LocalDate.of(2010, 5, 1), new BigDecimal("70")),
						new Beneficiaire("Martin", "Hugo", LocalDate.of(2012, 3, 9), new BigDecimal("30")))));
		service.envoyerCode(JETON, Produit.AV);
		var signe = service.signer(JETON, Produit.AV, "123456");

		assertThat(signe.etape()).isEqualTo(Etape.SIGNEE);
		assertThat(signe.beneficiaires()).extracting(Beneficiaire::prenom).containsExactly("Léa", "Hugo");
		assertThat(souscriptions.findByCustomerIdAndProduit(cliente, Produit.AV)).get().satisfies(s -> {
			assertThat(s.getCompartiment()).isNull();
			assertThat(s.getAgeTitulaire()).isNotNull().isPositive();
			assertThat(s.getNumeroContrat()).startsWith("AV-");
		});
		assertThat(base.queryForObject("select pg_get_constraintdef(oid) from pg_constraint "
				+ "where conname = 'mouvement_contrepartie'", String.class)).contains("VERSEMENT_EPARGNE");
	}

}
