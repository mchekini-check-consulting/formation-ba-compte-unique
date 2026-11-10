package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;
import com.example.compteunique.securite.JournalSecurite;

// CU-16 (US-11) et CU-48 : règles de blocage par identifiant et par adresse IP.
class ProtectionConnexionTest {

	private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-06T10:00:00Z");
	private static final String IP = "203.0.113.7";
	private static final String IDENTIFIANT = "12345678";

	private final BlocageIdentifiantRepository blocagesIdentifiant = mock(BlocageIdentifiantRepository.class);
	private final BlocageAdresseIpRepository blocagesAdresse = mock(BlocageAdresseIpRepository.class);
	private final JournalSecuriteRepository evenements = mock(JournalSecuriteRepository.class);
	private final JournalSecurite journal = mock(JournalSecurite.class);
	private final ProtectionConnexion protection = new ProtectionConnexion(blocagesIdentifiant, blocagesAdresse,
			evenements, journal, true);

	private List<EvenementSecurite> evenementsJournalises(int nombre) {
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(journal, times(nombre)).enregistrer(evenement.capture());
		return evenement.getAllValues();
	}

	// --- Par identifiant (CU-16) ---

	@Test
	void laTroisiemeErreurBloqueCinqMinutesAvecLeMessageDuTicket() {
		BlocageIdentifiant blocage = new BlocageIdentifiant(IDENTIFIANT);
		protection.erreur(blocage, 1L, T0);
		protection.erreur(blocage, 1L, T0);
		verifyNoInteractions(journal);

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.erreur(blocage, 1L, T0));

		assertEquals("Trop de tentatives. Réessayez dans 5 minutes.", refus.getMessage());
		assertEquals(T0.plusMinutes(5), blocage.getBloqueJusquAu());
		EvenementSecurite evenement = evenementsJournalises(1).get(0);
		assertEquals(EvenementSecurite.BLOCAGE_IDENTIFIANT, evenement.getEvenement());
		assertEquals(IDENTIFIANT, evenement.getIdentifiantSaisi());
		verify(blocagesIdentifiant, times(3)).save(blocage);
	}

	@Test
	void unIdentifiantInconnuEstBloqueDeLaMemeFacon() {
		BlocageIdentifiant blocage = new BlocageIdentifiant("99999999");
		protection.erreur(blocage, null, T0);
		protection.erreur(blocage, null, T0);
		assertThrows(TropDeTentativesException.class, () -> protection.erreur(blocage, null, T0));
	}

	@Test
	void pendantLeBlocageLaTentativeEstRefuseeAvecLeTempsRestant() {
		BlocageIdentifiant blocage = bloque();
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(blocage));

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.verifierIdentifiant(IDENTIFIANT, T0.plusMinutes(3).plusSeconds(30)));

		assertEquals("Trop de tentatives. Réessayez dans 2 minutes.", refus.getMessage());
		assertEquals(ProtectionConnexion.IDENTIFIANT_BLOQUE, evenementsJournalises(1).get(0).getMotif());
	}

	@Test
	void aLaDerniereMinuteLeMessageEstAuSingulier() {
		BlocageIdentifiant blocage = bloque();
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(blocage));

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.verifierIdentifiant(IDENTIFIANT, T0.plusMinutes(4).plusSeconds(59)));

		assertEquals("Trop de tentatives. Réessayez dans 1 minute.", refus.getMessage());
	}

	@Test
	void apresLeBlocageLeCompteurRepartDeZeroPuisDeUn() {
		BlocageIdentifiant blocage = bloque();
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.of(blocage));

		BlocageIdentifiant leve = protection.verifierIdentifiant(IDENTIFIANT, T0.plusMinutes(5));
		assertEquals(0, leve.getErreursConsecutives());
		assertThat(leve.getBloqueJusquAu()).isNull();

		protection.erreur(leve, 1L, T0.plusMinutes(6));
		assertEquals(1, leve.getErreursConsecutives());
	}

	@Test
	void unIdentifiantJamaisVuACompteurAZeroSansEnregistrement() {
		when(blocagesIdentifiant.findWithLockByIdentifiant(IDENTIFIANT)).thenReturn(Optional.empty());

		BlocageIdentifiant blocage = protection.verifierIdentifiant(IDENTIFIANT, T0);

		assertEquals(0, blocage.getErreursConsecutives());
		verify(blocagesIdentifiant, never()).save(any());
	}

	@Test
	void uneConnexionReussieRemetLeCompteurAZero() {
		BlocageIdentifiant blocage = new BlocageIdentifiant(IDENTIFIANT);
		when(blocagesIdentifiant.existsById(IDENTIFIANT)).thenReturn(true);
		protection.reussite(blocage);
		verify(blocagesIdentifiant).delete(blocage);

		BlocageIdentifiant jamaisEnregistre = new BlocageIdentifiant("87654321");
		protection.reussite(jamaisEnregistre);
		verify(blocagesIdentifiant, never()).delete(jamaisEnregistre);
	}

	private BlocageIdentifiant bloque() {
		BlocageIdentifiant blocage = new BlocageIdentifiant(IDENTIFIANT);
		blocage.erreur(T0);
		blocage.erreur(T0);
		blocage.erreur(T0);
		return blocage;
	}

	// --- Par adresse IP (CU-48) ---

	private void echecsRecents(long nombre) {
		when(evenements.countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(eq(IP),
				eq(EvenementSecurite.CONNEXION_ECHOUEE), eq(ProtectionConnexion.MOTIFS_ECHEC), any()))
				.thenReturn(nombre);
	}

	@Test
	void dixNeufEchecsEnQuinzeMinutesLaissentPasserLaTentative() {
		echecsRecents(19);
		protection.verifierAdresse(IP, IDENTIFIANT, T0);
		verifyNoInteractions(journal);
		verify(evenements).countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(IP,
				EvenementSecurite.CONNEXION_ECHOUEE, ProtectionConnexion.MOTIFS_ECHEC, T0.minusMinutes(15));
	}

	@Test
	void apresVingtEchecsEnQuinzeMinutesLaTentativeSuivanteBloqueLAdresseQuinzeMinutes() {
		echecsRecents(20);

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.verifierAdresse(IP, IDENTIFIANT, T0));

		assertEquals("Trop de tentatives depuis votre connexion. Réessayez plus tard.", refus.getMessage());
		ArgumentCaptor<BlocageAdresseIp> blocage = ArgumentCaptor.forClass(BlocageAdresseIp.class);
		verify(blocagesAdresse).save(blocage.capture());
		assertEquals(T0.plusMinutes(15), blocage.getValue().getBloqueJusquAu());
		List<EvenementSecurite> journalises = evenementsJournalises(2);
		assertEquals(EvenementSecurite.BLOCAGE_ADRESSE_IP, journalises.get(0).getEvenement());
		assertEquals(ProtectionConnexion.ADRESSE_IP_BLOQUEE, journalises.get(1).getMotif());
	}

	@Test
	void uneAdresseBloqueeEstRefuseeSansCompterSesEchecs() {
		when(blocagesAdresse.findById(IP)).thenReturn(Optional.of(new BlocageAdresseIp(IP, T0.plusMinutes(15))));

		assertThrows(TropDeTentativesException.class, () -> protection.verifierAdresse(IP, IDENTIFIANT, T0));

		verify(evenements, never()).countByAdresseIpAndEvenementAndMotifInAndSurvenuLeAfter(anyString(), anyString(),
				any(), any());
	}

	// CU-171 : le refus indique le temps restant du blocage, pas sa durée totale.
	@Test
	void leRefusDUneAdresseBloqueeIndiqueLeTempsRestant() {
		when(blocagesAdresse.findById(IP)).thenReturn(Optional.of(new BlocageAdresseIp(IP, T0.plusSeconds(250))));

		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.verifierAdresse(IP, IDENTIFIANT, T0));

		assertEquals(Duration.ofSeconds(250), refus.getRestant());
	}

	@Test
	void unNouveauBlocageDAdresseIndiqueQuinzeMinutes() {
		echecsRecents(20);
		TropDeTentativesException refus = assertThrows(TropDeTentativesException.class,
				() -> protection.verifierAdresse(IP, IDENTIFIANT, T0));
		assertEquals(Duration.ofMinutes(15), refus.getRestant());
	}

	// CU-48 (recette) : en dev et qualif, la limite par adresse IP est désactivée.
	@Test
	void limiteDesactiveeAucuneVerificationNiBlocageParAdresse() {
		ProtectionConnexion sansLimite = new ProtectionConnexion(blocagesIdentifiant, blocagesAdresse, evenements,
				journal, false);
		when(blocagesAdresse.findById(IP)).thenReturn(Optional.of(new BlocageAdresseIp(IP, T0.plusMinutes(15))));
		echecsRecents(50);

		sansLimite.verifierAdresse(IP, IDENTIFIANT, T0);

		verifyNoInteractions(blocagesAdresse, evenements, journal);
	}

	@Test
	void apresQuinzeMinutesLAdresseEstDebloqueeEtUnNouveauBlocageProlongeLaLigne() {
		BlocageAdresseIp ancien = new BlocageAdresseIp(IP, T0);
		when(blocagesAdresse.findById(IP)).thenReturn(Optional.of(ancien));
		echecsRecents(0);

		protection.verifierAdresse(IP, IDENTIFIANT, T0);

		echecsRecents(20);
		assertThrows(TropDeTentativesException.class, () -> protection.verifierAdresse(IP, IDENTIFIANT, T0));
		assertEquals(T0.plusMinutes(15), ancien.getBloqueJusquAu());
		verify(blocagesAdresse, never()).save(any());
	}

	@Test
	void sansAdresseConnuePasDeLimitationParAdresse() {
		protection.verifierAdresse(null, IDENTIFIANT, T0);
		verifyNoInteractions(blocagesAdresse, evenements, journal);
	}

	@Test
	void leTempsRestantEstNulHorsBlocage() {
		assertEquals(Duration.ZERO, new BlocageIdentifiant(IDENTIFIANT).restant(T0));
	}

}
