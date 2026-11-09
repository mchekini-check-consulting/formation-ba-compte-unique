package com.example.compteunique.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CU-212 : identifiant de demande (requestId), écrit sur toutes les lignes de log d'une même requête (clé MDC,
 * reprise par l'encodeur JSON de logback-spring.xml). Repris de l'en-tête X-Request-Id s'il est court et sûr (posé
 * par le gateway ou un appelant), sinon généré. La réponse n'est pas modifiée.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentifiantDemandeFilter extends OncePerRequestFilter {

	public static final String EN_TETE = "X-Request-Id";
	public static final String CLE_MDC = "requestId";

	/** Lettres, chiffres, point, tiret et tiret bas, 64 caractères au plus : rien d'autre n'entre dans les logs. */
	private static final Pattern FORMAT_ACCEPTE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
			throws ServletException, IOException {
		MDC.put(CLE_MDC, identifiant(requete.getHeader(EN_TETE)));
		try {
			chaine.doFilter(requete, reponse);
		} finally {
			MDC.remove(CLE_MDC);
		}
	}

	/** L'identifiant reçu s'il est valide, sinon un nouvel UUID. */
	static String identifiant(String recu) {
		return recu != null && FORMAT_ACCEPTE.matcher(recu).matches() ? recu : UUID.randomUUID().toString();
	}

}
