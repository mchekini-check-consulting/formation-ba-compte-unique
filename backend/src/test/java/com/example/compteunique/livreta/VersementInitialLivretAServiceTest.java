package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;

// CU-33 (US-24) : un cas de test par critère d'acceptation (cas de test de l'US).
class VersementInitialLivretAServiceTest {

	/** Jeudi 26 novembre 2026, 10 h. */
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 26, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();

	private final CompteService comptes = mock(CompteService.class);
	private final DemandeLivretARepository demandes = mock(DemandeLivretARepository.class);
	private final Compte compte = mock(Compte.class);
	private final VersementInitialLivretAService service = new VersementInitialLivretAService(comptes, demandes,
			Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));
	private DemandeLivretA demande;

	@BeforeEach
	void ouvertureValidee() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(compte.getId()).thenReturn(42L);
		when(compte.getSoldeCentimes()).thenReturn(5_000_000L);
		demande = new DemandeLivretA(7L, CLIENTE, true, MAINTENANT.minusDays(1));
		demande.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, MAINTENANT.minusDays(1));
		when(demandes.findByReferenceAndCustomerId(demande.getReference(), CLIENTE)).thenReturn(Optional.of(demande));
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private void refuse(String montant, String message) {
		assertRefus(HttpStatus.BAD_REQUEST, message, () -> service.choisir(JETON, demande.getReference(), montant));
		assertNull(demande.getVersementInitialCentimes());
	}

	// Montant accepté : enregistré dans la demande avec le compte courant à débiter et la date de saisie.
	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { "10;1000", "22950;2295000", "150,50;15050", "150.5;15050", "1 240,50;124050" })
	void montantAccepteEnregistreSansDebit(String saisi, long centimes) {
		assertSame(demande, service.choisir(JETON, demande.getReference(), saisi));
		assertEquals(centimes, demande.getVersementInitialCentimes());
		assertEquals(42L, demande.getCompteDebitId());
		assertEquals(MAINTENANT, demande.getVersementSaisiLe());
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, demande.getStatut());
	}

	// Aucun montant ou montant inférieur à 10 € : « Le versement minimum à l'ouverture est de 10 € ».
	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "9,99", "0", "0,01" })
	void sousLeMinimumRefuse(String saisi) {
		refuse(saisi, VersementInitialLivretAService.MINIMUM);
	}

	@Test
	void aucunMontantRefuse() {
		refuse(null, VersementInitialLivretAService.MINIMUM);
	}

	// Montant supérieur au plafond : « Le plafond du Livret A est de 22 950 € ».
	@ParameterizedTest
	@ValueSource(strings = { "22950,01", "30000", "999999999999999999999" })
	void auDessusDuPlafondRefuse(String saisi) {
		refuse(saisi, VersementInitialLivretAService.PLAFOND);
	}

	// Montant négatif, non numérique ou avec plus de deux décimales : « Montant invalide… ».
	@ParameterizedTest
	@ValueSource(strings = { "-50", "abc", "150,505", "12,", ",50", "1e3", "150 €" })
	void montantInvalideRefuse(String saisi) {
		refuse(saisi, VersementInitialLivretAService.MONTANT_INVALIDE);
	}

	// Solde du compte courant inférieur au montant saisi : « Solde de votre compte courant insuffisant ».
	@Test
	void soldeInsuffisantRefuse() {
		when(compte.getSoldeCentimes()).thenReturn(14_999L);
		refuse("150", VersementInitialLivretAService.SOLDE_INSUFFISANT);
		assertSame(demande, service.choisir(JETON, demande.getReference(), "149,99"));
	}

	// Avant la signature, un nouveau montant remplace le précédent.
	@Test
	void nouveauMontantRemplaceLePrecedent() {
		service.choisir(JETON, demande.getReference(), "150");
		service.choisir(JETON, demande.getReference(), "200,25");
		assertEquals(20_025L, demande.getVersementInitialCentimes());
	}

	// Prérequis : ouverture validée par l'administration fiscale.
	@Test
	void versementAvantLOuvertureValideeRefuse() {
		DemandeLivretA enCours = new DemandeLivretA(7L, CLIENTE, true, MAINTENANT);
		when(demandes.findByReferenceAndCustomerId(enCours.getReference(), CLIENTE)).thenReturn(Optional.of(enCours));
		assertRefus(HttpStatus.CONFLICT, VersementInitialLivretAService.HORS_ETAPE,
				() -> service.choisir(JETON, enCours.getReference(), "150"));
		assertNull(enCours.getVersementInitialCentimes());
	}

	@Test
	void demandeIntrouvable() {
		assertRefus(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE,
				() -> service.choisir(JETON, "inconnue", "150"));
	}

	// Message de la demande : montant enregistré, débité seulement à la signature.
	@Test
	void messageDeLaDemandeAvecLeVersement() {
		service.choisir(JETON, demande.getReference(), "1 240,50");
		DemandeLivretAVue vue = DemandeLivretAVue.de(new LivretAService.Suivi(demande, null));
		assertEquals(124_050L, vue.versementInitialCentimes());
		assertEquals(MAINTENANT, vue.versementSaisiLe());
		assertEquals(DemandeLivretAVue.VERSEMENT_ENREGISTRE.formatted("1 240,50 €"), vue.message());
		assertEquals("10,00 €", DemandeLivretAVue.euros(1000));
		assertEquals("22 950,00 €", DemandeLivretAVue.euros(2_295_000));
	}

	@Test
	void constructeurSpringUtiliseLHorlogeSysteme() {
		VersementInitialLivretAService spring = new VersementInitialLivretAService(comptes, demandes);
		assertSame(demande, spring.choisir(JETON, demande.getReference(), "10"));
	}

}
