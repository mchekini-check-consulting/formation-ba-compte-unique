package com.example.compteunique.logging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class WebLoggingConfigTest {

	@Test
	void enregistreLesInterceptorsDeLogging() {
		RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
		LogsControleur logsControleur = new LogsControleur();
		WebLoggingConfig config = new WebLoggingConfig(interceptor, logsControleur);
		InterceptorRegistry registry = mock(InterceptorRegistry.class);

		config.addInterceptors(registry);

		verify(registry).addInterceptor(interceptor);
		verify(registry).addInterceptor(logsControleur);
	}

}
