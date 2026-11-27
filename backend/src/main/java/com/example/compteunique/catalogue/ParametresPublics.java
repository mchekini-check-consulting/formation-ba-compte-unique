package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.compteunique.catalogue.CatalogueService.Produit;

/**
 * CU-24 (US-00) et D1 / D7 du PO (27/11/2026) : paramètres publics des produits, affichés par la page publique et
 * l'espace, fonction publique du module Catalogue (ADR-001). Une seule source : frais des unités de compte (CU-412)
 * et fonds en euros de l'assurance vie (taux brut visé, date de mise à jour du taux, frais de gestion). Un paramètre
 * vide vaut null : l'écran ne l'affiche pas, aucune valeur n'est inventée.
 */
@Service
public class ParametresPublics {

	/** Fonds en euros de l'assurance vie ; chaque champ est null tant que son paramètre n'est pas renseigné. */
	public record FondsEurosAv(BigDecimal tauxBrutVisePct, LocalDate dateMiseAJour, BigDecimal fraisGestionPct) {
	}

	/** Seuls champs exposés sans connexion (aucune donnée de cliente). */
	public record Vue(Map<Produit, BigDecimal> fraisUcPct, FondsEurosAv fondsEurosAv) {
	}

	private final SupportsEligibles eligibles;
	private final FondsEurosAv fondsEurosAv;

	public ParametresPublics(SupportsEligibles eligibles,
			@Value("${app.catalogue.fonds-euros-av.taux-brut-vise:}") String tauxBrutVise,
			@Value("${app.catalogue.fonds-euros-av.date-mise-a-jour:}") String dateMiseAJour,
			@Value("${app.catalogue.fonds-euros-av.frais-gestion:}") String fraisGestion) {
		this.eligibles = eligibles;
		this.fondsEurosAv = new FondsEurosAv(nombre(tauxBrutVise),
				dateMiseAJour.isBlank() ? null : LocalDate.parse(dateMiseAJour.strip()), nombre(fraisGestion));
	}

	public Vue lire() {
		Map<Produit, BigDecimal> frais = new EnumMap<>(Produit.class);
		for (Produit p : Produit.values()) {
			frais.put(p, eligibles.fraisContrat(p));
		}
		return new Vue(frais, fondsEurosAv);
	}

	private static BigDecimal nombre(String valeur) {
		return valeur.isBlank() ? null : new BigDecimal(valeur.strip());
	}

}
