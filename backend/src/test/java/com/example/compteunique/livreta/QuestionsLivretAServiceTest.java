package com.example.compteunique.livreta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.QuestionsLivretAService.Etape;
import com.example.compteunique.livreta.ReponsesQuestionnaire.ObjetEpargne;
import com.example.compteunique.livreta.ReponsesQuestionnaire.OrigineFonds;
import com.example.compteunique.profil.Consentements;

// CU-32 (US-23) : un cas de test par critère d'acceptation.
class QuestionsLivretAServiceTest {

	/** Mercredi 25 novembre 2026, 10 h. */
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.of(2026, 11, 25, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final String TYPE = QuestionsLivretAService.TYPE_CONSENTEMENT;
	private static final String VERSION = QuestionsLivretAService.VERSION_TEXTE;

	private final CompteService comptes = mock(CompteService.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final QuestionnaireLivretARepository questionnaires = mock(QuestionnaireLivretARepository.class);
	private final IdentiteLivretAService identites = mock(IdentiteLivretAService.class);
	private final Consentements consentements = mock(Consentements.class);
	private final Dossier dossier = mock(Dossier.class);
	private final Compte compte = new Compte(7L, CLIENTE, Iban.de("99000000001"), 3L, MAINTENANT.minusDays(30));
	private final QuestionsLivretAService service = new QuestionsLivretAService(comptes, dossiers, questionnaires,
			identites, consentements, Clock.fixed(MAINTENANT.toInstant(), ZoneOffset.UTC));

	@BeforeEach
	void cliente() {
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(dossier.getId()).thenReturn(7L);
		when(dossiers.findById(7L)).thenReturn(Optional.of(dossier));
		when(questionnaires.save(any(QuestionnaireLivretA.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(CLIENTE)).thenReturn(Optional.empty());
	}

	private static QuestionnaireLivretA questionnaire(String version) {
		return new QuestionnaireLivretA(7L, CLIENTE, ObjetEpargne.PROJET, OrigineFonds.VENTE_BIEN, null, version,
				MAINTENANT);
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	private void rienEnregistre() {
		verify(questionnaires, never()).save(any());
		verify(consentements, never()).accepter(any(), anyString(), anyString(), any());
	}

	// Texte d'information : il précise les données transmises à l'administration fiscale et pourquoi.
	@Test
	void premiereOuvertureTexteADemanderSansQuestionnaire() {
		Etape etape = service.etat(JETON);
		assertTrue(etape.texteAAccepter());
		assertEquals(VERSION, etape.versionTexte());
		assertTrue(etape.texte().contains("nom, prénom, sexe, date et lieu de naissance"));
		assertTrue(etape.texte().contains("administration fiscale"));
		assertTrue(etape.texte().contains("multidétention"));
		assertNull(etape.questionnaire());
	}

	// Version en vigueur déjà acceptée : texte non redemandé, seul le questionnaire est posé.
	@Test
	void versionEnVigueurDejaAccepteeTexteNonRedemande() {
		when(consentements.aAccepte(CLIENTE, TYPE, VERSION)).thenReturn(true);
		assertFalse(service.etat(JETON).texteAAccepter());
		QuestionnaireLivretA enregistre = service.repondre(JETON, ObjetEpargne.PRECAUTION,
				OrigineFonds.SALAIRE_REVENUS, null, null);
		assertEquals(ObjetEpargne.PRECAUTION, enregistre.getObjetEpargne());
		verify(consentements, never()).accepter(any(), anyString(), anyString(), any());
	}

	// Questionnaire complet et texte accepté : réponses et acceptation enregistrées avec la date, l'heure et la version.
	@Test
	void questionnaireCompletEtTexteAccepteEnregistres() {
		QuestionnaireLivretA enregistre = service.repondre(JETON, ObjetEpargne.AUTRE, OrigineFonds.AUTRE,
				"  Vente de parts sociales  ", true);
		assertEquals(7L, enregistre.getDossierId());
		assertEquals(CLIENTE, enregistre.getCustomerId());
		assertEquals(ObjetEpargne.AUTRE, enregistre.getObjetEpargne());
		assertEquals(OrigineFonds.AUTRE, enregistre.getOrigineFonds());
		assertEquals("Vente de parts sociales", enregistre.getPrecisionOrigine());
		assertEquals(VERSION, enregistre.getVersionTexteInformation());
		assertEquals(MAINTENANT, enregistre.getReponduLe());
		assertNull(enregistre.getDemandeReference());
		verify(consentements).accepter(CLIENTE, TYPE, VERSION, MAINTENANT);
	}

	// Questionnaire : sans objet de l'épargne ou sans origine des fonds, la cliente ne peut pas continuer.
	@Test
	void questionnaireIncompletRefuse() {
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.OBJET_OBLIGATOIRE,
				() -> service.repondre(JETON, null, OrigineFonds.SALAIRE_REVENUS, null, true));
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.ORIGINE_OBLIGATOIRE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, null, null, true));
		rienEnregistre();
	}

	// Origine « Autre » sans précision (ou précision trop longue) : refusé ; précision ignorée pour les autres origines.
	@Test
	void precisionDeLOrigineAutre() {
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.PRECISION_OBLIGATOIRE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.AUTRE, null, true));
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.PRECISION_OBLIGATOIRE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.AUTRE, "   ", true));
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.PRECISION_TROP_LONGUE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.AUTRE, "x".repeat(201), true));
		rienEnregistre();
		assertEquals("x".repeat(200), QuestionsLivretAService.precision(OrigineFonds.AUTRE, "x".repeat(200)));
		assertNull(QuestionsLivretAService.precision(OrigineFonds.HERITAGE_DONATION, "ignorée"));
	}

	// Texte refusé : la cliente ne peut pas aller plus loin, rien n'est enregistré.
	@Test
	void texteRefuseBloque() {
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.TEXTE_REFUSE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.EPARGNE_EXISTANTE, null, false));
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.TEXTE_REFUSE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.EPARGNE_EXISTANTE, null, null));
		rienEnregistre();
	}

	// Place dans le parcours : après l'identité confirmée (CU-28) et la pièce valide (CU-29).
	@Test
	void questionsApresIdentiteEtPiece() {
		when(identites.controleValide(compte)).thenThrow(
				new DemandeRefusee(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE));
		assertRefus(HttpStatus.CONFLICT, IdentiteLivretAService.SANS_IDENTITE_CONFIRMEE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, null, true));
		rienEnregistre();
	}

	@Test
	void questionsApresUneNouvellePieceSiCelleDuDossierAExpire() {
		when(dossier.pieceExpireeLe(MAINTENANT.toLocalDate())).thenReturn(true);
		assertRefus(HttpStatus.CONFLICT, LivretAService.PIECE_EXPIREE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, null, true));
		rienEnregistre();
	}

	@Test
	void dossierIntrouvable() {
		when(dossiers.findById(7L)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, LivretAService.DEMANDE_INTROUVABLE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, null, true));
	}

	// Règle d'ouverture : questionnaire de cette ouverture, avec la version en vigueur du texte.
	@Test
	void questionnaireValidePourLEligibilite() {
		assertRefus(HttpStatus.CONFLICT, QuestionsLivretAService.SANS_QUESTIONNAIRE,
				() -> service.questionnaireValide(compte));
		QuestionnaireLivretA rempli = questionnaire(VERSION);
		when(questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(CLIENTE)).thenReturn(Optional.of(rempli));
		assertSame(rempli, service.questionnaireValide(compte));
		assertSame(rempli, service.etat(JETON).questionnaire());
	}

	// Un questionnaire ne sert qu'à une ouverture.
	@Test
	void questionnaireDejaUtiliseParUneDemande() {
		QuestionnaireLivretA utilise = questionnaire(VERSION);
		utilise.utiliserPour("demande-1");
		when(questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(CLIENTE)).thenReturn(Optional.of(utilise));
		assertRefus(HttpStatus.CONFLICT, QuestionsLivretAService.SANS_QUESTIONNAIRE,
				() -> service.questionnaireValide(compte));
		assertNull(service.etat(JETON).questionnaire());
	}

	// Nouvelle version du texte depuis la dernière acceptation : à accepter de nouveau avant de continuer.
	@Test
	void nouvelleVersionDuTexteARaccepter() {
		when(consentements.aAccepte(CLIENTE, TYPE, "V0")).thenReturn(true);
		when(questionnaires.findFirstByCustomerIdOrderByReponduLeDescIdDesc(CLIENTE))
				.thenReturn(Optional.of(questionnaire("V0")));
		Etape etape = service.etat(JETON);
		assertTrue(etape.texteAAccepter());
		assertNull(etape.questionnaire());
		assertRefus(HttpStatus.CONFLICT, QuestionsLivretAService.SANS_QUESTIONNAIRE,
				() -> service.questionnaireValide(compte));
		assertRefus(HttpStatus.BAD_REQUEST, QuestionsLivretAService.TEXTE_REFUSE,
				() -> service.repondre(JETON, ObjetEpargne.PRECAUTION, OrigineFonds.SALAIRE_REVENUS, null, null));
	}

	@Test
	void constructeurSpringUtiliseLHorlogeSysteme() {
		QuestionsLivretAService spring = new QuestionsLivretAService(comptes, dossiers, questionnaires, identites,
				consentements);
		assertTrue(spring.etat(JETON).texteAAccepter());
	}

}
