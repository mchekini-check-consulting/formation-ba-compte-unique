package __PAQUET__.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebLogsConfig implements WebMvcConfigurer {

    private final LogsControleur logsControleur;

    public WebLogsConfig(LogsControleur logsControleur) {
        this.logsControleur = logsControleur;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logsControleur);
    }
}
