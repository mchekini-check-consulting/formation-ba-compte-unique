package com.example.compteunique.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggableAspect {

	private static final Logger log = LoggerFactory.getLogger(LoggableAspect.class);
	private static final String LAYER = "layer";

	@Around("@within(com.example.compteunique.logging.Loggable) && execution(public * *(..))")
	public Object logMethode(ProceedingJoinPoint jointPoint) throws Throwable {
		String methode = jointPoint.getSignature().toShortString();
		long debut = System.currentTimeMillis();
		MDC.put(LAYER, "service");
		try {
			log.info("{} : debut", methode);
			Object resultat = jointPoint.proceed();
			log.info("{} : fin ({} ms)", methode, System.currentTimeMillis() - debut);
			return resultat;
		} catch (Throwable erreur) {
			log.info("{} : echec ({} ms)", methode, System.currentTimeMillis() - debut);
			throw erreur;
		} finally {
			MDC.remove(LAYER);
		}
	}

}
