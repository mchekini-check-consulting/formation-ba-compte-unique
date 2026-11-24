package com.example.compteunique.catalogue;

import static com.example.compteunique.catalogue.FichiersCatalogue.fichier;
import static com.example.compteunique.catalogue.FichiersCatalogue.ligne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.compteunique.catalogue.LecteurCatalogue.FichierIllisible;
import com.example.compteunique.catalogue.LecteurCatalogue.Lecture;
import com.example.compteunique.catalogue.LecteurCatalogue.LigneCatalogue;

// CU-381 (TECH-13) : format du fichier et motifs de rejet d'une ligne.
class LecteurCatalogueTest {

	private static final LocalDate DATE_FICHIER = LocalDate.of(2026, 11, 23);

	@Test
	@DisplayName("Le fichier de test principal se lit entièrement : 102 supports, aucun rejet")
	void fichierPrincipal() {
		Lecture lecture = LecteurCatalogue.lire(FichiersCatalogue.principal(), DATE_FICHIER);

		assertThat(lecture.lignes()).hasSize(102);
		assertThat(lecture.rejets()).isEmpty();
		assertThat(lecture.lignes()).filteredOn(LigneCatalogue::cote).hasSize(80);
		assertThat(lecture.lignes()).filteredOn(l -> l.statut().equals(Support.RETIRE)).hasSize(3);
		assertThat(lecture.lignes()).filteredOn(l -> l.niveauRisque() == 7).hasSize(5);
		assertThat(lecture.lignes().stream().map(LigneCatalogue::dateVl).distinct()).hasSize(4);
		assertThat(lecture.lignes().getFirst()).satisfies(premiere -> {
			assertThat(premiere.numeroLigne()).isEqualTo(2);
			assertThat(premiere.codeIsin()).isEqualTo("QZ0000000017");
			assertThat(premiere.nom()).isEqualTo("Monétaire Euro Court Terme");
			assertThat(premiere.valeurLiquidative()).isEqualByComparingTo("9082.68");
			assertThat(premiere.dateVl()).isEqualTo(LocalDate.of(2026, 11, 20));
		});
	}

	@Test
	@DisplayName("Avec les 6 anomalies : les 6 lignes de test et la ligne d'origine de l'ISIN en double sont rejetées "
			+ "avec leur motif, les 101 autres sont lues")
	void fichierAvecAnomalies() {
		Lecture lecture = LecteurCatalogue.lire(FichiersCatalogue.avecAnomalies(), DATE_FICHIER);

		assertThat(lecture.lignes()).hasSize(101);
		assertThat(lecture.rejets()).extracting("numeroLigne", "codeIsin", "motif").containsExactly(
				tuple(2, "QZ0000000017", LecteurCatalogue.MOTIF_DOUBLON),
				tuple(104, null, "Code ISIN vide"),
				tuple(105, "QZ9000000018", "Niveau de risque vide"),
				tuple(106, "QZ9000000026", "Produit ouvert vide"),
				tuple(107, "QZ9000000034", "Niveau de risque hors de 1 à 7"),
				tuple(108, "QZ9000000040", "Code ISIN invalide (clé de contrôle fausse)"),
				tuple(109, "QZ0000000017", LecteurCatalogue.MOTIF_DOUBLON));
	}

	@ParameterizedTest(name = "{1}")
	@CsvSource(delimiter = '|', value = {
		"valeur_liquidative=                | Valeur liquidative absente ou invalide",
		"valeur_liquidative=0               | Valeur liquidative inférieure ou égale à 0",
		"valeur_liquidative=-3.5            | Valeur liquidative inférieure ou égale à 0",
		"valeur_liquidative=25,40           | Valeur liquidative absente ou invalide",
		"date_vl=2026-11-24                 | Date de valeur liquidative postérieure à la date du fichier",
		"date_vl=                           | Date de valeur liquidative absente ou invalide",
		"produits_ouverts=PEA               | Produit ouvert inconnu",
		"niveau_risque=0                    | Niveau de risque hors de 1 à 7",
		"niveau_risque=trois                | Niveau de risque hors de 1 à 7",
		"code_isin=QZ00000000               | Code ISIN invalide (clé de contrôle fausse)",
		"cote=peut-être                     | Coté : oui ou non attendu",
		"statut=suspendu                    | Statut : actif ou retire attendu",
		"frais_support_pct=                 | Frais du support absents ou invalides",
		"frais_support_pct=150              | Frais du support absents ou invalides",
		"devise=EURO                        | Valeur trop longue dans la colonne devise",
	})
	void ligneRejeteeAvecSonMotif(String remplacement, String motif) {
		Lecture lecture = LecteurCatalogue.lire(fichier(ligne(remplacement.trim())), DATE_FICHIER);

		assertThat(lecture.lignes()).isEmpty();
		assertThat(lecture.rejets()).singleElement().satisfies(rejet -> {
			assertThat(rejet.numeroLigne()).isEqualTo(2);
			assertThat(rejet.motif()).isEqualTo(motif);
		});
	}

	@Test
	@DisplayName("Une ligne avec des colonnes en moins est rejetée")
	void colonnesManquantes() {
		Lecture lecture = LecteurCatalogue.lire(fichier("QZ0000000017;Monétaire;1"), DATE_FICHIER);

		assertThat(lecture.rejets()).singleElement().extracting("motif").isEqualTo("Nombre de colonnes incorrect");
	}

	@Test
	@DisplayName("Fins de ligne Unix, marque UTF-8, lignes vides et minuscules sont acceptées ; AV|PER vaut PER|AV")
	void formatTolerant() {
		String contenu = "﻿" + fichier(ligne("code_isin=qz0000000017", "produits_ouverts=av|per", "cote=OUI",
				"statut=Retire", "date_vl=2026-11-23"), "", ligne("code_isin=US0378331005", "produits_ouverts=AV"));

		Lecture lecture = LecteurCatalogue.lire(contenu, DATE_FICHIER);

		assertThat(lecture.rejets()).isEmpty();
		assertThat(lecture.lignes()).extracting("codeIsin", "produitsOuverts", "cote", "statut", "numeroLigne")
				.containsExactly(tuple("QZ0000000017", "PER|AV", true, Support.RETIRE, 2),
						tuple("US0378331005", "AV", true, Support.ACTIF, 4));
		assertThat(lecture.lignes().getFirst().fraisSupportPct()).isEqualByComparingTo(new BigDecimal("0.10"));
	}

	@Test
	@DisplayName("Un fichier vide ou sans l'en-tête attendu est illisible : rien n'est lu")
	void fichierIllisible() {
		assertThatThrownBy(() -> LecteurCatalogue.lire("", DATE_FICHIER)).isInstanceOf(FichierIllisible.class)
				.hasMessage("fichier vide");
		assertThatThrownBy(() -> LecteurCatalogue.lire(null, DATE_FICHIER)).isInstanceOf(FichierIllisible.class);
		assertThatThrownBy(() -> LecteurCatalogue.lire("isin,nom\nQZ0000000017,Fonds\n", DATE_FICHIER))
				.isInstanceOf(FichierIllisible.class)
				.hasMessageStartingWith("colonnes absentes de l'en-tête : code_isin, nom");
	}

	@ParameterizedTest
	@CsvSource({ "US0378331005, true", "QZ0000000017, true", "QZ9000000018, true", "QZ9000000040, false",
		"US0378331006, false", "1S0378331005, false", "US037833100A, false", "US03783310, false" })
	void cleDeControleIsin(String isin, boolean valide) {
		assertThat(CodeIsin.estValide(isin)).isEqualTo(valide);
	}

	@Test
	void isinAbsent() {
		assertThat(CodeIsin.estValide(null)).isFalse();
	}

}
