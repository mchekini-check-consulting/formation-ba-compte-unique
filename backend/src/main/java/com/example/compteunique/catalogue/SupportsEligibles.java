package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.logging.Loggable;

/**
 * CU-412 (CAT-01B) : fonction publique « lister les supports éligibles » du module Catalogue (ADR-001, règle 1).
 * C'est le seul endroit où le filtre est fait : cet écran et les suivants (souscription, mode de gestion, arbitrage)
 * l'appellent. Seuls les supports actifs, ouverts au produit et dont le niveau de risque ne dépasse pas le maximum du
 * profil sont renvoyés ; le niveau 7 n'est jamais proposé en ligne. Matrice et frais du contrat sont des paramètres
 * (décision PO du 23/11/2026, à confirmer par la conformité).
 */
@Service
@Loggable
public class SupportsEligibles {

	/** Niveau de risque jamais proposé en ligne. */
	static final int NIVEAU_JAMAIS_EN_LIGNE = 7;

	private static final String SANS_LABEL = "aucun";

	/** Un support éligible, tel que l'écran l'affiche. Frais du support seulement : ceux du contrat sont à part. */
	public record SupportEligible(String codeIsin, String nom, String categorie, int niveauRisque,
			BigDecimal fraisSupportPct, BigDecimal valeurLiquidative, LocalDate dateVl, boolean nonCote,
			String label, String periodiciteVl) {

		static SupportEligible de(Support s) {
			return new SupportEligible(s.getCodeIsin(), s.getNom(), s.getCategorie(), s.getNiveauRisque(),
					s.getFraisSupportPct(), s.getValeurLiquidative(), s.getDateVl(), !s.isCote(),
					SANS_LABEL.equalsIgnoreCase(s.getLabel()) ? null : s.getLabel(), s.getPeriodiciteVl());
		}

	}

	private final SupportRepository supports;
	private final Map<String, Integer> niveauMax;
	private final Map<Produit, BigDecimal> fraisContratParProduit;

	@Autowired
	public SupportsEligibles(SupportRepository supports,
			@Value("${app.catalogue.risque-max.prudent:3}") int prudent,
			@Value("${app.catalogue.risque-max.equilibre:4}") int equilibre,
			@Value("${app.catalogue.risque-max.dynamique:6}") int dynamique,
			@Value("${app.catalogue.frais-contrat.per:0.50}") BigDecimal fraisPer,
			@Value("${app.catalogue.frais-contrat.av:0.65}") BigDecimal fraisAv) {
		this.supports = supports;
		this.niveauMax = Map.of("PRUDENT", prudent, "EQUILIBRE", equilibre, "DYNAMIQUE", dynamique);
		this.fraisContratParProduit = Map.of(Produit.PER, fraisPer, Produit.AV, fraisAv);
	}

	/**
	 * Supports éligibles pour un produit et un profil investisseur (PRUDENT, EQUILIBRE, DYNAMIQUE), triés par niveau de
	 * risque croissant puis par nom. Profil inconnu : aucun support. Un support inadapté n'apparaît pas, sans message.
	 */
	@Transactional(readOnly = true)
	public List<SupportEligible> lister(Produit produit, String profilInvestisseur) {
		int maximum = Math.min(niveauMax.getOrDefault(profilInvestisseur == null ? "" : profilInvestisseur, 0),
				NIVEAU_JAMAIS_EN_LIGNE - 1);
		return supports.findByStatutOrderByNom(Support.ACTIF).stream()
				.filter(s -> List.of(s.getProduitsOuverts().split("\\|")).contains(produit.name()))
				.filter(s -> s.getNiveauRisque() <= maximum)
				.map(SupportEligible::de)
				.sorted(Comparator.comparingInt(SupportEligible::niveauRisque).thenComparing(SupportEligible::nom,
						ordreAlphabetiqueFrancais()))
				.toList();
	}

	/**
	 * CU-417 : ordre alphabétique français, sans tenir compte des majuscules ni des accents (« Crédit Euro Investment
	 * Grade » avant « Crédit Euro ISR », « Énergie » avant « Europe »). Un comparateur par tri : Collator n'est pas
	 * partagé entre les appels.
	 */
	private static Comparator<String> ordreAlphabetiqueFrancais() {
		Collator collator = Collator.getInstance(Locale.FRENCH);
		collator.setStrength(Collator.PRIMARY);
		return collator::compare;
	}

	/**
	 * Niveau de risque maximum d'un profil (prudent 3, équilibré 4, dynamique 6 ; paramètres), 0 pour un profil inconnu.
	 * Lu par le module Gestion pour signaler les supports non cotés hors profil (AV-07).
	 */
	public int niveauRisqueMax(String profil) {
		return niveauMax.getOrDefault(profil == null ? "" : profil, 0);
	}

	/** Frais de gestion annuels du contrat pour les unités de compte, en % (0,50 PER, 0,65 assurance vie). */
	public BigDecimal fraisContrat(Produit produit) {
		return fraisContratParProduit.get(produit);
	}

}
