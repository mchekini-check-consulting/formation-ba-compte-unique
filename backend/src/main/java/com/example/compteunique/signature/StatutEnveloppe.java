package com.example.compteunique.signature;

public enum StatutEnveloppe {
	/** Code SMS envoyé, en attente de saisie. */
	ENVOYEE,
	/** Code expiré : un nouveau code peut être demandé (une seule relance). */
	EXPIREE,
	SIGNEE,
	ANNULEE
}
