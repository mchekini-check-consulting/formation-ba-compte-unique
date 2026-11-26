package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;
import com.example.compteunique.logging.Loggable;

/**
 * CU-314 / CU-338 : paramètres de la gestion (grille PER par horizon, quota de non coté AV, mandat). Ils sont lus à
 * chaque calcul : une modification s'applique aux allocations suivantes sans changer le code.
 */
@Service
@Loggable
public class ParametresGestion {

	private static final BigDecimal CENT = new BigDecimal("100");

	private final GrilleHorizonPerRepository grille;
	private final QuotaNonCoteAvRepository quotas;
	private final ParametreMandatRepository mandats;
	private final ContratGestionRepository contrats;
	private final DateMetier dateMetier;

	public ParametresGestion(GrilleHorizonPerRepository grille, QuotaNonCoteAvRepository quotas,
			ParametreMandatRepository mandats, ContratGestionRepository contrats, DateMetier dateMetier) {
		this.grille = grille;
		this.quotas = quotas;
		this.mandats = mandats;
		this.contrats = contrats;
		this.dateMetier = dateMetier;
	}

	@Transactional(readOnly = true)
	public List<GrilleHorizonPer> grille() {
		return grille.findAllByOrderByProfilAscAnneesMinAsc();
	}

	@Transactional(readOnly = true)
	public List<QuotaNonCoteAv> quotas() {
		return quotas.findAll().stream().sorted((a, b) -> a.getProfil().compareTo(b.getProfil())).toList();
	}

	@Transactional(readOnly = true)
	public List<ParametreMandat> mandats() {
		return mandats.findAll().stream().sorted((a, b) -> a.getProduit().compareTo(b.getProduit())).toList();
	}

	@Transactional(readOnly = true)
	public List<ContratGestion> contrats() {
		return contrats.findTop50ByOrderByIdDesc();
	}

	@Transactional
	public GrilleHorizonPer modifierGrille(long id, BigDecimal faibleRisque, BigDecimal nonCote) {
		pourcentage(faibleRisque, "La part minimale d'actifs à faible risque");
		pourcentage(nonCote, "La part minimale de non coté");
		if (faibleRisque.add(nonCote).compareTo(CENT) > 0) {
			throw new DemandeInvalide("Les deux parts minimales ne peuvent pas dépasser 100 % ensemble.");
		}
		GrilleHorizonPer ligne = grille.findById(id)
				.orElseThrow(() -> new DemandeInvalide("Ligne de grille inconnue."));
		ligne.modifier(faibleRisque, nonCote, dateMetier.maintenant().toOffsetDateTime());
		return ligne;
	}

	@Transactional
	public QuotaNonCoteAv modifierQuota(ProfilGestion profil, BigDecimal nonCote) {
		pourcentage(nonCote, "Le quota de non coté");
		QuotaNonCoteAv quota = quotas.findById(profil).orElseThrow(() -> new DemandeInvalide("Profil inconnu."));
		quota.modifier(nonCote, dateMetier.maintenant().toOffsetDateTime());
		return quota;
	}

	@Transactional
	public ParametreMandat modifierMandat(Produit produit, BigDecimal taux, BigDecimal minimum) {
		pourcentage(taux, "Le taux de frais tout compris");
		if (minimum == null || minimum.signum() < 0 || minimum.scale() > 2) {
			throw new DemandeInvalide("Le montant minimum du mandat doit être positif, en euros et centimes.");
		}
		ParametreMandat mandat = mandats.findById(produit).orElseThrow(() -> new DemandeInvalide("Produit inconnu."));
		mandat.modifier(taux, minimum, dateMetier.maintenant().toOffsetDateTime());
		return mandat;
	}

	private static void pourcentage(BigDecimal valeur, String nom) {
		if (valeur == null || valeur.signum() < 0 || valeur.compareTo(CENT) > 0 || valeur.scale() > 2) {
			throw new DemandeInvalide(nom + " doit être un pourcentage entre 0 et 100 (deux décimales).");
		}
	}

}
