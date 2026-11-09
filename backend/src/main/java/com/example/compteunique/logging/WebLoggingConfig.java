package com.example.compteunique.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Couche controller : RequestLoggingInterceptor (toutes les requêtes, sauf les méthodes annotées
 * {@link LogMetier}) et LogsControleur (une ligne détaillée par méthode annotée {@link LogMetier}).
 */
@Configuration
public class WebLoggingConfig implements WebMvcConfigurer {

	private final RequestLoggingInterceptor requestLoggingInterceptor;
	private final LogsControleur logsControleur;

	public WebLoggingConfig(RequestLoggingInterceptor requestLoggingInterceptor, LogsControleur logsControleur) {
		this.requestLoggingInterceptor = requestLoggingInterceptor;
		this.logsControleur = logsControleur;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestLoggingInterceptor);
		registry.addInterceptor(logsControleur);
	}

}
