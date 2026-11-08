package __PAQUET__.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class JsonLogLayoutTest {

    private final JsonLogLayout layout = new JsonLogLayout();
    private final ObjectMapper mapper = new ObjectMapper();

    private LoggingEvent evenement(Throwable erreur) {
        LoggerContext contexte = new LoggerContext();
        LoggingEvent evenement = new LoggingEvent("fqcn", contexte.getLogger("exemple"), Level.WARN,
                "service call {}", erreur, new Object[] {"failed"});
        evenement.setMDCPropertyMap(Map.of("layer", "service", "method", "TarifService.calculer"));
        evenement.setTimeStamp(0);
        return evenement;
    }

    @Test
    void ecritUneLigneJsonAvecLeContexteMdc() {
        String ligne = layout.doLayout(evenement(null));

        assertThat(ligne).endsWith(System.lineSeparator());
        JsonNode json = mapper.readTree(ligne);
        assertThat(json.get("time").asString()).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(json.get("level").asString()).isEqualTo("warn");
        assertThat(json.get("msg").asString()).isEqualTo("service call failed");
        assertThat(json.get("logger").asString()).isEqualTo("exemple");
        assertThat(json.get("layer").asString()).isEqualTo("service");
        assertThat(json.get("method").asString()).isEqualTo("TarifService.calculer");
        assertThat(json.has("err")).isFalse();
    }

    @Test
    void neGardeQueLaClasseDeLErreurPasSonMessage() {
        String ligne = layout.doLayout(evenement(new IllegalStateException("marie.dupont@exemple.fr")));

        assertThat(mapper.readTree(ligne).get("err").asString()).isEqualTo("java.lang.IllegalStateException");
        assertThat(ligne).doesNotContain("marie.dupont");
    }
}
