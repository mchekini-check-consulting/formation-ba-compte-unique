package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// CU-381 (TECH-13) : lecture et contrôle du fichier catalogue_uc_AAAA-MM-JJ.csv (UTF-8, séparateur « ; », une ligne
// d'en-tête, fins de ligne Windows ou Unix, décimales avec un point, dates AAAA-MM-JJ). Une ligne invalide est
// rejetée avec son motif ; les autres sont chargées. Un fichier sans l'en-tête attendu est illisible.
final class LecteurCatalogue {

	private static final String CODE_ISIN = "code_isin";
	private static final String NOM = "nom";
	private static final String TYPE = "type";
	private static final String CATEGORIE = "categorie";
	private static final String PRODUITS_OUVERTS = "produits_ouverts";
	private static final String NIVEAU_RISQUE = "niveau_risque";
	private static final String COTE = "cote";
	private static final String FRAIS = "frais_support_pct";
	private static final String STATUT = "statut";
	private static final String VALEUR_LIQUIDATIVE = "valeur_liquidative";
	private static final String DATE_VL = "date_vl";
	private static final String DEVISE = "devise";
	private static final String PERIODICITE_VL = "periodicite_vl";
	private static final String LABEL = "label";
	private static final String SOCIETE_GESTION = "societe_gestion";

	static final List<String> COLONNES = List.of(CODE_ISIN, NOM, TYPE, CATEGORIE, PRODUITS_OUVERTS, NIVEAU_RISQUE,
			COTE, FRAIS, STATUT, VALEUR_LIQUIDATIVE, DATE_VL, DEVISE, PERIODICITE_VL, LABEL, SOCIETE_GESTION);

	/** Longueurs maximales des colonnes texte (V27__catalogue_uc.sql). */
	private static final Map<String, Integer> LONGUEURS = Map.of(NOM, 200, TYPE, 50, CATEGORIE, 100, DEVISE, 3,
			PERIODICITE_VL, 30, LABEL, 100, SOCIETE_GESTION, 200);

	/** Marque d'ordre des octets (BOM) que certains logiciels placent au début d'un fichier UTF-8. */
	private static final String BOM = String.valueOf((char) 0xFEFF);

	static final String MOTIF_DOUBLON = "Code ISIN présent deux fois dans le fichier";

	private LecteurCatalogue() {
	}

	/** Une ligne valide, prête à être chargée. */
	record LigneCatalogue(int numeroLigne, String codeIsin, String nom, String type, String categorie,
			String produitsOuverts, int niveauRisque, boolean cote, BigDecimal fraisSupportPct, String statut,
			BigDecimal valeurLiquidative, LocalDate dateVl, String devise, String periodiciteVl, String label,
			String societeGestion) {
	}

	/** Une ligne rejetée : numéro de ligne dans le fichier (l'en-tête est la ligne 1), code ISIN lu, motif. */
	record Rejet(int numeroLigne, String codeIsin, String motif) {
	}

	record Lecture(List<LigneCatalogue> lignes, List<Rejet> rejets) {
	}

	/** Le fichier ne peut pas être lu du tout : rien n'est chargé. */
	static class FichierIllisible extends RuntimeException {

		FichierIllisible(String message) {
			super(message);
		}

	}

	static Lecture lire(String contenu, LocalDate dateFichier) {
		String texte = contenu == null ? "" : contenu.replace(BOM, "");
		String[] lignes = texte.split("\r?\n", -1);
		if (lignes[0].isBlank()) {
			throw new FichierIllisible("fichier vide");
		}
		Map<String, Integer> index = entete(lignes[0]);

		List<Brute> brutes = new ArrayList<>();
		for (int i = 1; i < lignes.length; i++) {
			if (!lignes[i].isBlank()) {
				brutes.add(new Brute(i + 1, lignes[i].split(";", -1)));
			}
		}
		Map<String, Long> occurrences = brutes.stream()
				.map(brute -> isin(brute, index))
				.filter(isin -> !isin.isEmpty())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		List<LigneCatalogue> valides = new ArrayList<>();
		List<Rejet> rejets = new ArrayList<>();
		for (Brute brute : brutes) {
			String isin = isin(brute, index);
			try {
				LigneCatalogue ligne = controler(brute, index, dateFichier);
				if (occurrences.get(isin) > 1) {
					throw new LigneInvalide(MOTIF_DOUBLON);
				}
				valides.add(ligne);
			} catch (LigneInvalide invalide) {
				rejets.add(new Rejet(brute.numero(), isin.isEmpty() ? null : tronquer(isin), invalide.getMessage()));
			}
		}
		return new Lecture(valides, rejets);
	}

	private record Brute(int numero, String[] valeurs) {
	}

	private static class LigneInvalide extends RuntimeException {

		LigneInvalide(String motif) {
			super(motif);
		}

	}

	private static Map<String, Integer> entete(String ligne) {
		String[] noms = ligne.split(";", -1);
		Map<String, Integer> index = new HashMap<>();
		for (int i = 0; i < noms.length; i++) {
			index.put(noms[i].trim().toLowerCase(Locale.ROOT), i);
		}
		List<String> manquantes = COLONNES.stream().filter(colonne -> !index.containsKey(colonne)).toList();
		if (!manquantes.isEmpty()) {
			throw new FichierIllisible("colonnes absentes de l'en-tête : " + String.join(", ", manquantes));
		}
		return index;
	}

	private static String valeur(Brute brute, Map<String, Integer> index, String colonne) {
		int position = index.get(colonne);
		return position < brute.valeurs().length ? brute.valeurs()[position].trim() : "";
	}

	private static String isin(Brute brute, Map<String, Integer> index) {
		return valeur(brute, index, CODE_ISIN).toUpperCase(Locale.ROOT);
	}

	private static LigneCatalogue controler(Brute brute, Map<String, Integer> index, LocalDate dateFichier) {
		if (brute.valeurs().length < index.size()) {
			throw new LigneInvalide("Nombre de colonnes incorrect");
		}
		Function<String, String> lu = colonne -> valeur(brute, index, colonne);
		String isin = isin(brute, index);
		if (isin.isEmpty()) {
			throw new LigneInvalide("Code ISIN vide");
		}
		if (!CodeIsin.estValide(isin)) {
			throw new LigneInvalide("Code ISIN invalide (clé de contrôle fausse)");
		}
		int niveauRisque = niveauRisque(lu.apply(NIVEAU_RISQUE));
		String produits = produitsOuverts(lu.apply(PRODUITS_OUVERTS));
		BigDecimal valeurLiquidative = valeurLiquidative(lu.apply(VALEUR_LIQUIDATIVE));
		LocalDate dateVl = dateVl(lu.apply(DATE_VL), dateFichier);
		boolean cote = cote(lu.apply(COTE));
		String statut = statut(lu.apply(STATUT));
		BigDecimal frais = frais(lu.apply(FRAIS));
		for (Map.Entry<String, Integer> longueur : LONGUEURS.entrySet()) {
			if (lu.apply(longueur.getKey()).length() > longueur.getValue()) {
				throw new LigneInvalide("Valeur trop longue dans la colonne " + longueur.getKey());
			}
		}
		return new LigneCatalogue(brute.numero(), isin, lu.apply(NOM), lu.apply(TYPE), lu.apply(CATEGORIE),
				produits, niveauRisque, cote, frais, statut, valeurLiquidative, dateVl,
				lu.apply(DEVISE).toUpperCase(Locale.ROOT), lu.apply(PERIODICITE_VL), lu.apply(LABEL),
				lu.apply(SOCIETE_GESTION));
	}

	private static int niveauRisque(String valeur) {
		if (valeur.isEmpty()) {
			throw new LigneInvalide("Niveau de risque vide");
		}
		if (!valeur.matches("[1-7]")) {
			throw new LigneInvalide("Niveau de risque hors de 1 à 7");
		}
		return Integer.parseInt(valeur);
	}

	private static String produitsOuverts(String valeur) {
		if (valeur.isEmpty()) {
			throw new LigneInvalide("Produit ouvert vide");
		}
		return switch (valeur.toUpperCase(Locale.ROOT).replace(" ", "")) {
			case "PER" -> "PER";
			case "AV" -> "AV";
			case "PER|AV", "AV|PER" -> "PER|AV";
			default -> throw new LigneInvalide("Produit ouvert inconnu");
		};
	}

	private static BigDecimal valeurLiquidative(String valeur) {
		BigDecimal vl = decimal(valeur);
		if (vl == null) {
			throw new LigneInvalide("Valeur liquidative absente ou invalide");
		}
		if (vl.signum() <= 0) {
			throw new LigneInvalide("Valeur liquidative inférieure ou égale à 0");
		}
		return vl;
	}

	private static LocalDate dateVl(String valeur, LocalDate dateFichier) {
		LocalDate date;
		try {
			date = LocalDate.parse(valeur);
		} catch (DateTimeParseException e) {
			throw new LigneInvalide("Date de valeur liquidative absente ou invalide");
		}
		if (date.isAfter(dateFichier)) {
			throw new LigneInvalide("Date de valeur liquidative postérieure à la date du fichier");
		}
		return date;
	}

	private static boolean cote(String valeur) {
		return switch (valeur.toLowerCase(Locale.ROOT)) {
			case "oui" -> true;
			case "non" -> false;
			default -> throw new LigneInvalide("Coté : oui ou non attendu");
		};
	}

	private static String statut(String valeur) {
		return switch (valeur.toLowerCase(Locale.ROOT)) {
			case Support.ACTIF -> Support.ACTIF;
			case Support.RETIRE -> Support.RETIRE;
			default -> throw new LigneInvalide("Statut : actif ou retire attendu");
		};
	}

	private static BigDecimal frais(String valeur) {
		BigDecimal frais = decimal(valeur);
		if (frais == null || frais.signum() < 0 || frais.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new LigneInvalide("Frais du support absents ou invalides");
		}
		return frais;
	}

	private static BigDecimal decimal(String valeur) {
		if (valeur.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(valeur);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String tronquer(String valeur) {
		return valeur.length() > 50 ? valeur.substring(0, 50) : valeur;
	}

}
