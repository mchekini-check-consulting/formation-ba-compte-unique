package com.example.compteunique.ordres;

import java.util.EnumSet;
import java.util.Set;

/** CU-376 : statuts d'un ordre. Les ordres « demandé » et « en cours » bloquent le montant vendu. */
public enum StatutOrdre {
	DEMANDE, EN_COURS, EXECUTE, REJETE, ANNULE;

	/** Statuts qui bloquent le montant : montant disponible = encours − ordres en cours. */
	static final Set<StatutOrdre> EN_COURS_D_EXECUTION = EnumSet.of(DEMANDE, EN_COURS);
}
