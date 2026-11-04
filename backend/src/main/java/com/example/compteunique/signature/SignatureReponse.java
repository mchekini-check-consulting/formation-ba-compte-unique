package com.example.compteunique.signature;

import java.time.OffsetDateTime;

import com.example.compteunique.dossier.Categories.StatutDossier;

/**
 * État de la signature affiché par l'écran (CU-12).
 *
 * @param etat               A_SIGNER (aucun code demandé), ENVOYEE, EXPIREE, SIGNEE ou ANNULEE
 * @param nouveauCodePossible un nouveau code peut être demandé (code expiré, pas encore de relance)
 */
public record SignatureReponse(
		String etat,
		String enveloppeId,
		int codesFaux,
		int essaisRestants,
		boolean nouveauCodePossible,
		String motifAnnulation,
		OffsetDateTime signeeLe,
		String convention,
		String empreinteDocument,
		StatutDossier statutDossier) {

	static final String A_SIGNER = "A_SIGNER";

	static SignatureReponse aSigner(StatutDossier statutDossier) {
		return new SignatureReponse(A_SIGNER, null, 0, SignatureService.CODES_FAUX_MAX, false, null, null,
				Convention.TEXTE, Convention.EMPREINTE, statutDossier);
	}

	static SignatureReponse de(SignatureConvention signature, StatutDossier statutDossier) {
		return new SignatureReponse(signature.getStatutEnveloppe().name(), signature.getEnveloppeId(),
				signature.getCodesFaux(), Math.max(0, SignatureService.CODES_FAUX_MAX - signature.getCodesFaux()),
				signature.getStatutEnveloppe() == StatutEnveloppe.EXPIREE
						&& signature.getRelances() < SignatureService.RELANCES_MAX,
				signature.getMotifAnnulation(), signature.getSigneeLe(), Convention.TEXTE,
				signature.getEmpreinteDocument(), statutDossier);
	}

}
