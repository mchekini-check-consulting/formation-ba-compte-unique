package com.example.compteunique.livreta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.livreta.CertificatClotureLivretA.Decision;
import com.example.compteunique.livreta.LivretAService.Suivi;
import com.example.compteunique.messagerie.Messagerie;

// CU-296 (US-22B) : un cas de test par critère d'acceptation.
class ClotureLivretAServiceTest {

	/** Réponse « Livret A existant » reçue le jeudi 12 novembre 2026 : date limite le 12 février 2027. */
	private static final OffsetDateTime REPONSE = OffsetDateTime.of(2026, 11, 12, 10, 0, 0, 0, ZoneOffset.UTC);
	private static final LocalDate DATE_LIMITE = LocalDate.of(2027, 2, 12);
	private static final String JETON = "jeton";
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final byte[] PDF = new byte[] { 1, 2, 3 };

	private final CompteService comptes = mock(CompteService.class);
	private final DemandeLivretARepository demandes = mock(DemandeLivretARepository.class);
	private final CertificatClotureLivretARepository certificats = mock(CertificatClotureLivretARepository.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final Messagerie messagerie = mock(Messagerie.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final Dossier dossier = mock(Dossier.class);
	private final ClotureLivretAService service = new ClotureLivretAService(comptes, demandes, certificats, dossiers,
			messagerie, dateMetier, "https://frontend.test");

	private DemandeLivretA demande;

	@BeforeEach
	void demandeLivretAExistant() throws Exception {
		when(comptes.compteCourant(JETON))
				.thenReturn(new Compte(7L, CLIENTE, Iban.de("99000000001"), 3L, REPONSE.minusDays(30)));
		when(dossier.getEmail()).thenReturn("cliente@exemple.test");
		when(dossiers.findById(7L)).thenReturn(Optional.of(dossier));
		when(certificats.save(any(CertificatClotureLivretA.class))).thenAnswer(invocation -> invocation.getArgument(0));
		aujourdhui(REPONSE.toLocalDate());
		demande = new DemandeLivretA(7L, CLIENTE, true, REPONSE);
		demande.recevoirReponse("interro", ReponseFiscale.LIVRET_A_EXISTANT, REPONSE);
		identifiant(demande, 42L);
		when(demandes.findByReferenceAndCustomerId(demande.getReference(), CLIENTE)).thenReturn(Optional.of(demande));
		when(demandes.findByReference(demande.getReference())).thenReturn(Optional.of(demande));
	}

	private void aujourdhui(LocalDate jour) {
		when(dateMetier.aujourdhui()).thenReturn(jour);
		when(dateMetier.maintenant()).thenReturn(ZonedDateTime.of(jour.atTime(9, 0), ZoneOffset.UTC));
	}

	private static void identifiant(DemandeLivretA demande, Long id) throws Exception {
		Field champ = DemandeLivretA.class.getDeclaredField("id");
		champ.setAccessible(true);
		champ.set(demande, id);
	}

	private String reference() {
		return demande.getReference();
	}

	/** Cliente qui a choisi de clôturer elle-même et déposé un certificat (dernier certificat renvoyé). */
	private CertificatClotureLivretA certificatDepose() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		service.deposerCertificat(JETON, reference(), "certificat.pdf", "application/pdf", PDF);
		ArgumentCaptor<CertificatClotureLivretA> depose = ArgumentCaptor.forClass(CertificatClotureLivretA.class);
		verify(certificats).save(depose.capture());
		when(certificats.findFirstByDemandeIdOrderByIdDesc(42L)).thenReturn(Optional.of(depose.getValue()));
		return depose.getValue();
	}

	private static void assertRefus(HttpStatus statut, String message, Runnable action) {
		DemandeRefusee refus = assertThrows(DemandeRefusee.class, action::run);
		assertEquals(statut, refus.getStatut());
		assertEquals(message, refus.getMessage());
	}

	// Critère 1 : Livret A non ouvert, deux choix proposés (avec ou sans partage du détail).
	@Test
	void livretAExistantProposeLesDeuxChoix() {
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(demande, null));
		assertEquals(StatutDemandeLivretA.LIVRET_A_EXISTANT, vue.statut());
		assertEquals(DemandeLivretAVue.EXISTANT, vue.message());
		assertNull(vue.suiteChoisie());
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.CHOIX_OBLIGATOIRE,
				() -> service.choisirSuite(JETON, reference(), null));
	}

	// Critère 2 : partage accepté -> établissement, agence et date d'ouverture enregistrés et affichés.
	@Test
	void partageAccepteLeDetailEstEnregistre() {
		DetailLivretA detail = new DetailLivretA("Banque Exemple", "Agence Paris Centre", LocalDate.of(2015, 3, 2));
		demande.recevoirDetail(detail);
		assertEquals(detail, demande.getDetail());
		assertEquals(detail, DemandeLivretAVue.de(new Suivi(demande, null)).detail());
	}

	// Critère 3 : partage refusé -> aucun détail (« établissement non communiqué »), même si un détail arrive.
	@Test
	void partageRefuseAucunDetail() {
		DemandeLivretA sansPartage = new DemandeLivretA(7L, CLIENTE, false, REPONSE);
		sansPartage.recevoirReponse("i", ReponseFiscale.LIVRET_A_EXISTANT, REPONSE);
		sansPartage.recevoirDetail(new DetailLivretA("Banque", "Agence", LocalDate.of(2015, 3, 2)));
		assertNull(sansPartage.getDetail());
		assertNull(DemandeLivretAVue.de(new Suivi(sansPartage, null)).detail());
	}

	// Le détail n'est enregistré que pour une réponse « Livret A existant ».
	@Test
	void detailIgnoreHorsLivretAExistant() {
		DemandeLivretA validee = new DemandeLivretA(7L, CLIENTE, true, REPONSE);
		validee.recevoirReponse("i", ReponseFiscale.AUCUN_LIVRET_A, REPONSE);
		validee.recevoirDetail(new DetailLivretA("Banque", "Agence", LocalDate.of(2015, 3, 2)));
		validee.recevoirDetail(null);
		assertNull(validee.getDetail());
	}

	// Critère 4 : « Je clôture moi-même » -> en attente du certificat, date limite = réponse + 3 mois.
	@Test
	void clotureParLaClienteDateLimiteATroisMois() {
		DemandeLivretA suite = service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, suite.getStatut());
		assertEquals(SuiteLivretAExistant.CLOTURE_PAR_CLIENTE, suite.getSuiteChoisie());
		assertThat(suite.getSuiteChoisieLe()).isNotNull();
		assertEquals(DATE_LIMITE, suite.getDateLimiteCertificat());
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(suite, null));
		assertEquals(DemandeLivretAVue.EN_ATTENTE_CERTIFICAT.formatted("12/02/2027"), vue.message());
		assertEquals("48 heures ouvrées", vue.delaiVerification());
	}

	// Un seul choix : il n'est plus possible une fois fait.
	@Test
	void choixUneSeuleFois() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.RENONCIATION);
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.PAS_DE_CHOIX_ATTENDU,
				() -> service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE));
	}

	// Critère 5 : certificat enregistré (fichier et date de dépôt), statut « Certificat à vérifier ».
	@Test
	void certificatDeposeAVerifier() {
		CertificatClotureLivretA certificat = certificatDepose();
		assertEquals(StatutDemandeLivretA.CERTIFICAT_A_VERIFIER, demande.getStatut());
		assertEquals(42L, certificat.getDemandeId());
		assertEquals("certificat.pdf", certificat.getNomFichier());
		assertEquals("application/pdf", certificat.getTypeFichier());
		assertEquals(3, certificat.getTaille());
		assertThat(certificat.getFichier()).containsExactly(PDF);
		assertThat(certificat.getDeposeLe()).isNotNull();
		assertNull(certificat.getDecision());
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(demande, null, certificat));
		assertEquals(DemandeLivretAVue.CERTIFICAT_A_VERIFIER, vue.message());
		assertEquals("certificat.pdf", vue.certificat().nomFichier());
	}

	// Critère 6 : plus de 2 Mo ou autre format refusé (aussi contrôlé à l'écran avant l'envoi).
	@Test
	void fichierTropLourdOuMauvaisFormatRefuse() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.TROP_LOURD, () -> service.deposerCertificat(JETON,
				reference(), "lourd.pdf", "application/pdf", new byte[3 * 1024 * 1024]));
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.FORMAT_REFUSE,
				() -> service.deposerCertificat(JETON, reference(), "c.docx", "application/msword", PDF));
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.FORMAT_REFUSE,
				() -> service.deposerCertificat(JETON, reference(), "c", null, PDF));
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.FICHIER_OBLIGATOIRE,
				() -> service.deposerCertificat(JETON, reference(), null, null, null));
		verify(certificats, never()).save(any());
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, demande.getStatut());
	}

	// Pas de dépôt avant d'avoir choisi de clôturer, ni pendant la vérification.
	@Test
	void depotSeulementEnAttenteDuCertificat() {
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.PAS_DE_CERTIFICAT_ATTENDU,
				() -> service.deposerCertificat(JETON, reference(), "c.pdf", "application/pdf", PDF));
		certificatDepose();
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.PAS_DE_CERTIFICAT_ATTENDU,
				() -> service.deposerCertificat(JETON, reference(), "c.pdf", "application/pdf", PDF));
	}

	// Critère 7 et bouchon de test : certificat validé par le conseiller (simulé) -> l'ouverture reprend.
	@Test
	void certificatValideLOuvertureReprend() {
		CertificatClotureLivretA certificat = certificatDepose();
		DemandeLivretA validee = service.simulerValidation(reference());
		assertEquals(StatutDemandeLivretA.OUVERTURE_VALIDEE, validee.getStatut());
		assertEquals(Decision.VALIDE, certificat.getDecision());
		assertEquals(ClotureLivretAService.CONSEILLER_SIMULE, certificat.getVerifiePar());
		assertThat(certificat.getVerifieLe()).isNotNull();
		assertEquals(DemandeLivretAVue.CERTIFICAT_VALIDE,
				DemandeLivretAVue.de(new Suivi(validee, null, certificat)).message());
	}

	// Critère 8 : certificat refusé avec un motif -> la cliente le voit et peut déposer un nouveau certificat.
	@Test
	void certificatRefuseAvecMotifNouveauDepotPossible() {
		CertificatClotureLivretA certificat = certificatDepose();
		assertRefus(HttpStatus.BAD_REQUEST, ClotureLivretAService.MOTIF_OBLIGATOIRE,
				() -> service.simulerRefus(reference(), "  "));
		DemandeLivretA refusee = service.simulerRefus(reference(), "Document illisible");
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, refusee.getStatut());
		assertEquals(Decision.REFUSE, certificat.getDecision());
		assertEquals("Document illisible", certificat.getMotifRefus());
		DemandeLivretAVue vue = DemandeLivretAVue.de(new Suivi(refusee, null, certificat));
		assertEquals(DemandeLivretAVue.CERTIFICAT_REFUSE.formatted("Document illisible", "12/02/2027"), vue.message());
		assertEquals(Decision.REFUSE, vue.certificat().decision());

		service.deposerCertificat(JETON, reference(), "nouveau.pdf", "application/pdf", PDF);
		assertEquals(StatutDemandeLivretA.CERTIFICAT_A_VERIFIER, demande.getStatut());
	}

	// Validation ou refus simulés seulement pour un certificat à vérifier.
	@Test
	void simulationSansCertificatAVerifierRefusee() {
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.PAS_DE_CERTIFICAT_A_VERIFIER,
				() -> service.simulerValidation(reference()));
		when(demandes.findByReference("inconnue")).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, ClotureLivretAService.DEMANDE_INTROUVABLE,
				() -> service.simulerValidation("inconnue"));
	}

	// Critère 9 : relance par e-mail 15 jours avant la date limite, une seule fois.
	@Test
	void relanceAJMoins15() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		aujourdhui(DATE_LIMITE.minusDays(16));
		service.appliquerEcheance(demande);
		assertNull(demande.getRelanceLe());

		aujourdhui(DATE_LIMITE.minusDays(15));
		service.appliquerEcheance(demande);
		assertThat(demande.getRelanceLe()).isNotNull();
		verify(messagerie).envoyerEmail(eq("cliente@exemple.test"), eq(ClotureLivretAService.OBJET_RELANCE),
				contains("12/02/2027"));

		service.appliquerEcheance(demande);
		verify(messagerie).envoyerEmail(anyString(), eq(ClotureLivretAService.OBJET_RELANCE), anyString());
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, demande.getStatut());
	}

	// Critère 10 et bouchon de test (date avancée) : date limite dépassée -> annulée, écran et e-mail.
	@Test
	void dateLimiteDepasseeDemandeAnnulee() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		aujourdhui(DATE_LIMITE);
		service.appliquerEcheance(demande);
		assertEquals(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT, demande.getStatut());

		aujourdhui(DATE_LIMITE.plusDays(1));
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.DATE_LIMITE_DEPASSEE,
				() -> service.deposerCertificat(JETON, reference(), "c.pdf", "application/pdf", PDF));
		assertEquals(StatutDemandeLivretA.ANNULEE, demande.getStatut());
		assertThat(demande.getAnnuleeLe()).isNotNull();
		verify(messagerie).envoyerEmail(eq("cliente@exemple.test"), eq(ClotureLivretAService.OBJET_ANNULATION),
				contains("annulée"));
		assertEquals(DemandeLivretAVue.ANNULEE.formatted("12/02/2027"),
				DemandeLivretAVue.de(new Suivi(demande, null)).message());
	}

	// Certificat déposé mais non validé à la date limite : annulée aussi (« sans certificat validé »).
	@Test
	void certificatNonValideALaDateLimiteAnnule() {
		certificatDepose();
		aujourdhui(DATE_LIMITE.plusDays(1));
		assertRefus(HttpStatus.CONFLICT, ClotureLivretAService.PAS_DE_CERTIFICAT_A_VERIFIER,
				() -> service.simulerValidation(reference()));
		assertEquals(StatutDemandeLivretA.ANNULEE, demande.getStatut());
	}

	// Tâche quotidienne : demandes en attente du certificat ou de sa vérification.
	@Test
	void tacheQuotidienne() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		when(demandes.findByStatutIn(List.of(StatutDemandeLivretA.EN_ATTENTE_CERTIFICAT,
				StatutDemandeLivretA.CERTIFICAT_A_VERIFIER))).thenReturn(List.of(demande));
		aujourdhui(DATE_LIMITE.plusDays(1));
		assertEquals(1, service.traiterLesEcheances());
		assertEquals(StatutDemandeLivretA.ANNULEE, demande.getStatut());
	}

	// Pas d'adresse e-mail dans le dossier : pas d'envoi, mais l'annulation est faite.
	@Test
	void sansEmailPasDEnvoi() {
		when(dossier.getEmail()).thenReturn(" ");
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		aujourdhui(DATE_LIMITE.plusDays(1));
		service.appliquerEcheance(demande);
		assertEquals(StatutDemandeLivretA.ANNULEE, demande.getStatut());
		verify(messagerie, never()).envoyerEmail(anyString(), anyString(), anyString());
	}

	// Critère 11 : renonciation -> demande annulée (statut « Renoncée »).
	@Test
	void renonciation() {
		DemandeLivretA renoncee = service.choisirSuite(JETON, reference(), SuiteLivretAExistant.RENONCIATION);
		assertEquals(StatutDemandeLivretA.RENONCEE, renoncee.getStatut());
		assertEquals(SuiteLivretAExistant.RENONCIATION, renoncee.getSuiteChoisie());
		assertThat(renoncee.getAnnuleeLe()).isNotNull();
		assertTrue(renoncee.getStatut().estTerminee());
		assertEquals(DemandeLivretAVue.RENONCEE, DemandeLivretAVue.de(new Suivi(renoncee, null)).message());
		assertThat(service.dernierCertificat(renoncee)).isEmpty();
	}

	// Une demande d'une autre cliente reste introuvable.
	@Test
	void demandeDUneAutreClienteIntrouvable() {
		when(demandes.findByReferenceAndCustomerId("autre", CLIENTE)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, ClotureLivretAService.DEMANDE_INTROUVABLE,
				() -> service.choisirSuite(JETON, "autre", SuiteLivretAExistant.RENONCIATION));
	}

	@Test
	void nomDeFichierParDefautEtTexteDesEmails() {
		service.choisirSuite(JETON, reference(), SuiteLivretAExistant.CLOTURE_PAR_CLIENTE);
		service.deposerCertificat(JETON, reference(), " ", "image/png", PDF);
		ArgumentCaptor<CertificatClotureLivretA> depose = ArgumentCaptor.forClass(CertificatClotureLivretA.class);
		verify(certificats).save(depose.capture());
		assertEquals("certificat", depose.getValue().getNomFichier());
		assertThat(service.texteRelance(demande)).contains("https://frontend.test/livret-a");
		assertThat(service.texteAnnulation(demande)).contains("nouvelle demande");
		assertEquals("", ClotureLivretAService.jour(null));
	}

}
