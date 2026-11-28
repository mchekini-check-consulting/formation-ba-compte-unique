package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;

/**
 * CU-314 / CU-338 : allocation cible de la gestion pilotée, calculée sur les supports éligibles au produit et au profil
 * de gestion (CU-412). Trois poches :
 * <ul>
 * <li>non coté : la part minimale de non coté (grille PER par horizon, quota AV), sur les supports non cotés ;</li>
 * <li>faible risque (PER) : la part minimale d'actifs de niveau de risque 3 ou moins, sur les supports cotés ;</li>
 * <li>le reste, sur les supports cotés de niveau 4 et plus (à défaut, sur ceux de faible risque).</li>
 * </ul>
 * Chaque poche est partagée à parts égales entre ses supports. Sur un versement, les poches minimales sont arrondies
 * au centime supérieur : la part minimale est toujours respectée (au moins 80 € de non coté sur 1 000 € à 8 %).
 */
public final class Allocation {

	static final int NIVEAU_FAIBLE_RISQUE_MAX = 3;

	private static final BigDecimal CENT = new BigDecimal("100");

	/** Un support de l'allocation et sa part, en %. */
	public record Ligne(String codeIsin, String nom, String categorie, int niveauRisque, boolean nonCote,
			BigDecimal partPct) {
	}

	/** Un montant à verser sur un support. */
	public record Montant(String codeIsin, BigDecimal montant) {
	}

	private record Poche(BigDecimal partPct, boolean minimale, List<SupportEligible> supports) {
	}

	private final List<Poche> poches;

	private Allocation(List<Poche> poches) {
		this.poches = poches;
	}

	/** Allocation sur les supports éligibles ; refusée si une part minimale n'a aucun support pour la recevoir. */
	public static Allocation calculer(List<SupportEligible> eligibles, BigDecimal partMinNonCotePct,
			BigDecimal partMinFaibleRisquePct) {
		List<SupportEligible> nonCotes = eligibles.stream().filter(SupportEligible::nonCote).toList();
		List<SupportEligible> faibleRisque = eligibles.stream()
				.filter(s -> !s.nonCote() && s.niveauRisque() <= NIVEAU_FAIBLE_RISQUE_MAX).toList();
		List<SupportEligible> autres = eligibles.stream()
				.filter(s -> !s.nonCote() && s.niveauRisque() > NIVEAU_FAIBLE_RISQUE_MAX).toList();
		List<Poche> poches = new ArrayList<>();
		ajouterMinimale(poches, partMinNonCotePct, nonCotes, "non coté");
		ajouterMinimale(poches, partMinFaibleRisquePct, faibleRisque, "à faible risque");
		BigDecimal reste = CENT.subtract(partMinNonCotePct).subtract(partMinFaibleRisquePct);
		if (reste.signum() > 0) {
			List<SupportEligible> pourLeReste = !autres.isEmpty() ? autres
					: !faibleRisque.isEmpty() ? faibleRisque : nonCotes;
			if (pourLeReste.isEmpty()) {
				throw new AllocationImpossible("Aucun support n'est disponible pour cette gestion pilotée.");
			}
			poches.add(new Poche(reste, false, pourLeReste));
		}
		return new Allocation(poches);
	}

	private static void ajouterMinimale(List<Poche> poches, BigDecimal part, List<SupportEligible> supports,
			String nature) {
		if (part.signum() <= 0) {
			return;
		}
		if (supports.isEmpty()) {
			throw new AllocationImpossible("Aucun support " + nature + " n'est disponible pour respecter la part "
					+ "minimale de " + part.stripTrailingZeros().toPlainString() + " %.");
		}
		poches.add(new Poche(part, true, supports));
	}

	/** Les supports et leur part, en % (deux décimales, total 100). */
	public List<Ligne> lignes() {
		Map<String, Ligne> parSupport = new LinkedHashMap<>();
		for (Poche poche : poches) {
			List<BigDecimal> parts = partager(poche.partPct(), poche.supports().size());
			for (int i = 0; i < parts.size(); i++) {
				SupportEligible s = poche.supports().get(i);
				BigDecimal part = parts.get(i);
				parSupport.merge(s.codeIsin(),
						new Ligne(s.codeIsin(), s.nom(), s.categorie(), s.niveauRisque(), s.nonCote(), part),
						(a, b) -> new Ligne(a.codeIsin(), a.nom(), a.categorie(), a.niveauRisque(), a.nonCote(),
								a.partPct().add(b.partPct())));
			}
		}
		return List.copyOf(parSupport.values());
	}

	/** Répartition d'un versement en euros : les poches minimales arrondies au centime supérieur. */
	public List<Montant> repartir(BigDecimal montant) {
		List<BigDecimal> parPoche = new ArrayList<>();
		BigDecimal minimales = BigDecimal.ZERO;
		for (Poche poche : poches) {
			BigDecimal partEuros = poche.minimale()
					? montant.multiply(poche.partPct()).divide(CENT, 2, RoundingMode.CEILING)
					: BigDecimal.ZERO;
			parPoche.add(partEuros);
			minimales = minimales.add(partEuros);
		}
		BigDecimal reste = montant.subtract(minimales);
		int derniere = poches.size() - 1;
		if (!poches.get(derniere).minimale()) {
			parPoche.set(derniere, reste.max(BigDecimal.ZERO));
		}
		if (reste.signum() < 0) {
			// Arrondis cumulés au-delà du montant (parts minimales à 100 %) : la dernière poche rend l'excédent.
			parPoche.set(derniere, parPoche.get(derniere).add(reste));
		}
		Map<String, BigDecimal> parSupport = new LinkedHashMap<>();
		for (int p = 0; p < poches.size(); p++) {
			List<SupportEligible> supports = poches.get(p).supports();
			List<BigDecimal> montants = partager(parPoche.get(p), supports.size());
			for (int i = 0; i < montants.size(); i++) {
				parSupport.merge(supports.get(i).codeIsin(), montants.get(i), BigDecimal::add);
			}
		}
		return parSupport.entrySet().stream().filter(e -> e.getValue().signum() > 0)
				.map(e -> new Montant(e.getKey(), e.getValue())).toList();
	}

	/** Partage à parts égales, au centième près ; le dernier reçoit le reste de l'arrondi. */
	private static List<BigDecimal> partager(BigDecimal total, int nombre) {
		BigDecimal part = total.divide(BigDecimal.valueOf(nombre), 2, RoundingMode.DOWN);
		List<BigDecimal> parts = new ArrayList<>();
		for (int i = 0; i < nombre - 1; i++) {
			parts.add(part);
		}
		parts.add(total.subtract(part.multiply(BigDecimal.valueOf(nombre - 1L))).setScale(2, RoundingMode.HALF_EVEN));
		return parts;
	}

	/** Une part minimale ne peut être placée faute de support (catalogue vide ou incomplet). */
	public static class AllocationImpossible extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public AllocationImpossible(String message) {
			super(message);
		}

	}

}
