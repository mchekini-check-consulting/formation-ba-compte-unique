package com.example.compteunique.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
	private static final String LAYER = "layer";
	private static final String ATTRIBUT_DEBUT = RequestLoggingInterceptor.class.getName() + ".debut";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		request.setAttribute(ATTRIBUT_DEBUT, System.currentTimeMillis());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception exception) {
		Long debut = (Long) request.getAttribute(ATTRIBUT_DEBUT);
		long duree = debut == null ? 0 : System.currentTimeMillis() - debut;
		MDC.put(LAYER, "controller");
		try {
			log.info("{} {} -> {} ({} ms)", request.getMethod(), route(request), response.getStatus(), duree);
		} finally {
			MDC.remove(LAYER);
		}
	}

	private String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern != null ? pattern.toString() : request.getRequestURI();
	}

}
