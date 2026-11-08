package __PAQUET__.logging;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Méthodes annotées {@link LogMetier}. Service et db : une ligne par appel (layer, method, durationMs, err en cas
 * d'échec, puis les champs approuvés). Contrôleur : les champs approuvés sont confiés à {@link LogsControleur},
 * qui écrit la ligne une fois le code HTTP connu.
 */
@Aspect
@Component
public class LogsMetierAspect {

    /** Au-delà, un appel db est écrit en warn (visible même avec LOG_LEVEL=warn). */
    static final long SEUIL_DB_LENT_MS = 200;
    static final String ATTRIBUT_CHAMPS = LogsMetierAspect.class.getName() + ".champs";

    private static final Logger log = LoggerFactory.getLogger(LogsMetierAspect.class);

    @Around("@annotation(__PAQUET__.logging.LogMetier)")
    public Object journaliser(ProceedingJoinPoint point) throws Throwable {
        Object cible = point.getTarget();
        Method methode = methodeAnnotee(((MethodSignature) point.getSignature()).getMethod(), cible);
        LogMetier logMetier = methode.getAnnotation(LogMetier.class);
        String[] champs = logMetier == null ? new String[0] : logMetier.champs();

        if (AnnotatedElementUtils.hasAnnotation(AopUtils.getTargetClass(cible), RestController.class)) {
            return confierAuControleur(point, methode, champs);
        }
        String couche = cible instanceof Repository<?, ?> ? "db" : "service";
        String nom = methode.getDeclaringClass().getSimpleName() + "." + methode.getName();

        long debut = System.nanoTime();
        try {
            Object resultat = point.proceed();
            ecrire(couche, nom, debut, null, Champs.extraire(champs, methode, point.getArgs(), resultat, true));
            return resultat;
        } catch (Throwable erreur) {
            ecrire(couche, nom, debut, erreur, Champs.extraire(champs, methode, point.getArgs(), null, false));
            throw erreur;
        }
    }

    /**
     * Méthode qui porte l'annotation : celle de la classe pour un service appelé par son interface, celle de
     * l'interface pour un repository Spring Data (sa classe d'implémentation ne la déclare pas).
     */
    private static Method methodeAnnotee(Method appelee, Object cible) {
        Method specifique = AopUtils.getMostSpecificMethod(appelee, AopUtils.getTargetClass(cible));
        return specifique.isAnnotationPresent(LogMetier.class) ? specifique : appelee;
    }

    private Object confierAuControleur(ProceedingJoinPoint point, Method methode, String[] champs) throws Throwable {
        try {
            Object resultat = point.proceed();
            memoriser(Champs.extraire(champs, methode, point.getArgs(), resultat, true));
            return resultat;
        } catch (Throwable erreur) {
            memoriser(Champs.extraire(champs, methode, point.getArgs(), null, false));
            throw erreur;
        }
    }

    private static void memoriser(Map<String, String> valeurs) {
        RequestAttributes requete = RequestContextHolder.getRequestAttributes();
        if (requete != null) {
            requete.setAttribute(ATTRIBUT_CHAMPS, valeurs, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private static void ecrire(String couche, String nom, long debut, Throwable erreur, Map<String, String> valeurs) {
        long dureeMs = Duration.ofNanos(System.nanoTime() - debut).toMillis();
        ContexteMdc contexte = ContexteMdc.ouvrir()
                .avec("layer", couche)
                .avec("method", nom)
                .avec("durationMs", String.valueOf(dureeMs))
                .avec(valeurs);
        if (erreur != null) {
            contexte.avec("err", erreur.getClass().getSimpleName());
        }
        try (contexte) {
            if (erreur != null) {
                log.warn("{} call failed", couche);
            } else if ("db".equals(couche) && dureeMs >= SEUIL_DB_LENT_MS) {
                log.warn("slow db call");
            } else {
                log.info("{} call completed", couche);
            }
        }
    }
}
