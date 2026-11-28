package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.compteunique.catalogue.CatalogueService.Produit;

/**
 * CU-24 (US-00) et D1 / D7 du PO (27/11/2026) : paramètres publics des produits, affichés par la page publique et
 * l'espace, fonction publique du module Catalogue (ADR-001). Une seule source : frais des unités de compte (CU-412),
 * fonds en euros de l'assurance vie et du PER (taux brut visé, date de mise à jour du taux, frais de gestion, taux
 * servi) et regroupement des catégories en familles (lot 6). Un paramètre vide vaut null (ou une liste vide) : l'écran
 * ne l'affiche pas, aucune valeur n'est inventée.
 */
@Service
public class ParametresPublics {

	/** Fonds en euros d'un produit ; chaque champ est null tant que son paramètre n'est pas renseigné. */
	public record FondsEuros(BigDecimal tauxBrutVisePct, LocalDate dateMiseAJour, BigDecimal fraisGestionPct,
			BigDecimal tauxServiPct) {
	}

	/** Seuls champs exposés sans connexion (aucune donnée de cliente). */
	public record Vue(Map<Produit, BigDecimal> fraisUcPct, FondsEuros fondsEurosAv, FondsEuros fondsEurosPer,
			Map<String, String> famillesCategories) {
	}

	private final SupportsEligibles eligibles;
	private final FondsEuros fondsEurosAv;
	private final FondsEuros fondsEurosPer;
	private final Map<String, String> familles;

	@Autowired
	public ParametresPublics(SupportsEligibles eligibles,
			@Value("${app.catalogue.fonds-euros-av.taux-brut-vise:}") String tauxBrutVise,
			@Value("${app.catalogue.fonds-euros-av.date-mise-a-jour:}") String dateMiseAJour,
			@Value("${app.catalogue.fonds-euros-av.frais-gestion:}") String fraisGestion,
			@Value("${app.catalogue.fonds-euros-av.taux-servi:}") String tauxServi,
			@Value("${app.catalogue.fonds-euros-per.taux-brut-vise:}") String tauxBrutVisePer,
			@Value("${app.catalogue.fonds-euros-per.date-mise-a-jour:}") String dateMiseAJourPer,
			@Value("${app.catalogue.fonds-euros-per.frais-gestion:}") String fraisGestionPer,
			@Value("${app.catalogue.fonds-euros-per.taux-servi:}") String tauxServiPer,
			@Value("${app.catalogue.familles:}") String familles) {
		this.eligibles = eligibles;
		this.fondsEurosAv = fonds(tauxBrutVise, dateMiseAJour, fraisGestion, tauxServi);
		this.fondsEurosPer = fonds(tauxBrutVisePer, dateMiseAJourPer, fraisGestionPer, tauxServiPer);
		this.familles = familles(familles);
	}

	/** Fonds en euros de l'assurance vie seul (les autres paramètres vides). */
	ParametresPublics(SupportsEligibles eligibles, String tauxBrutVise, String dateMiseAJour, String fraisGestion) {
		this(eligibles, tauxBrutVise, dateMiseAJour, fraisGestion, "", "", "", "", "", "");
	}

	public Vue lire() {
		Map<Produit, BigDecimal> frais = new EnumMap<>(Produit.class);
		for (Produit p : Produit.values()) {
			frais.put(p, eligibles.fraisContrat(p));
		}
		return new Vue(frais, fondsEurosAv, fondsEurosPer, familles);
	}

	private static FondsEuros fonds(String tauxBrutVise, String date, String frais, String tauxServi) {
		return new FondsEuros(nombre(tauxBrutVise), date.isBlank() ? null : LocalDate.parse(date.strip()),
				nombre(frais), nombre(tauxServi));
	}

	/** « SCPI:Immobilier;OPCVM actions:Actions » → {SCPI=Immobilier, OPCVM actions=Actions} ; vide → {}. */
	static Map<String, String> familles(String valeur) {
		Map<String, String> familles = new LinkedHashMap<>();
		for (String paire : valeur.split(";")) {
			int separateur = paire.lastIndexOf(':');
			if (separateur > 0 && separateur < paire.length() - 1) {
				familles.put(paire.substring(0, separateur).strip(), paire.substring(separateur + 1).strip());
			}
		}
		return Map.copyOf(familles);
	}

	private static BigDecimal nombre(String valeur) {
		return valeur.isBlank() ? null : new BigDecimal(valeur.strip());
	}

}
