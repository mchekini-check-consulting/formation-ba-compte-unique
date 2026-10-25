package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import net.ttddyy.dsproxy.support.ProxyDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;

class DataSourceLoggingConfigTest {

	private final BeanPostProcessor processor = DataSourceLoggingConfig.dataSourceLoggingBeanPostProcessor();

	@Test
	void envelopeUnDataSourceOrdinaire() {
		DataSource original = mock(DataSource.class);
		Object resultat = processor.postProcessAfterInitialization(original, "dataSource");
		assertThat(resultat).isInstanceOf(ProxyDataSource.class);
	}

	@Test
	void neReenvelopePasUnProxyDataSourceDejaPresent() {
		ProxyDataSource proxy = mock(ProxyDataSource.class);
		Object resultat = processor.postProcessAfterInitialization(proxy, "dataSource");
		assertThat(resultat).isSameAs(proxy);
	}

	@Test
	void laisseLesAutresBeansInchanges() {
		Object bean = new Object();
		Object resultat = processor.postProcessAfterInitialization(bean, "autreBean");
		assertThat(resultat).isSameAs(bean);
	}

}
