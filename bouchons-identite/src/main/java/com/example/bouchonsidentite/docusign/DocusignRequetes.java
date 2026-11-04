package com.example.bouchonsidentite.docusign;

import java.time.OffsetDateTime;

// Corps des requêtes et réponses du bouchon Docusign (CU-89).
public final class DocusignRequetes {

	private DocusignRequetes() {
	}

	/** POST /docusign/enveloppes */
	public record DemandeEnveloppe(String dossierId) {
	}

	/** POST /docusign/enveloppes/{enveloppeId}/code */
	public record SaisieCode(String code) {
	}

	public record ReponseDocusign(String enveloppeId, ResultatDocusign resultat, String message,
			OffsetDateTime appeleLe) {
	}

}
