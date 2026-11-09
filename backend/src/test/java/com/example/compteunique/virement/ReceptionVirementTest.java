package com.example.compteunique.virement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.Iban;
import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.virement.ReceptionVirement.Resultat;

class ReceptionVirementTest {

	private static final String IBAN = "FR7699999000019900000000105";
	private static final OffsetDateTime OUVERTURE = OffsetDateTime.parse("2026-10-01T09:00:00+02:00");

	private final CompteRepository comptes = mock(CompteRepository.class);
	private final MouvementRepository mouvements = mock(MouvementRepository.class);
	private final RetourVirementRepository retours = mock(RetourVirementRepository.class);
	private final ReceptionVirement reception = new ReceptionVirement(comptes, mouvements, retours);
	private final Compte compte = new Compte(1L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OUVERTURE);

	@BeforeEach
	void compteDeLIban() {
		ReflectionTestUtils.setField(compte, "id", 5L);
		when(comptes.verrouillerParIban(IBAN)).thenReturn(Optional.of(compte));
	}

	private static VirementEntrant virement(String iban, long montant) {
		return new VirementEntrant(iban, montant, "Emetteur Test", "Premier versement", true);
	}

	@Test
	void unVirementSurUnCompteActifAugmenteLeSoldeEtCreeUnMouvementDuJour() {
		assertEquals(Resultat.CREDITE, reception.recevoir(virement(IBAN, 1250)));

		assertEquals(1250L, compte.getSoldeCentimes());
		assertThat(compte.getSoldeMisAJourLe()).isAfter(OUVERTURE);
		ArgumentCaptor<Mouvement> mouvement = ArgumentCaptor.forClass(Mouvement.class);
		verify(mouvements).save(mouvement.capture());
		Mouvement enregistre = mouvement.getValue();
		assertEquals(5L, enregistre.getCompteId());
		assertEquals(Mouvement.VIREMENT_ENTRANT, enregistre.getType());
		assertEquals(1250L, enregistre.getMontantCentimes());
		assertEquals("Premier versement", enregistre.getLibelle());
		assertEquals("Emetteur Test", enregistre.getEmetteur());
		assertThat(enregistre.isSimule()).isTrue();
		assertEquals(compte.getSoldeMisAJourLe(), enregistre.getEffectueLe());
		verify(retours, never()).save(any());
	}

	@Test
	void lIbanEstReconnuAvecEspacesEtMinuscules() {
		assertEquals(Resultat.CREDITE, reception.recevoir(virement("fr76 9999 9000 0199 0000 0000 105", 100)));
	}

	@Test
	void deuxVirementsSAdditionnent() {
		reception.recevoir(virement(IBAN, 1000));
		reception.recevoir(virement(IBAN, 250));

		assertEquals(1250L, compte.getSoldeCentimes());
	}

	@Test
	void unMontantNulOuNegatifEstRefuseSansChercherLeCompte() {
		assertThrows(MontantInvalideException.class, () -> reception.recevoir(virement(IBAN, 0)));
		assertThrows(MontantInvalideException.class, () -> reception.recevoir(virement(IBAN, -100)));

		verify(comptes, never()).verrouillerParIban(anyString());
		assertEquals(0L, compte.getSoldeCentimes());
	}

	@Test
	void unIbanInconnuEstRefuse() {
		when(comptes.verrouillerParIban("FR7600000000000000000000000")).thenReturn(Optional.empty());

		assertThrows(CompteIntrouvableException.class,
				() -> reception.recevoir(virement("FR7600000000000000000000000", 1250)));
		verify(mouvements, never()).save(any());
	}

	@Test
	void unEmetteurOuUnLibelleAbsentOuTropLongEstRefuse() {
		assertThrows(InformationsVirementIncompletesException.class,
				() -> reception.recevoir(new VirementEntrant(IBAN, 100, " ", "Libellé", true)));
		assertThrows(InformationsVirementIncompletesException.class,
				() -> reception.recevoir(new VirementEntrant(IBAN, 100, "Emetteur", null, true)));
		assertThrows(InformationsVirementIncompletesException.class,
				() -> reception.recevoir(new VirementEntrant(IBAN, 100, "E".repeat(71), "Libellé", true)));
		assertThrows(InformationsVirementIncompletesException.class,
				() -> reception.recevoir(new VirementEntrant(IBAN, 100, "Emetteur", "L".repeat(141), true)));
		verify(mouvements, never()).save(any());
	}

	@Test
	void unVirementSurUnCompteClotureEstRenvoyeAvecLeMotifAC04SansToucherAuSolde() {
		ReflectionTestUtils.setField(compte, "statut", Compte.CLOTURE);

		assertEquals(Resultat.RENVOYE, reception.recevoir(virement(IBAN, 1250)));

		assertEquals(0L, compte.getSoldeCentimes());
		verify(mouvements, never()).save(any());
		ArgumentCaptor<RetourVirement> retour = ArgumentCaptor.forClass(RetourVirement.class);
		verify(retours).save(retour.capture());
		RetourVirement enregistre = retour.getValue();
		assertEquals(5L, enregistre.getCompteId());
		assertEquals("AC04", enregistre.getMotif());
		assertEquals(1250L, enregistre.getMontantCentimes());
		assertEquals("Premier versement", enregistre.getLibelle());
		assertEquals("Emetteur Test", enregistre.getEmetteur());
		assertThat(enregistre.isSimule()).isTrue();
		assertThat(enregistre.getRetourneLe()).isNotNull();
	}

	@Test
	void unSoldeQuiDepasseraitLaCapaciteEstRefuse() {
		ReflectionTestUtils.setField(compte, "soldeCentimes", Long.MAX_VALUE - 10);

		assertThrows(MontantInvalideException.class, () -> reception.recevoir(virement(IBAN, 1250)));

		assertEquals(Long.MAX_VALUE - 10, compte.getSoldeCentimes());
	}

}
