package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.Adequation;
import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.Allocation.AllocationImpossible;
import com.example.compteunique.gestion.Allocation.Ligne;
import com.example.compteunique.gestion.Allocation.Montant;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.ordres.OrdresService;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.PositionsContrat;
import com.example.compteunique.ordres.TypeOrdre;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilKyc;

/**
 * CU-314 (PER-07) et CU-338 (AV-07) : fonctions publiques du module Gestion (ADR-001, règle 1).
 * <ul>
 * <li>À la création du contrat : gestion pilotée, au profil le moins risqué entre « équilibré » et le profil
 * investisseur ({@link #initialiser}, appelée par PER-04 et AV-01 ; en attendant, par la création d'un contrat de test
 * en dev et qualif).</li>
 * <li>Changement de mode ou de profil : toujours gratuit, enregistré dans l'historique avec sa date métier.</li>
 * <li>PER et assurance vie : le profil de gestion ne peut pas dépasser le profil investisseur (CU-10 ; décision PO du
 * 26/11/2026 pour le PER).</li>
 * <li>Gestion sous mandat : refusée si l'encours est inférieur au montant minimum (paramètre) ; sortie libre, sans
 * préavis.</li>
 * <li>Gestion pilotée : allocation cible qui respecte la grille PER par horizon ou le quota de non coté AV (paramètres,
 * lus à chaque calcul) ; au passage en pilotée, des ordres d'arbitrage gratuits sont passés au module Ordres ; un
 * versement est réparti selon l'allocation (en AV, le quota ne s'applique qu'aux nouveaux versements). Le contrôle
 * quotidien des écarts au quota (CU-414) n'est pas ici : il démarrera sur l'événement « positions calculées » de
 * TECH-10.</li>
 * </ul>
 */
@Service
@Loggable
public class ModeDeGestion {

	static final String PROFIL_TROP_RISQUE = "Votre profil de gestion ne peut pas dépasser votre profil investisseur";

	private static final int MONTANT_ECHELLE_MAX = 2;

	/** Paramètres du mandat affichés avant validation. */
	public record Mandat(BigDecimal tauxToutComprisPct, BigDecimal montantMinimum) {
	}

	/** Parts minimales qui s'imposent à la gestion pilotée du contrat (en %). */
	public record Contraintes(BigDecimal partMinNonCotePct, BigDecimal partMinFaibleRisquePct, Integer horizonAnnees,
			boolean grilleAValider) {
	}

	public record ChangementVue(ModeGestion mode, ProfilGestion profil, Integer horizonAnnees, BigDecimal frais,
			OffsetDateTime changeLe) {
	}

	public record ArbitrageVue(UUID ordreId, String supportDepart, String supportArrivee, BigDecimal montant) {
	}

	public record ReequilibrageVue(OffsetDateTime demandeLe, BigDecimal montantArbitre, List<ArbitrageVue> arbitrages) {
	}

	/** Le contrat, son mode de gestion, son encours et l'allocation cible de la gestion pilotée au profil en vigueur. */
	public record ContratVue(String numeroContrat, ModeGestion mode, ProfilGestion profil, OffsetDateTime changeLe,
			BigDecimal encours, BigDecimal encoursNonCote, Contraintes contraintes, List<Ligne> allocation,
			String allocationIndisponible,
			List<ChangementVue> historique, List<ReequilibrageVue> reequilibrages) {
	}

	/** Ce que la page « Choisir mon mode de gestion » affiche. Contrat vide tant qu'il n'est pas créé. */
	public record Etat(Produit produit, String profilInvestisseur, boolean contratsDeTest, Mandat mandat,
			ContratVue contrat) {
	}

	/** Un versement réparti par la gestion pilotée : un ordre de versement par support. */
	public record Versement(UUID id, BigDecimal montant, List<Montant> repartition) {
	}

	private final CompteService comptes;
	private final ProfilClientService profils;
	private final DateMetier dateMetier;
	private final SupportsEligibles supports;
	private final CatalogueService catalogue;
	private final PositionsContrat positions;
	private final OrdresService ordres;
	private final ContratGestionRepository contrats;
	private final ChangementGestionRepository changements;
	private final ParametreMandatRepository mandats;
	private final GrilleHorizonPerRepository grille;
	private final QuotaNonCoteAvRepository quotas;
	private final ReequilibrageRepository reequilibrages;
	private final SignalementNonCoteRepository signalements;
	private final Adequation adequation;
	private final boolean contratsDeTest;

	@Autowired
	@SuppressWarnings("java:S107") // un service de module qui appelle plusieurs fonctions publiques (ADR-001)
	public ModeDeGestion(CompteService comptes, ProfilClientService profils, DateMetier dateMetier,
			SupportsEligibles supports, CatalogueService catalogue, PositionsContrat positions, OrdresService ordres,
			ContratGestionRepository contrats, ChangementGestionRepository changements,
			ParametreMandatRepository mandats, GrilleHorizonPerRepository grille, QuotaNonCoteAvRepository quotas,
			ReequilibrageRepository reequilibrages, SignalementNonCoteRepository signalements, Adequation adequation,
			@Value("${app.test.ordres-simules:false}") boolean contratsDeTest) {
		this.comptes = comptes;
		this.profils = profils;
		this.dateMetier = dateMetier;
		this.supports = supports;
		this.catalogue = catalogue;
		this.positions = positions;
		this.ordres = ordres;
		this.contrats = contrats;
		this.changements = changements;
		this.mandats = mandats;
		this.grille = grille;
		this.quotas = quotas;
		this.reequilibrages = reequilibrages;
		this.signalements = signalements;
		this.adequation = adequation;
		this.contratsDeTest = contratsDeTest;
	}

	/**
	 * Lot 4 (PO, 27/11/2026) : une ligne par contrat PER ou assurance vie de la cliente, avec les seules données déjà en
	 * base (produit, numéro, date d'ouverture, mode et profil de gestion). Pas de valorisation (PER-09, AV-09).
	 */
	public record LigneContrat(Produit produit, String numeroContrat, OffsetDateTime ouvertLe, ModeGestion mode,
			ProfilGestion profil) {
	}

	@Transactional(readOnly = true)
	public List<LigneContrat> contrats(String jetonSession) {
		return contrats.findByCustomerIdOrderByProduit(customerId(jetonSession)).stream()
				.map(c -> new LigneContrat(c.getProduit(), c.getNumeroContrat(), c.getCreeLe(), c.getMode(),
						c.getProfil()))
				.toList();
	}

	/**
	 * Lot 5 : profil de gestion avec lequel le contrat démarrera en gestion pilotée (PER-04, AV-01 : le moins risqué
	 * entre « équilibré » et le profil investisseur), annoncé avant la signature. Même règle que {@link #initialiser}.
	 */
	public ProfilGestion profilParDefaut(String profilInvestisseur) {
		return ProfilGestion.parDefaut(profilInvestisseur);
	}

	/** CU-312 : numéro du contrat de la cliente pour ce produit, vide sans contrat. */
	@Transactional(readOnly = true)
	public Optional<String> numeroContrat(UUID customerId, Produit produit) {
		return contrats.findByCustomerIdAndProduit(customerId, produit).map(ContratGestion::getNumeroContrat);
	}

	@Transactional
	public Etat etat(String jetonSession, Produit produit) {
		UUID cliente = customerId(jetonSession);
		return etat(cliente, produit, contrats.findByCustomerIdAndProduit(cliente, produit));
	}

	/**
	 * Dev et qualif seulement : crée le contrat de test de la cliente pour ce produit (en attendant PER-04 et AV-01),
	 * avec un profil validé, comme une souscription. Un contrat déjà créé est renvoyé tel quel.
	 */
	@Transactional
	public Etat creerContratDeTest(String jetonSession, Produit produit) {
		if (!contratsDeTest) {
			throw new ContratsDeTestIndisponibles();
		}
		UUID cliente = customerId(jetonSession);
		ProfilKyc profil = profils.profilValide(cliente).orElseThrow(ProfilRequis::new);
		Optional<ContratGestion> existant = contrats.findByCustomerIdAndProduit(cliente, produit);
		if (existant.isEmpty()) {
			String numero = produit.name() + "-TEST-"
					+ UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
			initialiser(numero, cliente, produit, profil.getReponses().anneeDepartRetraite(),
					profil.getProfilInvestisseur());
		}
		return etat(cliente, produit, contrats.findByCustomerIdAndProduit(cliente, produit));
	}

	/**
	 * PER-04 et AV-01 : à la création du contrat, gestion pilotée (sans choix de la cliente), au profil le moins risqué
	 * entre « équilibré » et le profil investisseur (décision PO du 26/11/2026) : jamais au-dessus de ce dernier.
	 */
	@Transactional
	public ContratGestion initialiser(String numeroContrat, UUID customerId, Produit produit,
			Integer anneeDepartRetraite, String profilInvestisseur) {
		if (profilInvestisseur == null) {
			throw new ProfilRequis();
		}
		OffsetDateTime maintenant = maintenant();
		ContratGestion contrat = contrats.saveAndFlush(new ContratGestion(numeroContrat, customerId, produit,
				produit == Produit.PER ? anneeDepartRetraite : null, ProfilGestion.parDefaut(profilInvestisseur),
				maintenant));
		changements.save(new ChangementGestion(numeroContrat, contrat.getMode(), contrat.getProfil(),
				horizon(contrat), maintenant));
		return contrat;
	}

	/**
	 * Choix d'un mode et d'un profil de gestion (profil vide : celui en vigueur), gratuit. Refusé si le profil dépasse
	 * le profil investisseur (AV), si l'encours n'atteint pas le minimum du mandat, ou si l'allocation pilotée ne peut
	 * pas respecter ses parts minimales. Au passage en gestion pilotée, les positions sont rééquilibrées.
	 */
	@Transactional
	public Etat choisir(String jetonSession, Produit produit, ModeGestion mode, ProfilGestion profilDemande) {
		UUID cliente = customerId(jetonSession);
		ContratGestion contrat = contrats.findWithLockByCustomerIdAndProduit(cliente, produit)
				.orElseThrow(ContratIntrouvable::new);
		// CU-460 : en gestion libre, le profil de gestion n'est pas utilisé (champ masqué à l'écran) : celui en vigueur
		// est conservé, sans contrôle du profil envoyé.
		ProfilGestion profil = profilDemande != null && mode != ModeGestion.LIBRE ? profilDemande : contrat.getProfil();
		Optional<ProfilKyc> kyc = profils.profilValide(cliente);
		// PER et AV (décision PO du 26/11/2026) : jamais au-dessus du profil investisseur.
		if (profil != contrat.getProfil()) {
			String investisseur = kyc.map(ProfilKyc::getProfilInvestisseur).orElseThrow(ProfilRequis::new);
			if (profil.depasse(investisseur)) {
				throw new ChoixRefuse(PROFIL_TROP_RISQUE);
			}
		}
		if (mode == ModeGestion.MANDAT && contrat.getMode() != ModeGestion.MANDAT) {
			verifierMinimumDuMandat(contrat);
		}
		ModeGestion avant = contrat.getMode();
		if (avant == mode && profil == contrat.getProfil()) {
			return etat(cliente, produit, Optional.of(contrat));
		}
		Integer anneeRetraite = produit == Produit.PER
				? kyc.map(k -> k.getReponses().anneeDepartRetraite()).orElse(null)
				: null;
		OffsetDateTime maintenant = maintenant();
		contrat.changer(mode, profil, anneeRetraite, maintenant);
		Allocation allocation = mode == ModeGestion.PILOTEE ? allocation(contrat) : null;
		changements.save(new ChangementGestion(contrat.getNumeroContrat(), mode, profil, horizon(contrat), maintenant));
		if (allocation != null && avant != ModeGestion.PILOTEE) {
			reequilibrer(contrat, maintenant);
			if (produit == Produit.AV) {
				signalerNonCotesHorsProfil(contrat, maintenant);
			}
		}
		return etat(cliente, produit, Optional.of(contrat));
	}

	/** Gestion pilotée : un versement réparti selon l'allocation, un ordre de versement par support (doublon ignoré). */
	@Transactional
	public Versement verser(String jetonSession, Produit produit, UUID id, BigDecimal montant) {
		if (id == null || montant == null || montant.signum() <= 0 || montant.scale() > MONTANT_ECHELLE_MAX) {
			throw new DemandeInvalide("Indiquez un montant positif, en euros et centimes.");
		}
		ContratGestion contrat = contrats.findByCustomerIdAndProduit(customerId(jetonSession), produit)
				.orElseThrow(ContratIntrouvable::new);
		if (contrat.getMode() != ModeGestion.PILOTEE) {
			throw new VersementImpossible();
		}
		List<Montant> repartition = allocation(contrat).repartir(montant);
		String profilEnVigueur = profilInvestisseur(contrat);
		for (Montant ligne : repartition) {
			controlerAdequation(contrat, profilEnVigueur, ligne.codeIsin(), TypeOrdre.VERSEMENT);
		}
		for (int i = 0; i < repartition.size(); i++) {
			Montant ligne = repartition.get(i);
			UUID ordreId = UUID.nameUUIDFromBytes((id + ":" + i).getBytes(StandardCharsets.UTF_8));
			ordres.passer(new DemandeOrdre(ordreId, contrat.getNumeroContrat(), TypeOrdre.VERSEMENT, null,
					ligne.codeIsin(), ligne.montant()));
		}
		return new Versement(id, montant, repartition);
	}

	/** Parts minimales du contrat : grille PER selon le profil et l'horizon, ou quota de non coté AV. */
	@Transactional(readOnly = true)
	public Contraintes contraintes(ContratGestion contrat) {
		if (contrat.getProduit() == Produit.AV) {
			BigDecimal quota = quotas.findById(contrat.getProfil()).map(QuotaNonCoteAv::getPartMinNonCotePct)
					.orElse(BigDecimal.ZERO);
			return new Contraintes(quota, BigDecimal.ZERO, null, false);
		}
		Integer horizon = horizon(contrat);
		int annees = horizon == null ? 0 : horizon;
		return grille.findByProfilOrderByAnneesMinDesc(contrat.getProfil()).stream()
				.filter(ligne -> ligne.getAnneesMin() <= annees).findFirst()
				.map(l -> new Contraintes(l.getPartMinNonCotePct(), l.getPartMinFaibleRisquePct(), horizon,
						l.isAValider()))
				.orElse(new Contraintes(BigDecimal.ZERO, BigDecimal.ZERO, horizon, false));
	}

	/** Mandat : paramètres du produit (taux tout compris, montant minimum). */
	@Transactional(readOnly = true)
	public Mandat mandat(Produit produit) {
		return mandats.findById(produit).map(m -> new Mandat(m.getTauxToutComprisPct(), m.getMontantMinimum()))
				.orElseThrow(() -> new IllegalStateException("Paramètres du mandat absents pour " + produit));
	}

	private void verifierMinimumDuMandat(ContratGestion contrat) {
		BigDecimal minimum = mandat(contrat.getProduit()).montantMinimum();
		BigDecimal encours = encours(contrat.getNumeroContrat());
		if (encours.compareTo(minimum) < 0) {
			throw new ChoixRefuse("La gestion sous mandat demande un encours d'au moins " + euros(minimum)
					+ " € (encours de votre contrat : " + euros(encours) + " €).");
		}
	}

	private Allocation allocation(ContratGestion contrat) {
		Contraintes c = contraintes(contrat);
		return Allocation.calculer(supports.lister(contrat.getProduit(), contrat.getProfil().name()),
				c.partMinNonCotePct(), c.partMinFaibleRisquePct());
	}

	/**
	 * Rééquilibrage au passage en gestion pilotée : des ordres d'arbitrage interne gratuits passés au module Ordres
	 * (TECH-08, statut « demandé », exécutés plus tard par TECH-09). Les positions ne sont jamais modifiées ici.
	 * Arbitrages des supports au-dessus de leur cible vers ceux en dessous, sur le montant disponible.
	 * <p>
	 * Assurance vie : le quota de non coté ne s'applique qu'aux nouveaux versements (AV-07). Les positions non cotées
	 * existantes ne bougent pas ; seule la part cotée est rééquilibrée, sur l'allocation du profil sans quota.
	 */
	private void reequilibrer(ContratGestion contrat, OffsetDateTime le) {
		String numero = contrat.getNumeroContrat();
		boolean assuranceVie = contrat.getProduit() == Produit.AV;
		Contraintes c = contraintes(contrat);
		List<SupportEligible> eligibles = supports.lister(contrat.getProduit(), contrat.getProfil().name());
		Allocation allocation = assuranceVie
				? Allocation.calculer(eligibles.stream().filter(s -> !s.nonCote()).toList(), BigDecimal.ZERO,
						c.partMinFaibleRisquePct())
				: Allocation.calculer(eligibles, c.partMinNonCotePct(), c.partMinFaibleRisquePct());
		Map<String, BigDecimal> actuel = new HashMap<>();
		for (String isin : positions.encoursParSupport(numero).keySet()) {
			if (!assuranceVie || !nonCote(isin)) {
				actuel.put(isin, ordres.montantDisponible(numero, isin));
			}
		}
		BigDecimal total = actuel.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		Map<String, BigDecimal> cible = new HashMap<>();
		if (total.signum() > 0) {
			allocation.repartir(total).forEach(m -> cible.put(m.codeIsin(), m.montant()));
		}
		List<Ecart> vendeurs = new ArrayList<>();
		List<Ecart> acheteurs = new ArrayList<>();
		TreeSet<String> isins = new TreeSet<>(actuel.keySet());
		isins.addAll(cible.keySet());
		for (String isin : isins) {
			BigDecimal ecart = actuel.getOrDefault(isin, BigDecimal.ZERO).subtract(cible.getOrDefault(isin,
					BigDecimal.ZERO));
			if (ecart.signum() > 0) {
				vendeurs.add(new Ecart(isin, ecart));
			} else if (ecart.signum() < 0) {
				acheteurs.add(new Ecart(isin, ecart.negate()));
			}
		}
		String profilEnVigueur = profilInvestisseur(contrat);
		for (Ecart acheteur : acheteurs) {
			controlerAdequation(contrat, profilEnVigueur, acheteur.isin, TypeOrdre.ARBITRAGE);
		}
		Reequilibrage reequilibrage = new Reequilibrage(numero, le);
		arbitrer(numero, vendeurs, acheteurs, reequilibrage);
		reequilibrages.save(reequilibrage);
	}

	/** CU-349 : profil investisseur en vigueur à la date de validation (vide si aucun profil validé). */
	private String profilInvestisseur(ContratGestion contrat) {
		return profils.profilValide(contrat.getCustomerId()).map(ProfilKyc::getProfilInvestisseur).orElse(null);
	}

	/** CU-349 : chaque support acheté est contrôlé de nouveau avant le premier ordre ; un refus n'investit rien. */
	private void controlerAdequation(ContratGestion contrat, String profil, String isin, TypeOrdre type) {
		adequation.controler(contrat.getCustomerId(), contrat.getProduit(), profil, isin, type.name());
	}

	/** Un ordre d'arbitrage par paire vendeur → acheteur, jusqu'à épuiser l'un des deux côtés. */
	private void arbitrer(String numero, List<Ecart> vendeurs, List<Ecart> acheteurs, Reequilibrage reequilibrage) {
		int v = 0;
		int a = 0;
		while (v < vendeurs.size() && a < acheteurs.size()) {
			Ecart vendeur = vendeurs.get(v);
			Ecart acheteur = acheteurs.get(a);
			BigDecimal montant = vendeur.reste.min(acheteur.reste);
			UUID ordreId = UUID.randomUUID();
			ordres.passer(new DemandeOrdre(ordreId, numero, TypeOrdre.ARBITRAGE, vendeur.isin, acheteur.isin, montant));
			reequilibrage.ajouter(ordreId, vendeur.isin, acheteur.isin, montant);
			vendeur.reste = vendeur.reste.subtract(montant);
			acheteur.reste = acheteur.reste.subtract(montant);
			if (vendeur.reste.signum() == 0) {
				v++;
			}
			if (acheteur.reste.signum() == 0) {
				a++;
			}
		}
	}

	/** Support non coté selon le catalogue (un support inconnu est traité comme coté). */
	private boolean nonCote(String codeIsin) {
		return catalogue.support(codeIsin).map(s -> !s.cote()).orElse(false);
	}

	/**
	 * AV-07 (décision PO du 26/11/2026) : encours des supports non cotés du contrat d'assurance vie, qui restent hors
	 * gestion pilotée ; affiché avant validation du passage en pilotée. Zéro pour le PER.
	 */
	private BigDecimal encoursNonCote(ContratGestion contrat) {
		if (contrat.getProduit() != Produit.AV) {
			return BigDecimal.ZERO.setScale(2);
		}
		return positions.encoursParSupport(contrat.getNumeroContrat()).entrySet().stream()
				.filter(p -> nonCote(p.getKey())).map(Map.Entry::getValue)
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}

	/**
	 * AV-07 (décision PO du 26/11/2026) : après le passage en pilotée, chaque support non coté conservé dont le niveau
	 * de risque dépasse le maximum du profil de gestion choisi est signalé au back-office, « à traiter ». Les autres ne
	 * le sont pas.
	 */
	private void signalerNonCotesHorsProfil(ContratGestion contrat, OffsetDateTime le) {
		int maximum = supports.niveauRisqueMax(contrat.getProfil().name());
		for (Map.Entry<String, BigDecimal> position : positions.encoursParSupport(contrat.getNumeroContrat())
				.entrySet()) {
			catalogue.support(position.getKey())
					.filter(s -> !s.cote() && s.niveauRisque() > maximum && position.getValue().signum() > 0)
					.ifPresent(s -> signalements.save(new SignalementNonCote(contrat.getNumeroContrat(), s.codeIsin(),
							position.getValue(), s.niveauRisque(), contrat.getProfil(), le)));
		}
	}

	/** Les 50 derniers signalements au back-office (route de test, en attendant un écran back-office). */
	@Transactional(readOnly = true)
	public List<SignalementNonCote> signalements() {
		return signalements.findTop50ByOrderByIdDesc();
	}

	/** Écart d'un support à sa cible pendant le rééquilibrage (montant restant à vendre ou à acheter). */
	private static final class Ecart {

		private final String isin;
		private BigDecimal reste;

		private Ecart(String isin, BigDecimal reste) {
			this.isin = isin;
			this.reste = reste;
		}

	}

	private Etat etat(UUID cliente, Produit produit, Optional<ContratGestion> contrat) {
		String investisseur = profils.profilValide(cliente).map(ProfilKyc::getProfilInvestisseur).orElse(null);
		return new Etat(produit, investisseur, contratsDeTest, mandat(produit), contrat.map(this::vue).orElse(null));
	}

	private ContratVue vue(ContratGestion contrat) {
		Contraintes contraintes = contraintes(contrat);
		List<Ligne> lignes = List.of();
		String indisponible = null;
		try {
			lignes = Allocation.calculer(supports.lister(contrat.getProduit(), contrat.getProfil().name()),
					contraintes.partMinNonCotePct(), contraintes.partMinFaibleRisquePct()).lignes();
		} catch (AllocationImpossible e) {
			indisponible = e.getMessage();
		}
		String numero = contrat.getNumeroContrat();
		return new ContratVue(numero, contrat.getMode(), contrat.getProfil(), paris(contrat.getChangeLe()),
				encours(numero), encoursNonCote(contrat), contraintes, lignes, indisponible,
				changements.findByNumeroContratOrderByIdDesc(numero).stream()
						.map(c -> new ChangementVue(c.getMode(), c.getProfil(), c.getHorizonAnnees(), c.getFrais(),
								paris(c.getChangeLe())))
						.toList(),
				reequilibrages.findByNumeroContratOrderByIdDesc(numero).stream()
						.map(r -> new ReequilibrageVue(paris(r.getDemandeLe()), r.getMontantArbitre(),
								r.getOrdres().stream().map(o -> new ArbitrageVue(o.getOrdreId(), o.getSupportDepart(),
										o.getSupportArrivee(), o.getMontant())).toList()))
						.toList());
	}

	private BigDecimal encours(String numeroContrat) {
		return positions.encoursParSupport(numeroContrat).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_EVEN);
	}

	/** PER : années avant la retraite selon la date métier (jamais négatif) ; vide sans année de départ. */
	private Integer horizon(ContratGestion contrat) {
		if (contrat.getProduit() != Produit.PER || contrat.getAnneeDepartRetraite() == null) {
			return null;
		}
		return Math.max(0, contrat.getAnneeDepartRetraite() - dateMetier.aujourdhui().getYear());
	}

	private OffsetDateTime maintenant() {
		return dateMetier.maintenant().toOffsetDateTime();
	}

	private static OffsetDateTime paris(OffsetDateTime heure) {
		return DateMetier.heureDeParis(heure);
	}

	private UUID customerId(String jetonSession) {
		return comptes.compteCourant(jetonSession).getCustomerId();
	}

	static String euros(BigDecimal montant) {
		DecimalFormatSymbols symboles = new DecimalFormatSymbols();
		symboles.setGroupingSeparator(' ');
		symboles.setDecimalSeparator(',');
		return new DecimalFormat("#,##0.00", symboles).format(montant);
	}

	/** Aucun contrat pour ce produit (404). */
	public static class ContratIntrouvable extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public ContratIntrouvable() {
			super("Aucun contrat pour ce produit.");
		}

	}

	/** Choix refusé par une règle métier (422), avec le message à afficher. */
	public static class ChoixRefuse extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public ChoixRefuse(String message) {
			super(message);
		}

	}

	/** Profil client absent ou à revalider (409). */
	public static class ProfilRequis extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public ProfilRequis() {
			super("Complétez ou revalidez votre profil avant de continuer.");
		}

	}

	/** Versement réparti hors gestion pilotée (409). */
	public static class VersementImpossible extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public VersementImpossible() {
			super("Le versement réparti automatiquement est réservé à la gestion pilotée.");
		}

	}

	/** Demande incomplète (400). */
	public static class DemandeInvalide extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public DemandeInvalide(String message) {
			super(message);
		}

	}

	/** Contrats de test demandés en preprod ou en prod (404). */
	public static class ContratsDeTestIndisponibles extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public ContratsDeTestIndisponibles() {
			super("Les contrats de test ne sont pas disponibles dans cet environnement.");
		}

	}

}
