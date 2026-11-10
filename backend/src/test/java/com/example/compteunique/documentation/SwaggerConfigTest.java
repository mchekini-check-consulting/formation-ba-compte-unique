package com.example.compteunique.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.example.compteunique.securite.SessionObligatoire;

class SwaggerConfigTest {

	private final SwaggerConfig config = new SwaggerConfig();

	@Test
	void laDescriptionPorteLeNomDuBackend() {
		var info = config.descriptionDesApi("compte-unique").getInfo();
		assertThat(info.getTitle()).isEqualTo("compte-unique");
		assertThat(info.getDescription()).contains("compte-unique");
	}

	@Test
	void lesAdressesSwaggerSontPubliques() {
		assertThat(SessionObligatoire.ROUTES_PUBLIQUES).containsAll(SwaggerConfig.ADRESSES);
	}

	@Test
	void seulesLesAdressesSwaggerLisentLePrefixeDuGateway() {
		FilterRegistrationBean<ForwardedHeaderFilter> filtre = config.adresseSwaggerDerriereLeGateway();
		assertThat(filtre.getUrlPatterns()).containsExactlyInAnyOrderElementsOf(SwaggerConfig.ADRESSES_FILTRE);
	}

	// Le gateway retire /backend et transmet l'adresse publique : Swagger doit afficher et viser
	// https://gateway.../backend/..., pas l'adresse interne du backend.
	@Test
	void derriereLeGatewayLAdresseVueEstLAdressePublique() throws Exception {
		MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/v3/api-docs");
		requete.addHeader("X-Forwarded-Proto", "https");
		requete.addHeader("X-Forwarded-Host", "gateway.projet.dev.exemple.net");
		requete.addHeader("X-Forwarded-Prefix", "/backend");
		AtomicReference<HttpServletRequest> vue = new AtomicReference<>();

		config.adresseSwaggerDerriereLeGateway().getFilter().doFilter(requete, new MockHttpServletResponse(),
				(req, rep) -> vue.set((HttpServletRequest) req));

		assertThat(vue.get().getRequestURL())
				.hasToString("https://gateway.projet.dev.exemple.net/backend/v3/api-docs");
	}

}
