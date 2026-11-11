package com.example.compteunique.virement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.Beneficiaire;
import com.example.compteunique.beneficiaire.BeneficiaireService;
import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.compte.CompteNonActifException;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.logging.Loggable;

// CU-147 (US-21) : virement depuis le compte courant de la cliente vers un de ses bénéficiaires actifs (CU-146).
// 1. preparer : contrôles (compte clôturé, bénéficiaire, montant, libellé, plafond, premier virement, solde), puis
//    code SMS ; rien n'est débité.
// 2. signer : avec le bon code dans les 5 minutes, compte verrouillé, solde contrôlé à nouveau (deux virements
//    simultanés : un seul passe), débit, mouvement du jour et statut « exécuté ».
// 3. refuserParBanque (bouchon de test) : recrédit, mouvement de recrédit et statut « refusé » avec le motif.
// Pas de découvert : le solde ne devient jamais négatif. Jamais d'IBAN, de nom, de libellé ni de code dans les logs.
@Service
@Loggable
public class VirementService {

	/** Critère 6 : premier virement vers un bénéficiaire ajouté il y a moins de 24 heures (à valider). */
	static final long PREMIER_VIREMENT_MAX_CENTIMES = 50_000;
	static final Duration DELAI_PREMIER_VIREMENT = Duration.ofHours(24);

	static final String COMPTE_CLOTURE = "Compte clôturé";
	static final String IBAN_DE_TEST = "Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.";
	static final String MONTANT_INVALIDE = "Montant invalide";
	static final String LIBELLE_TROP_LONG = "Libellé trop long";
	static final String PLAFOND_DEPASSE = "Montant supérieur au plafond de virement";
	static final String PREMIER_VIREMENT = "Premier virement vers ce bénéficiaire : montant limité à 500 €";
	static final String SOLDE_INSUFFISANT = "Solde insuffisant";
	static final String CODE_ENVOYE = "Un code à 6 chiffres vous a été envoyé par SMS. Il est valable 5 minutes.";
	static final String CODE_INCORRECT = "Code incorrect";
	static final String VIREMENT_ANNULE = "Code incorrect. Après trois codes incorrects, le virement est annulé.";
	static final String TROIS_CODES_FAUX = "Trois codes incorrects";
	static final String CODE_EXPIRE = "Code expiré";
	static final String VIREMENT_EXECUTE = "Virement exécuté : votre solde a été mis à jour.";
	static final String LIBELLE_PAR_DEFAUT = "Virement";
	static final String NON_EXECUTE = "Seul un virement exécuté peut être refusé par la banque destinataire";
	static final String MOTIF_PAR_DEFAUT = "Refusé par la banque destinataire";
	static final String RECREDITE = "Virement refusé par la banque destinataire : le montant a été recrédité.";

	private final CompteService compteService;
	private final CompteRepository comptes;
	private final MouvementRepository mouvements;
	private final VirementRepository virements;
	private final BeneficiaireService beneficiaires;
	private final CodesSms codes;
	private final boolean ibanDeTestAcceptes;
	private final long plafondCentimes;
	private final Clock horloge;

	/** Récapitulatif affiché avant la saisie du code (critère 9) : jamais l'IBAN complet. */
	public record Recapitulatif(Long virementId, String beneficiaire, String nomBeneficiaire, String ibanMasque,
			long montantCentimes, String libelle, String message) {
	}

	/** Résultat d'un virement exécuté ou recrédité, avec le nouveau solde. */
	public record Execution(String message, long soldeCentimes) {
	}

	@Autowired
	public VirementService(CompteService compteService, CompteRepository comptes, MouvementRepository mouvements,
			VirementRepository virements, BeneficiaireService beneficiaires, CodesSms codes,
			@Value("${app.test.virements-simules}") boolean ibanDeTestAcceptes,
			@Value("${app.virements.plafond-centimes:0}") long plafondCentimes) {
		this(compteService, comptes, mouvements, virements, beneficiaires, codes, ibanDeTestAcceptes,
				plafondCentimes, Clock.systemDefaultZone());
	}

	VirementService(CompteService compteService, CompteRepository comptes, MouvementRepository mouvements,
			VirementRepository virements, BeneficiaireService beneficiaires, CodesSms codes,
			boolean ibanDeTestAcceptes, long plafondCentimes, Clock horloge) {
		this.compteService = compteService;
		this.comptes = comptes;
		this.mouvements = mouvements;
		this.virements = virements;
		this.beneficiaires = beneficiaires;
		this.codes = codes;
		this.ibanDeTestAcceptes = ibanDeTestAcceptes;
		this.plafondCentimes = plafondCentimes;
		this.horloge = horloge;
	}

	/** Critères 1 à 9 et 17 : contrôles, puis code SMS. montant : en euros, tel que saisi (« 12,50 »). */
	@Transactional
	public Recapitulatif preparer(String jetonSession, Long beneficiaireId, String montant, String libelle) {
		Compte compte = compteService.compteCourant(jetonSession);
		if (!compte.isActif()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, COMPTE_CLOTURE);
		}
		Beneficiaire beneficiaire = beneficiaires.actif(compte.getCustomerId(), beneficiaireId);
		if (!ibanDeTestAcceptes && IbanBeneficiaire.estDeTest(beneficiaire.getIban())) {
			throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, IBAN_DE_TEST);
		}
		Long centimes = enCentimes(montant);
		if (centimes == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MONTANT_INVALIDE);
		}
		String libelleSaisi = libelle == null ? "" : libelle.strip();
		if (libelleSaisi.length() > Virement.LIBELLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, LIBELLE_TROP_LONG);
		}
		if (plafondCentimes > 0 && centimes > plafondCentimes) {
			throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, PLAFOND_DEPASSE);
		}
		OffsetDateTime maintenant = maintenant();
		if (centimes > PREMIER_VIREMENT_MAX_CENTIMES && premierVirementRecent(beneficiaire, maintenant)) {
			throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, PREMIER_VIREMENT);
		}
		if (centimes > compte.getSoldeCentimes()) {
			throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, SOLDE_INSUFFISANT);
		}
		String codeHache = codes.envoyer(compte, code -> "Hi-Tech Bank : votre code pour valider le virement de "
				+ euros(centimes) + " est " + code + ". Il est valable 5 minutes. Ne le communiquez à personne.");
		String libelleRetenu = libelleSaisi.isEmpty() ? LIBELLE_PAR_DEFAUT : libelleSaisi;
		Virement virement = virements
				.save(new Virement(compte.getId(), beneficiaire, centimes, libelleRetenu, codeHache, maintenant));
		return new Recapitulatif(virement.getId(), beneficiaire.getLibelle(), beneficiaire.getNomVerifie(),
				IbanBeneficiaire.masquer(beneficiaire.getIban()), centimes, libelleRetenu, CODE_ENVOYE);
	}

	/** Critères 10 à 13 : exécution avec le bon code saisi dans les 5 minutes. */
	@Transactional(noRollbackFor = DemandeRefusee.class)
	public Execution signer(String jetonSession, Long virementId, String code) {
		Long compteId = compteService.compteCourantId(jetonSession);
		Virement virement = virements.verrouiller(virementId, compteId)
				.filter(Virement::isEnAttenteDeSignature)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.GONE, CODE_EXPIRE, true));
		OffsetDateTime maintenant = maintenant();
		switch (CodesSms.controler(virement.getCodeHache(), virement.getCodeEnvoyeLe(), virement.getCodesFaux(),
				code, maintenant)) {
			case EXPIRE -> {
				virement.annuler(CODE_EXPIRE);
				throw new DemandeRefusee(HttpStatus.GONE, CODE_EXPIRE, true);
			}
			case INCORRECT -> {
				virement.codeFaux();
				throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CODE_INCORRECT);
			}
			case INCORRECT_DERNIER -> {
				virement.codeFaux();
				virement.annuler(TROIS_CODES_FAUX);
				throw new DemandeRefusee(HttpStatus.BAD_REQUEST, VIREMENT_ANNULE, true);
			}
			case CORRECT -> {
				// suite ci-dessous
			}
		}
		// Compte relu verrouillé : un autre virement signé en même temps attend la fin de celui-ci.
		Compte compte = comptes.verrouiller(compteId).orElseThrow(CompteInaccessibleException::new);
		if (!compte.isActif()) {
			virement.annuler(COMPTE_CLOTURE);
			throw new DemandeRefusee(HttpStatus.CONFLICT, COMPTE_CLOTURE, true);
		}
		if (virement.getMontantCentimes() > compte.getSoldeCentimes()) {
			virement.annuler(SOLDE_INSUFFISANT);
			throw new DemandeRefusee(HttpStatus.CONFLICT, SOLDE_INSUFFISANT, true);
		}
		compte.debiter(virement.getMontantCentimes(), maintenant);
		mouvements.save(Mouvement.virementSortant(compteId, virement.getMontantCentimes(), virement.getLibelle(),
				virement.getNomBeneficiaire(), virement.getIbanBeneficiaire(), false, maintenant)
				.duVirement(virement.getId()));
		virement.executer(maintenant);
		return new Execution(VIREMENT_EXECUTE, compte.getSoldeCentimes());
	}

	/**
	 * Critère 14 : refus par la banque destinataire (bouchon de test, en attendant un vrai partenaire) : montant
	 * recrédité, mouvement de recrédit, statut « refusé » avec le motif.
	 */
	@Transactional
	public Execution refuserParBanque(Long virementId, String motif) {
		Virement virement = virements.verrouiller(virementId).filter(Virement::isExecute)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, NON_EXECUTE));
		Compte compte = comptes.verrouiller(virement.getCompteId()).orElseThrow(CompteInaccessibleException::new);
		OffsetDateTime maintenant = maintenant();
		try {
			compte.crediter(virement.getMontantCentimes(), maintenant);
		} catch (CompteNonActifException cloture) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, COMPTE_CLOTURE);
		}
		String motifRetenu = motif == null || motif.isBlank() ? MOTIF_PAR_DEFAUT : motif.strip();
		virement.refuser(motifRetenu.length() > 140 ? motifRetenu.substring(0, 140) : motifRetenu);
		mouvements.save(Mouvement.recredit(compte.getId(), virement.getMontantCentimes(), virement.getLibelle(),
				virement.getNomBeneficiaire(), virement.getIbanBeneficiaire(), virement.getId(), maintenant));
		return new Execution(RECREDITE, compte.getSoldeCentimes());
	}

	/** Critère 6 : bénéficiaire ajouté il y a moins de 24 heures, sans virement exécuté vers lui. */
	private boolean premierVirementRecent(Beneficiaire beneficiaire, OffsetDateTime maintenant) {
		return beneficiaire.getAjouteLe().isAfter(maintenant.minus(DELAI_PREMIER_VIREMENT))
				&& !virements.existsByBeneficiaireIdAndStatut(beneficiaire.getId(), Virement.EXECUTE);
	}

	/**
	 * Critère 3 : montant en euros saisi (« 1 000,5 », « 12.50 ») -> centimes ; vide si absent, nul, négatif, avec
	 * plus de deux décimales ou trop grand.
	 */
	static Long enCentimes(String montant) {
		if (montant == null) {
			return null;
		}
		String texte = montant.replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.');
		if (!texte.matches("\\d{1,13}(\\.\\d+)?")) {
			return null;
		}
		BigDecimal valeur = new BigDecimal(texte);
		if (valeur.stripTrailingZeros().scale() > 2) {
			return null;
		}
		long centimes = valeur.movePointRight(2).longValue();
		return centimes > 0 ? centimes : null;
	}

	/** 125050 -> « 1250,50 € » (texte du SMS). */
	static String euros(long centimes) {
		return centimes / 100 + "," + String.format("%02d", centimes % 100) + " €";
	}

	private OffsetDateTime maintenant() {
		return OffsetDateTime.now(horloge);
	}

}
