package com.example.compteunique.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebLoggingConfig implements WebMvcConfigurer {

	private final RequestLoggingInterceptor requestLoggingInterceptor;

	public WebLoggingConfig(RequestLoggingInterceptor requestLoggingInterceptor) {
		this.requestLoggingInterceptor = requestLoggingInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestLoggingInterceptor);
	}

}
