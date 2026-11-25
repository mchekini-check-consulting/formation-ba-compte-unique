package com.example.compteunique.catalogue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/**
 * CU-417 : en test seulement, simule la liste des supports indisponible pour vérifier le message de l'écran
 * (CU-412, critère 11). Seule la page de test /test/catalogue la change ; elle est absente en preprod et prod, où la
 * simulation reste donc toujours désactivée. Gardée en mémoire (une seule instance du backend en dev et qualif) : un
 * redémarrage la désactive.
 */
@Component
public class SimulationSupports {

	private final AtomicBoolean indisponible = new AtomicBoolean(false);

	public boolean estIndisponible() {
		return indisponible.get();
	}

	void indisponible(boolean valeur) {
		indisponible.set(valeur);
	}

}
