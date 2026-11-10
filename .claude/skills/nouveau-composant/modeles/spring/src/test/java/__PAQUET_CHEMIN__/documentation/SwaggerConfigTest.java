package __PAQUET__.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SwaggerConfigTest {

    private final SwaggerConfig config = new SwaggerConfig();

    @Test
    void laDescriptionPorteLeNomDuBackend() {
        var info = config.descriptionDesApi("__SLUG__").getInfo();
        assertThat(info.getTitle()).isEqualTo("__SLUG__");
        assertThat(info.getDescription()).contains("__SLUG__");
    }

    @Test
    void seulesLesAdressesSwaggerLisentLePrefixeDuGateway() {
        assertThat(config.adresseSwaggerDerriereLeGateway().getUrlPatterns())
                .containsExactlyInAnyOrderElementsOf(SwaggerConfig.ADRESSES_FILTRE);
    }

    /** Le gateway retire /__SLUG__ et transmet l'adresse publique : Swagger doit viser celle-ci. */
    @Test
    void derriereLeGatewayLAdresseVueEstLAdressePublique() throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/v3/api-docs");
        requete.addHeader("X-Forwarded-Proto", "https");
        requete.addHeader("X-Forwarded-Host", "gateway.projet.dev.exemple.net");
        requete.addHeader("X-Forwarded-Prefix", "/__SLUG__");
        AtomicReference<HttpServletRequest> vue = new AtomicReference<>();

        config.adresseSwaggerDerriereLeGateway().getFilter().doFilter(requete, new MockHttpServletResponse(),
                (req, rep) -> vue.set((HttpServletRequest) req));

        assertThat(vue.get().getRequestURL())
                .hasToString("https://gateway.projet.dev.exemple.net/__SLUG__/v3/api-docs");
    }
}
