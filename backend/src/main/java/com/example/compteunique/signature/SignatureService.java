package com.example.compteunique.signature;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;

// CU-12 (US-07) : signature de la convention avec un code reçu par SMS (second facteur).
// L'écran appelle Docusign (ou son bouchon, CU-89) puis transmet ici l'enveloppe envoyée et le
// résultat de chaque saisie ; les règles (validité 5 minutes, une relance, 3 codes faux,
// statut du dossier) sont appliquées ici.
@Service
@Loggable
public class SignatureService {

	static final Duration VALIDITE_CODE = Duration.ofMinutes(5);
	static final int CODES_FAUX_MAX = 3;
	static final int RELANCES_MAX = 1;

	static final String MOTIF_CODE_EXPIRE = "Code expiré";
	static final String MOTIF_TENTATIVES = "3 tentatives dépassées";
	static final String MOTIF_ANNULATION = "Annulation demandée";

	private final DossierRepository dossiers;
	private final SignatureConventionRepository signatures;
	private final ApplicationEventPublisher evenements;
	private final Clock horloge;

	@Autowired
	public SignatureService(DossierRepository dossiers, SignatureConventionRepository signatures,
			ApplicationEventPublisher evenements) {
		this(dossiers, signatures, evenements, Clock.systemDefaultZone());
	}

	SignatureService(DossierRepository dossiers, SignatureConventionRepository signatures,
			ApplicationEventPublisher evenements, Clock horloge) {
		this.dossiers = dossiers;
		this.signatures = signatures;
		this.evenements = evenements;
		this.horloge = horloge;
	}

	/** État de la signature, pour reprendre au même endroit après une fermeture de l'espace. */
	@Transactional(readOnly = true)
	public SignatureReponse etat(String reference) {
		Dossier dossier = dossier(reference);
		return signatures.findByDossierId(dossier.getId())
				.map(signature -> SignatureReponse.de(signature, dossier.getStatut()))
				.orElseGet(() -> SignatureReponse.aSigner(dossier.getStatut()));
	}

	/** Enveloppe envoyée par Docusign (code SMS envoyé) : première demande ou nouveau code après expiration. */
	@Transactional
	public SignatureReponse enveloppeEnvoyee(String reference, String enveloppeId) {
		if (enveloppeId == null || enveloppeId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enveloppeId obligatoire");
		}
		Dossier dossier = dossier(reference);
		if (!dossier.estPretASigner()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "convention non prête à signer");
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		SignatureConvention signature = signatures.findByDossierId(dossier.getId()).orElse(null);
		if (signature == null) {
			signature = signatures.save(
					new SignatureConvention(dossier.getId(), enveloppeId, Convention.EMPREINTE, maintenant));
		} else if (signature.getStatutEnveloppe() == StatutEnveloppe.EXPIREE
				&& signature.getRelances() < RELANCES_MAX) {
			signature.relancer(enveloppeId, maintenant);
		} else {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "nouveau code impossible");
		}
		return SignatureReponse.de(signature, dossier.getStatut());
	}

	/** Résultat de la saisie du code (ou de l'annulation) transmis par l'écran. */
	@Transactional
	public SignatureReponse enregistrerResultat(String reference, ResultatSignature resultat) {
		if (resultat == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resultat obligatoire");
		}
		Dossier dossier = dossier(reference);
		SignatureConvention signature = signatures.findByDossierId(dossier.getId())
				.filter(s -> s.getStatutEnveloppe() == StatutEnveloppe.ENVOYEE
						|| resultat == ResultatSignature.ANNULATION_DEMANDEE
								&& s.getStatutEnveloppe() == StatutEnveloppe.EXPIREE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "aucune signature en cours"));
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		if (resultat == ResultatSignature.ANNULATION_DEMANDEE) {
			annuler(signature, dossier, MOTIF_ANNULATION);
		} else if (signature.getCodesFaux() >= CODES_FAUX_MAX) {
			annuler(signature, dossier, MOTIF_TENTATIVES);
		} else if (resultat == ResultatSignature.CODE_EXPIRE
				|| maintenant.isAfter(signature.getCodeEnvoyeLe().plus(VALIDITE_CODE))) {
			codeExpire(signature, dossier);
		} else if (resultat == ResultatSignature.CODE_FAUX) {
			signature.codeFaux();
		} else {
			signature.signer(maintenant);
			dossier.conventionSignee(maintenant);
			evenements.publishEvent(new ConventionSigneeEvent(dossier.getId()));
		}
		return SignatureReponse.de(signature, dossier.getStatut());
	}

	/** Premier code expiré : un nouveau code peut être demandé ; code de la relance expiré : annulation. */
	private static void codeExpire(SignatureConvention signature, Dossier dossier) {
		if (signature.getRelances() < RELANCES_MAX) {
			signature.codeExpire();
		} else {
			annuler(signature, dossier, MOTIF_CODE_EXPIRE);
		}
	}

	private static void annuler(SignatureConvention signature, Dossier dossier, String motif) {
		signature.annuler(motif);
		dossier.annuler();
	}

	private Dossier dossier(String reference) {
		return dossiers.findByReference(reference)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dossier inconnu"));
	}

}
