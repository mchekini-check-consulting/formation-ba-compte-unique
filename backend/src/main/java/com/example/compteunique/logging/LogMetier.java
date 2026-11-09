package com.example.compteunique.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Log métier d'une méthode, ajouté par le skill logs-metier avec l'accord du BA. La couche (champ layer) dépend de
 * la classe : contrôleur REST (controller), repository Spring Data (db), sinon service. Les champs obligatoires de
 * la plateforme sont toujours écrits ; {@link #champs()} n'ajoute que ceux approuvés par le BA.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogMetier {

    /**
     * Chemins {@code <paramètre>.<propriété>...} ou {@code resultat.<propriété>...} (taille : nombre d'éléments
     * d'une liste), vers une valeur simple. Jamais de donnée personnelle : vérifié par LogMetierConventionTest.
     */
    String[] champs() default {};

    /**
     * Message métier court, en français, d'un appel réussi (service et db), ex. « connexion réussie ». Vide : message
     * générique « service call completed ». Jamais de donnée : un texte fixe.
     */
    String message() default "";

    /**
     * Message métier d'un appel en échec (exception), ex. « identifiant bloqué ». Vide : message générique
     * « service call failed ». Jamais de donnée : un texte fixe.
     */
    String messageEchec() default "";
}
