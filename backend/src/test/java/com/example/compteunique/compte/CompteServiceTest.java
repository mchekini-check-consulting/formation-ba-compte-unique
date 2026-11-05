package com.example.compteunique.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.connexion.ConnexionService;

class CompteServiceTest {

	private static final String JETON = "jeton";
	private static final Long CLIENTE = 7L;
	private static final UUID TITULAIRE = UUID.randomUUID();

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final JournalSecuriteRepository journal = mock(JournalSecuriteRepository.class);
	private final CompteService service = new CompteService(connexion, comptes, journal);
	private final Compte compte = new Compte(1L, TITULAIRE, Iban.de("99000000001"), CLIENTE, OffsetDateTime.now());

	@BeforeEach
	void clienteConnectee() {
		when(connexion.idDeLaSession(JETON)).thenReturn(Optional.of(CLIENTE));
	}

	private void clienteAvecCompte() {
		when(comptes.findByIdentifiantConnexionId(CLIENTE)).thenReturn(Optional.of(compte));
	}

	private EvenementSecurite evenementEnregistre() {
		ArgumentCaptor<EvenementSecurite> evenement = ArgumentCaptor.forClass(EvenementSecurite.class);
		verify(journal).save(evenement.capture());
		return evenement.getValue();
	}

	@Test
	void laListeNeContientQueLesComptesDuCustomerIdDeLaCliente() {
		clienteAvecCompte();
		when(comptes.findByCustomerId(TITULAIRE)).thenReturn(List.of(compte));

		assertEquals(List.of(compte), service.mesComptes(JETON));
	}

	@Test
	void uneClienteSansCompteAUneListeVide() {
		when(comptes.findByIdentifiantConnexionId(CLIENTE)).thenReturn(Optional.empty());

		assertThat(service.mesComptes(JETON)).isEmpty();
		verify(comptes, never()).findByCustomerId(any());
	}

	@Test
	void sansSessionLaListeEstRefusee() {
		when(connexion.idDeLaSession(null)).thenReturn(Optional.empty());

		assertThrows(ClienteNonConnecteeException.class, () -> service.mesComptes(null));
	}

	@Test
	void laTitulaireConsulteSonCompteSansEvenementAuJournal() {
		clienteAvecCompte();
		when(comptes.findByIdAndCustomerId(5L, TITULAIRE)).thenReturn(Optional.of(compte));

		assertSame(compte, service.consulter(JETON, "5"));
		verify(journal, never()).save(any());
	}

	@Test
	void leCompteDUneAutreClienteEstRefuseEtEnregistreAuJournal() {
		clienteAvecCompte();
		when(comptes.findByIdAndCustomerId(6L, TITULAIRE)).thenReturn(Optional.empty());

		assertThrows(CompteInaccessibleException.class, () -> service.consulter(JETON, "6"));

		EvenementSecurite evenement = evenementEnregistre();
		assertEquals(EvenementSecurite.ACCES_COMPTE_REFUSE, evenement.getEvenement());
		assertEquals(CLIENTE, evenement.getIdentifiantConnexionId());
		assertEquals(TITULAIRE, evenement.getCustomerId());
		assertEquals("6", evenement.getCompteDemande());
		assertThat(evenement.getSurvenuLe()).isNotNull();
	}

	@Test
	void unIdentifiantQuiNEstPasUnNombreEstRefuseCommeUnCompteInexistant() {
		clienteAvecCompte();

		assertThrows(CompteInaccessibleException.class, () -> service.consulter(JETON, "abc"));

		assertEquals("abc", evenementEnregistre().getCompteDemande());
	}

	@Test
	void unIdentifiantTropLongEstTronqueDansLeJournal() {
		clienteAvecCompte();

		assertThrows(CompteInaccessibleException.class, () -> service.consulter(JETON, "9".repeat(100)));

		assertEquals("9".repeat(EvenementSecurite.COMPTE_DEMANDE_MAX), evenementEnregistre().getCompteDemande());
	}

	@Test
	void uneClienteSansCompteEstRefuseeEtEnregistreeSansCustomerId() {
		when(comptes.findByIdentifiantConnexionId(CLIENTE)).thenReturn(Optional.empty());

		assertThrows(CompteInaccessibleException.class, () -> service.consulter(JETON, "5"));

		assertNull(evenementEnregistre().getCustomerId());
	}

	@Test
	void sansSessionLaConsultationEstRefuseeSansEvenementAuJournal() {
		when(connexion.idDeLaSession(null)).thenReturn(Optional.empty());

		assertThrows(ClienteNonConnecteeException.class, () -> service.consulter(null, "5"));
		verify(journal, never()).save(any());
	}

}
