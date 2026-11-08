package __PAQUET__.logging;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;

/**
 * Champs approuvés de {@link LogMetier} : lecture des valeurs à l'exécution ({@link #extraire}) et contrôle des
 * chemins avant la mise en ligne ({@link #erreurs}, appelé par LogMetierConventionTest).
 */
public final class Champs {

    static final String RESULTAT = "resultat";
    static final String TAILLE = "taille";

    /** Écrits par la plateforme : un champ approuvé ne peut pas porter ces noms. */
    static final Set<String> RESERVES =
            Set.of("time", "level", "msg", "logger", "layer", "method", "route", "status", "durationMs", "err");

    /**
     * Mots (découpage camelCase) qui signalent une donnée personnelle ou un secret : refusés même si le BA les
     * approuve. "code" couvre code postal, code de vérification, code de signature.
     */
    private static final List<String> INTERDITS = List.of("nom", "prenom", "email", "mail", "telephone",
            "portable", "adresse", "code", "naissance", "iban", "bic", "mot de passe", "password", "jeton", "token",
            "authorization", "secret", "signature", "cookie", "ip", "secu");

    private Champs() {
    }

    /** Valeurs des champs ; un chemin illisible (valeur absente, erreur) est ignoré, jamais bloquant. */
    static Map<String, String> extraire(String[] champs, Method methode, Object[] arguments, Object resultat,
            boolean avecResultat) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        Parameter[] parametres = methode.getParameters();
        for (String champ : champs) {
            List<String> segments = Arrays.asList(champ.split("\\."));
            Object racine = null;
            boolean trouvee = false;
            if (RESULTAT.equals(segments.getFirst())) {
                racine = resultat;
                trouvee = avecResultat;
            } else {
                for (int i = 0; i < parametres.length && i < arguments.length; i++) {
                    if (parametres[i].getName().equals(segments.getFirst())) {
                        racine = arguments[i];
                        trouvee = true;
                    }
                }
            }
            if (trouvee) {
                lire(racine, segments.subList(1, segments.size())).ifPresent(valeur -> valeurs.put(champ, valeur));
            }
        }
        return valeurs;
    }

    private static Optional<String> lire(Object racine, List<String> proprietes) {
        Object courant = deballer(racine);
        try {
            for (String propriete : proprietes) {
                if (courant == null) {
                    return Optional.empty();
                }
                if (courant instanceof Collection<?> liste && TAILLE.equals(propriete)) {
                    courant = liste.size();
                } else {
                    Method accesseur = accesseur(courant.getClass(), propriete);
                    if (accesseur == null) {
                        return Optional.empty();
                    }
                    courant = deballer(accesseur.invoke(courant));
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            return Optional.empty();
        }
        return Optional.ofNullable(courant).map(Champs::texte);
    }

    private static Object deballer(Object valeur) {
        return valeur instanceof Optional<?> optionnel ? optionnel.orElse(null) : valeur;
    }

    private static String texte(Object valeur) {
        if (valeur instanceof Collection<?> liste) {
            return liste.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return String.valueOf(valeur);
    }

    private static Method accesseur(Class<?> type, String propriete) {
        if (type.isRecord()) {
            for (RecordComponent composant : type.getRecordComponents()) {
                if (composant.getName().equals(propriete)) {
                    return composant.getAccessor();
                }
            }
            return null;
        }
        PropertyDescriptor descripteur = BeanUtils.getPropertyDescriptor(type, propriete);
        return descripteur == null ? null : descripteur.getReadMethod();
    }

    /** Problèmes des champs de {@link LogMetier} sur cette méthode ; vide si tout est conforme. */
    public static List<String> erreurs(Method methode) {
        LogMetier logMetier = methode.getAnnotation(LogMetier.class);
        List<String> erreurs = new ArrayList<>();
        if (logMetier == null) {
            return erreurs;
        }
        String nomMethode = methode.getDeclaringClass().getSimpleName() + "." + methode.getName();
        for (String champ : logMetier.champs()) {
            erreur(methode, champ).ifPresent(probleme -> erreurs.add(nomMethode + " : " + champ + " : " + probleme));
        }
        return erreurs;
    }

    private static Optional<String> erreur(Method methode, String champ) {
        if (RESERVES.contains(champ)) {
            return Optional.of("nom réservé à la plateforme");
        }
        List<String> segments = Arrays.asList(champ.split("\\."));
        for (String segment : segments) {
            if (personnel(segment)) {
                return Optional.of("donnée personnelle ou secret (" + segment + "), interdit dans les logs");
            }
        }
        Type type = racine(methode, segments.getFirst());
        if (type == null) {
            return Optional.of("paramètre inconnu (" + segments.getFirst() + ")");
        }
        for (String propriete : segments.subList(1, segments.size())) {
            type = sansOptional(type);
            Class<?> classe = brute(type);
            if (Collection.class.isAssignableFrom(classe) && TAILLE.equals(propriete)) {
                type = Integer.class;
            } else {
                Method accesseur = accesseur(classe, propriete);
                if (accesseur == null) {
                    return Optional.of("propriété inconnue (" + propriete + ") dans " + classe.getSimpleName());
                }
                type = accesseur.getGenericReturnType();
            }
        }
        return simple(sansOptional(type)) ? Optional.empty()
                : Optional.of("valeur non simple (" + brute(sansOptional(type)).getSimpleName()
                        + ") : choisir une de ses propriétés");
    }

    private static Type racine(Method methode, String nom) {
        if (RESULTAT.equals(nom)) {
            return methode.getReturnType() == void.class ? null : methode.getGenericReturnType();
        }
        for (Parameter parametre : methode.getParameters()) {
            if (parametre.getName().equals(nom)) {
                return parametre.getParameterizedType();
            }
        }
        return null;
    }

    static boolean personnel(String segment) {
        String mots = " " + segment.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT) + " ";
        return INTERDITS.stream().anyMatch(interdit -> mots.contains(" " + interdit + " "));
    }

    private static Type sansOptional(Type type) {
        if (type instanceof ParameterizedType parametre && parametre.getRawType() == Optional.class) {
            return parametre.getActualTypeArguments()[0];
        }
        return type;
    }

    private static Class<?> brute(Type type) {
        if (type instanceof Class<?> classe) {
            return classe;
        }
        if (type instanceof ParameterizedType parametre) {
            return (Class<?>) parametre.getRawType();
        }
        return Object.class;
    }

    private static boolean simple(Type type) {
        Class<?> classe = brute(type);
        if (Collection.class.isAssignableFrom(classe)) {
            return type instanceof ParameterizedType parametre
                    && scalaire(brute(parametre.getActualTypeArguments()[0]));
        }
        return scalaire(classe);
    }

    private static boolean scalaire(Class<?> classe) {
        return classe.isPrimitive() || CharSequence.class.isAssignableFrom(classe)
                || Number.class.isAssignableFrom(classe) || Boolean.class == classe || classe.isEnum()
                || UUID.class == classe || Temporal.class.isAssignableFrom(classe);
    }
}
