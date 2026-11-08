package __PAQUET__.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Une ligne JSON par log (time, level, msg, logger, puis le contexte MDC : layer...), lue par Alloy. Pour une
 * exception, seulement sa classe : son message peut contenir des données personnelles.
 */
public class JsonLogLayout extends LayoutBase<ILoggingEvent> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("time", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        champs.put("level", event.getLevel().toString().toLowerCase(Locale.ROOT));
        champs.put("msg", event.getFormattedMessage());
        champs.put("logger", event.getLoggerName());
        champs.putAll(event.getMDCPropertyMap());

        IThrowableProxy erreur = event.getThrowableProxy();
        if (erreur != null) {
            champs.put("err", erreur.getClassName());
        }

        try {
            return mapper.writeValueAsString(champs) + System.lineSeparator();
        } catch (JacksonException e) {
            return "{\"level\":\"error\",\"msg\":\"log serialization failed\"}" + System.lineSeparator();
        }
    }
}
