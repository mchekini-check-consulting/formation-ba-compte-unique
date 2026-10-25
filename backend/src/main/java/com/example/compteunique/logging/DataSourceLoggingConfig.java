package com.example.compteunique.logging;

import javax.sql.DataSource;

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Enveloppe le DataSource auto-configure par Spring Boot (y compris celui fourni par
// Testcontainers en test d'integration) plutot que d'en definir un nouveau, pour ne
// pas court-circuiter sa configuration de connexion.
@Configuration
public class DataSourceLoggingConfig {

	@Bean
	public static BeanPostProcessor dataSourceLoggingBeanPostProcessor() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
					return ProxyDataSourceBuilder.create(dataSource)
							.name("db")
							.listener(new SlowQueryLoggingListener())
							.build();
				}
				return bean;
			}
		};
	}

}
