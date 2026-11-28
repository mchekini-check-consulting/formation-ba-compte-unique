package com.example.compteunique.souscription;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.example.compteunique.decouverte.DocumentsProduits;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.gestion.ModeDeGestion;
import com.example.compteunique.gestion.ProfilGestion;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilClientService.Statut;
import com.example.compteunique.profil.ProfilKyc;

/**
 * CU-312 (PER-04) et CU-330 (AV-01) : souscrire un PER ou une assurance vie par code SMS, avec un premier versement
 * (module Souscription, ADR-001).
 * <ul>
 * <li>Profil investisseur validé de moins de 12 mois obligatoire (CU-10), contrôlé à la saisie et à la signature :
 * sinon la cliente refait le questionnaire et aucun versement n'est enregistré ; la souscription en cours est
 * conservée.</li>
 * <li>Premier versement d'au moins 100 € depuis le compte courant Hi-Tech Bank (seule source : au nom de la cliente,
 * jamais en espèces), avec l'origine des fonds. PER : compartiment 1.</li>
 * <li>Assurance vie : clause bénéficiaire facultative (sans bénéficiaire, le capital entre dans la succession) ;
 * quotes-parts à 100 % ; âge de la titulaire enregistré avec le versement.</li>
 * <li>Signature par code SMS distincte de la convention : code valable 5 minutes, nouveau code possible ; au 3e code
 * erroné, signature bloquée 30 minutes (décision PO du 22/11/2026).</li>
 * <li>Code correct : compte courant débité, contrat créé en gestion pilotée (module Gestion) et versement réparti
 * selon l'allocation (ordres, contrôle d'adéquation CU-349), dans une seule transaction.</li>
 * <li>PPE : reporté au contrôle PPE (décision BA du 27/11/2026).</li>
 * </ul>
 */
@Service
@Loggable
public class SouscriptionService {

	static final BigDecimal MINIMUM = new BigDecimal("100");
	static final BigDecimal CENT_POUR_CENT = new BigDecimal("100");
	static final Duration BLOCAGE = Duration.ofMinutes(30);
	static final String AUTRE = "AUTRE";
	static final Set<String> ORIGINES = Set.of("REVENUS", "EPARGNE", "HERITAGE_DONATION", "VENTE_BIEN", AUTRE);
	static final int PRECISION_LONGUEUR_MAX = 200;
	static final int NOM_LONGUEUR_MAX = 70;
	static final int BENEFICIAIRES_MAX = 10;
	private static final int MONTANT_ECHELLE_MAX = 2;

	// Pas de message dans PER-02 / AV-02 : l'écran renvoie d'abord vers Découvrir ; refus du serveur si l'adresse est forcée.
	// PER-02 / AV-02, libellé du PO (28/11/2026).
	static final String DOCUMENTS_REQUIS = "Vous devez ouvrir et lire les documents d'information avant de souscrire.";
	static final String PROFIL_REQUIS = "Complétez ou revalidez votre profil investisseur avant de souscrire.";
	static final Map<Produit, String> MESSAGE_MINIMUM = Map.of(Produit.PER, "Le premier versement minimum est de 100 €",
			Produit.AV, "Le versement initial minimum est de 100 €");
	static final String MONTANT_INVALIDE = "Saisissez un montant en euros et centimes.";
	static final String ORIGINE_REQUISE = "Indiquez l'origine des fonds.";
	static final String PRECISION_REQUISE = "Précisez l'origine des fonds (200 caractères au plus).";
	static final String SOLDE_INSUFFISANT = "Solde de votre compte courant insuffisant";
	static final Map<Produit, String> DEJA_SOUSCRIT = Map.of(Produit.PER, "Vous avez déjà un PER.", Produit.AV,
			"Vous avez déjà une assurance vie.");
	static final String VERSEMENT_REQUIS = "Indiquez d'abord votre premier versement.";
	static final String CODE_REQUIS = "Demandez un code pour signer.";
	static final String CODE_EXPIRE = "Code expiré : demandez un nouveau code.";
	static final String CODE_INCORRECT = "Code incorrect : vérifiez-le ou demandez un nouveau code.";
	static final String BLOQUEE = "Trois codes erronés : la signature est bloquée jusqu'à %s.";
	static final String QUOTES_PARTS = "Les quotes-parts doivent totaliser 100 %";
	static final String BENEFICIAIRE_INCOMPLET = "Indiquez le nom, le prénom, la date de naissance et la quote-part de "
			+ "chaque bénéficiaire.";
	static final String TROP_DE_BENEFICIAIRES = "10 bénéficiaires au plus.";

	private static final Map<Produit, String> NOMS = Map.of(Produit.PER, "PER", Produit.AV, "assurance vie");
	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRENCH);

	public enum Etape {
		VERSEMENT, SIGNATURE, SIGNEE
	}

	/** Un bénéficiaire de la clause, tel que l'écran l'envoie et l'affiche. */
	public record Beneficiaire(String nom, String prenom, LocalDate dateNaissance, BigDecimal quotePartPct) {

		static Beneficiaire de(BeneficiaireClause b) {
			return new Beneficiaire(b.getNom(), b.getPrenom(), b.getDateNaissance(), b.getQuotePartPct());
		}

	}

	/** Demande de l'étape du premier versement ; bénéficiaires : assurance vie seulement (vide : succession). */
	public record DemandeVersement(BigDecimal montant, String origineFonds, String originePrecision,
			List<Beneficiaire> beneficiaires) {
	}

	/** État du parcours, pour reprendre au même endroit. */
	public record Etat(Produit produit, Statut profil, Etape etape, BigDecimal minimum, BigDecimal montant,
			String origineFonds, String originePrecision, List<Beneficiaire> beneficiaires, OffsetDateTime codeEnvoyeLe,
			OffsetDateTime bloqueeJusquAu, String numeroContrat, boolean documentsLus, ProfilGestion profilGestionParDefaut) {
	}

	private final CompteService comptes;
	private final CompteRepository comptesVerrouilles;
	private final MouvementRepository mouvements;
	private final DossierRepository dossiers;
	private final ProfilClientService profils;
	private final ModeDeGestion gestion;
	private final CodesSms codes;
	private final DateMetier dateMetier;
	private final SouscriptionRepository souscriptions;
	private final BeneficiaireClauseRepository clauses;
	private final DocumentsProduits documents;

	@SuppressWarnings("java:S107") // un service de module qui appelle plusieurs fonctions publiques (ADR-001)
	public SouscriptionService(CompteService comptes, CompteRepository comptesVerrouilles,
			MouvementRepository mouvements, DossierRepository dossiers, ProfilClientService profils,
			ModeDeGestion gestion, CodesSms codes, DateMetier dateMetier, SouscriptionRepository souscriptions,
			BeneficiaireClauseRepository clauses, DocumentsProduits documents) {
		this.comptes = comptes;
		this.comptesVerrouilles = comptesVerrouilles;
		this.mouvements = mouvements;
		this.dossiers = dossiers;
		this.profils = profils;
		this.gestion = gestion;
		this.codes = codes;
		this.dateMetier = dateMetier;
		this.souscriptions = souscriptions;
		this.clauses = clauses;
		this.documents = documents;
	}

	@Transactional // la lecture du profil peut enregistrer « à revalider » (CU-415)
	public Etat etat(String jetonSession, Produit produit) {
		UUID cliente = comptes.compteCourant(jetonSession).getCustomerId();
		Statut profil = profils.statut(cliente);
		boolean documentsLus = documentsLus(cliente, produit);
		// Lot 5 : « Votre contrat démarrera en gestion pilotée [profil] » (règle de PER-07 / AV-07, module Gestion).
		ProfilGestion parDefaut = profils.profilValide(cliente).map(ProfilKyc::getProfilInvestisseur)
				.map(gestion::profilParDefaut).orElse(null);
		Optional<Souscription> souscription = souscriptions.findByCustomerIdAndProduit(cliente, produit);
		if (souscription.isEmpty()) {
			String contratExistant = contratExistant(cliente, produit);
			return new Etat(produit, profil, contratExistant == null ? Etape.VERSEMENT : Etape.SIGNEE, MINIMUM, null,
					null, null, List.of(), null, null, contratExistant, documentsLus, parDefaut);
		}
		Souscription s = souscription.get();
		return new Etat(produit, profil, s.estSignee() ? Etape.SIGNEE : Etape.SIGNATURE, MINIMUM, s.getMontant(),
				s.getOrigineFonds(), s.getOriginePrecision(),
				clauses.findBySouscriptionIdOrderByRang(s.getId()).stream().map(Beneficiaire::de).toList(),
				s.getCodeEnvoyeLe(), s.getBloqueeJusquAu(), s.getNumeroContrat(), documentsLus, parDefaut);
	}

	/**
	 * Étape du premier versement : montant (100 € au moins, solde suffisant), origine des fonds et, pour l'assurance
	 * vie, la clause bénéficiaire. Une nouvelle saisie remplace la précédente et annule le code déjà envoyé.
	 */
	@Transactional
	public Etat choisirVersement(String jetonSession, Produit produit, DemandeVersement demande) {
		Compte compte = comptes.compteCourant(jetonSession);
		UUID cliente = compte.getCustomerId();
		exigerDocumentsLus(cliente, produit);
		exigerProfilValide(cliente);
		BigDecimal montant = montantValide(demande.montant(), produit);
		String origine = demande.origineFonds();
		String precision = precisionValide(origine, demande.originePrecision());
		List<Beneficiaire> beneficiaires = produit == Produit.AV ? beneficiaires(demande.beneficiaires()) : List.of();
		if (centimes(montant) > compte.getSoldeCentimes()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, SOLDE_INSUFFISANT);
		}
		Optional<Souscription> existante = souscriptions.findWithLockByCustomerIdAndProduit(cliente, produit);
		if (existante.map(Souscription::estSignee).orElse(false) || contratExistant(cliente, produit) != null) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEJA_SOUSCRIT.get(produit));
		}
		// CU-521 : le montant et l'origine sont renseignés avant le premier enregistrement (colonnes obligatoires).
		Souscription souscription = existante.orElseGet(() -> new Souscription(cliente, produit, maintenant()));
		souscription.versement(montant.setScale(MONTANT_ECHELLE_MAX), origine, precision);
		Long id = souscriptions.saveAndFlush(souscription).getId();
		clauses.supprimer(id);
		for (int rang = 0; rang < beneficiaires.size(); rang++) {
			Beneficiaire b = beneficiaires.get(rang);
			clauses.save(new BeneficiaireClause(id, rang + 1, b.nom(), b.prenom(), b.dateNaissance(),
					b.quotePartPct()));
		}
		return etat(jetonSession, produit);
	}

	/** Envoie un code de signature par SMS (nouveau code après expiration ou erreur), sauf pendant un blocage. */
	@Transactional
	public Etat envoyerCode(String jetonSession, Produit produit) {
		Compte compte = comptes.compteCourant(jetonSession);
		Souscription souscription = enCoursAvecVersement(compte.getCustomerId(), produit);
		OffsetDateTime maintenant = maintenant();
		refuserSiBloquee(souscription, maintenant);
		String contrat = produit == Produit.PER ? "votre PER" : "votre contrat d'assurance vie";
		souscription.codeEnvoye(codes.envoyer(compte, code -> "Hi-Tech Bank : votre code pour signer " + contrat
				+ " est " + code + ". Il est valable 5 minutes."), maintenant);
		return etat(jetonSession, produit);
	}

	/**
	 * Signature : code correct dans les 5 minutes → débit, contrat et versement ; sinon le refus (code erroné, expiré,
	 * blocage) est enregistré et la cliente peut demander un nouveau code.
	 */
	@Transactional(noRollbackFor = DemandeRefusee.class)
	public Etat signer(String jetonSession, Produit produit, String code) {
		Compte compte = comptes.compteCourant(jetonSession);
		UUID cliente = compte.getCustomerId();
		Souscription souscription = enCoursAvecVersement(cliente, produit);
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
		exigerDocumentsLus(cliente, produit);
		ProfilKyc profil = exigerProfilValide(cliente);
		Compte aDebiter = comptesVerrouilles.verrouiller(compte.getId()).orElseThrow(CompteInaccessibleException::new);
		long centimes = centimes(souscription.getMontant());
		if (!aDebiter.isActif() || centimes > aDebiter.getSoldeCentimes()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, SOLDE_INSUFFISANT);
		}
		if (produit == Produit.AV) {
			souscription.ageTitulaire(age(compte, maintenant.toLocalDate()));
		}
		String numero = produit.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		String nom = NOMS.get(produit);
		aDebiter.debiter(centimes, maintenant);
		mouvements.save(Mouvement.versementEpargne(aDebiter.getId(), centimes,
				(produit == Produit.PER ? "Premier versement " : "Versement initial ") + nom,
				(produit == Produit.PER ? "PER" : "Assurance vie") + " Hi-Tech Bank " + numero, maintenant));
		gestion.initialiser(numero, cliente, produit, profil.getReponses().anneeDepartRetraite(),
				profil.getProfilInvestisseur());
		gestion.verser(jetonSession, produit,
				UUID.nameUUIDFromBytes(("souscription:" + numero).getBytes(StandardCharsets.UTF_8)),
				souscription.getMontant());
		souscription.signer(numero, maintenant);
		return etat(jetonSession, produit);
	}

	/** Clause bénéficiaire facultative : chaque ligne complète, quotes-parts positives totalisant 100 %. */
	private static List<Beneficiaire> beneficiaires(List<Beneficiaire> saisis) {
		if (saisis == null || saisis.isEmpty()) {
			return List.of();
		}
		if (saisis.size() > BENEFICIAIRES_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, TROP_DE_BENEFICIAIRES);
		}
		List<Beneficiaire> valides = saisis.stream().map(SouscriptionService::beneficiaire).toList();
		BigDecimal total = valides.stream().map(Beneficiaire::quotePartPct).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(CENT_POUR_CENT) != 0) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, QUOTES_PARTS);
		}
		return valides;
	}

	/** Une ligne de la clause, noms sans espaces superflus ; refusée si incomplète ou hors limites. */
	private static Beneficiaire beneficiaire(Beneficiaire b) {
		if (b == null) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, BENEFICIAIRE_INCOMPLET);
		}
		String nom = nomValide(b.nom());
		String prenom = nomValide(b.prenom());
		BigDecimal part = b.quotePartPct();
		if (nom == null || prenom == null || b.dateNaissance() == null || !partValide(part)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, BENEFICIAIRE_INCOMPLET);
		}
		return new Beneficiaire(nom, prenom, b.dateNaissance(), part);
	}

	private static String nomValide(String saisi) {
		String nom = saisi == null ? "" : saisi.strip();
		return nom.isEmpty() || nom.length() > NOM_LONGUEUR_MAX ? null : nom;
	}

	private static boolean partValide(BigDecimal part) {
		return part != null && part.signum() > 0 && part.compareTo(CENT_POUR_CENT) <= 0
				&& part.stripTrailingZeros().scale() <= MONTANT_ECHELLE_MAX;
	}

	private static BigDecimal montantValide(BigDecimal montant, Produit produit) {
		if (montant == null || montant.signum() <= 0 || montant.stripTrailingZeros().scale() > MONTANT_ECHELLE_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MONTANT_INVALIDE);
		}
		if (montant.compareTo(MINIMUM) < 0) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, MESSAGE_MINIMUM.get(produit));
		}
		return montant;
	}

	/** Origine des fonds connue ; sa précision n'est exigée et conservée que pour « autre ». */
	private static String precisionValide(String origine, String saisie) {
		if (origine == null || !ORIGINES.contains(origine)) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, ORIGINE_REQUISE);
		}
		if (!AUTRE.equals(origine)) {
			return null;
		}
		String precision = saisie == null ? "" : saisie.strip();
		if (precision.isEmpty() || precision.length() > PRECISION_LONGUEUR_MAX) {
			throw new DemandeRefusee(HttpStatus.BAD_REQUEST, PRECISION_REQUISE);
		}
		return precision;
	}

	/** Âge de la titulaire (date de naissance du dossier d'ouverture) à la date de signature. */
	private int age(Compte compte, LocalDate le) {
		LocalDate naissance = dossiers.findById(compte.getDossierId()).map(Dossier::getDateNaissance)
				.orElseThrow(CompteInaccessibleException::new);
		return Period.between(naissance, le).getYears();
	}

	private Souscription enCoursAvecVersement(UUID cliente, Produit produit) {
		Souscription souscription = souscriptions.findWithLockByCustomerIdAndProduit(cliente, produit)
				.orElseThrow(() -> new DemandeRefusee(HttpStatus.CONFLICT, VERSEMENT_REQUIS));
		if (souscription.estSignee()) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DEJA_SOUSCRIT.get(produit));
		}
		return souscription;
	}

	/** Contrat déjà ouvert pour ce produit (par exemple un contrat de test du module Gestion), vide sinon. */
	private String contratExistant(UUID cliente, Produit produit) {
		return gestion.numeroContrat(cliente, produit).orElse(null);
	}

	/** PER-02 / AV-02 : les deux documents du produit ouverts et cochés (fonction publique du module Découverte). */
	private boolean documentsLus(UUID cliente, Produit produit) {
		return documents.peutContinuer(cliente, DocumentsProduits.Produit.valueOf(produit.name()));
	}

	private void exigerDocumentsLus(UUID cliente, Produit produit) {
		if (!documentsLus(cliente, produit)) {
			throw new DemandeRefusee(HttpStatus.CONFLICT, DOCUMENTS_REQUIS);
		}
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
