package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerMapping;

class RequestLoggingInterceptorTest {

	private final RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();

	@Test
	void nePasLaisserLeLayerEnMdcApresCompletion() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("/items");
		when(request.getMethod()).thenReturn("GET");
		when(response.getStatus()).thenReturn(200);

		interceptor.preHandle(request, response, new Object());
		interceptor.afterCompletion(request, response, new Object(), null);

		assertThat(MDC.get("layer")).isNull();
	}

	@Test
	void utiliseLUriBrutSiAucunPatternResolu() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn(null);
		when(request.getRequestURI()).thenReturn("/items");
		when(request.getMethod()).thenReturn("GET");
		when(response.getStatus()).thenReturn(200);

		interceptor.afterCompletion(request, response, new Object(), null);

		assertThat(MDC.get("layer")).isNull();
	}

}
