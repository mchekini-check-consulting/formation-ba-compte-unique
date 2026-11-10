package com.example.bouchonsidentite.documentation;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

// Swagger : toutes les adresses du backend sont décrites automatiquement (/v3/api-docs) et
// peuvent être essayées depuis /swagger-ui.html. Une nouvelle adresse y apparaît sans rien faire.
// Activé en local, dev et qualif ; absent en preprod et prod (SWAGGER_ACTIF, helm.yaml).
@Configuration
public class SwaggerConfig {

	/** Adresses de Swagger, au format des filtres HTTP (servlet). */
	static final List<String> ADRESSES_FILTRE = List.of("/v3/api-docs/*", "/v3/api-docs.yaml", "/swagger-ui.html",
			"/swagger-ui/*");

	@Bean
	OpenAPI descriptionDesApi(@Value("${spring.application.name}") String application) {
		return new OpenAPI().info(new Info().title(application)
				.description("Adresses du backend " + application + ", générées depuis le code."));
	}

	// Derrière le gateway, le backend est appelé sans son préfixe (/bouchons-identite retiré). Pour
	// que la page Swagger et ses essais visent la bonne adresse, ces seules adresses lisent le
	// préfixe (X-Forwarded-Prefix) et l'adresse publique transmis par le gateway.
	@Bean
	FilterRegistrationBean<ForwardedHeaderFilter> adresseSwaggerDerriereLeGateway() {
		FilterRegistrationBean<ForwardedHeaderFilter> filtre = new FilterRegistrationBean<>(
				new ForwardedHeaderFilter());
		filtre.setUrlPatterns(ADRESSES_FILTRE);
		return filtre;
	}

}
