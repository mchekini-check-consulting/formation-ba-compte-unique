package com.example.compteunique.catalogue;

import static com.example.compteunique.catalogue.FichiersCatalogue.fichier;
import static com.example.compteunique.catalogue.FichiersCatalogue.ligne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.compteunique.alertes.AlertesEquipe;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.catalogue.CatalogueTestController.Depot;
import com.example.compteunique.catalogue.CatalogueTestController.JournalVue;
import com.example.compteunique.catalogue.ChargementCatalogue.DepotInvalide;
import com.example.compteunique.catalogue.ChargementCatalogue.JourNonOuvre;
import com.example.compteunique.catalogue.JournalBatch.Declenchement;
import com.example.compteunique.catalogue.JournalBatch.Statut;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;

// CU-381 (TECH-13) : critères d'acceptation du chargement nocturne du catalogue, base simulée en mémoire.
// Critère 9 (TECH-09 utilise les VL chargées) : vérifié par CatalogueService.valeurLiquidative, que l'exécution des
// ordres appellera ; TECH-09 n'est pas encore développé.
class ChargementCatalogueTest {

	/** Lundi 23/11/2026, 2 h à Paris : date du fichier de test. */
	private static final ZonedDateTime LUNDI_2H = ZonedDateTime.of(2026, 11, 23, 2, 0, 0, 0, DateMetier.PARIS);

	private final SupportRepository supports = mock(SupportRepository.class);
	private final HistoriqueVlRepository historique = mock(HistoriqueVlRepository.class);
	private final FichierDeposeRepository fichiers = mock(FichierDeposeRepository.class);
	private final JournalBatchRepository journaux = mock(JournalBatchRepository.class);
	private final EvenementCatalogueRepository evenements = mock(EvenementCatalogueRepository.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final Evenements kafka = mock(Evenements.class);

	private final List<Support> baseSupports = new ArrayList<>();
	private final List<HistoriqueVl> baseHistorique = new ArrayList<>();
	private final List<FichierDepose> baseFichiers = new ArrayList<>();
	private final List<JournalBatch> baseJournaux = new ArrayList<>();
	private final List<EvenementCatalogue> baseEvenements = new ArrayList<>();

	private final AlertesEquipe alertes = mock(AlertesEquipe.class);
	private final ChargementCatalogue chargement = new ChargementCatalogue(supports, historique, fichiers, journaux,
			evenements, dateMetier, kafka, alertes);
	private final CatalogueService catalogue = new CatalogueService(supports, historique, evenements);

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(LUNDI_2H);
		when(supports.save(any())).thenAnswer(appel -> ajouter(baseSupports, appel.getArgument(0)));
		when(supports.findByCodeIsinIn(anyCollection())).thenAnswer(appel -> {
			Collection<String> isins = appel.getArgument(0);
			return baseSupports.stream().filter(s -> isins.contains(s.getCodeIsin())).toList();
		});
		when(supports.findByCodeIsin(anyString())).thenAnswer(appel -> baseSupports.stream()
				.filter(s -> s.getCodeIsin().equals(appel.getArgument(0))).findFirst());
		when(supports.findByStatutOrderByNom(anyString())).thenAnswer(appel -> baseSupports.stream()
				.filter(s -> s.getStatut().equals(appel.getArgument(0)))
				.sorted(Comparator.comparing(Support::getNom))
				.toList());
		when(historique.save(any())).thenAnswer(appel -> ajouter(baseHistorique, appel.getArgument(0)));
		when(historique.existsBySupportAndDateVl(any(), any())).thenAnswer(appel -> baseHistorique.stream()
				.anyMatch(h -> h.getSupport() == appel.getArgument(0) && h.getDateVl().equals(appel.getArgument(1))));
		when(historique.findBySupportCodeIsinAndDateVl(anyString(), any())).thenAnswer(appel -> baseHistorique
				.stream()
				.filter(h -> h.getSupport().getCodeIsin().equals(appel.getArgument(0))
						&& h.getDateVl().equals(appel.getArgument(1)))
				.findFirst());
		when(historique.findBySupportCodeIsinOrderByDateVlDesc(anyString())).thenAnswer(appel -> baseHistorique
				.stream()
				.filter(h -> h.getSupport().getCodeIsin().equals(appel.getArgument(0)))
				.sorted(Comparator.comparing(HistoriqueVl::getDateVl).reversed())
				.toList());
		org.mockito.Mockito.doAnswer(appel -> {
			baseHistorique.clear();
			return null;
		}).when(historique).deleteAllInBatch();
		org.mockito.Mockito.doAnswer(appel -> {
			if (!baseHistorique.isEmpty()) {
				throw new IllegalStateException("historique_vl référence encore des supports");
			}
			baseSupports.clear();
			return null;
		}).when(supports).deleteAllInBatch();
		when(fichiers.save(any())).thenAnswer(appel -> ajouter(baseFichiers, appel.getArgument(0)));
		when(fichiers.findFirstByDateFichierOrderByIdDesc(any())).thenAnswer(appel -> baseFichiers.reversed().stream()
				.filter(f -> f.getDateFichier().equals(appel.getArgument(0))).findFirst());
		when(fichiers.findTop20ByOrderByIdDesc()).thenAnswer(appel -> baseFichiers.reversed());
		when(journaux.save(any())).thenAnswer(appel -> ajouter(baseJournaux, appel.getArgument(0)));
		when(journaux.existsByDateMetierAndDeclenchement(any(), any())).thenAnswer(appel -> baseJournaux.stream()
				.anyMatch(j -> j.getDateMetier().equals(appel.getArgument(0))
						&& j.getDeclenchement() == appel.getArgument(1)));
		when(journaux.findTop20ByOrderByIdDesc()).thenAnswer(appel -> baseJournaux.reversed());
		when(evenements.save(any())).thenAnswer(appel -> ajouter(baseEvenements, appel.getArgument(0)));
		when(evenements.existsByTypeAndDateMetier(anyString(), any())).thenAnswer(appel -> baseEvenements.stream()
				.anyMatch(e -> e.getType().equals(appel.getArgument(0))
						&& e.getDateMetier().equals(appel.getArgument(1))));
	}

	private static <T> T ajouter(List<T> table, T ligne) {
		if (!table.contains(ligne)) {
			table.add(ligne);
		}
		return ligne;
	}

	private void deposer(String contenu) {
		chargement.deposer("catalogue_uc_2026-11-23.csv", contenu);
	}

	private void dateMetier(ZonedDateTime date) {
		when(dateMetier.maintenant()).thenReturn(date);
	}

	private Support support(String isin) {
		return baseSupports.stream().filter(s -> s.getCodeIsin().equals(isin)).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("Critère 1 : étant donné le fichier du 23/11/2026 et un catalogue vide, quand le batch s'exécute, "
			+ "alors 102 supports sont enregistrés avec leur VL et sa date, et le journal indique 102 ajoutés, 0 rejeté")
	void critere1CatalogueVide() {
		deposer(FichiersCatalogue.principal());

		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getStatut()).isEqualTo(Statut.TERMINE);
		assertThat(journal.getAjoutes()).isEqualTo(102);
		assertThat(journal.getModifies()).isZero();
		assertThat(journal.getRetires()).isZero();
		assertThat(journal.getRejetees()).isZero();
		assertThat(journal.getNomFichier()).isEqualTo("catalogue_uc_2026-11-23.csv");
		assertThat(baseSupports).hasSize(102);
		assertThat(baseHistorique).hasSize(102);
		assertThat(support("QZ0000000017").getValeurLiquidative()).isEqualByComparingTo("9082.68");
		assertThat(support("QZ0000000017").getDateVl()).isEqualTo(LocalDate.of(2026, 11, 20));
		assertThat(baseHistorique.getFirst().getSource()).isEqualTo("catalogue_uc_2026-11-23.csv");
		assertThat(baseEvenements).singleElement().satisfies(evenement -> {
			assertThat(evenement.getType()).isEqualTo("CATALOGUE_MIS_A_JOUR");
			assertThat(evenement.getDateMetier()).isEqualTo(LocalDate.of(2026, 11, 23));
			assertThat(evenement.getAjoutes()).isEqualTo(102);
			assertThat(evenement.getModifies()).isZero();
			assertThat(evenement.getRetires()).isZero();
			assertThat(evenement.getRejetees()).isZero();
		});
		verify(journaux).verrouiller();
		// CU-382 : « catalogue mis à jour » publié dans Kafka, clé = date métier
		verify(kafka).enregistrer(Topic.CATALOGUE, "CatalogueMisAJour", "2026-11-23",
				Map.of("ajoutes", 102, "modifies", 0, "retires", 0, "rejetees", 0));
	}

	@Test
	@DisplayName("Critère 2 : étant donné le même fichier chargé une deuxième fois, alors le catalogue compte "
			+ "toujours 102 supports et le journal indique 0 ajouté, 0 modifié")
	void critere2RejouerNeChangeRien() {
		deposer(FichiersCatalogue.principal());
		chargement.lancer(Declenchement.AUTOMATIQUE);

		JournalBatch deuxieme = chargement.lancer(Declenchement.MANUEL);

		assertThat(deuxieme.getAjoutes()).isZero();
		assertThat(deuxieme.getModifies()).isZero();
		assertThat(deuxieme.getRetires()).isZero();
		assertThat(baseSupports).hasSize(102);
		assertThat(baseHistorique).hasSize(102);
	}

	@Test
	@DisplayName("Critère 3 : étant donné le fichier complété des 6 anomalies, alors les 6 lignes de test et la "
			+ "ligne d'origine de l'ISIN en double sont rejetées avec leur motif, et les 101 autres supports chargés")
	void critere3Anomalies() {
		deposer(FichiersCatalogue.avecAnomalies());

		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getAjoutes()).isEqualTo(101);
		assertThat(journal.getRejetees()).isEqualTo(7);
		assertThat(journal.getLignesRejetees()).extracting(LigneRejetee::getMotif).contains("Code ISIN vide",
				"Niveau de risque vide", "Produit ouvert vide", "Niveau de risque hors de 1 à 7",
				"Code ISIN invalide (clé de contrôle fausse)", LecteurCatalogue.MOTIF_DOUBLON);
		assertThat(baseSupports).hasSize(101).noneMatch(s -> s.getCodeIsin().equals("QZ0000000017"));
	}

	@Test
	@DisplayName("Critère 4 : étant donné un support au statut « retire », alors il n'est plus proposé aux "
			+ "versements ni aux arbitrages entrants, et reste visible sur les contrats qui le détiennent")
	void critere4SupportRetire() {
		deposer(fichier(ligne(), ligne("code_isin=US0378331005", "nom=Actions Monde")));
		chargement.lancer(Declenchement.AUTOMATIQUE);
		assertThat(catalogue.supportsProposes(Produit.PER)).extracting(SupportUc::codeIsin)
				.containsExactly("US0378331005", "QZ0000000017");

		dateMetier(LUNDI_2H.plusDays(1));
		chargement.deposer("catalogue_uc_2026-11-24.csv",
				fichier(ligne("statut=retire"), ligne("code_isin=US0378331005", "nom=Actions Monde")));
		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getRetires()).isEqualTo(1);
		assertThat(journal.getModifies()).isZero();
		assertThat(catalogue.supportsProposes(Produit.PER)).extracting(SupportUc::codeIsin)
				.containsExactly("US0378331005");
		assertThat(catalogue.supportsProposes(Produit.AV)).extracting(SupportUc::codeIsin)
				.containsExactly("US0378331005");
		assertThat(catalogue.support("QZ0000000017")).hasValueSatisfying(s -> assertThat(s.retire()).isTrue());
		// CU-383 : en plus de « catalogue mis à jour », « support retiré » est publié, clé = code ISIN.
		verify(kafka).enregistrer(Topic.CATALOGUE, "SupportRetire", "QZ0000000017",
				Map.of("codeIsin", "QZ0000000017", "nom", support("QZ0000000017").getNom()));
		verify(kafka).enregistrer(Topic.CATALOGUE, "CatalogueMisAJour", "2026-11-24",
				Map.of("ajoutes", 0, "modifies", 0, "retires", 1, "rejetees", 0));
	}

	@Test
	@DisplayName("CU-383 : étant donné QZ8000000010 actif, quand un nouveau fichier du même jour le passe au statut "
			+ "« retire », alors le batch compte 1 retiré et publie « support retiré » avec son ISIN comme clé")
	void cu383RetraitLeMemeJour() {
		deposer(fichier(ligne("code_isin=QZ8000000010")));
		chargement.lancer(Declenchement.MANUEL);

		deposer(fichier(ligne("code_isin=QZ8000000010", "statut=retire")));
		JournalBatch journal = chargement.lancer(Declenchement.MANUEL);

		assertThat(journal.getRetires()).isEqualTo(1);
		assertThat(journal.getModifies()).isZero();
		verify(kafka).enregistrer(Topic.CATALOGUE, "SupportRetire", "QZ8000000010",
				Map.of("codeIsin", "QZ8000000010", "nom", support("QZ8000000010").getNom()));
	}

	@Test
	@DisplayName("CU-383 : au premier chargement, un support déjà au statut « retire » est enregistré comme retiré, "
			+ "compté comme ajouté (CU-381, critère 1), sans événement « support retiré »")
	void cu383DejaRetireAuPremierChargement() {
		deposer(FichiersCatalogue.principal());

		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getAjoutes()).isEqualTo(102);
		assertThat(journal.getRetires()).isZero();
		assertThat(catalogue.support("QZ0000001023")).hasValueSatisfying(s -> {
			assertThat(s.nom()).isEqualTo("SCPI Bureaux Paris (fermée)");
			assertThat(s.retire()).isTrue();
		});
		verify(kafka, never()).enregistrer(any(), org.mockito.ArgumentMatchers.eq("SupportRetire"), anyString(),
				any());
	}

	@Test
	@DisplayName("CU-383 / TECH-13 en test : l'historique des valeurs liquidatives d'un support est consultable, "
			+ "de la plus récente à la plus ancienne, avec sa source")
	void historiqueDesValeursLiquidatives() {
		deposer(fichier(ligne("valeur_liquidative=25.00", "date_vl=2026-11-20")));
		chargement.lancer(Declenchement.AUTOMATIQUE);
		dateMetier(LUNDI_2H.plusDays(1));
		chargement.deposer("catalogue_uc_2026-11-24.csv",
				fichier(ligne("valeur_liquidative=25.40", "date_vl=2026-11-23")));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(catalogue.historiqueVl("QZ0000000017"))
				.extracting(CatalogueService.ValeurLiquidative::dateVl, v -> v.valeur().toPlainString(),
						CatalogueService.ValeurLiquidative::source)
				.containsExactly(tuple(LocalDate.of(2026, 11, 23), "25.40", "catalogue_uc_2026-11-24.csv"),
						tuple(LocalDate.of(2026, 11, 20), "25.00", "catalogue_uc_2026-11-23.csv"));
		assertThat(catalogue.historiqueVl("INCONNU")).isEmpty();
	}

	@Test
	@DisplayName("CU-383 / TECH-13 en test : « Vider le catalogue » efface les supports et les valeurs liquidatives, "
			+ "garde le journal et les fichiers déposés ; le fichier peut ensuite être rechargé")
	void viderLeCatalogue() {
		deposer(FichiersCatalogue.principal());
		chargement.lancer(Declenchement.MANUEL);

		chargement.vider();

		assertThat(baseSupports).isEmpty();
		assertThat(baseHistorique).isEmpty();
		assertThat(baseJournaux).hasSize(1);
		assertThat(baseFichiers).hasSize(1);
		JournalBatch rechargement = chargement.lancer(Declenchement.MANUEL);
		assertThat(rechargement.getAjoutes()).isEqualTo(102);
	}

	@Test
	@DisplayName("Critère 5 : étant donné un support présent hier et absent du fichier du jour, alors il reste "
			+ "actif avec sa dernière VL et sa date")
	void critere5SupportAbsentDuFichier() {
		deposer(fichier(ligne(), ligne("code_isin=US0378331005")));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		dateMetier(LUNDI_2H.plusDays(1));
		chargement.deposer("catalogue_uc_2026-11-24.csv", fichier(ligne("valeur_liquidative=26", "date_vl=2026-11-23")));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		Support absent = support("US0378331005");
		assertThat(absent.estActif()).isTrue();
		assertThat(absent.getValeurLiquidative()).isEqualByComparingTo("25.00");
		assertThat(absent.getDateVl()).isEqualTo(LocalDate.of(2026, 11, 20));
	}

	@Test
	@DisplayName("Critère 6 : étant donné une VL passée de 25,00 € à 25,40 €, alors 25,40 € est enregistrée avec "
			+ "sa date et 25,00 € reste dans l'historique")
	void critere6HistoriqueDesVl() {
		deposer(fichier(ligne("valeur_liquidative=25.00", "date_vl=2026-11-20")));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		dateMetier(LUNDI_2H.plusDays(1));
		chargement.deposer("catalogue_uc_2026-11-24.csv",
				fichier(ligne("valeur_liquidative=25.40", "date_vl=2026-11-23")));
		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getModifies()).isEqualTo(1);
		assertThat(support("QZ0000000017").getValeurLiquidative()).isEqualByComparingTo("25.40");
		assertThat(support("QZ0000000017").getDateVl()).isEqualTo(LocalDate.of(2026, 11, 23));
		assertThat(baseHistorique).extracting(HistoriqueVl::getValeurLiquidative, HistoriqueVl::getDateVl)
				.containsExactly(tuple(new BigDecimal("25.00"), LocalDate.of(2026, 11, 20)),
						tuple(new BigDecimal("25.40"), LocalDate.of(2026, 11, 23)));
	}

	@Test
	@DisplayName("Critère 7 : étant donné aucun fichier à 2 h un jour ouvré, alors le catalogue n'est pas modifié, "
			+ "le journal est en échec (équipe alertée) et l'étape des ordres ne démarre pas")
	void critere7FichierAbsent() {
		deposer(fichier(ligne()));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		dateMetier(LUNDI_2H.plusDays(1));
		JournalBatch journal = chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(journal.getStatut()).isEqualTo(Statut.ECHEC);
		assertThat(journal.getMessage()).isEqualTo("Fichier absent : catalogue_uc_2026-11-24.csv");
		assertThat(baseSupports).hasSize(1);
		assertThat(baseEvenements).hasSize(1);
		assertThat(catalogue.catalogueMisAJour(LocalDate.of(2026, 11, 24))).isFalse();
		assertThat(catalogue.valeurLiquidative("QZ0000000017", LocalDate.of(2026, 11, 24))).isEmpty();
		assertThat(chargement.dejaLance(LocalDate.of(2026, 11, 24))).isTrue();
		// CU-382 critère 11 : l'équipe est alertée par le mécanisme commun (e-mail et journal des alertes).
		verify(alertes).signaler("Batch catalogue", "Batch catalogue en échec : fichier absent");
		verify(kafka, never()).enregistrer(Topic.CATALOGUE, "CatalogueMisAJour", "2026-11-24", Map.of("ajoutes", 0,
				"modifies", 0, "retires", 0, "rejetees", 0));
	}

	@Test
	@DisplayName("Un fichier illisible (sans l'en-tête attendu) ne modifie rien et le journal est en échec")
	void fichierIllisible() {
		deposer("isin;nom\nQZ0000000017;Fonds\n");

		JournalBatch journal = chargement.lancer(Declenchement.MANUEL);

		assertThat(journal.getStatut()).isEqualTo(Statut.ECHEC);
		assertThat(journal.getMessage()).startsWith("Fichier illisible : colonnes absentes de l'en-tête");
		assertThat(baseSupports).isEmpty();
		assertThat(baseEvenements).isEmpty();
		verify(alertes).signaler("Batch catalogue", "Batch catalogue en échec : fichier illisible");
	}

	@Test
	@DisplayName("Critère 8 : étant donné un samedi en date métier, quand l'heure du batch arrive, alors le batch "
			+ "ne s'exécute pas")
	void critere8Samedi() {
		dateMetier(ZonedDateTime.of(2026, 11, 28, 2, 0, 0, 0, DateMetier.PARIS));
		BatchCatalogue batch = new BatchCatalogue(chargement, dateMetier, 2);

		batch.verifier();

		assertThat(baseJournaux).isEmpty();
		verify(journaux, never()).verrouiller();
		assertThatThrownBy(() -> chargement.lancer(Declenchement.MANUEL)).isInstanceOf(JourNonOuvre.class);
		assertThat(ChargementCatalogue.estOuvre(LocalDate.of(2026, 11, 29))).isFalse();
	}

	@Test
	@DisplayName("Critère 9 : après le batch, l'exécution des ordres lit les VL chargées et sait que le catalogue "
			+ "est à jour pour la date métier")
	void critere9ValeursPourLesOrdres() {
		deposer(FichiersCatalogue.principal());
		chargement.lancer(Declenchement.AUTOMATIQUE);

		assertThat(catalogue.catalogueMisAJour(LocalDate.of(2026, 11, 23))).isTrue();
		assertThat(catalogue.valeurLiquidative("QZ0000000017", LocalDate.of(2026, 11, 20)))
				.hasValueSatisfying(vl -> assertThat(vl).isEqualByComparingTo("9082.68"));
		assertThat(catalogue.valeurLiquidative("QZ0000000017", LocalDate.of(2026, 11, 19))).isEmpty();
	}

	@Test
	@DisplayName("Le batch automatique s'exécute une seule fois par jour ouvré, à partir de l'heure prévue")
	void batchAutomatiqueUneFoisParJour() {
		deposer(fichier(ligne()));
		BatchCatalogue batch = new BatchCatalogue(chargement, dateMetier, 2);

		dateMetier(LUNDI_2H.minusMinutes(1));
		batch.verifier();
		assertThat(baseJournaux).isEmpty();

		dateMetier(LUNDI_2H);
		batch.verifier();
		batch.verifier();
		assertThat(baseJournaux).singleElement()
				.satisfies(j -> assertThat(j.getDeclenchement()).isEqualTo(Declenchement.AUTOMATIQUE));
		assertThat(chargement.lancer(Declenchement.AUTOMATIQUE)).isNull();
	}

	@Test
	@DisplayName("Une erreur inattendue du batch automatique est journalisée sans arrêter les vérifications")
	void erreurInattendue() {
		when(journaux.existsByDateMetierAndDeclenchement(any(), any())).thenThrow(new IllegalStateException());
		BatchCatalogue batch = new BatchCatalogue(chargement, dateMetier, 2);

		batch.verifier();

		assertThat(baseJournaux).isEmpty();
	}

	@Test
	@DisplayName("Le dépôt n'accepte qu'un fichier non vide nommé catalogue_uc_AAAA-MM-JJ.csv")
	void depotInvalide() {
		assertThatThrownBy(() -> chargement.deposer("catalogue.csv", "x")).isInstanceOf(DepotInvalide.class);
		assertThatThrownBy(() -> chargement.deposer(null, "x")).isInstanceOf(DepotInvalide.class);
		assertThatThrownBy(() -> chargement.deposer("catalogue_uc_2026-02-30.csv", "x"))
				.isInstanceOf(DepotInvalide.class).hasMessage("La date du nom de fichier n'existe pas.");
		assertThatThrownBy(() -> chargement.deposer("catalogue_uc_2026-11-23.csv", " "))
				.isInstanceOf(DepotInvalide.class).hasMessage("Le fichier est vide.");
		assertThatThrownBy(() -> chargement.deposer("catalogue_uc_2026-11-23.csv",
				"x".repeat(ChargementCatalogue.TAILLE_MAX + 1))).isInstanceOf(DepotInvalide.class);
		assertThat(baseFichiers).isEmpty();
	}

	@Test
	@DisplayName("Page de test : dépôt, lancement, journal, et refus clairs (400 dépôt invalide, 409 week-end)")
	void routesDeTest() {
		CatalogueTestController routes = new CatalogueTestController(chargement, catalogue);

		assertThat(routes.deposer(new Depot(" catalogue_uc_2026-11-23.csv ", FichiersCatalogue.avecAnomalies())))
				.satisfies(f -> assertThat(f.dateFichier()).isEqualTo(LocalDate.of(2026, 11, 23)));
		JournalVue vue = routes.lancer();

		assertThat(vue.statut()).isEqualTo("TERMINE");
		assertThat(vue.declenchement()).isEqualTo("MANUEL");
		assertThat(vue.ajoutes()).isEqualTo(101);
		assertThat(vue.lignesRejetees()).hasSize(7);
		assertThat(routes.journal()).singleElement().extracting(JournalVue::rejetees).isEqualTo(7);
		assertThat(routes.fichiers()).singleElement().extracting("nomFichier")
				.isEqualTo("catalogue_uc_2026-11-23.csv");
		assertThat(routes.depotInvalide(new DepotInvalide("Le fichier est vide.")).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(routes.jourNonOuvre(new JourNonOuvre()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(routes.jourNonOuvre(new JourNonOuvre()).getBody().message())
				.isEqualTo("La date métier n'est pas un jour ouvré : le batch ne s'exécute pas.");
	}

	@Test
	@DisplayName("Page de test : historique des valeurs liquidatives (404 si le support est inconnu) et action "
			+ "« Vider le catalogue »")
	void routesHistoriqueEtVider() {
		CatalogueTestController routes = new CatalogueTestController(chargement, catalogue);
		routes.deposer(new Depot("catalogue_uc_2026-11-23.csv", FichiersCatalogue.principal()));
		routes.lancer();

		assertThat(routes.historique("QZ0000001023").getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(routes.historique("QZ0000001023").getBody()).singleElement().satisfies(v -> {
			assertThat(v.dateVl()).isEqualTo(LocalDate.of(2026, 11, 1));
			assertThat(v.valeur()).isEqualByComparingTo("171.77");
		});
		assertThat(routes.historique("INCONNU").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		routes.vider();

		assertThat(baseSupports).isEmpty();
		assertThat(routes.historique("QZ0000001023").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("Un support retiré qui revient actif compte comme modifié, pas comme retiré")
	void supportRemisActif() {
		deposer(fichier(ligne("statut=retire")));
		chargement.lancer(Declenchement.AUTOMATIQUE);

		deposer(fichier(ligne()));
		JournalBatch journal = chargement.lancer(Declenchement.MANUEL);

		assertThat(journal.getModifies()).isEqualTo(1);
		assertThat(journal.getRetires()).isZero();
		assertThat(catalogue.supportsProposes(Produit.PER)).hasSize(1);
		assertThat(catalogue.support("INCONNU")).isEqualTo(Optional.empty());
	}

}
