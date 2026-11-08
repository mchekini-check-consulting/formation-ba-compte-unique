package __PAQUET__.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChampsTest {

    record Logement(String typeLogement, int surface, String adresse) {
    }

    record Demande(UUID reference, Logement logement, String formule, List<String> options) {
    }

    /** Bean à getters, comme les entités JPA. */
    static class Devis {

        private final BigDecimal prixMensuel;

        Devis(BigDecimal prixMensuel) {
            this.prixMensuel = prixMensuel;
        }

        public BigDecimal getPrixMensuel() {
            return prixMensuel;
        }

        public Logement getLogement() {
            throw new IllegalStateException("chargement impossible");
        }
    }

    static class Exemples {

        @LogMetier(champs = {"demande.formule", "demande.logement.typeLogement", "demande.logement.surface",
            "demande.options", "demande.options.taille", "resultat.prixMensuel", "inconnu.x", "demande.absente",
            "resultat.logement.typeLogement"})
        public Devis enregistrer(String authorization, Demande demande) {
            return null;
        }

        @LogMetier(champs = {"reference", "resultat.prixMensuel", "resultat.taille"})
        public Optional<Devis> chercher(UUID reference) {
            return Optional.empty();
        }

        @LogMetier(champs = {"resultat.taille", "resultat"})
        public List<Devis> lister(String formule) {
            return List.of();
        }

        @LogMetier(champs = {"demande.logement.adresse", "authorization", "demande.logement", "demande.options",
            "resultat", "status", "inconnu", "demande.prix", "demande.reference"})
        public void nonConforme(String authorization, Demande demande) {
        }

        public void sansAnnotation() {
        }
    }

    private static Method methode(String nom) {
        for (Method methode : Exemples.class.getMethods()) {
            if (methode.getName().equals(nom)) {
                return methode;
            }
        }
        throw new IllegalArgumentException(nom);
    }

    private static String[] champs(String nom) {
        return methode(nom).getAnnotation(LogMetier.class).champs();
    }

    @Test
    void extraitLesValeursDesParametresEtDuResultat() {
        Demande demande = new Demande(UUID.randomUUID(), new Logement("appartement", 60, "12 rue des Lilas"),
                "confort", List.of("objets-valeur", "assistance-depannage"));

        Map<String, String> valeurs = Champs.extraire(champs("enregistrer"), methode("enregistrer"),
                new Object[] {"Bearer jeton", demande}, new Devis(new BigDecimal("14.72")), true);

        assertThat(valeurs).containsExactly(
                Map.entry("demande.formule", "confort"),
                Map.entry("demande.logement.typeLogement", "appartement"),
                Map.entry("demande.logement.surface", "60"),
                Map.entry("demande.options", "objets-valeur,assistance-depannage"),
                Map.entry("demande.options.taille", "2"),
                Map.entry("resultat.prixMensuel", "14.72"));
    }

    @Test
    void ignoreLeResultatQuandLAppelAEchoue() {
        Demande demande = new Demande(null, null, "confort", List.of());

        Map<String, String> valeurs = Champs.extraire(champs("enregistrer"), methode("enregistrer"),
                new Object[] {null, demande}, null, false);

        assertThat(valeurs).containsOnly(Map.entry("demande.formule", "confort"),
                Map.entry("demande.options", ""), Map.entry("demande.options.taille", "0"));
    }

    @Test
    void deballeLesOptionalEtCompteLesListes() {
        UUID reference = UUID.randomUUID();

        assertThat(Champs.extraire(champs("chercher"), methode("chercher"), new Object[] {reference},
                Optional.of(new Devis(BigDecimal.TEN)), true))
                .containsOnly(Map.entry("reference", reference.toString()), Map.entry("resultat.prixMensuel", "10"));
        assertThat(Champs.extraire(champs("lister"), methode("lister"), new Object[] {"confort"},
                List.of(new Devis(BigDecimal.ONE)), true))
                .containsEntry("resultat.taille", "1");
    }

    @Test
    void signaleLesParametresEtProprietesInconnus() {
        assertThat(Champs.erreurs(methode("enregistrer")))
                .containsExactly("Exemples.enregistrer : inconnu.x : paramètre inconnu (inconnu)",
                        "Exemples.enregistrer : demande.absente : propriété inconnue (absente) dans Demande");
        assertThat(Champs.erreurs(methode("chercher")))
                .containsExactly("Exemples.chercher : resultat.taille : propriété inconnue (taille) dans Devis");
        assertThat(Champs.erreurs(methode("sansAnnotation"))).isEmpty();
    }

    @Test
    void refuseLesChampsNonConformes() {
        assertThat(Champs.erreurs(methode("nonConforme"))).containsExactly(
                "Exemples.nonConforme : demande.logement.adresse : "
                        + "donnée personnelle ou secret (adresse), interdit dans les logs",
                "Exemples.nonConforme : authorization : donnée personnelle ou secret (authorization), "
                        + "interdit dans les logs",
                "Exemples.nonConforme : demande.logement : valeur non simple (Logement) : choisir une de ses propriétés",
                "Exemples.nonConforme : resultat : paramètre inconnu (resultat)",
                "Exemples.nonConforme : status : nom réservé à la plateforme",
                "Exemples.nonConforme : inconnu : paramètre inconnu (inconnu)",
                "Exemples.nonConforme : demande.prix : propriété inconnue (prix) dans Demande");
        assertThat(Champs.erreurs(methode("lister")))
                .containsExactly("Exemples.lister : resultat : valeur non simple (List) : choisir une de ses propriétés");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nom", "prenom", "nomClient", "email", "emailContact", "telephone", "adresse",
        "codePostal", "dateNaissance", "iban", "motDePasse", "jeton", "authorization", "ip"})
    void reconnaitLesDonneesPersonnelles(String segment) {
        assertThat(Champs.personnel(segment)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombrePieces", "formule", "typeLogement", "reference", "description", "surface",
        "franchise"})
    void laisseLesDonneesMetier(String segment) {
        assertThat(Champs.personnel(segment)).isFalse();
    }
}
