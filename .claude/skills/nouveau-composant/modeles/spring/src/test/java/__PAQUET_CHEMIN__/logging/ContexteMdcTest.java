package __PAQUET__.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ContexteMdcTest {

    @AfterEach
    void viderMdc() {
        MDC.clear();
    }

    @Test
    void restaureLeContexteEnglobantALaFermeture() {
        MDC.put("layer", "service");

        try (ContexteMdc contexte = ContexteMdc.ouvrir().avec("layer", "db").avec(Map.of("durationMs", "3"))) {
            contexte.avec("layer", "db");
            assertThat(MDC.get("layer")).isEqualTo("db");
            assertThat(MDC.get("durationMs")).isEqualTo("3");
        }

        assertThat(MDC.get("layer")).isEqualTo("service");
        assertThat(MDC.get("durationMs")).isNull();
    }
}
