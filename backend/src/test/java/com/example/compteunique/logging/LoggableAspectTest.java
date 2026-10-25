package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class LoggableAspectTest {

	@Loggable
	static class ServiceDeTest {

		public String operation(String valeur) {
			if ("echec".equals(valeur)) {
				throw new IllegalStateException("echec");
			}
			return valeur.toUpperCase();
		}

	}

	private ServiceDeTest proxy() {
		AspectJProxyFactory factory = new AspectJProxyFactory(new ServiceDeTest());
		factory.addAspect(new LoggableAspect());
		return factory.getProxy();
	}

	@Test
	void appelleLaMethodeEtNettoieLeMdc() {
		ServiceDeTest service = proxy();
		assertThat(service.operation("ok")).isEqualTo("OK");
		assertThat(MDC.get("layer")).isNull();
	}

	@Test
	void propageLExceptionEtNettoieLeMdc() {
		ServiceDeTest service = proxy();
		assertThatThrownBy(() -> service.operation("echec")).isInstanceOf(IllegalStateException.class);
		assertThat(MDC.get("layer")).isNull();
	}

}
