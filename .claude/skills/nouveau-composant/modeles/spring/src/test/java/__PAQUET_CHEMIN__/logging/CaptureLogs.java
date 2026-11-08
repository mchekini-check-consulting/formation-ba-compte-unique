package __PAQUET__.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/** Lignes écrites par un logger, avec le contexte MDC copié au moment du log (il est restauré juste après). */
public final class CaptureLogs extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    public record Ligne(String niveau, String message, Map<String, String> mdc) {
    }

    private final Logger logger;
    private final List<Ligne> lignes = new CopyOnWriteArrayList<>();

    private CaptureLogs(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        start();
        logger.addAppender(this);
    }

    public static CaptureLogs de(Class<?> source) {
        return new CaptureLogs(source);
    }

    @Override
    protected void append(ILoggingEvent event) {
        lignes.add(new Ligne(event.getLevel().toString(), event.getFormattedMessage(),
                Map.copyOf(event.getMDCPropertyMap())));
    }

    public List<Ligne> lignes() {
        return List.copyOf(lignes);
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }
}
