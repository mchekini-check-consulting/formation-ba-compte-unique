package com.example.compteunique.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.dossier.Categories.MotifAnnulation;
import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.DossierController.RefusOuverture;
import com.example.compteunique.dossier.DossierController.Reprise;
import com.example.compteunique.dossier.OuvertureRefuseeException.EtapeReprise;
import com.example.compteunique.dossier.OuvertureRefuseeException.Refus;
import com.example.compteunique.securite.JournalSecurite;

// CU-9 (US-04) : un seul profil client et un seul dossier en cours par personne.
class ControleDoublonTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneId.of("Europe/Paris"));
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.now(HORLOGE);
	private static final String REF = "nouvelle";

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final IdentifiantConnexionRepository identifiants = mock(IdentifiantConnexionRepository.class);
	private final JournalSecurite journal = mock(JournalSecurite.class);
	private final ControleDoublon controle = new ControleDoublon(dossiers, comptes, identifiants, journal);
	private final IdentiteRequete identite = ExempleIdentite.identite();

	private void existants(Dossier... memePersonne) {
		when(dossiers.memePersonne(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(List.of(memePersonne));
	}

	private static Dossier dossier(long id, String reference, StatutDossier statut) {
		Dossier dossier = new Dossier(reference);
		ReflectionTestUtils.setField(dossier, "id", id);
		ReflectionTestUtils.setField(dossier, "statut", statut);
		return dossier;
	}

	private static Dossier annule(long id, MotifAnnulation motif) {
		Dossier dossier = dossier(id, "annule-" + id, StatutDossier.EN_COURS_DE_VERIFICATION);
		dossier.annuler(motif, MAINTENANT);
		return dossier;
	}

	/** Profil client : dossier « Compte ouvert », compte ACTIF et son identifiant. */
	private Dossier profil(long id, boolean active, String statutCompte) {
		Dossier dossier = dossier(id, "profil-" + id, StatutDossier.COMPTE_OUVERT);
		Compte compte = new Compte(id, UUID.randomUUID(), Iban.de(String.format("%011d", id)), 100 + id, MAINTENANT);
		ReflectionTestUtils.setField(compte, "statut", statutCompte);
		when(comptes.findByDossierId(id)).thenReturn(Optional.of(compte));
		IdentifiantConnexion identifiant = new IdentifiantConnexion("1234567" + id);
		if (active) {
			identifiant.definirMotDePasse("hache", MAINTENANT);
		}
		when(identifiants.findById(100 + id)).thenReturn(Optional.of(identifiant));
		return dossier;
	}

	private OuvertureRefuseeException refus() {
		return assertThrows(OuvertureRefuseeException.class, () -> controle.verifier(REF, identite, MAINTENANT));
	}

	private EvenementSecurite evenementJournalise() {
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(journal).enregistrer(evenement.capture());
		return evenement.getValue();
	}

	@Test
	void laRechercheUtiliseLaPieceEtLIdentiteNormalisees() {
		existants();
		controle.verifier(REF, identite, MAINTENANT);
		verify(dossiers).memePersonne(REF, "CARTE_NATIONALE_IDENTITE", "2000000000123", "DUPONT", "MARIECLAIRE",
				LocalDate.of(1990, 5, 14), "LYON", "FRANCE");
		verify(journal, never()).enregistrer(any());
		assertEquals(ControleDoublon.normaliser("Marie-Claire"), ControleDoublon.normaliser("marie claire"));
	}

	@Test
	void criteres1Et3ProfilAvecIdentifiantActiveRenvoieVersLaConnexion() {
		existants(profil(1, true, Compte.ACTIF));

		OuvertureRefuseeException refus = refus();

		assertEquals(Refus.ESPACE_EXISTANT, refus.getRefus());
		assertNull(refus.getReferenceReprise());
		EvenementSecurite evenement = evenementJournalise();
		assertEquals(EvenementSecurite.DOUBLON_DETECTE, evenement.getEvenement());
		assertEquals("ESPACE_EXISTANT", evenement.getMotif());
		assertNull(evenement.getCompteDemande());
	}

	@Test
	void critere2ProfilAvecIdentifiantPasEncoreActiveRenvoieVersLActivation() {
		existants(profil(2, false, Compte.ACTIF));
		assertEquals(Refus.ACTIVATION_ATTENDUE, refus().getRefus());
	}

	@Test
	void unCompteClotureNEstPasUnProfilExistant() {
		existants(profil(3, true, Compte.CLOTURE));
		controle.verifier(REF, identite, MAINTENANT);
		verify(journal, never()).enregistrer(any());
	}

	@Test
	void critere8DossierAnnuleParControleBloqueSansMotifNiReprise() {
		existants(annule(4, MotifAnnulation.CONTROLE_AUTOMATIQUE));
		OuvertureRefuseeException refus = refus();
		assertEquals(Refus.OUVERTURE_ANNULEE, refus.getRefus());
		assertNull(refus.getReferenceReprise());

		existants(annule(5, MotifAnnulation.DECISION_CONFORMITE));
		assertEquals(Refus.OUVERTURE_ANNULEE, refus().getRefus());
	}

	@Test
	void critere9DossierAnnuleParLaClienteOuPourInactiviteNeBloquePas() {
		existants(annule(6, MotifAnnulation.DEMANDE_CLIENTE), annule(7, MotifAnnulation.INACTIVITE),
				annule(8, MotifAnnulation.CODE_EXPIRE), annule(9, MotifAnnulation.TROIS_TENTATIVES),
				dossier(10, "ancien", StatutDossier.ANNULE));
		controle.verifier(REF, identite, MAINTENANT);
		verify(journal, never()).enregistrer(any());
	}

	@Test
	void critere4DossierEnCoursEnVerificationSeReprendALIdentite() {
		existants(dossier(11, "existante", StatutDossier.EN_COURS_DE_VERIFICATION));

		OuvertureRefuseeException refus = refus();

		assertEquals(Refus.OUVERTURE_EN_COURS, refus.getRefus());
		assertEquals("existante", refus.getReferenceReprise());
		assertEquals(EtapeReprise.IDENTITE, refus.getEtapeReprise());
		assertEquals(EvenementSecurite.REPRISE_DOSSIER, evenementJournalise().getEvenement());
	}

	@Test
	void critere4DossierAccepteSigneOuARelancerSeReprendAuSuiviDeLOuverture() {
		for (StatutDossier statut : List.of(StatutDossier.ACCEPTE, StatutDossier.CONVENTION_SIGNEE,
				StatutDossier.CREATION_A_RELANCER)) {
			existants(dossier(12, "signee", statut));
			assertEquals(EtapeReprise.OUVERTURE, refus().getEtapeReprise());
		}
	}

	@Test
	void leProfilPasseAvantLAnnulationParControleQuiPasseAvantLeDossierEnCours() {
		existants(dossier(13, "en-cours", StatutDossier.EN_COURS_DE_VERIFICATION),
				annule(14, MotifAnnulation.CONTROLE_AUTOMATIQUE), profil(15, true, Compte.ACTIF));
		assertEquals(Refus.ESPACE_EXISTANT, refus().getRefus());

		existants(dossier(13, "en-cours", StatutDossier.EN_COURS_DE_VERIFICATION),
				annule(14, MotifAnnulation.CONTROLE_AUTOMATIQUE));
		assertEquals(Refus.OUVERTURE_ANNULEE, refus().getRefus());
	}

	@Test
	void unSeulIdentifiantActiveSuffitPourLeMessageDeConnexion() {
		existants(profil(16, false, Compte.ACTIF), profil(17, true, Compte.ACTIF));
		assertEquals(Refus.ESPACE_EXISTANT, refus().getRefus());
	}

	// --- Réponse HTTP (critères 8 et 14) ---

	@Test
	void laReponseNeDonneLesCoordonneesQuePourUneOuvertureAnnuleeEtLaReferenceQuePourUneReprise() {
		DossierController controleur = new DossierController(mock(DossierService.class), "Conseiller 01 23");

		ResponseEntity<RefusOuverture> annulee = controleur
				.refus(new OuvertureRefuseeException(Refus.OUVERTURE_ANNULEE));
		assertEquals(HttpStatus.CONFLICT, annulee.getStatusCode());
		assertEquals(new RefusOuverture(Refus.OUVERTURE_ANNULEE, "Votre demande d’ouverture en ligne est annulée. "
				+ "Pour ouvrir un compte à la banque, contactez un conseiller.", null, "Conseiller 01 23"),
				annulee.getBody());

		assertEquals(new RefusOuverture(Refus.ESPACE_EXISTANT,
				"Vous avez déjà un espace client. Connectez-vous avec votre identifiant.", null, null),
				controleur.refus(new OuvertureRefuseeException(Refus.ESPACE_EXISTANT)).getBody());
		assertEquals("Votre espace n’est pas encore activé. Ouvrez le lien d’activation reçu par SMS.",
				controleur.refus(new OuvertureRefuseeException(Refus.ACTIVATION_ATTENDUE)).getBody().message());

		RefusOuverture enCours = controleur.refus(
				new OuvertureRefuseeException(Refus.OUVERTURE_EN_COURS, "existante", EtapeReprise.IDENTITE)).getBody();
		assertEquals("Vous avez déjà une ouverture de compte en cours. Reprenez-la.", enCours.message());
		assertEquals(new Reprise("existante", EtapeReprise.IDENTITE), enCours.reprise());
	}

	// --- Annulation signalée par l'écran et contrôles ---

	private final DossierService service = new DossierService(dossiers, controle, HORLOGE);

	@Test
	void lEcranAnnuleUnDossierEnVerificationPourUnControleAutomatique() {
		Dossier dossier = dossier(20, "a-annuler", StatutDossier.EN_COURS_DE_VERIFICATION);
		when(dossiers.findByReference("a-annuler")).thenReturn(Optional.of(dossier));
		when(dossiers.save(dossier)).thenReturn(dossier);
		DossierController controleur = new DossierController(service, "c");

		DossierReponse reponse = controleur.annuler("a-annuler",
				new DossierController.DemandeAnnulation(MotifAnnulation.CONTROLE_AUTOMATIQUE));

		assertEquals(StatutDossier.ANNULE, reponse.statut());
		assertEquals(MotifAnnulation.CONTROLE_AUTOMATIQUE, dossier.getMotifAnnulation());
		assertEquals(MAINTENANT, dossier.getAnnuleLe());
	}

	@Test
	void lEcranNePeutNiDonnerUneDecisionDeConformiteNiAnnulerUnDossierSigne() {
		assertStatut(HttpStatus.BAD_REQUEST, () -> service.annuler("x", MotifAnnulation.DECISION_CONFORMITE));
		assertStatut(HttpStatus.BAD_REQUEST, () -> service.annuler("x", null));
		assertStatut(HttpStatus.BAD_REQUEST,
				() -> new DossierController(service, "c").annuler("x", null));
		when(dossiers.findByReference("inconnu")).thenReturn(Optional.empty());
		assertStatut(HttpStatus.NOT_FOUND, () -> service.annuler("inconnu", MotifAnnulation.DEMANDE_CLIENTE));
		when(dossiers.findByReference("signe"))
				.thenReturn(Optional.of(dossier(21, "signe", StatutDossier.CONVENTION_SIGNEE)));
		assertStatut(HttpStatus.CONFLICT, () -> service.annuler("signe", MotifAnnulation.DEMANDE_CLIENTE));
	}

	private static void assertStatut(HttpStatus statut, Runnable action) {
		ResponseStatusException refus = assertThrows(ResponseStatusException.class, action::run);
		assertEquals(statut, refus.getStatusCode());
	}

	@Test
	void unControleSanctionsOuPepEnEchecAnnuleParControleEtCompteCommeUneAction() {
		Dossier dossier = dossier(22, "ctrl", StatutDossier.EN_COURS_DE_VERIFICATION);
		dossier.enregistrerControles(ResultatControle.OK, ResultatControle.NOK, MAINTENANT);
		assertEquals(StatutDossier.ANNULE, dossier.getStatut());
		assertEquals(MotifAnnulation.CONTROLE_AUTOMATIQUE, dossier.getMotifAnnulation());
		assertEquals(MAINTENANT, dossier.getDerniereActionLe());

		Dossier conforme = dossier(23, "ok", StatutDossier.EN_COURS_DE_VERIFICATION);
		conforme.enregistrerControles(ResultatControle.OK, ResultatControle.OK, MAINTENANT);
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, conforme.getStatut());
		assertThat(conforme.getMotifAnnulation()).isNull();
		assertThat(conforme.isAttenteAnalyste()).isFalse();
	}

	@Test
	void uneOuvertureRefuseeNeCreeNiNeModifieAucunDossier() {
		existants(dossier(24, "existante", StatutDossier.EN_COURS_DE_VERIFICATION));
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());

		assertThrows(OuvertureRefuseeException.class, () -> service.renseignerIdentite(REF, identite));

		verify(dossiers, never()).save(any());
	}

}
