package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.logging.Loggable;

/**
 * CU-376 (TECH-08) : fonctions publiques du module Ordres (ADR-001, sections 2 à 4).
 * <ul>
 * <li>Un ordre par identifiant (créé par l'écran) : un double envoi renvoie l'ordre déjà enregistré.</li>
 * <li>Un ordre est en euros, ou en nombre de parts pour un rachat ou un arbitrage (un versement reste en euros).</li>
 * <li>Montant disponible d'un support = encours (« positions du contrat ») − ordres en cours qui le vendent ; un
 * ordre en parts bloque parts × dernière VL du catalogue, recalculé à chaque lecture.</li>
 * <li>Heure limite (app.ordres.heure-limite, 12:00) selon la date métier : avant, l'ordre part sur la VL du jour
 * ouvré ; à l'heure pile ou après, sur celle du jour ouvré suivant.</li>
 * <li>Annulation : seulement un ordre « demandé », avant l'heure limite du jour de sa VL d'exécution ; le montant
 * redevient disponible.</li>
 * <li>Les événements « ordre demandé » et « ordre annulé » sont enregistrés dans la même transaction (outbox) :
 * jamais d'écriture directe dans Kafka. Si Kafka est indisponible, l'ordre est quand même enregistré ; le relais
 * publiera plus tard.</li>
 * </ul>
 */
@Service
@Loggable
public class OrdresService {

	static final int CONTRAT_LONGUEUR_MAX = 30;

	private static final Set<DayOfWeek> WEEK_END = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private static final Map<StatutOrdre, String> LIBELLES = Map.of(StatutOrdre.DEMANDE, "demandé",
			StatutOrdre.EN_COURS, "en cours", StatutOrdre.EXECUTE, "exécuté", StatutOrdre.REJETE, "rejeté",
			StatutOrdre.ANNULE, "annulé");

	private static final DateTimeFormatter LIMITE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' H 'h' mm");

	private final OrdreRepository ordres;
	private final PositionsContrat positions;
	private final Evenements evenements;
	private final DateMetier dateMetier;
	private final CatalogueService catalogue;
	private final LocalTime heureLimite;

	@Autowired
	public OrdresService(OrdreRepository ordres, PositionsContrat positions, Evenements evenements,
			DateMetier dateMetier, CatalogueService catalogue,
			@Value("${app.ordres.heure-limite:12:00}") LocalTime heureLimite) {
		this.ordres = ordres;
		this.positions = positions;
		this.evenements = evenements;
		this.dateMetier = dateMetier;
		this.catalogue = catalogue;
		this.heureLimite = heureLimite;
	}

	/**
	 * Demande d'ordre telle que l'écran l'envoie ; l'identifiant est créé par l'écran à la validation. Soit un montant
	 * en euros, soit un nombre de parts du support vendu (rachat, arbitrage).
	 */
	public record DemandeOrdre(UUID id, String numeroContrat, TypeOrdre type, String supportDepart,
			String supportArrivee, BigDecimal montant, BigDecimal nombreParts) {

		/** Ordre en euros. */
		public DemandeOrdre(UUID id, String numeroContrat, TypeOrdre type, String supportDepart,
				String supportArrivee, BigDecimal montant) {
			this(id, numeroContrat, type, supportDepart, supportArrivee, montant, null);
		}

	}

	/** Résultat : l'ordre, et s'il vient d'être créé (faux pour un doublon). */
	public record Enregistrement(Ordre ordre, boolean nouveau) {
	}

	/** Résultat : l'ordre annulé, et s'il vient de l'être (faux s'il l'était déjà). */
	public record Annulation(Ordre ordre, boolean nouvelle) {
	}

	/** Enregistre un ordre au statut « demandé » avec son événement, ou renvoie l'ordre existant (doublon). */
	@Transactional
	public Enregistrement passer(DemandeOrdre demande) {
		valider(demande);
		String contrat = demande.numeroContrat().trim();
		ordres.verrouiller(contrat);
		var existant = ordres.findById(demande.id());
		if (existant.isPresent()) {
			return new Enregistrement(existant.get(), false);
		}
		if (demande.supportDepart() != null) {
			BigDecimal demandeEnEuros = demande.montant() != null ? demande.montant()
					: valoriser(demande.nombreParts(), demande.supportDepart()).orElseThrow(() -> new DemandeInvalide(
							"Aucune valeur liquidative connue pour " + demande.supportDepart()
									+ " : ordre en parts impossible."));
			BigDecimal disponible = montantDisponible(contrat, demande.supportDepart());
			if (demandeEnEuros.compareTo(disponible) > 0) {
				throw new MontantInsuffisant(disponible);
			}
		}
		ZonedDateTime maintenant = dateMetier.maintenant();
		Ordre ordre = ordres.saveAndFlush(
				new Ordre(demande, maintenant.toOffsetDateTime(), dateExecutionPrevue(maintenant, heureLimite)));
		ordre.evenementEnregistre(evenements.enregistrer(Topic.ORDRES, "OrdreDemande", contrat, contenu(ordre)));
		return new Enregistrement(ordre, true);
	}

	/**
	 * Annule un ordre « demandé » avant l'heure limite du jour de sa VL d'exécution, avec l'événement « ordre
	 * annulé ». Un ordre déjà annulé est renvoyé tel quel, sans second événement.
	 */
	@Transactional
	public Annulation annuler(UUID id) {
		Ordre ordre = ordres.verrouillerOrdre(id).orElseThrow(OrdreIntrouvable::new);
		if (ordre.getStatut() == StatutOrdre.ANNULE) {
			return new Annulation(ordre, false);
		}
		if (ordre.getStatut() != StatutOrdre.DEMANDE) {
			throw new AnnulationImpossible("Cet ordre est « " + LIBELLES.get(ordre.getStatut())
					+ " » : seul un ordre « demandé » peut être annulé.");
		}
		ZonedDateTime maintenant = dateMetier.maintenant();
		ZonedDateTime limite = ordre.getDateExecutionPrevue().atTime(heureLimite).atZone(DateMetier.PARIS);
		if (!maintenant.isBefore(limite)) {
			throw new AnnulationImpossible("L'heure limite est passée (" + limite.format(LIMITE)
					+ ") : l'ordre ne peut plus être annulé.");
		}
		ordre.annuler(maintenant.toOffsetDateTime());
		evenements.enregistrer(Topic.ORDRES, "OrdreAnnule", ordre.getNumeroContrat(), contenuAnnulation(ordre));
		return new Annulation(ordre, true);
	}

	/** Montant disponible d'un support du contrat, en euros (jamais négatif). */
	@Transactional(readOnly = true)
	public BigDecimal montantDisponible(String numeroContrat, String codeIsin) {
		BigDecimal partsBloquees = ordres.partsEnCours(numeroContrat, codeIsin, StatutOrdre.EN_COURS_D_EXECUTION);
		BigDecimal disponible = positions.encours(numeroContrat, codeIsin)
				.subtract(ordres.montantEnCours(numeroContrat, codeIsin, StatutOrdre.EN_COURS_D_EXECUTION))
				.subtract(partsBloquees.signum() == 0 ? BigDecimal.ZERO
						: valoriser(partsBloquees, codeIsin).orElse(BigDecimal.ZERO));
		return disponible.max(BigDecimal.ZERO);
	}

	/** Parts × dernière valeur liquidative du catalogue, en euros ; vide si la VL n'est pas connue. */
	private Optional<BigDecimal> valoriser(BigDecimal parts, String codeIsin) {
		return catalogue.support(codeIsin).map(SupportUc::valeurLiquidative)
				.map(vl -> parts.multiply(vl).setScale(2, RoundingMode.HALF_EVEN));
	}

	/** Les 50 derniers ordres (page de test). */
	@Transactional(readOnly = true)
	public List<Ordre> derniers() {
		return ordres.findTop50ByOrderByDemandeLeDesc();
	}

	/** Dev et qualif seulement : efface les ordres et les positions de test (refusé ailleurs). */
	@Transactional
	public void viderOrdresEtPositionsDeTest() {
		positions.viderPositionsDeTest();
		ordres.deleteAllInBatch();
	}

	/**
	 * Date de la VL d'exécution : le jour ouvré même si l'ordre arrive avant l'heure limite un jour ouvré ; sinon le
	 * jour ouvré suivant (12:00:00 pile compte comme après). Jours ouvrés : du lundi au vendredi, sans jours fériés,
	 * comme le batch du catalogue.
	 */
	static LocalDate dateExecutionPrevue(ZonedDateTime maintenant, LocalTime heureLimite) {
		LocalDate jour = maintenant.toLocalDate();
		if (estOuvre(jour) && maintenant.toLocalTime().isBefore(heureLimite)) {
			return jour;
		}
		LocalDate suivant = jour.plusDays(1);
		while (!estOuvre(suivant)) {
			suivant = suivant.plusDays(1);
		}
		return suivant;
	}

	private static boolean estOuvre(LocalDate jour) {
		return !WEEK_END.contains(jour.getDayOfWeek());
	}

	// Règle 5 de l'ADR-001 : un événement petit, identifiants et montants seulement.
	private static Map<String, Object> contenu(Ordre ordre) {
		Map<String, Object> contenu = new LinkedHashMap<>();
		contenu.put("ordreId", ordre.getId().toString());
		contenu.put("type", ordre.getType().name());
		contenu.put("numeroContrat", ordre.getNumeroContrat());
		if (ordre.enParts()) {
			contenu.put("nombreParts", ordre.getNombreParts().toPlainString());
		} else {
			contenu.put("montant", ordre.getMontant().toPlainString());
		}
		if (ordre.getSupportDepart() != null) {
			contenu.put("supportDepart", ordre.getSupportDepart());
		}
		if (ordre.getSupportArrivee() != null) {
			contenu.put("supportArrivee", ordre.getSupportArrivee());
		}
		contenu.put("dateExecutionPrevue", ordre.getDateExecutionPrevue().toString());
		return contenu;
	}

	private static Map<String, Object> contenuAnnulation(Ordre ordre) {
		Map<String, Object> contenu = new LinkedHashMap<>();
		contenu.put("ordreId", ordre.getId().toString());
		contenu.put("type", ordre.getType().name());
		contenu.put("numeroContrat", ordre.getNumeroContrat());
		contenu.put("annuleLe", DateMetier.heureDeParis(ordre.getAnnuleLe()).toString());
		return contenu;
	}

	private void valider(DemandeOrdre demande) {
		if (demande.id() == null) {
			throw new DemandeInvalide("L'identifiant de l'ordre est obligatoire.");
		}
		String contrat = demande.numeroContrat() == null ? "" : demande.numeroContrat().trim();
		if (contrat.isEmpty() || contrat.length() > CONTRAT_LONGUEUR_MAX) {
			throw new DemandeInvalide("Le numéro de contrat est obligatoire (30 caractères au plus).");
		}
		if (demande.type() == null) {
			throw new DemandeInvalide("Le type d'ordre est obligatoire : versement, rachat ou arbitrage.");
		}
		validerQuantite(demande);
		boolean depart = demande.type() != TypeOrdre.VERSEMENT;
		boolean arrivee = demande.type() != TypeOrdre.RACHAT;
		if (depart != (demande.supportDepart() != null) || arrivee != (demande.supportArrivee() != null)) {
			throw new DemandeInvalide("Supports attendus : rachat → support vendu ; versement → support acheté ; "
					+ "arbitrage → les deux.");
		}
		if (depart && arrivee && demande.supportDepart().equals(demande.supportArrivee())) {
			throw new DemandeInvalide("Un arbitrage doit changer de support.");
		}
		for (String support : new String[] { demande.supportDepart(), demande.supportArrivee() }) {
			if (support != null && catalogue.support(support).isEmpty()) {
				throw new DemandeInvalide("Support inconnu : " + support + ".");
			}
		}
	}

	private static void validerQuantite(DemandeOrdre demande) {
		if (demande.montant() != null && demande.nombreParts() != null) {
			throw new DemandeInvalide("Indiquez un montant en euros ou un nombre de parts, pas les deux.");
		}
		if (demande.nombreParts() == null) {
			if (demande.montant() == null || demande.montant().signum() <= 0 || demande.montant().scale() > 2) {
				throw new DemandeInvalide("Le montant doit être positif, en euros et centimes.");
			}
			return;
		}
		if (demande.type() == TypeOrdre.VERSEMENT) {
			throw new DemandeInvalide("Un versement s'exprime en euros, pas en nombre de parts.");
		}
		if (demande.nombreParts().signum() <= 0 || demande.nombreParts().stripTrailingZeros().scale() > 6) {
			throw new DemandeInvalide("Le nombre de parts doit être positif (6 décimales au plus).");
		}
	}

	/** Demande incomplète ou incohérente (400). */
	public static class DemandeInvalide extends RuntimeException {

		public DemandeInvalide(String message) {
			super(message);
		}

	}

	/** Ordre inconnu (404). */
	public static class OrdreIntrouvable extends RuntimeException {

		public OrdreIntrouvable() {
			super("Ordre introuvable.");
		}

	}

	/** Annulation refusée : ordre qui n'est plus « demandé » ou heure limite passée (409). */
	public static class AnnulationImpossible extends RuntimeException {

		public AnnulationImpossible(String message) {
			super(message);
		}

	}

	/** Montant demandé supérieur au montant disponible (422) : le montant disponible est renvoyé. */
	public static class MontantInsuffisant extends RuntimeException {

		private final transient BigDecimal disponible;

		public MontantInsuffisant(BigDecimal disponible) {
			super("Montant disponible insuffisant : " + euros(disponible) + " € disponibles.");
			this.disponible = disponible;
		}

		public BigDecimal getDisponible() {
			return disponible;
		}

		private static String euros(BigDecimal montant) {
			DecimalFormatSymbols symboles = new DecimalFormatSymbols();
			symboles.setGroupingSeparator(' ');
			symboles.setDecimalSeparator(',');
			return new DecimalFormat("#,##0.00", symboles).format(montant);
		}

	}

}
