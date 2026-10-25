package com.example.compteunique.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class SlowQueryLoggingListenerTest {

	private final SlowQueryLoggingListener listener = new SlowQueryLoggingListener();

	@Test
	void nIgnorePasEtNettoieLeMdcQuandLaRequeteEstLente() {
		ExecutionInfo executionInfo = mock(ExecutionInfo.class);
		when(executionInfo.getElapsedTime()).thenReturn(SlowQueryLoggingListener.SEUIL_LENT_MS + 1);
		when(executionInfo.getThrowable()).thenReturn(null);

		listener.afterQuery(executionInfo, List.of(new QueryInfo("select 1")));

		assertThat(MDC.get("layer")).isNull();
	}

	@Test
	void nettoieLeMdcQuandLaRequeteEstEnErreurMemeRapide() {
		ExecutionInfo executionInfo = mock(ExecutionInfo.class);
		when(executionInfo.getElapsedTime()).thenReturn(1L);
		when(executionInfo.getThrowable()).thenReturn(new RuntimeException("boom"));

		listener.afterQuery(executionInfo, List.of(new QueryInfo("select 1")));

		assertThat(MDC.get("layer")).isNull();
	}

	@Test
	void nIgnoreLaRequeteNormaleSansLogguer() {
		ExecutionInfo executionInfo = mock(ExecutionInfo.class);
		when(executionInfo.getElapsedTime()).thenReturn(1L);
		when(executionInfo.getThrowable()).thenReturn(null);

		listener.afterQuery(executionInfo, List.of(new QueryInfo("select 1")));

		assertThat(MDC.get("layer")).isNull();
	}

	@Test
	void beforeQueryNeDeclencheAucunLog() {
		listener.beforeQuery(mock(ExecutionInfo.class), List.of(new QueryInfo("select 1")));

		assertThat(MDC.get("layer")).isNull();
	}

}
