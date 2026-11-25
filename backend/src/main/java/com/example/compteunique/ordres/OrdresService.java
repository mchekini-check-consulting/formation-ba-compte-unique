package com.example.compteunique.ordres;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.logging.Loggable;

/**
 * CU-376 (TECH-08) : fonctions publiques du module Ordres (ADR-001, sections 2 à 4).
 * <ul>
 * <li>Un ordre par identifiant (créé par l'écran) : un double envoi renvoie l'ordre déjà enregistré.</li>
 * <li>Montant disponible d'un support = encours (« positions du contrat ») − ordres en cours qui le vendent.</li>
 * <li>Heure limite (app.ordres.heure-limite, 12:00) selon la date métier : avant, l'ordre part sur la VL du jour
 * ouvré ; à l'heure pile ou après, sur celle du jour ouvré suivant.</li>
 * <li>L'événement « ordre demandé » est enregistré dans la même transaction (outbox) : jamais d'écriture directe
 * dans Kafka. Si Kafka est indisponible, l'ordre est quand même enregistré ; le relais publiera plus tard.</li>
 * </ul>
 */
@Service
@Loggable
public class OrdresService {

	static final int CONTRAT_LONGUEUR_MAX = 30;

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

	/** Demande d'ordre telle que l'écran l'envoie ; l'identifiant est créé par l'écran à la validation. */
	public record DemandeOrdre(UUID id, String numeroContrat, TypeOrdre type, String supportDepart,
			String supportArrivee, BigDecimal montant) {
	}

	/** Résultat : l'ordre, et s'il vient d'être créé (faux pour un doublon). */
	public record Enregistrement(Ordre ordre, boolean nouveau) {
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
			BigDecimal disponible = montantDisponible(contrat, demande.supportDepart());
			if (demande.montant().compareTo(disponible) > 0) {
				throw new MontantInsuffisant(disponible);
			}
		}
		ZonedDateTime maintenant = dateMetier.maintenant();
		Ordre ordre = ordres.saveAndFlush(
				new Ordre(demande, maintenant.toOffsetDateTime(), dateExecutionPrevue(maintenant, heureLimite)));
		ordre.evenementEnregistre(evenements.enregistrer(Topic.ORDRES, "OrdreDemande", contrat, contenu(ordre)));
		return new Enregistrement(ordre, true);
	}

	/** Montant disponible d'un support du contrat, en euros (jamais négatif). */
	@Transactional(readOnly = true)
	public BigDecimal montantDisponible(String numeroContrat, String codeIsin) {
		BigDecimal disponible = positions.encours(numeroContrat, codeIsin)
				.subtract(ordres.montantEnCours(numeroContrat, codeIsin, StatutOrdre.EN_COURS_D_EXECUTION));
		return disponible.max(BigDecimal.ZERO);
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
		return jour.getDayOfWeek() != DayOfWeek.SATURDAY && jour.getDayOfWeek() != DayOfWeek.SUNDAY;
	}

	// Règle 5 de l'ADR-001 : un événement petit, identifiants et montants seulement.
	private static Map<String, Object> contenu(Ordre ordre) {
		Map<String, Object> contenu = new LinkedHashMap<>();
		contenu.put("ordreId", ordre.getId().toString());
		contenu.put("type", ordre.getType().name());
		contenu.put("numeroContrat", ordre.getNumeroContrat());
		contenu.put("montant", ordre.getMontant().toPlainString());
		if (ordre.getSupportDepart() != null) {
			contenu.put("supportDepart", ordre.getSupportDepart());
		}
		if (ordre.getSupportArrivee() != null) {
			contenu.put("supportArrivee", ordre.getSupportArrivee());
		}
		contenu.put("dateExecutionPrevue", ordre.getDateExecutionPrevue().toString());
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
		if (demande.montant() == null || demande.montant().signum() <= 0 || demande.montant().scale() > 2) {
			throw new DemandeInvalide("Le montant doit être positif, en euros et centimes.");
		}
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

	/** Demande incomplète ou incohérente (400). */
	public static class DemandeInvalide extends RuntimeException {

		public DemandeInvalide(String message) {
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
