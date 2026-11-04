package com.example.bouchonsidentite.docusign;

// Résultats simulés du bouchon Docusign (CU-89).
public enum ResultatDocusign {
	/** Enveloppe créée et code SMS « envoyé » (rien n'est réellement envoyé). */
	ENVELOPPE_ENVOYEE,
	/** Code 111111. */
	SIGNATURE_VALIDE,
	/** Code 000000, et tout autre code que 111111 et 999999. */
	CODE_FAUX,
	/** Code 999999. */
	CODE_EXPIRE,
	/** Demande d'annulation de la signature. */
	ANNULATION_DEMANDEE
}
