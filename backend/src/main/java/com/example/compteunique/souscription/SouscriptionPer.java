package com.example.compteunique.souscription;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.beneficiaire.CodesSms;
import com.example.compteunique.beneficiaire.DemandeRefusee;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteInaccessibleException;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.ModeDeGestion;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;

/**
 * CU-312 (PER-04) : souscrire un PER par code SMS et faire un premier versement (module Souscription, ADR-001).
 * <ul>
 * <li>Profil investisseur validé de moins de 12 mois obligatoire (CU-10), contrôlé à la saisie et à la signature.</li>
 * <li>Premier versement d'au moins 100 € depuis le compte courant Hi-Tech Bank (seule source, au nom de la
 * cliente), avec l'origine des fonds ; affecté au compartiment 1.</li>
 * <li>Signature par code SMS distincte de la convention : code valable 5 minutes, nouveau code possible ; au 3e code
 * erroné, signature bloquée 30 minutes (décision PO du 22/11/2026).</li>
 * <li>Code correct : compte courant débité, contrat créé en gestion pilotée (module Gestion) et versement réparti
 * selon l'allocation (ordres, contrôle d'adéquation CU-349), dans une seule transaction.</li>
 * <li>PPE : reporté au contrôle PPE (décision BA du 27/11/2026).</li>
 * </ul>
 */
@Service
@Loggable
public class SouscriptionPer {

	static final Produit PER = Produit.PER;
	static final BigDecimal MINIMUM = new BigDecimal("100");
	static final Duration BLOCAGE = Duration.ofMinutes(30);
	static final String AUTRE = "AUTRE";
	static final Set<String> ORIGINES = Set.of("REVENUS", "EPARGNE", "HERITAGE_DONATION", "VENTE_BIEN", AUTRE);
	static final int PRECISION_LONGUEUR_MAX = 200;
	private static final int MONTANT_ECHELLE_MAX = 2;

	static final String PROFIL_REQUIS = "Complétez ou revalidez votre profil investisseur avant de souscrire.";
	static final String MESSAGE_MINIMUM = "Le premier versement minimum est de 100 €";
	static final String MONTANT_INVALIDE = "Saisissez un montant en euros et centimes.";
	static final String ORIGINE_REQUISE = "Indiquez l'origine des fonds.";
	static final String PRECISION_REQUISE = "Précisez l'origine des fonds (200 caractères au plus).";
	static final String SOLDE_INSUFFISANT = "Solde de votre compte courant insuffisant";
	static final String DEJA_SOUSCRIT = "Vous avez déjà un PER.";
	static final String VERSEMENT_REQUIS = "Indiquez d'abord votre premier versement.";
	static final String CODE_REQUIS = "Demandez un code pour signer.";
	static final String CODE_EXPIRE = "Code expiré : demandez un nouveau code.";
	static final String CODE_INCORRECT = "Code incorrect : vérifiez-le ou demandez un nouveau code.";
	static final String BLOQUEE = "Trois codes erronés : la signature est bloquée jusqu'à %s.";

	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRENCH);

	public enum Etape {
		VERSEMENT, SIGNATURE, SIGNEE
	}

	/** État du parcours, pour reprendre au même endroit. */
	public record Etat(Statut profil, Etape etape, BigDecimal minimum, BigDecimal montant, String origineFonds,
			String originePrecision, OffsetDateTime codeEnvoyeLe, OffsetDateTime bloqueeJusquAu,
			String numeroContrat) {
	}

	private final CompteService comptes;
	private final CompteRepository comptesVerrouilles;
	private final MouvementRepository mouvements;
	private final ProfilClientService profils;
	private final ModeDeGestion gestion;
	private final CodesSms codes;
	private final DateMetier dateMetier;
	private final SouscriptionRepository souscriptions;

	@SuppressWarnings("java:S107") // un service de module qui appelle plusieurs fonctions publiques (ADR-001)
	public SouscriptionPer(CompteService comptes, CompteRepository comptesVerrouilles, MouvementRepository mouvements,
			ProfilClientService profils, ModeDeGestion gestion, CodesSms codes, DateMetier dateMetier,
			SouscriptionRepository souscriptions) {
		this.comptes = comptes;
		this.comptesVerrouilles = comptesVerrouilles;
		this.mouvements = mouvements;
		this.profils = profils;
		this.gestion = gestion;
		this.codes = codes;
		this.dateMetier = dateMetier;
		this.souscriptions = souscriptions;
	}

	@Transactional // la lecture du profil peut enregistrer « à revalider » (CU-415)
	public Etat etat(String jetonSession) {
		UUID cliente = comptes.compteCourant(jetonSession).getCustomerId();
		Statut profil = profils.statut(cliente);
		Optional<Souscription> souscription = souscriptions.findByCustomerIdAndProduit(cliente, PER);
		if (souscription.isEmpty()) {
			String contratExistant = contratExistant(cliente);
			return new Etat(profil, contratExistant == null ? Etape.VERSEMENT : Etape.SIGNEE, MINIMUM, null, null,
					null, null, null, contratExistant);
		}
		Souscription s = souscription.get();
		return new Etat(profil, s.estSignee() ? Etape.SIGNEE : Etape.SIGNATURE, MINIMUM, s.getMontant(),
				s.getOrigineFonds(), s.getOriginePrecision(), s.getCodeEnvoyeLe(), s.getBloqueeJusquAu(),
				s.getNumeroContrat());
	}

	/** Étape du premier versement : montant (100 € au moins, solde suffisant) et origine des fonds. */
	@Transactional
	public Etat choisirVersement(String jetonSession, BigDecimal montant, String origineFonds,
			String originePrecision) {
		Compte compte = comptes.compteCourant(jetonSession);
		UUID cliente = compte.getCustomerId();
		exigerProfilValide(cliente);
		if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > MONTANT_ECHELLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MONTANT_INVALIDE);
		}
		if (montant.compareTo(MINIMUM) < 0) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MESSAGE_MINIMUM);
		}
		if (origineFonds == null || !ORIGINES.contains(origineFonds)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, ORIGINE_REQUISE);
		}
		String precision = originePrecision == null ? "" : originePrecision.strip();
		if (AUTRE.equals(origineFonds) && (precision.isEmpty() || precision.length() > PRECISION_LONGUEUR_MAX)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, PRECISION_REQUISE);
		}
		if (centimes(montant) > compte.getSoldeCentimes()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, SOLDE_INSUFFISANT);
		}
		Souscription souscription = enCours(cliente);
		souscription.versement(montant.setScale(MONTANT_ECHELLE_MAX), origineFonds,
				AUTRE.equals(origineFonds) ? precision : null);
		return etat(jetonSession);
	}

	/** Envoie un code de signature par SMS (nouveau code après expiration ou erreur), sauf pendant un blocage. */
	@Transactional
	public Etat envoyerCode(String jetonSession) {
		Compte compte = comptes.compteCourant(jetonSession);
		Souscription souscription = enCoursAvecVersement(compte.getCustomerId());
		OffsetDateTime maintenant = maintenant();
		refuserSiBloquee(souscription, maintenant);
		souscription.codeEnvoye(codes.envoyer(compte, code -> "Hi-Tech Bank : votre code pour signer votre PER est "
				+ code + ". Il est valable 5 minutes."), maintenant);
		return etat(jetonSession);
	}

	/**
	 * Signature : code correct dans les 5 minutes → débit, contrat et versement ; sinon le refus (code erroné, expiré,
	 * blocage) est enregistré et la cliente peut demander un nouveau code.
	 */
	@Transactional(noRollbackFor = DemandeRefusee.class)
	public Etat signer(String jetonSession, String code) {
		Compte compte = comptes.compteCourant(jetonSession);
		UUID cliente = compte.getCustomerId();
		Souscription souscription = enCoursAvecVersement(cliente);
		OffsetDateTime maintenant = maintenant();
		refuserSiBloquee(souscription, maintenant);
		if (souscription.getCodeHache() == null) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, CODE_REQUIS);
		}
		switch (CodesSms.controler(souscription.getCodeHache(), souscription.getCodeEnvoyeLe(),
				souscription.getCodesFaux(), code, maintenant)) {
			case EXPIRE -> {
				souscription.codeExpire();
				throw new DemandeRefusee(HttpStatus.GONE, CODE_EXPIRE);
			}
			case INCORRECT -> {
				souscription.codeFaux();
				throw new DemandeRefusee(HttpStatus.BAD_REQUEST, CODE_INCORRECT);
			}
			case INCORRECT_DERNIER -> {
				souscription.bloquer(maintenant, BLOCAGE);
				throw bloquee(souscription);
			}
			case CORRECT -> {
				// suite ci-dessous
			}
		}
		// Tous les contrôles avant la première écriture : un refus ici ne laisse ni débit ni contrat.
		ProfilKyc profil = exigerProfilValide(cliente);
		Compte aDebiter = comptesVerrouilles.verrouiller(compte.getId()).orElseThrow(CompteInaccessibleException::new);
		long centimes = centimes(souscription.getMontant());
		if (!aDebiter.isActif() || centimes > aDebiter.getSoldeCentimes()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, SOLDE_INSUFFISANT);
		}
		String numero = "PER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		aDebiter.debiter(centimes, maintenant);
		mouvements.save(Mouvement.versementEpargne(aDebiter.getId(), centimes, "Premier versement PER",
				"PER Hi-Tech Bank " + numero, maintenant));
		gestion.initialiser(numero, cliente, PER, profil.getReponses().anneeDepartRetraite(),
				profil.getProfilInvestisseur());
		gestion.verser(jetonSession, PER,
				UUID.nameUUIDFromBytes(("souscription:" + numero).getBytes(StandardCharsets.UTF_8)),
				souscription.getMontant());
		souscription.signer(numero, maintenant);
		return etat(jetonSession);
	}

	private Souscription enCours(UUID cliente) {
		Optional<Souscription> existante = souscriptions.findWithLockByCustomerIdAndProduit(cliente, PER);
		if (existante.map(Souscription::estSignee).orElse(false) || contratExistant(cliente) != null) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEJA_SOUSCRIT);
		}
		return existante.orElseGet(() -> souscriptions.save(new Souscription(cliente, PER, maintenant())));
	}

	private Souscription enCoursAvecVersement(UUID cliente) {
		Souscription souscription = souscriptions.findWithLockByCustomerIdAndProduit(cliente, PER)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, VERSEMENT_REQUIS));
		if (souscription.estSignee()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEJA_SOUSCRIT);
		}
		return souscription;
	}

	/** Contrat PER déjà ouvert (par exemple un contrat de test du module Gestion), vide sinon. */
	private String contratExistant(UUID cliente) {
		return gestion.numeroContrat(cliente, PER).orElse(null);
	}

	private ProfilKyc exigerProfilValide(UUID cliente) {
		return profils.profilValide(cliente)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, PROFIL_REQUIS));
	}

	private static void refuserSiBloquee(Souscription souscription, OffsetDateTime maintenant) {
		if (souscription.estBloquee(maintenant)) {
			throw bloquee(souscription);
		}
	}

	private static DemandeRefusee bloquee(Souscription souscription) {
		return new DemandeRefusee(HttpStatus.LOCKED, String.format(BLOQUEE,
				DateMetier.heureDeParis(souscription.getBloqueeJusquAu()).format(HEURE)));
	}

	private OffsetDateTime maintenant() {
		return dateMetier.maintenant().toOffsetDateTime();
	}

	private static long centimes(BigDecimal montant) {
		return montant.movePointRight(MONTANT_ECHELLE_MAX).longValueExact();
	}

}
