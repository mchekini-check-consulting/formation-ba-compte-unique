package __PAQUET__.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Couche controller : une ligne par requête vers une méthode annotée {@link LogMetier} (method, route, status,
 * durationMs, puis les champs approuvés relevés par {@link LogsMetierAspect}). Route = modèle de l'URL
 * (/contrats/{reference}), jamais les valeurs qu'elle contient.
 */
@Component
public class LogsControleur implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LogsControleur.class);
    private static final String ATTRIBUT_DEBUT = LogsControleur.class.getName() + ".debut";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTRIBUT_DEBUT, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (!(handler instanceof HandlerMethod methode) || !methode.hasMethodAnnotation(LogMetier.class)) {
            return;
        }
        long dureeMs = request.getAttribute(ATTRIBUT_DEBUT) instanceof Long debut
                ? Duration.ofNanos(System.nanoTime() - debut).toMillis()
                : 0;
        Object modele = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        int statut = ex != null && response.getStatus() < 500 ? 500 : response.getStatus();
        @SuppressWarnings("unchecked")
        Map<String, String> valeurs = request.getAttribute(LogsMetierAspect.ATTRIBUT_CHAMPS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        ContexteMdc contexte = ContexteMdc.ouvrir()
                .avec("layer", "controller")
                .avec("method", request.getMethod())
                .avec("route", modele != null ? modele.toString() : "unmatched")
                .avec("status", String.valueOf(statut))
                .avec("durationMs", String.valueOf(dureeMs))
                .avec(valeurs);
        try (contexte) {
            if (statut >= 500) {
                log.warn("http request failed");
            } else {
                log.info("http request handled");
            }
        }
    }
}
