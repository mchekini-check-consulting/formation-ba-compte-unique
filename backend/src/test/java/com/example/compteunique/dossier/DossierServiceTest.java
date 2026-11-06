package com.example.compteunique.dossier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.dossier.Categories.Nationalite;
import com.example.compteunique.dossier.Categories.PaysNaissance;
import com.example.compteunique.dossier.Categories.ResultatControle;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Categories.TypePiece;


// CUB-8 : un cas de test par règle d'enregistrement du dossier.
class DossierServiceTest {

	private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), PARIS);
	private static final OffsetDateTime MAINTENANT = OffsetDateTime.now(HORLOGE);
	private static final String REF = "ref-1";

	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ControleDoublon doublons = mock(ControleDoublon.class);
	private final DossierService service = new DossierService(dossiers, doublons, HORLOGE);

	private static IdentiteRequete identite() {
		return ExempleIdentite.identite();
	}

	private static IdentiteRequete avecPays(PaysNaissance pays) {
		IdentiteRequete i = identite();
		return new IdentiteRequete(i.civilite(), i.nomNaissance(), i.nomUsage(), i.prenoms(), i.dateNaissance(),
				i.communeNaissance(), pays, i.nationalite(), i.voie(), i.complement(), i.codePostal(), i.ville(),
				i.paysAdresse(), i.email(), i.mobile(), i.fixe(), i.typePiece(), i.numeroPiece(),
				i.dateExpiration());
	}

	private static IdentiteRequete avecDates(LocalDate naissance, LocalDate expiration) {
		IdentiteRequete i = identite();
		return new IdentiteRequete(i.civilite(), i.nomNaissance(), i.nomUsage(), i.prenoms(), naissance,
				i.communeNaissance(), i.paysNaissance(), i.nationalite(), i.voie(), i.complement(), i.codePostal(),
				i.ville(), i.paysAdresse(), i.email(), i.mobile(), i.fixe(), i.typePiece(), i.numeroPiece(),
				expiration);
	}

	private static IdentiteRequete avecCivilite(String civilite) {
		IdentiteRequete i = identite();
		return new IdentiteRequete(civilite, i.nomNaissance(), i.nomUsage(), i.prenoms(), i.dateNaissance(),
				i.communeNaissance(), i.paysNaissance(), i.nationalite(), i.voie(), i.complement(), i.codePostal(),
				i.ville(), i.paysAdresse(), i.email(), i.mobile(), i.fixe(), i.typePiece(), i.numeroPiece(),
				i.dateExpiration());
	}

	private static IdentiteRequete sansNationalite() {
		IdentiteRequete i = identite();
		return new IdentiteRequete(i.civilite(), i.nomNaissance(), i.nomUsage(), i.prenoms(), i.dateNaissance(),
				i.communeNaissance(), i.paysNaissance(), null, i.voie(), i.complement(), i.codePostal(), i.ville(),
				i.paysAdresse(), i.email(), i.mobile(), i.fixe(), i.typePiece(), i.numeroPiece(),
				i.dateExpiration());
	}

	private static void assertRefus(HttpStatus statut, Runnable appel) {
		ResponseStatusException erreur = assertThrows(ResponseStatusException.class, appel::run);
		assertEquals(statut, erreur.getStatusCode());
	}

	@BeforeEach
	void enregistrementDirect() {
		when(dossiers.save(any(Dossier.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void premiereSaisieCreeLeDossierEnCoursDeVerification() {
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());
		DossierReponse reponse = service.renseignerIdentite(REF, identite());
		assertEquals(REF, reponse.reference());
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statut());
		assertFalse(reponse.indiceAmericain());
		assertTrue(reponse.residenceFrancaise());
		assertNull(reponse.controleSanctions());
	}

	@Test
	void identiteEnregistreeNettoyeeAvecSaDateDeSaisie() {
		Dossier dossier = new Dossier(REF);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		service.renseignerIdentite(REF, identite());
		assertEquals("Dupont", dossier.getNomNaissance());
		assertNull(dossier.getNomUsage());
		assertNull(dossier.getComplement());
		assertNull(dossier.getFixe());
		assertEquals(MAINTENANT, dossier.getSaisiLe());
		assertEquals(Nationalite.FRANCE, dossier.getNationalite());
		assertEquals(TypePiece.CARTE_NATIONALE_IDENTITE, dossier.getTypePiece());
	}

	@Test
	void neAuxEtatsUnisPorteLIndiceAmericain() {
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());
		assertTrue(service.renseignerIdentite(REF, avecPays(PaysNaissance.ETATS_UNIS)).indiceAmericain());
	}

	@Test
	void nouvelleSaisieEffaceLesControlesPrecedents() {
		Dossier dossier = new Dossier(REF);
		dossier.renseignerIdentite(identite(), MAINTENANT);
		dossier.enregistrerControles(ResultatControle.OK, ResultatControle.OK, MAINTENANT);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		DossierReponse reponse = service.renseignerIdentite(REF, identite());
		assertNull(reponse.controleSanctions());
		assertNull(reponse.controlePep());
		assertNull(reponse.controleLe());
	}

	@Test
	void dossierAnnuleNePeutPlusEtreModifie() {
		Dossier dossier = new Dossier(REF);
		dossier.enregistrerControles(ResultatControle.NOK, ResultatControle.OK, MAINTENANT);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		assertRefus(HttpStatus.CONFLICT, () -> service.renseignerIdentite(REF, identite()));
	}

	@Test
	void mineureRefusee() {
		IdentiteRequete mineure = avecDates(LocalDate.of(2008, 11, 5), LocalDate.of(2031, 6, 30));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, mineure));
		verify(dossiers, never()).save(any(Dossier.class));
	}

	@Test
	void dixHuitAnsLeJourMemeAcceptes() {
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());
		IdentiteRequete majeure = avecDates(LocalDate.of(2008, 11, 4), LocalDate.of(2031, 6, 30));
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, service.renseignerIdentite(REF, majeure).statut());
	}

	@Test
	void pieceExpireeRefusee() {
		IdentiteRequete expiree = avecDates(LocalDate.of(1990, 5, 14), LocalDate.of(2026, 11, 4));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, expiree));
	}

	@ParameterizedTest
	@CsvSource({ "Mlle", "' '" })
	void civiliteInconnueOuVideRefusee(String civilite) {
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, avecCivilite(civilite)));
	}

	@Test
	void champObligatoireAbsentRefuse() {
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, sansNationalite()));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, avecDates(null, null)));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(REF, null));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.renseignerIdentite(" ", identite()));
	}

	@Test
	void controlesOkGardentLeDossierEnCours() {
		Dossier dossier = new Dossier(REF);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		DossierReponse reponse = service.enregistrerControles(REF,
				new ControlesRequete(ResultatControle.OK, ResultatControle.OK));
		assertEquals(StatutDossier.EN_COURS_DE_VERIFICATION, reponse.statut());
		assertEquals(ResultatControle.OK, reponse.controleSanctions());
		assertEquals(MAINTENANT, reponse.controleLe());
	}

	@ParameterizedTest
	@CsvSource({ "NOK, OK", "OK, NOK" })
	void correspondanceSanctionsOuPepAnnuleLeDossier(ResultatControle sanctions, ResultatControle pep) {
		Dossier dossier = new Dossier(REF);
		when(dossiers.findByReference(REF)).thenReturn(Optional.of(dossier));
		DossierReponse reponse = service.enregistrerControles(REF, new ControlesRequete(sanctions, pep));
		assertEquals(StatutDossier.ANNULE, reponse.statut());
		assertEquals(sanctions, dossier.getControleSanctions());
		assertEquals(pep, dossier.getControlePep());
	}

	@Test
	void controlesSurDossierInconnu() {
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());
		assertRefus(HttpStatus.NOT_FOUND, () -> service.enregistrerControles(REF,
				new ControlesRequete(ResultatControle.OK, ResultatControle.OK)));
	}

	@Test
	void controlesIncomplets() {
		assertRefus(HttpStatus.BAD_REQUEST,
				() -> service.enregistrerControles(REF, new ControlesRequete(ResultatControle.OK, null)));
		assertRefus(HttpStatus.BAD_REQUEST,
				() -> service.enregistrerControles(REF, new ControlesRequete(null, ResultatControle.OK)));
		assertRefus(HttpStatus.BAD_REQUEST, () -> service.enregistrerControles(REF, null));
	}

	@Test
	void constructeurSpringUtiliseLHorlogeSysteme() {
		DossierService avecHorlogeSysteme = new DossierService(dossiers, doublons);
		when(dossiers.findByReference(REF)).thenReturn(Optional.empty());
		assertEquals(REF, avecHorlogeSysteme.renseignerIdentite(REF, identite()).reference());
	}

}
