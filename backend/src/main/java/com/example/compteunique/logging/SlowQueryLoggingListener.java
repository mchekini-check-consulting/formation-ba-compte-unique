package com.example.compteunique.logging;

import java.util.List;
import java.util.stream.Collectors;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class SlowQueryLoggingListener implements QueryExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(SlowQueryLoggingListener.class);
	private static final String LAYER = "layer";
	static final long SEUIL_LENT_MS = 200;

	@Override
	public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
	}

	@Override
	public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
		boolean enErreur = executionInfo.getThrowable() != null;
		long duree = executionInfo.getElapsedTime();
		if (!enErreur && duree < SEUIL_LENT_MS) {
			return;
		}
		String requetes = queryInfoList.stream().map(QueryInfo::getQuery).collect(Collectors.joining("; "));
		MDC.put(LAYER, "db");
		try {
			if (enErreur) {
				log.warn("requete en echec ({} ms) : {}", duree, requetes, executionInfo.getThrowable());
			} else {
				log.warn("requete lente ({} ms) : {}", duree, requetes);
			}
		} finally {
			MDC.remove(LAYER);
		}
	}

}
