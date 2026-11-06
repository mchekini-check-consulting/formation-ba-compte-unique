package com.example.compteunique.securite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CU-18 : aucune réponse du backend n'est gardée en cache par le navigateur. Après une
// déconnexion, revenir en arrière ne réaffiche donc aucune donnée de l'espace.
@Configuration
public class SansCacheConfig implements WebMvcConfigurer {

	static final String SANS_CACHE = "no-store";

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {

			@Override
			public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler) {
				reponse.setHeader(HttpHeaders.CACHE_CONTROL, SANS_CACHE);
				return true;
			}

		});
	}

}
