package __PAQUET__.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import __PAQUET__.logging.CaptureLogs.Ligne;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Classes d'exemple internes (non statiques) : jamais détectées par le scan des composants des autres tests. */
class LogsMetierAspectTest {

    record Tarif(String formule, int prix) {
    }

    class TarifService {

        @LogMetier(champs = {"formule", "resultat.prix"})
        public Tarif calculer(String formule, String email) {
            if ("inconnue".equals(formule)) {
                throw new IllegalArgumentException("formule inconnue");
            }
            return new Tarif(formule, 12);
        }

        public Tarif sansLog(String formule) {
            return new Tarif(formule, 0);
        }
    }

    interface Remise {

        int pourcentage(String formule);
    }

    /** Annotation sur la classe, appel par l'interface : champs approuvés retrouvés. */
    class RemiseService implements Remise {

        @Override
        @LogMetier(champs = "formule")
        public int pourcentage(String formule) {
            return 5;
        }
    }

    class TarifRepository implements Repository<Tarif, Long> {

        @LogMetier(champs = "resultat.taille")
        public List<Tarif> findByFormule(String formule) throws InterruptedException {
            if ("lente".equals(formule)) {
                Thread.sleep(LogsMetierAspect.SEUIL_DB_LENT_MS + 10);
            }
            return List.of(new Tarif(formule, 1), new Tarif(formule, 2));
        }

        @LogMetier
        public Optional<Tarif> findFirstByFormule(String formule) {
            throw new IllegalStateException("base indisponible");
        }
    }

    static class FormuleInconnueException extends RuntimeException {
    }

    @RestController
    class TarifControleur {

        @LogMetier(champs = {"formule", "resultat.prix"})
        @GetMapping("/tarifs/{formule}")
        public Tarif tarif(@PathVariable String formule, @RequestParam(required = false) String email) {
            if ("inconnue".equals(formule)) {
                throw new FormuleInconnueException();
            }
            return new Tarif(formule, 12);
        }

        @LogMetier
        @PostMapping("/tarifs/erreur")
        public void erreur() {
            throw new IllegalStateException("panne");
        }

        @GetMapping("/tarifs")
        public List<Tarif> lister() {
            return List.of();
        }

        @ExceptionHandler(FormuleInconnueException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        public void formuleInconnue() {
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T avecLogs(T cible) {
        AspectJProxyFactory fabrique = new AspectJProxyFactory(cible);
        fabrique.setProxyTargetClass(true);
        fabrique.addAspect(new LogsMetierAspect());
        return (T) fabrique.getProxy();
    }

    @Test
    void serviceEcritLesChampsObligatoiresEtApprouves() {
        TarifService service = avecLogs(new TarifService());

        try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
            service.calculer("confort", "marie.dupont@exemple.fr");
            service.sansLog("confort");

            assertThat(logs.lignes()).singleElement().satisfies(ligne -> {
                assertThat(ligne.niveau()).isEqualTo("INFO");
                assertThat(ligne.message()).isEqualTo("service call completed");
                assertThat(ligne.mdc()).containsEntry("layer", "service")
                        .containsEntry("method", "TarifService.calculer")
                        .containsEntry("formule", "confort")
                        .containsEntry("resultat.prix", "12")
                        .containsKey("durationMs")
                        .doesNotContainKey("err");
                assertThat(ligne.mdc().values()).doesNotContain("marie.dupont@exemple.fr");
            });
        }
    }

    @Test
    void serviceAppeleParSonInterfaceGardeSesChamps() {
        AspectJProxyFactory fabrique = new AspectJProxyFactory(new RemiseService());
        fabrique.addInterface(Remise.class);
        fabrique.addAspect(new LogsMetierAspect());
        Remise remise = fabrique.getProxy();

        try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
            remise.pourcentage("confort");

            assertThat(logs.lignes()).singleElement().satisfies(ligne -> assertThat(ligne.mdc())
                    .containsEntry("method", "RemiseService.pourcentage")
                    .containsEntry("formule", "confort"));
        }
    }

    @Test
    void serviceEnEchecEcritLaClasseDeLErreurSansResultat() {
        TarifService service = avecLogs(new TarifService());

        try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
            assertThatThrownBy(() -> service.calculer("inconnue", null)).isInstanceOf(IllegalArgumentException.class);

            Ligne ligne = logs.lignes().getFirst();
            assertThat(ligne.niveau()).isEqualTo("WARN");
            assertThat(ligne.message()).isEqualTo("service call failed");
            assertThat(ligne.mdc()).containsEntry("err", "IllegalArgumentException")
                    .containsEntry("formule", "inconnue")
                    .doesNotContainKey("resultat.prix");
        }
    }

    @Test
    void repositoryEcritEnCoucheDbEtSignaleLesAppelsLents() throws InterruptedException {
        TarifRepository repository = avecLogs(new TarifRepository());

        try (CaptureLogs logs = CaptureLogs.de(LogsMetierAspect.class)) {
            repository.findByFormule("confort");
            repository.findByFormule("lente");
            assertThatThrownBy(() -> repository.findFirstByFormule("confort"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(logs.lignes()).extracting(Ligne::niveau, Ligne::message).containsExactly(
                    tuple("INFO", "db call completed"),
                    tuple("WARN", "slow db call"),
                    tuple("WARN", "db call failed"));
            assertThat(logs.lignes().getFirst().mdc()).containsEntry("layer", "db")
                    .containsEntry("method", "TarifRepository.findByFormule")
                    .containsEntry("resultat.taille", "2");
            assertThat(Long.parseLong(logs.lignes().get(1).mdc().get("durationMs")))
                    .isGreaterThanOrEqualTo(LogsMetierAspect.SEUIL_DB_LENT_MS);
            assertThat(logs.lignes().get(2).mdc()).containsEntry("err", "IllegalStateException");
        }
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(avecLogs(new TarifControleur()))
                .addInterceptors(new LogsControleur())
                .build();
    }

    @Test
    void controleurEcritRouteStatutEtChampsApprouves() throws Exception {
        MockMvc mockMvc = mockMvc();

        try (CaptureLogs logs = CaptureLogs.de(LogsControleur.class)) {
            mockMvc.perform(get("/tarifs/confort").param("email", "marie.dupont@exemple.fr"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/tarifs/inconnue")).andExpect(status().isNotFound());
            mockMvc.perform(get("/tarifs")).andExpect(status().isOk());

            assertThat(logs.lignes()).hasSize(2);
            assertThat(logs.lignes().getFirst().mdc()).containsAllEntriesOf(Map.of(
                    "layer", "controller",
                    "method", "GET",
                    "route", "/tarifs/{formule}",
                    "status", "200",
                    "formule", "confort",
                    "resultat.prix", "12")).containsKey("durationMs");
            assertThat(logs.lignes().getFirst().mdc().values()).doesNotContain("marie.dupont@exemple.fr");
            assertThat(logs.lignes().get(1).mdc()).containsEntry("status", "404")
                    .containsEntry("formule", "inconnue")
                    .doesNotContainKey("resultat.prix");
            assertThat(logs.lignes().get(1).niveau()).isEqualTo("INFO");
        }
    }

    @Test
    void controleurEnErreurNonGereeEcritUn500() {
        MockMvc mockMvc = mockMvc();

        try (CaptureLogs logs = CaptureLogs.de(LogsControleur.class)) {
            assertThatThrownBy(() -> mockMvc.perform(post("/tarifs/erreur")))
                    .hasRootCauseInstanceOf(IllegalStateException.class);

            assertThat(logs.lignes()).singleElement().satisfies(ligne -> {
                assertThat(ligne.niveau()).isEqualTo("WARN");
                assertThat(ligne.message()).isEqualTo("http request failed");
                assertThat(ligne.mdc()).containsEntry("status", "500").containsEntry("route", "/tarifs/erreur");
            });
        }
    }
}
