package com.example.compteunique.fonctionnalites;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Lot 3 (PO, 27/11/2026) : fonction publique du module de configuration des fonctionnalités (ADR-001). Un interrupteur
 * par fonctionnalité et par environnement (helm.yaml, variables FONCTIONNALITE_*, section app.fonctionnalites) : livrer
 * une fonctionnalité, c'est passer son interrupteur à true ; l'écran affiche sinon son bouton en « Bientôt ». Lecture
 * seule, aucune donnée en base.
 */
@Service
public class Fonctionnalites {

	private final Map<String, Boolean> etats;

	public Fonctionnalites(Environment environnement) {
		this.etats = new TreeMap<>(Binder.get(environnement)
				.bind("app.fonctionnalites", Bindable.mapOf(String.class, Boolean.class)).orElse(Map.of()));
	}

	/** Nom et état de chaque interrupteur, par ordre alphabétique. */
	public Map<String, Boolean> etats() {
		return etats;
	}

}
