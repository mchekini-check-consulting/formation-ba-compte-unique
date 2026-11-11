package com.example.compteunique.compte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.compteunique.connexion.ConnexionService;

// CU-146 et CU-147 : compte courant de la cliente connectée.
class CompteCourantTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final CompteRepository comptes = mock(CompteRepository.class);
	private final CompteService service = new CompteService(connexion, comptes, mock(JournalSecuriteRepository.class),
			mock(MouvementRepository.class));
	private final Compte compte = new Compte(1L, UUID.randomUUID(), Iban.de("99000000001"), 7L, OffsetDateTime.now());

	@BeforeEach
	void clienteConnectee() {
		when(connexion.idDeLaSession("jeton")).thenReturn(Optional.of(7L));
	}

	@Test
	void leCompteCourantEstCeluiDeLIdentifiantConnecte() {
		when(comptes.findByIdentifiantConnexionId(7L)).thenReturn(Optional.of(compte));
		when(comptes.idParIdentifiantConnexion(7L)).thenReturn(Optional.of(5L));

		assertEquals(compte, service.compteCourant("jeton"));
		assertEquals(5L, service.compteCourantId("jeton"));
	}

	@Test
	void sansCompteOuSansSessionLaDemandeEstRefusee() {
		assertThrows(CompteInaccessibleException.class, () -> service.compteCourant("jeton"));
		assertThrows(CompteInaccessibleException.class, () -> service.compteCourantId("jeton"));
		assertThrows(ClienteNonConnecteeException.class, () -> service.compteCourant("autre"));
	}

}
