package com.example.bouchonsidentite.docusign;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Textes;
import com.example.bouchonsidentite.docusign.DocusignRequetes.ReponseDocusign;
import com.example.bouchonsidentite.logging.Loggable;

// CU-89 : bouchon de la signature Docusign. Simule l'enveloppe, l'envoi du code SMS et le
// résultat, sans aucun appel à Docusign ni envoi de SMS. Codes de test :
//   111111 -> signature valide
//   000000 -> code faux (comme tout autre code à 6 chiffres)
//   999999 -> code expiré
// Chaque appel est tracé en base (date, code reçu, résultat).
@Service
@Loggable
public class DocusignService {

	static final String CODE_VALIDE = "111111";
	static final String CODE_EXPIRE = "999999";

	private final AppelDocusignRepository appels;
	private final Clock horloge;

	public DocusignService(AppelDocusignRepository appels, Clock horloge) {
		this.appels = appels;
		this.horloge = horloge;
	}

	public ReponseDocusign envoyerEnveloppe(String dossierId) {
		String dossier = Textes.obligatoire(dossierId, "dossierId");
		return tracer(dossier, UUID.randomUUID().toString(), OperationDocusign.ENVOI_ENVELOPPE, null,
				ResultatDocusign.ENVELOPPE_ENVOYEE, "Code de signature envoyé par SMS");
	}

	public ReponseDocusign saisirCode(String enveloppeId, String code) {
		AppelDocusign envoi = enveloppe(enveloppeId);
		String saisi = Textes.obligatoire(code, "code");
		if (!saisi.matches("\\d{6}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code : 6 chiffres");
		}
		return switch (saisi) {
			case CODE_VALIDE -> tracer(envoi.getDossierId(), enveloppeId, OperationDocusign.SAISIE_CODE, saisi,
					ResultatDocusign.SIGNATURE_VALIDE, "Signature valide");
			case CODE_EXPIRE -> tracer(envoi.getDossierId(), enveloppeId, OperationDocusign.SAISIE_CODE, saisi,
					ResultatDocusign.CODE_EXPIRE, "Code expiré");
			default -> tracer(envoi.getDossierId(), enveloppeId, OperationDocusign.SAISIE_CODE, saisi,
					ResultatDocusign.CODE_FAUX, "Code faux");
		};
	}

	public ReponseDocusign annuler(String enveloppeId) {
		AppelDocusign envoi = enveloppe(enveloppeId);
		return tracer(envoi.getDossierId(), enveloppeId, OperationDocusign.ANNULATION, null,
				ResultatDocusign.ANNULATION_DEMANDEE, "Annulation demandée");
	}

	private AppelDocusign enveloppe(String enveloppeId) {
		return appels.findFirstByEnveloppeIdAndOperation(Textes.obligatoire(enveloppeId, "enveloppeId"),
				OperationDocusign.ENVOI_ENVELOPPE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "enveloppe inconnue"));
	}

	private ReponseDocusign tracer(String dossierId, String enveloppeId, OperationDocusign operation, String code,
			ResultatDocusign resultat, String message) {
		AppelDocusign appel = appels.save(
				new AppelDocusign(dossierId, enveloppeId, operation, code, resultat, OffsetDateTime.now(horloge)));
		return new ReponseDocusign(enveloppeId, resultat, message, appel.getAppeleLe());
	}

}
