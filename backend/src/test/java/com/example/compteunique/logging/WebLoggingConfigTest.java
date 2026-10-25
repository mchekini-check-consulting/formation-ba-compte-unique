package com.example.compteunique.logging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class WebLoggingConfigTest {

	@Test
	void enregistreLInterceptorDeLogging() {
		RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
		WebLoggingConfig config = new WebLoggingConfig(interceptor);
		InterceptorRegistry registry = mock(InterceptorRegistry.class);

		config.addInterceptors(registry);

		verify(registry).addInterceptor(interceptor);
	}

}
