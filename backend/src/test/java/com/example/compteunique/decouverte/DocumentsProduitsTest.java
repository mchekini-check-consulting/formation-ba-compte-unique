package com.example.compteunique.decouverte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.decouverte.DecouverteController.Demande;
import com.example.compteunique.decouverte.DocumentsProduits.Document;
import com.example.compteunique.decouverte.DocumentsProduits.DocumentNonOuvert;
import com.example.compteunique.decouverte.DocumentsProduits.Etat;
import com.example.compteunique.decouverte.DocumentsProduits.EtatDocument;
import com.example.compteunique.decouverte.DocumentsProduits.Produit;

// CU-311 (PER-02) et CU-331 (AV-02) : critères d'acceptation des documents à lire avant de souscrire.
class DocumentsProduitsTest {

	private static final ZonedDateTime LE = ZonedDateTime.of(2026, 11, 26, 15, 30, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();

	private final CompteService comptes = mock(CompteService.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final ConsultationDocumentRepository consultations = mock(ConsultationDocumentRepository.class);
	private final DocumentsProduits service = new DocumentsProduits(comptes, dateMetier, consultations);
	private final DecouverteController controller = new DecouverteController(service);
	private final List<ConsultationDocument> base = new ArrayList<>();

	@BeforeEach
	void baseEnMemoire() {
		Compte compte = mock(Compte.class);
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(comptes.compteCourant("jeton")).thenReturn(compte);
		when(dateMetier.maintenant()).thenReturn(LE);
		when(consultations.save(any())).thenAnswer(appel -> {
			ConsultationDocument c = appel.getArgument(0);
			ReflectionTestUtils.setField(c, "id", (long) base.size() + 1);
			base.add(c);
			return c;
		});
		when(consultations.findFirstByCustomerIdAndProduitAndDocumentAndVersionDocumentOrderByIdDesc(any(), any(),
				any(), any())).thenAnswer(appel -> lignes(appel.getArguments()).reduce((a, b) -> b));
		when(consultations.existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(any(), any(),
				any(), any())).thenAnswer(appel -> lignes(appel.getArguments()).anyMatch(c -> c.getCocheLe() != null));
	}

	private Stream<ConsultationDocument> lignes(Object[] critere) {
		return base.stream()
				.filter(c -> CLIENTE.equals(critere[0])
						&& ReflectionTestUtils.getField(c, "produit") == critere[1]
						&& ReflectionTestUtils.getField(c, "document") == critere[2]
						&& c.getVersionDocument().equals(critere[3]));
	}

	private Etat ouvrir(Produit produit, Document document) {
		return controller.ouvrir("jeton", new Demande(produit, document)).getBody();
	}

	private Etat cocher(Produit produit, Document document) {
		return controller.cocher("jeton", new Demande(produit, document)).getBody();
	}

	private static EtatDocument document(Etat etat, Document code) {
		return etat.documents().stream().filter(d -> d.code() == code).findFirst().orElseThrow();
	}

	@Test
	void uneClienteSansContratPeutConsulterLeDicEtLaFicheProduitDesDeuxProduits() {
		for (Produit produit : Produit.values()) {
			Etat etat = controller.etat("jeton", produit);

			assertThat(etat.documents()).extracting(EtatDocument::code)
					.containsExactly(Document.DIC, Document.FICHE_PRODUIT);
			assertThat(etat.documents()).allSatisfy(d -> {
				assertThat(d.titre()).isNotBlank();
				assertThat(d.contenu()).isNotBlank();
				assertThat(d.ouvert()).isFalse();
				assertThat(d.lu()).isFalse();
			});
			assertThat(etat.peutContinuer()).isFalse();
		}
	}

	@Test
	void chaqueOuvertureEstEnregistreeAvecSaDateSonHeureEtLaVersionDuDocument() {
		ouvrir(Produit.PER, Document.DIC);
		Etat etat = ouvrir(Produit.PER, Document.DIC);

		assertThat(base).hasSize(2).allSatisfy(c -> {
			assertThat(c.getOuvertLe()).isEqualTo(LE.toOffsetDateTime());
			assertThat(c.getVersionDocument()).isEqualTo("DIC-PER-2026-11");
			assertThat(c.getCocheLe()).isNull();
		});
		assertThat(document(etat, Document.DIC).ouvert()).isTrue();
		assertThat(document(etat, Document.FICHE_PRODUIT).ouvert()).isFalse();
	}

	@Test
	void laCaseNePeutPasEtreCocheeAvantLOuvertureDuDocument() {
		assertThatThrownBy(() -> cocher(Produit.AV, Document.DIC)).isInstanceOf(DocumentNonOuvert.class);
		assertThat(base).isEmpty();

		var refus = controller.nonOuvert();
		assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(refus.getBody().message()).isEqualTo(DecouverteController.OUVRIR_D_ABORD);
	}

	@Test
	void continuerResteDesactiveTantQueLesDeuxCasesNeSontPasCochees() {
		ouvrir(Produit.AV, Document.DIC);
		ouvrir(Produit.AV, Document.FICHE_PRODUIT);
		Etat etat = cocher(Produit.AV, Document.DIC);

		assertThat(document(etat, Document.DIC).lu()).isTrue();
		assertThat(document(etat, Document.FICHE_PRODUIT).lu()).isFalse();
		assertThat(etat.peutContinuer()).isFalse();
	}

	@Test
	void continuerEstActifQuandLesDeuxDocumentsSontOuvertsEtCoches() {
		ouvrir(Produit.PER, Document.DIC);
		ouvrir(Produit.PER, Document.FICHE_PRODUIT);
		cocher(Produit.PER, Document.DIC);
		Etat etat = cocher(Produit.PER, Document.FICHE_PRODUIT);

		assertThat(etat.peutContinuer()).isTrue();
		assertThat(service.peutContinuer(CLIENTE, Produit.PER)).isTrue();
		assertThat(base).allSatisfy(c -> assertThat(c.getCocheLe()).isEqualTo(LE.toOffsetDateTime()));
		// Les documents lus pour le PER ne valent pas pour l'assurance vie.
		assertThat(service.peutContinuer(CLIENTE, Produit.AV)).isFalse();
	}

	@Test
	void uneCaseCocheeResteValableSiLaClienteRouvreLeDocument() {
		ouvrir(Produit.PER, Document.DIC);
		cocher(Produit.PER, Document.DIC);
		Etat etat = ouvrir(Produit.PER, Document.DIC);

		assertThat(document(etat, Document.DIC).lu()).isTrue();
		assertThat(base).hasSize(2);
		assertThat(base.get(1).getCocheLe()).isNull();
	}

	@Test
	void uneDemandeIncompleteEstRefusee() {
		assertThat(controller.ouvrir("jeton", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(controller.cocher("jeton", new Demande(Produit.PER, null)).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(base).isEmpty();
	}

	@Test
	void sansSessionOuSansCompteLaDemandeEstRefusee() {
		when(comptes.compteCourant("inconnu")).thenThrow(new ClienteNonConnecteeException());
		assertThatThrownBy(() -> controller.etat("inconnu", Produit.PER))
				.isInstanceOf(ClienteNonConnecteeException.class);
		assertThat(controller.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(controller.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
