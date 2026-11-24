package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.logging.Loggable;

/**
 * CU-381 (TECH-13) : fonctions publiques du catalogue des unités de compte (ADR-001, règle 1). Les autres modules
 * (CAT-01, exécution des ordres TECH-09, versements, arbitrages) ne lisent jamais les tables du schéma catalogue :
 * ils appellent ces fonctions.
 */
@Service
@Loggable
public class CatalogueService {

	/** Produit d'épargne qui peut proposer un support. */
	public enum Produit {
		PER, AV
	}

	/** Un support du catalogue, tel que les autres modules le voient. */
	public record SupportUc(String codeIsin, String nom, String type, String categorie, String produitsOuverts,
			int niveauRisque, boolean cote, BigDecimal fraisSupportPct, boolean retire, BigDecimal valeurLiquidative,
			LocalDate dateVl, String devise, String periodiciteVl, String label, String societeGestion) {

		static SupportUc de(Support s) {
			return new SupportUc(s.getCodeIsin(), s.getNom(), s.getType(), s.getCategorie(), s.getProduitsOuverts(),
					s.getNiveauRisque(), s.isCote(), s.getFraisSupportPct(), !s.estActif(), s.getValeurLiquidative(),
					s.getDateVl(), s.getDevise(), s.getPeriodiciteVl(), s.getLabel(), s.getSocieteGestion());
		}

	}

	private final SupportRepository supports;
	private final HistoriqueVlRepository historique;
	private final EvenementCatalogueRepository evenements;

	public CatalogueService(SupportRepository supports, HistoriqueVlRepository historique,
			EvenementCatalogueRepository evenements) {
		this.supports = supports;
		this.historique = historique;
		this.evenements = evenements;
	}

	/** Supports proposés aux versements et aux arbitrages entrants d'un produit : jamais les supports retirés. */
	@Transactional(readOnly = true)
	public List<SupportUc> supportsProposes(Produit produit) {
		return supports.findByStatutOrderByNom(Support.ACTIF).stream()
				.filter(support -> List.of(support.getProduitsOuverts().split("\\|")).contains(produit.name()))
				.map(SupportUc::de)
				.toList();
	}

	/** Un support, même retiré (il reste visible sur les contrats qui le détiennent). */
	@Transactional(readOnly = true)
	public Optional<SupportUc> support(String codeIsin) {
		return supports.findByCodeIsin(codeIsin).map(SupportUc::de);
	}

	/**
	 * Valeur liquidative d'un support à une date précise (exécution des ordres, TECH-09). Vide si elle n'a pas été
	 * chargée : l'ordre reste « en cours » et sera exécuté la nuit suivante.
	 */
	@Transactional(readOnly = true)
	public Optional<BigDecimal> valeurLiquidative(String codeIsin, LocalDate date) {
		return historique.findBySupportCodeIsinAndDateVl(codeIsin, date).map(HistoriqueVl::getValeurLiquidative);
	}

	/** Vrai si l'événement « catalogue mis à jour » existe pour ce jour métier : l'étape suivante peut démarrer. */
	@Transactional(readOnly = true)
	public boolean catalogueMisAJour(LocalDate dateMetier) {
		return evenements.existsByTypeAndDateMetier(EvenementCatalogue.CATALOGUE_MIS_A_JOUR, dateMetier);
	}

}
