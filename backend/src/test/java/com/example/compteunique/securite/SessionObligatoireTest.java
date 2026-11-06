package com.example.compteunique.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.compteunique.connexion.ConnexionService;

// CU-170 : refus par défaut des adresses non publiques.
class SessionObligatoireTest {

	private final ConnexionService connexion = mock(ConnexionService.class);
	private final SessionObligatoire garde = new SessionObligatoire(connexion);
	private final MockHttpServletResponse reponse = new MockHttpServletResponse();

	private static MockHttpServletRequest demande(Cookie... cookies) {
		MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/comptes");
		if (cookies.length > 0) {
			requete.setCookies(cookies);
		}
		return requete;
	}

	@Test
	void sansCookieLaDemandeEstRefuseeSansMotif() throws Exception {
		assertThat(garde.preHandle(demande(), reponse, null)).isFalse();
		assertThat(reponse.getStatus()).isEqualTo(401);
		assertThat(reponse.getContentAsString()).isEmpty();
		verify(connexion, never()).idDeLaSession(any());
	}

	@Test
	void cookieVideOuAutreCookieTraitesCommeSansCookie() throws Exception {
		assertThat(garde.preHandle(demande(new Cookie("SESSION_CLIENT", ""), new Cookie("autre", "x")), reponse,
				null)).isFalse();
		assertThat(reponse.getStatus()).isEqualTo(401);
		assertThat(reponse.getContentAsString()).isEmpty();
	}

	@Test
	void sessionFermeeLaDemandeEstRefuseeAvecLeMotifExpiree() throws Exception {
		when(connexion.idDeLaSession("revoque")).thenReturn(Optional.empty());
		assertThat(garde.preHandle(demande(new Cookie("SESSION_CLIENT", "revoque")), reponse, null)).isFalse();
		assertThat(reponse.getStatus()).isEqualTo(401);
		assertThat(reponse.getContentAsString()).isEqualTo("{\"motif\":\"EXPIREE\"}");
	}

	@Test
	void sessionActiveLaDemandeContinue() throws Exception {
		when(connexion.idDeLaSession("jeton")).thenReturn(Optional.of(7L));
		assertThat(garde.preHandle(demande(new Cookie("SESSION_CLIENT", "jeton")), reponse, null)).isTrue();
		assertThat(reponse.getStatus()).isEqualTo(200);
	}

	@Test
	void laVerificationCorsDuNavigateurPasseSansSession() throws Exception {
		MockHttpServletRequest requete = new MockHttpServletRequest("OPTIONS", "/comptes");
		assertThat(garde.preHandle(requete, reponse, null)).isTrue();
	}

	@Test
	void lesComptesNeFontPasPartieDesRoutesPubliques() {
		assertThat(SessionObligatoire.ROUTES_PUBLIQUES).noneMatch(route -> route.startsWith("/comptes"))
				.doesNotContain("/**");
	}

}
