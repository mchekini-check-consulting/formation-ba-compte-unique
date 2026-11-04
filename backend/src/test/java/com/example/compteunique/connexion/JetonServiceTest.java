package com.example.compteunique.connexion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class JetonServiceTest {

	private static final String JETON = "jeton-en-clair";

	private final JetonRepository jetons = mock(JetonRepository.class);
	private final JetonService service = new JetonService(jetons, "https://frontend.test");

	private void jetonEnBase(Jeton jeton) {
		when(jetons.findByJetonHacheAndType(SecretsAleatoires.empreinte(JETON), jeton.getType()))
				.thenReturn(Optional.of(jeton));
	}

	private Jeton jetonValide() {
		OffsetDateTime maintenant = OffsetDateTime.now();
		return new Jeton(1L, TypeJeton.ACTIVATION, "empreinte", maintenant, maintenant.plusHours(24));
	}

	@Test
	void genererEnregistreUneEmpreinteValable24HeuresEtInvalideLAncienAvant() {
		String jeton = service.generer(1L, TypeJeton.ACTIVATION);

		ArgumentCaptor<Jeton> enregistre = ArgumentCaptor.forClass(Jeton.class);
		InOrder ordre = inOrder(jetons);
		ordre.verify(jetons).invaliderLesJetonsActifs(eq(1L), eq(TypeJeton.ACTIVATION), any());
		ordre.verify(jetons).save(enregistre.capture());

		assertThat(Base64.getUrlDecoder().decode(jeton)).hasSize(32);
		assertEquals(SecretsAleatoires.empreinte(jeton), enregistre.getValue().getJetonHache());
		assertThat(enregistre.getValue().getJetonHache()).isNotEqualTo(jeton);
		assertEquals(Duration.ofHours(24),
				Duration.between(enregistre.getValue().getCreeLe(), enregistre.getValue().getExpireLe()));
	}

	@Test
	void utiliserMarqueLeJetonEtRenvoieLeClient() {
		Jeton jeton = jetonValide();
		jetonEnBase(jeton);
		assertEquals(1L, service.utiliser(JETON, TypeJeton.ACTIVATION));
		assertThat(jeton.getUtiliseLe()).isNotNull();
	}

	@Test
	void unJetonDejaUtiliseEstRefuse() {
		jetonEnBase(jetonValide());
		service.utiliser(JETON, TypeJeton.ACTIVATION);
		assertThrows(JetonInvalideException.class, () -> service.utiliser(JETON, TypeJeton.ACTIVATION));
	}

	@Test
	void unJetonExpireEtUnJetonInconnuRecoiventLaMemeReponse() {
		OffsetDateTime hier = OffsetDateTime.now().minusDays(1);
		jetonEnBase(new Jeton(1L, TypeJeton.ACTIVATION, "empreinte", hier.minusDays(1), hier));
		JetonInvalideException expire = assertThrows(JetonInvalideException.class,
				() -> service.utiliser(JETON, TypeJeton.ACTIVATION));
		JetonInvalideException inconnu = assertThrows(JetonInvalideException.class,
				() -> service.utiliser("inconnu", TypeJeton.ACTIVATION));
		assertEquals(HttpStatus.GONE, expire.getStatusCode());
		assertEquals(expire.getStatusCode(), inconnu.getStatusCode());
		assertEquals(expire.getReason(), inconnu.getReason());
	}

	@Test
	void leLienUtiliseLAdresseDeBaseDeLEnvironnement() {
		assertEquals("https://frontend.test/activation?jeton=abc", service.lien(TypeJeton.ACTIVATION, "abc"));
		assertEquals("https://frontend.test/reinitialisation?jeton=abc",
				service.lien(TypeJeton.REINITIALISATION, "abc"));
	}

}
