package com.example.compteunique.beneficiaire;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.virement.IbanBeneficiaire;

// CU-146 (US-22) : bénéficiaires de la cliente connectée (customer_id de son compte courant). Ajout en deux temps :
// contrôles et vérification du nom (CU-145), puis code SMS ; le bénéficiaire n'est enregistré qu'avec le bon code.
// Une cliente ne voit et ne modifie jamais les bénéficiaires d'une autre (« Bénéficiaire introuvable »).
// Les refus dont l'état doit rester enregistré (journal de vérification, code faux compté, ajout annulé) ne
// défont pas la transaction (noRollbackFor). Jamais d'IBAN, de nom, de libellé ni de code dans les logs.
@Service
@Loggable
public class BeneficiaireService {

	/** CU-146 critère 12 : plafond provisoire, à valider. */
	static final int MAXIMUM = 50;

	static final String NOM_VIDE = "Indiquez un nom pour ce bénéficiaire";
	static final String IBAN_INVALIDE = "IBAN invalide";
	static final String LIBELLE_TROP_LONG = "Libellé trop long";
	static final String NE_CORRESPOND_PAS = "Le nom ne correspond pas à ce compte. Vérifiez la saisie.";
	static final String INTROUVABLE = "Ce compte n’a pas pu être trouvé. Vérifiez l’IBAN.";
	static final String INDISPONIBLE = "La vérification est momentanément indisponible. Réessayez plus tard.";
	static final String EXISTE_DEJA = "Ce bénéficiaire existe déjà";
	static final String MAXIMUM_ATTEINT = "Vous avez atteint le nombre maximum de bénéficiaires";
	static final String CODE_ENVOYE = "Un code à 6 chiffres vous a été envoyé par SMS. Il est valable 5 minutes.";
	static final String CODE_INCORRECT = "Code incorrect";
	static final String AJOUT_ANNULE = "Code incorrect. Après trois codes incorrects, l’ajout est annulé.";
	static final String CODE_EXPIRE = "Code expiré";
	static final String BENEFICIAIRE_INTROUVABLE = "Bénéficiaire introuvable";

	private final CompteService comptes;
	private final BeneficiaireRepository beneficiaires;
	private final AjoutBeneficiaireRepository ajouts;
	private final VerificationBeneficiaire verification;
	private final CodesSms codes;
	private final boolean ibanDeTestAcceptes;
	private final Clock horloge;

	/** Ajout envoyé à la vérification et en attente du code SMS. */
	public record AjoutEnAttente(Long ajoutId, String nomVerifie, String message) {
	}

	@Autowired
	public BeneficiaireService(CompteService comptes, BeneficiaireRepository beneficiaires,
			AjoutBeneficiaireRepository ajouts, VerificationBeneficiaire verification, CodesSms codes,
			@Value("${app.test.virements-simules}") boolean ibanDeTestAcceptes) {
		this(comptes, beneficiaires, ajouts, verification, codes, ibanDeTestAcceptes, Clock.systemDefaultZone());
	}

	BeneficiaireService(CompteService comptes, BeneficiaireRepository beneficiaires, AjoutBeneficiaireRepository ajouts,
			VerificationBeneficiaire verification, CodesSms codes, boolean ibanDeTestAcceptes, Clock horloge) {
		this.comptes = comptes;
		this.beneficiaires = beneficiaires;
		this.ajouts = ajouts;
		this.verification = verification;
		this.codes = codes;
		this.ibanDeTestAcceptes = ibanDeTestAcceptes;
		this.horloge = horloge;
	}

	/** Critère 11 et 15 : bénéficiaires actifs de la cliente, du plus récent au plus ancien. */
	@Transactional(readOnly = true)
	public List<Beneficiaire> lister(String jetonSession) {
		return beneficiaires.findByCustomerIdAndStatutOrderByAjouteLeDescIdDesc(customerId(jetonSession),
				Beneficiaire.ACTIF);
	}

	/**
	 * Critères 1 à 8 et 12 : contrôles, vérification du nom, puis envoi du code SMS. libelle vide : le nom vérifié.
	 */
	@Transactional(noRollbackFor = DemandeRefusee.class)
	public AjoutEnAttente demanderAjout(String jetonSession, String iban, String nom, String libelle) {
		Compte compte = comptes.compteCourant(jetonSession);
		UUID customerId = compte.getCustomerId();
		String nomSaisi = nom == null ? "" : nom.strip();
		if (nomSaisi.isEmpty()) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, NOM_VIDE);
		}
		String ibanSaisi = IbanBeneficiaire.normaliser(iban);
		if (!IbanBeneficiaire.estValide(ibanSaisi)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, IBAN_INVALIDE);
		}
		String libelleSaisi = libelle == null ? "" : libelle.strip();
		if (libelleSaisi.length() > Beneficiaire.LIBELLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, LIBELLE_TROP_LONG);
		}
		// Critère 19 : en production, un IBAN de test (CU-88) n'existe pas.
		if (!ibanDeTestAcceptes && IbanBeneficiaire.estDeTest(ibanSaisi)) {
			throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, INTROUVABLE);
		}
		controlerDoublonEtMaximum(customerId, ibanSaisi);
		Verification verifiee = verification.verifier(ibanSaisi, nomSaisi);
		switch (verifiee.resultat()) {
			case NE_CORRESPOND_PAS -> throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, NE_CORRESPOND_PAS);
			case INTROUVABLE -> throw new DemandeRefusee(HttpStatus.UNPROCESSABLE_CONTENT, INTROUVABLE);
			case INDISPONIBLE -> throw new DemandeRefusee(HttpStatus.SERVICE_UNAVAILABLE, INDISPONIBLE);
			case CORRESPOND -> {
				// suite ci-dessous
			}
		}
		String codeHache = codes.envoyer(compte, code -> "Hi-Tech Bank : votre code pour ajouter un bénéficiaire est "
				+ code + ". Il est valable 5 minutes. Ne le communiquez à personne.");
		AjoutBeneficiaire ajout = ajouts.save(new AjoutBeneficiaire(customerId, ibanSaisi, verifiee,
				libelleSaisi.isEmpty() ? verifiee.nomVerifie() : libelleSaisi, codeHache, maintenant()));
		return new AjoutEnAttente(ajout.getId(), verifiee.nomVerifie(), CODE_ENVOYE);
	}

	/** Critères 8 à 10 : le bénéficiaire n'est enregistré qu'avec le bon code, saisi dans les 5 minutes. */
	@Transactional(noRollbackFor = DemandeRefusee.class)
	public Beneficiaire confirmerAjout(String jetonSession, Long ajoutId, String code) {
		UUID customerId = customerId(jetonSession);
		AjoutBeneficiaire ajout = ajouts.verrouiller(ajoutId, customerId)
				.filter(AjoutBeneficiaire::isEnAttente)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.GONE, CODE_EXPIRE, true));
		switch (CodesSms.controler(ajout.getCodeHache(), ajout.getCodeEnvoyeLe(), ajout.getCodesFaux(), code,
				maintenant())) {
			case EXPIRE -> {
				ajout.terminer(AjoutBeneficiaire.EXPIRE);
				throw new DemandeRefusee(HttpStatus.GONE, CODE_EXPIRE, true);
			}
			case INCORRECT -> {
				ajout.codeFaux();
				throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CODE_INCORRECT);
			}
			case INCORRECT_DERNIER -> {
				ajout.codeFaux();
				ajout.terminer(AjoutBeneficiaire.ANNULE);
				throw new DemandeRefusee(HttpStatus.BAD_REQUEST, AJOUT_ANNULE, true);
			}
			case CORRECT -> {
				// suite ci-dessous
			}
		}
		try {
			controlerDoublonEtMaximum(customerId, ajout.getIban());
		} catch (DemandeRefusee refus) {
			ajout.terminer(AjoutBeneficiaire.ANNULE);
			throw new DemandeRefusee(refus.getStatut(), refus.getMessage(), true);
		}
		ajout.terminer(AjoutBeneficiaire.CONFIRME);
		return beneficiaires.save(new Beneficiaire(ajout, maintenant()));
	}

	/** Critère 13 : seul le libellé change ; vide : le nom vérifié. */
	@Transactional
	public Beneficiaire renommer(String jetonSession, Long id, String libelle) {
		Beneficiaire beneficiaire = actif(customerId(jetonSession), id);
		String nouveau = libelle == null ? "" : libelle.strip();
		if (nouveau.length() > Beneficiaire.LIBELLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, LIBELLE_TROP_LONG);
		}
		beneficiaire.renommer(nouveau.isEmpty() ? beneficiaire.getNomVerifie() : nouveau);
		return beneficiaire;
	}

	/** Critères 15 et 16 : statut « supprimé », la ligne reste ; une seconde suppression est refusée. */
	@Transactional
	public void supprimer(String jetonSession, Long id) {
		actif(customerId(jetonSession), id).supprimer(maintenant());
	}

	/** CU-147 : bénéficiaire actif de la cliente, sinon « Bénéficiaire introuvable » (autre cliente, supprimé). */
	@Transactional(readOnly = true)
	public Beneficiaire actif(UUID customerId, Long id) {
		return beneficiaires.findByIdAndCustomerIdAndStatut(id, customerId, Beneficiaire.ACTIF)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.NOT_FOUND, BENEFICIAIRE_INTROUVABLE));
	}

	private void controlerDoublonEtMaximum(UUID customerId, String iban) {
		if (beneficiaires.existsByCustomerIdAndIbanAndStatut(customerId, iban, Beneficiaire.ACTIF)) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, EXISTE_DEJA);
		}
		if (beneficiaires.countByCustomerIdAndStatut(customerId, Beneficiaire.ACTIF) >= MAXIMUM) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, MAXIMUM_ATTEINT);
		}
	}

	private UUID customerId(String jetonSession) {
		return comptes.compteCourant(jetonSession).getCustomerId();
	}

	private OffsetDateTime maintenant() {
		return OffsetDateTime.now(horloge);
	}

}
