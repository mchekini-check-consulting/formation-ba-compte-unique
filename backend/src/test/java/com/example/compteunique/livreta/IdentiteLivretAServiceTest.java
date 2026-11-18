package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.Dossier.Coordonnees;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.IdentiteLivretAService.Identite;
import com.example.compteunique.livreta.IdentiteLivretAService.IdentiteAControler;
import com.example.compteunique.livreta.IdentiteLivretAService.Suivi;

// CU-28 (US-19) : un ou plusieurs cas de test par critère d'acceptation.
class IdentiteLivretAServiceTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 18, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final Coordonnees COORDONNEES = new Coordonnees("12 rue des Lilas", null, "75011", "Paris",
			"France", "cliente@exemple.fr", "06 12 34 56 78");

	private final CompteService comptes = mock(CompteService.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ControleIdentiteLivretARepository controles = mock(ControleIdentiteLivretARepository.class);
	private final Dossier dossier = mock(Dossier.class);
	private final Compte compte = new Compte(7L, CLIENTE, Iban.de("99000000001"), 3L, MAINTENANT.minusDays(30));
	private final IdentiteLivretAService service = new IdentiteLivretAService(comptes, dossiers, controles,
			Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));

	@BeforeEach
	void cliente() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(dossier.getId()).thenReturn(7L);
		when(dossier.getCivilite()).thenReturn("Mme");
		when(dossier.getNomNaissance()).thenReturn("Cliente-A");
		when(dossier.getPrenoms()).thenReturn("Test");
		when(dossier.getDateNaissance()).thenReturn(LocalDate.of(1990, 5, 14));
		when(dossier.getNationalite()).thenReturn(Nationalite.FRANCE);
		when(dossier.getVoie()).thenReturn("12 rue des Lilas");
		when(dossier.getCodePostal()).thenReturn("75011");
		when(dossier.getVille()).thenReturn("Paris");
		when(dossier.getPaysAdresse()).thenReturn("France");
		when(dossier.getEmail()).thenReturn("cliente@exemple.fr");
		when(dossier.getMobile()).thenReturn("06 12 34 56 78");
		when(dossier.isResidenceFrancaise()).thenReturn(true);
		when(dossiers.findById(7L)).thenReturn(Optional.of(dossier));
		when(controles.save(any(ControleIdentiteLivretA.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private ControleIdentiteLivretA controle(StatutControleIdentite statut) {
		ControleIdentiteLivretA controle = new ControleIdentiteLivretA(7L, CLIENTE, false, MAINTENANT);
		switch (statut) {
			case VALIDE -> controle.recevoirResultat(ResultatSanctions.AUCUNE_CORRESPONDANCE, 1, MAINTENANT);
			case BLOQUE -> controle.recevoirResultat(ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1, MAINTENANT);
			case ATTENTE_RESPONSABLE -> controle.recevoirResultat(ResultatSanctions.CORRESPONDANCE_PEP, 1, MAINTENANT);
			case EN_ATTENTE -> controle.recevoirResultat(ResultatSanctions.SANS_REPONSE, 4, MAINTENANT);
			case EN_COURS -> {
				// déjà en cours
			}
		}
		when(controles.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.of(controle));
		when(controles.findByReferenceAndCustomerId(controle.getReference(), CLIENTE)).thenReturn(Optional.of(controle));
		when(controles.findByReference(controle.getReference())).thenReturn(Optional.of(controle));
		return controle;
	}

	// Critère 1 : identité et coordonnées pré-remplies depuis le dossier client, avec la résidence fiscale.
	@Test
	void identitePreRemplieDepuisLeDossier() {
		Identite identite = service.identite(JETON);
		assertEquals("Cliente-A", identite.nom());
		assertEquals("Test", identite.prenoms());
		assertEquals(LocalDate.of(1990, 5, 14), identite.dateNaissance());
		assertEquals(COORDONNEES, identite.coordonnees());
		assertEquals("France", identite.residenceFiscale());
		when(dossier.isResidenceFrancaise()).thenReturn(false);
		assertEquals("Hors de France", service.identite(JETON).residenceFiscale());
	}

	// Critère 2 : seules les coordonnées sont reçues ; le dossier est mis à jour tout de suite (décision métier).
	@Test
	void coordonneesCorrigeesMettentAJourLeDossier() {
		Coordonnees corrigees = new Coordonnees(" 3 avenue Foch ", " ", "69006", "Lyon", "France",
				"nouvelle@exemple.fr", "07 00 00 00 01");
		Coordonnees nettoyees = new Coordonnees("3 avenue Foch", null, "69006", "Lyon", "France",
				"nouvelle@exemple.fr", "07 00 00 00 01");
		when(dossier.corrigerCoordonnees(nettoyees, MAINTENANT)).thenReturn(true);
		Suivi suivi = service.confirmer(JETON, corrigees, true);
		verify(dossier).corrigerCoordonnees(nettoyees, MAINTENANT);
		assertEquals(MAINTENANT, suivi.controle().getCoordonneesModifieesLe());
	}

	@Test
	void coordonneesInchangeesSansDateDeModification() {
		Suivi suivi = service.confirmer(JETON, COORDONNEES, true);
		assertNull(suivi.controle().getCoordonneesModifieesLe());
	}

	// Critère 3 : une coordonnée vidée bloque la validation.
	@Test
	void coordonneeVideeBloque() {
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.CHAMP_OBLIGATOIRE + "adresse.",
				() -> service.confirmer(JETON, new Coordonnees(" ", null, "75011", "Paris", "France", "a@b.fr",
						"0612345678"), true));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.CHAMP_OBLIGATOIRE + "e-mail.",
				() -> service.confirmer(JETON, new Coordonnees("1 rue", null, "75011", "Paris", "France", "",
						"0612345678"), true));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.CHAMP_OBLIGATOIRE + "téléphone.",
				() -> service.confirmer(JETON, new Coordonnees("1 rue", null, "75011", "Paris", "France", "a@b.fr",
						null), true));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.CHAMP_OBLIGATOIRE + "adresse.",
				() -> service.confirmer(JETON, null, true));
		verify(controles, never()).save(any());
	}

	// Critère 4 : une adresse e-mail sans « @ » est refusée.
	@ParameterizedTest
	@ValueSource(strings = { "cliente.exemple.fr", "cliente@", "@exemple.fr", "cli ente@exemple.fr", "a@b@c.fr" })
	void emailSansArobaseRefuse(String email) {
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.EMAIL_INVALIDE, () -> service.confirmer(JETON,
				new Coordonnees("1 rue", null, "75011", "Paris", "France", email, "0612345678"), true));
	}

	@Test
	void codePostalEtMobileControles() {
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.CODE_POSTAL_INVALIDE, () -> service.confirmer(JETON,
				new Coordonnees("1 rue", null, "750", "Paris", "France", "a@b.fr", "0612345678"), true));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.MOBILE_INVALIDE, () -> service.confirmer(JETON,
				new Coordonnees("1 rue", null, "75011", "Paris", "France", "a@b.fr", "0112345678"), true));
		// Hors de France, le code postal n'a pas de format imposé.
		service.confirmer(JETON, new Coordonnees("1 Hauptstraße", null, "D-10115", "Berlin", "Allemagne", "a@b.fr",
				"+33 6 12 34 56 78"), true);
	}

	// Critère 5 : résidence fiscale non confirmée, la cliente ne peut pas continuer.
	@Test
	void residenceFiscaleNonConfirmeeBloque() {
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.RESIDENCE_OBLIGATOIRE,
				() -> service.confirmer(JETON, COORDONNEES, false));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.RESIDENCE_OBLIGATOIRE,
				() -> service.confirmer(JETON, COORDONNEES, null));
		verify(controles, never()).save(any());
	}

	// Critère 6 : identité confirmée, le contrôle sanctions/PPE est à envoyer avec l'identité du dossier.
	@Test
	void identiteConfirmeeEnvoieLeControle() {
		Suivi suivi = service.confirmer(JETON, COORDONNEES, true);
		ControleIdentiteLivretA controle = suivi.controle();
		assertEquals(StatutControleIdentite.EN_COURS, controle.getStatut());
		assertEquals(MAINTENANT, controle.getResidenceFiscaleConfirmeeLe());
		assertEquals(MAINTENANT, controle.getCreeLe());
		assertEquals(7L, controle.getDossierId());
		assertEquals(CLIENTE, controle.getCustomerId());
		assertEquals(new IdentiteAControler("Cliente-A", "Test", LocalDate.of(1990, 5, 14), "FRANCE"),
				suivi.aControler());
	}

	@Test
	void aucuneCorrespondanceValideLIdentite() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		Suivi suivi = service.enregistrerResultat(JETON, controle.getReference(),
				ResultatSanctions.AUCUNE_CORRESPONDANCE, 1);
		assertEquals(StatutControleIdentite.VALIDE, suivi.controle().getStatut());
		assertEquals(1, controle.getTentatives());
		assertEquals(MAINTENANT, controle.getResultatLe());
		assertNull(suivi.aControler());
		assertSame(controle, service.controleValide(compte));
	}

	// Critère 7 : sans réponse après 3 relances, démarche en attente et conseiller alerté ; aucune ouverture.
	@Test
	void sansReponseMetEnAttenteEtAlerteLeConseiller() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		service.enregistrerResultat(JETON, controle.getReference(), ResultatSanctions.SANS_REPONSE, 4);
		assertEquals(StatutControleIdentite.EN_ATTENTE, controle.getStatut());
		assertEquals(4, controle.getTentatives());
		assertEquals(MAINTENANT, controle.getConseillerAlerteLe());
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.controleValide(compte));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 5, -1 })
	void nombreDEssaisLimiteA4(int essais) {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.TENTATIVES_INVALIDES, () -> service
				.enregistrerResultat(JETON, controle.getReference(), ResultatSanctions.SANS_REPONSE, essais));
	}

	@Test
	void resultatEtEssaisObligatoires() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.RESULTAT_OBLIGATOIRE,
				() -> service.enregistrerResultat(JETON, controle.getReference(), null, 1));
		assertRefus(HttpStatus.BAD_REQUEST, IdentiteLivretAService.TENTATIVES_INVALIDES, () -> service
				.enregistrerResultat(JETON, controle.getReference(), ResultatSanctions.AUCUNE_CORRESPONDANCE, null));
	}

	// Critère 8 : gel des avoirs, démarche bloquée et conformité alertée, message sans la raison.
	@Test
	void gelDesAvoirsBloqueEtAlerteLaConformite() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		Suivi suivi = service.enregistrerResultat(JETON, controle.getReference(),
				ResultatSanctions.CORRESPONDANCE_SANCTIONS, 2);
		assertEquals(StatutControleIdentite.BLOQUE, controle.getStatut());
		assertEquals(MAINTENANT, controle.getAlerteConformiteLe());
		assertNull(controle.getConseillerAlerteLe());
		String message = ControleIdentiteVue.de(suivi).message();
		assertThat(message.toLowerCase()).doesNotContain("gel", "sanction", "registre", "avoirs");
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.controleValide(compte));
	}

	// Critère 9 : PPE, la démarche continue après validation par un responsable.
	@Test
	void ppeContinueApresValidationDuResponsable() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.EN_COURS);
		service.enregistrerResultat(JETON, controle.getReference(), ResultatSanctions.CORRESPONDANCE_PEP, 1);
		assertEquals(StatutControleIdentite.ATTENTE_RESPONSABLE, controle.getStatut());
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.controleValide(compte));
		service.validerParResponsable(controle.getReference());
		assertEquals(StatutControleIdentite.VALIDE, controle.getStatut());
		assertEquals(MAINTENANT, controle.getResponsableValideLe());
		assertSame(controle, service.controleValide(compte));
	}

	@Test
	void seulUnePPEEnAttentePeutEtreValidee() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.VALIDE);
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.PAS_EN_ATTENTE_RESPONSABLE,
				() -> service.validerParResponsable(controle.getReference()));
		assertRefus(HttpStatus.NOT_FOUND, IdentiteLivretAService.CONTROLE_INTROUVABLE,
				() -> service.validerParResponsable("inconnu"));
	}

	// Démarche bloquée, en attente d'un responsable ou d'un conseiller : pas de nouvelle confirmation en ligne.
	@ParameterizedTest
	@EnumSource(value = StatutControleIdentite.class, names = { "BLOQUE", "ATTENTE_RESPONSABLE", "EN_ATTENTE" })
	void demarcheSuspendueNePeutPasEtreRelancee(StatutControleIdentite statut) {
		controle(statut);
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.DEMARCHE_SUSPENDUE,
				() -> service.confirmer(JETON, COORDONNEES, true));
	}

	// Un contrôle validé (ou déjà utilisé) n'empêche pas de reconfirmer son identité pour une nouvelle ouverture.
	@ParameterizedTest
	@EnumSource(value = StatutControleIdentite.class, names = { "EN_COURS", "VALIDE" })
	void nouvelleConfirmationPossible(StatutControleIdentite statut) {
		controle(statut);
		assertEquals(StatutControleIdentite.EN_COURS, service.confirmer(JETON, COORDONNEES, true).controle().getStatut());
	}

	@Test
	void unResultatNEstRecuQuUneFois() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.VALIDE);
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.CONTROLE_TERMINE, () -> service
				.enregistrerResultat(JETON, controle.getReference(), ResultatSanctions.CORRESPONDANCE_SANCTIONS, 1));
	}

	@Test
	void controleDUneAutreClienteIntrouvable() {
		assertRefus(HttpStatus.NOT_FOUND, IdentiteLivretAService.CONTROLE_INTROUVABLE, () -> service
				.enregistrerResultat(JETON, "autre", ResultatSanctions.AUCUNE_CORRESPONDANCE, 1));
	}

	// Un contrôle ne sert qu'à une ouverture.
	@Test
	void controleDejaUtiliseNePermetPasUneAutreDemande() {
		ControleIdentiteLivretA controle = controle(StatutControleIdentite.VALIDE);
		assertTrue(controle.permetUneDemande());
		controle.utiliserPour("demande-1");
		assertEquals("demande-1", controle.getDemandeReference());
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.controleValide(compte));
	}

	@Test
	void sansControleAucuneDemande() {
		when(controles.findFirstByCustomerIdOrderByCreeLeDesc(CLIENTE)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.controleValide(compte));
		assertTrue(service.dernierControle(JETON).isEmpty());
	}

	@Test
	void dernierControleEnCoursRenvoieLIdentiteAControler() {
		controle(StatutControleIdentite.EN_COURS);
		Suivi suivi = service.dernierControle(JETON).orElseThrow();
		assertEquals("Cliente-A", suivi.aControler().nomNaissance());
	}

	@Test
	void dossierIntrouvable() {
		when(dossiers.findById(eq(7L))).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, IdentiteLivretAService.CONTROLE_INTROUVABLE, () -> service.identite(JETON));
	}

}
