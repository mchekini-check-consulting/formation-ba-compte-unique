package com.example.compteunique.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.compteunique.catalogue.Adequation;
import com.example.compteunique.catalogue.Adequation.SupportInadapte;
import com.example.compteunique.catalogue.CatalogueService;
import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.CatalogueService.SupportUc;
import com.example.compteunique.catalogue.SupportsEligibles;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.gestion.ModeDeGestion.ChoixRefuse;
import com.example.compteunique.gestion.ModeDeGestion.ContratIntrouvable;
import com.example.compteunique.gestion.ModeDeGestion.ContratsDeTestIndisponibles;
import com.example.compteunique.gestion.ModeDeGestion.DemandeInvalide;
import com.example.compteunique.gestion.ModeDeGestion.Etat;
import com.example.compteunique.gestion.ModeDeGestion.ProfilRequis;
import com.example.compteunique.gestion.ModeDeGestion.Versement;
import com.example.compteunique.gestion.ModeDeGestion.VersementImpossible;
import com.example.compteunique.ordres.OrdresService;
import com.example.compteunique.ordres.OrdresService.DemandeOrdre;
import com.example.compteunique.ordres.PositionsContrat;
import com.example.compteunique.ordres.TypeOrdre;
import com.example.compteunique.profil.ProfilClientService;
import com.example.compteunique.profil.ProfilKyc;
import com.example.compteunique.profil.Reponses;

// CU-314 (PER-07) et CU-338 (AV-07) : critères d'acceptation du choix du mode de gestion.
class ModeDeGestionTest {

	private static final ZonedDateTime LE = ZonedDateTime.of(2026, 11, 26, 15, 30, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();
	private static final String JETON = "jeton";

	private final CompteService comptes = mock(CompteService.class);
	private final ProfilClientService profils = mock(ProfilClientService.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final SupportsEligibles supports = mock(SupportsEligibles.class);
	private final CatalogueService catalogue = mock(CatalogueService.class);
	private final PositionsContrat positions = mock(PositionsContrat.class);
	private final OrdresService ordres = mock(OrdresService.class);
	private final ContratGestionRepository contrats = mock(ContratGestionRepository.class);
	private final ChangementGestionRepository changements = mock(ChangementGestionRepository.class);
	private final ParametreMandatRepository mandats = mock(ParametreMandatRepository.class);
	private final GrilleHorizonPerRepository grille = mock(GrilleHorizonPerRepository.class);
	private final QuotaNonCoteAvRepository quotas = mock(QuotaNonCoteAvRepository.class);
	private final ReequilibrageRepository reequilibrages = mock(ReequilibrageRepository.class);
	private final SignalementNonCoteRepository signalements = mock(SignalementNonCoteRepository.class);
	private final Adequation adequation = mock(Adequation.class);

	private final List<ContratGestion> baseContrats = new ArrayList<>();
	private final List<ChangementGestion> baseChangements = new ArrayList<>();
	private final List<Reequilibrage> baseReequilibrages = new ArrayList<>();
	private final Map<String, BigDecimal> encours = new TreeMap<>();
	private final List<GrilleHorizonPer> lignesGrille = new ArrayList<>();

	private ModeDeGestion service(boolean contratsDeTest) {
		return new ModeDeGestion(comptes, profils, dateMetier, supports, catalogue, positions, ordres, contrats, changements,
				mandats, grille, quotas, reequilibrages, signalements, adequation, contratsDeTest);
	}

	private final ModeDeGestion service = service(true);

	@BeforeEach
	void baseEnMemoire() {
		Compte compte = mock(Compte.class);
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(comptes.compteCourant(JETON)).thenReturn(compte);
		when(dateMetier.maintenant()).thenReturn(LE);
		when(dateMetier.aujourdhui()).thenReturn(LE.toLocalDate());
		profil("EQUILIBRE", 2041);
		when(contrats.saveAndFlush(any())).thenAnswer(appel -> {
			baseContrats.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(contrats.findByCustomerIdAndProduit(any(), any())).thenAnswer(appel -> contrat(appel.getArgument(1)));
		when(contrats.findWithLockByCustomerIdAndProduit(any(), any()))
				.thenAnswer(appel -> contrat(appel.getArgument(1)));
		when(changements.save(any())).thenAnswer(appel -> {
			baseChangements.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(changements.findByNumeroContratOrderByIdDesc(anyString()))
				.thenAnswer(appel -> baseChangements.reversed());
		when(reequilibrages.save(any())).thenAnswer(appel -> {
			baseReequilibrages.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(reequilibrages.findByNumeroContratOrderByIdDesc(anyString())).thenAnswer(appel -> baseReequilibrages);
		OffsetDateTime maintenant = LE.toOffsetDateTime();
		for (Produit produit : Produit.values()) {
			when(mandats.findById(produit)).thenReturn(Optional.of(
					new ParametreMandat(produit, new BigDecimal("1.50"), new BigDecimal("50000.00"), maintenant)));
		}
		when(quotas.findById(ProfilGestion.PRUDENT))
				.thenReturn(Optional.of(new QuotaNonCoteAv(ProfilGestion.PRUDENT, BigDecimal.ZERO, maintenant)));
		when(quotas.findById(ProfilGestion.EQUILIBRE))
				.thenReturn(Optional.of(new QuotaNonCoteAv(ProfilGestion.EQUILIBRE, new BigDecimal("4"), maintenant)));
		when(quotas.findById(ProfilGestion.DYNAMIQUE))
				.thenReturn(Optional.of(new QuotaNonCoteAv(ProfilGestion.DYNAMIQUE, new BigDecimal("8"), maintenant)));
		lignesGrille.add(new GrilleHorizonPer(ProfilGestion.DYNAMIQUE, 10, new BigDecimal("10"), new BigDecimal("8"),
				maintenant));
		lignesGrille.add(new GrilleHorizonPer(ProfilGestion.DYNAMIQUE, 5, new BigDecimal("25"), new BigDecimal("6"),
				maintenant));
		lignesGrille.add(new GrilleHorizonPer(ProfilGestion.EQUILIBRE, 10, new BigDecimal("20"), new BigDecimal("5"),
				maintenant));
		when(grille.findByProfilOrderByAnneesMinDesc(any())).thenAnswer(appel -> lignesGrille.stream()
				.filter(l -> l.getProfil() == appel.getArgument(0))
				.sorted((a, b) -> Integer.compare(b.getAnneesMin(), a.getAnneesMin())).toList());
		when(supports.lister(any(), anyString())).thenReturn(AllocationTest.CATALOGUE);
		when(positions.encoursParSupport(anyString())).thenAnswer(appel -> new TreeMap<>(encours));
		when(ordres.montantDisponible(anyString(), anyString()))
				.thenAnswer(appel -> encours.getOrDefault(appel.getArgument(1), BigDecimal.ZERO));
	}

	private void profil(String investisseur, int anneeRetraite) {
		ProfilKyc kyc = mock(ProfilKyc.class);
		Reponses reponses = mock(Reponses.class);
		when(reponses.anneeDepartRetraite()).thenReturn(anneeRetraite);
		when(kyc.getReponses()).thenReturn(reponses);
		when(kyc.getProfilInvestisseur()).thenReturn(investisseur);
		when(profils.profilValide(CLIENTE)).thenReturn(Optional.of(kyc));
	}

	private Optional<ContratGestion> contrat(Produit produit) {
		return baseContrats.stream().filter(c -> c.getProduit() == produit).findFirst();
	}

	private Etat creer(Produit produit) {
		return service.creerContratDeTest(JETON, produit);
	}

	@Test
	@DisplayName("Une cliente qui n'a pas choisi de mode de gestion : à la création du contrat, la gestion pilotée au "
			+ "profil équilibré est enregistrée (PER et assurance vie)")
	void parDefautPiloteeEquilibree() {
		Etat per = creer(Produit.PER);
		Etat av = creer(Produit.AV);

		for (Etat etat : List.of(per, av)) {
			assertThat(etat.contrat().mode()).isEqualTo(ModeGestion.PILOTEE);
			assertThat(etat.contrat().profil()).isEqualTo(ProfilGestion.EQUILIBRE);
		}
		assertThat(per.contrat().numeroContrat()).startsWith("PER-TEST-").hasSize(17);
		assertThat(baseChangements).hasSize(2);
		assertThat(baseChangements.get(0).getHorizonAnnees()).isEqualTo(15);
		assertThat(baseChangements.get(1).getHorizonAnnees()).isNull();
		// Un second clic renvoie le contrat existant.
		assertThat(creer(Produit.PER).contrat().numeroContrat()).isEqualTo(per.contrat().numeroContrat());
		assertThat(baseContrats).hasSize(2);
	}

	@Test
	@DisplayName("Décision PO du 26/11/2026 : profil par défaut = le moins risqué entre équilibré et le profil "
			+ "investisseur (prudente → prudent, dynamique → équilibré), jamais au-dessus")
	void profilParDefautPlafonneParLeProfilInvestisseur() {
		profil("PRUDENT", 2041);
		assertThat(creer(Produit.PER).contrat().profil()).isEqualTo(ProfilGestion.PRUDENT);
		profil("DYNAMIQUE", 2041);
		assertThat(creer(Produit.AV).contrat().profil()).isEqualTo(ProfilGestion.EQUILIBRE);
		assertThat(baseChangements).extracting(ChangementGestion::getProfil).containsExactly(ProfilGestion.PRUDENT,
				ProfilGestion.EQUILIBRE);
		// PER-04 / AV-01 sans profil investisseur : création refusée.
		assertThatThrownBy(() -> service.initialiser("PER-X", CLIENTE, Produit.PER, 2041, null))
				.isInstanceOf(ProfilRequis.class);
	}

	@Test
	@DisplayName("Contrat de test : refusé hors dev et qualif, et sans profil client validé")
	void contratDeTestConditions() {
		assertThatThrownBy(() -> service(false).creerContratDeTest(JETON, Produit.PER))
				.isInstanceOf(ContratsDeTestIndisponibles.class);
		when(profils.profilValide(CLIENTE)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> creer(Produit.PER)).isInstanceOf(ProfilRequis.class);
		assertThat(service(false).etat(JETON, Produit.AV).contratsDeTest()).isFalse();
	}

	@Test
	@DisplayName("Une cliente en gestion pilotée qui choisit la gestion libre : le changement est enregistré sans frais")
	void piloteeVersLibreSansFrais() {
		creer(Produit.AV);

		Etat etat = service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);

		assertThat(etat.contrat().mode()).isEqualTo(ModeGestion.LIBRE);
		assertThat(etat.contrat().historique()).hasSize(2);
		assertThat(etat.contrat().historique().get(0).mode()).isEqualTo(ModeGestion.LIBRE);
		assertThat(etat.contrat().historique().get(0).frais()).isEqualByComparingTo("0");
		assertThat(etat.contrat().historique().get(0).changeLe().toInstant()).isEqualTo(LE.toInstant());
	}

	@Test
	@DisplayName("Une cliente en gestion libre qui passe en gestion pilotée (AV) : ordres d'arbitrage gratuits passés au "
			+ "module Ordres, positions jamais modifiées ; le quota de non coté ne s'applique pas aux positions existantes")
	void libreVersPiloteeReequilibre() {
		creer(Produit.AV);
		service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);
		encours.put("ACTIONS", new BigDecimal("1000.00"));
		encours.put("NONCOTE", new BigDecimal("200.00"));
		when(catalogue.support("NONCOTE")).thenReturn(Optional.of(supportUc("NONCOTE", false)));

		Etat etat = service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, null);

		ArgumentCaptor<DemandeOrdre> demandes = ArgumentCaptor.forClass(DemandeOrdre.class);
		verify(ordres).passer(demandes.capture());
		// Part cotée (1 000 €) rééquilibrée sur les supports cotés de niveau 4 et plus, sans quota : 500 € chacun.
		// Les 200 € de non coté ne bougent pas, et aucun support non coté n'est acheté.
		assertThat(demandes.getAllValues()).singleElement().satisfies(d -> {
			assertThat(d.type()).isEqualTo(TypeOrdre.ARBITRAGE);
			assertThat(d.supportDepart()).isEqualTo("ACTIONS");
			assertThat(d.supportArrivee()).isEqualTo("MONDE");
			assertThat(d.montant()).isEqualByComparingTo("500");
		});
		assertThat(etat.contrat().reequilibrages()).singleElement()
				.satisfies(r -> assertThat(r.montantArbitre()).isEqualByComparingTo("500"));
		assertThat(etat.contrat().historique().get(0).frais()).isEqualByComparingTo("0");
		verify(positions, never()).enregistrerPositionTest(any(), any(), any(), any());
		verify(positions, never()).viderPositionsDeTest();
	}

	@Test
	@DisplayName("Passage en gestion pilotée (PER) : rééquilibrage par ordres d'arbitrage sur l'allocation de la grille")
	void libreVersPiloteeReequilibrePer() {
		creer(Produit.PER);
		service.choisir(JETON, Produit.PER, ModeGestion.LIBRE, null);
		encours.put("ACTIONS", new BigDecimal("1000.00"));

		service.choisir(JETON, Produit.PER, ModeGestion.PILOTEE, null);

		ArgumentCaptor<DemandeOrdre> demandes = ArgumentCaptor.forClass(DemandeOrdre.class);
		verify(ordres, times(4)).passer(demandes.capture());
		// Équilibré à 15 ans : 5 % de non coté (50 €), 20 % de faible risque (100 € chacun), le reste sur MONDE.
		assertThat(demandes.getAllValues()).extracting(DemandeOrdre::supportArrivee, DemandeOrdre::montant)
				.containsExactly(tuple("MONDE", new BigDecimal("375.00")), tuple("MONETAIRE", new BigDecimal("100.00")),
						tuple("NONCOTE", new BigDecimal("50.00")), tuple("OBLIG", new BigDecimal("100.00")));
		verify(positions, never()).enregistrerPositionTest(any(), any(), any(), any());
	}

	private static SupportUc supportUc(String isin, boolean cote) {
		return supportUc(isin, cote, 6);
	}

	private static SupportUc supportUc(String isin, boolean cote, int niveauRisque) {
		return new SupportUc(isin, isin, "OPCVM", "Actions", "AV", niveauRisque, cote, BigDecimal.ONE, false,
				BigDecimal.TEN, LE.toLocalDate(), "EUR", "quotidienne", "aucun", "Gestion");
	}

	/** Cliente dynamique en gestion libre : 2 000 € de SCPI (niveau 3) et 1 500 € de capital-investissement (niveau 6). */
	private void clienteDynamiqueAvecNonCotes() {
		profil("DYNAMIQUE", 2041);
		creer(Produit.AV);
		service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);
		encours.put("SCPI", new BigDecimal("2000.00"));
		encours.put("FCPR", new BigDecimal("1500.00"));
		when(catalogue.support("SCPI")).thenReturn(Optional.of(supportUc("SCPI", false, 3)));
		when(catalogue.support("FCPR")).thenReturn(Optional.of(supportUc("FCPR", false, 6)));
		when(supports.niveauRisqueMax("PRUDENT")).thenReturn(3);
	}

	@Test
	@DisplayName("AV-07, scénario 1 : cliente dynamique avec 2 000 € de SCPI (niveau 3) et 1 500 € de "
			+ "capital-investissement (niveau 6), passage en pilotée prudente : avant validation, 3 500 € de non coté "
			+ "restent dans le contrat hors gestion pilotée")
	void nonCotesAnnoncesAvantValidation() {
		clienteDynamiqueAvecNonCotes();

		Etat etat = service.etat(JETON, Produit.AV);

		assertThat(etat.contrat().encoursNonCote()).isEqualByComparingTo("3500");
		verify(signalements, never()).save(any());
		// Le PER n'a pas cette règle.
		creer(Produit.PER);
		assertThat(service.etat(JETON, Produit.PER).contrat().encoursNonCote()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("AV-07, scénario 2 : même cas, passage validé : supports non cotés conservés (aucun arbitrage), seul le "
			+ "fonds de niveau 6 est signalé au back-office, « à traiter »")
	void seulLeNonCoteHorsProfilEstSignale() {
		clienteDynamiqueAvecNonCotes();
		ArgumentCaptor<SignalementNonCote> signale = ArgumentCaptor.forClass(SignalementNonCote.class);

		service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.PRUDENT);

		verify(ordres, never()).passer(any());
		verify(signalements).save(signale.capture());
		assertThat(signale.getValue()).satisfies(s -> {
			assertThat(s.getNumeroContrat()).startsWith("AV-TEST-");
			assertThat(s.getCodeIsin()).isEqualTo("FCPR");
			assertThat(s.getMontant()).isEqualByComparingTo("1500");
			assertThat(s.getNiveauRisque()).isEqualTo(6);
			assertThat(s.getProfil()).isEqualTo(ProfilGestion.PRUDENT);
			assertThat(s.getSignaleLe()).isEqualTo(LE.toOffsetDateTime());
			assertThat(s.getStatut()).isEqualTo("A_TRAITER");
		});
	}

	@Test
	@DisplayName("AV-07 : changement de profil en restant en gestion pilotée, aucun nouveau signalement")
	void pasDeSignalementSansPassageEnPilotee() {
		clienteDynamiqueAvecNonCotes();
		when(supports.niveauRisqueMax("EQUILIBRE")).thenReturn(4);
		service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.EQUILIBRE);
		// Passage en pilotée équilibrée (maximum 4) : seul le fonds de niveau 6 est signalé.
		verify(signalements, times(1)).save(any());

		service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.PRUDENT);

		verify(signalements, times(1)).save(any());
		when(signalements.findTop50ByOrderByIdDesc()).thenReturn(List.of());
		assertThat(service.signalements()).isEmpty();
	}

	@Test
	@DisplayName("Une cliente au profil investisseur équilibré qui choisit le profil de gestion dynamique : refusé avec "
			+ "« Votre profil de gestion ne peut pas dépasser votre profil investisseur »")
	void profilDeGestionPlafonne() {
		creer(Produit.AV);

		assertThatThrownBy(() -> service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.DYNAMIQUE))
				.isInstanceOf(ChoixRefuse.class)
				.hasMessage("Votre profil de gestion ne peut pas dépasser votre profil investisseur");
		assertThat(service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.PRUDENT).contrat().profil())
				.isEqualTo(ProfilGestion.PRUDENT);
		// Décision PO du 26/11/2026 : même règle et même message pour le PER.
		creer(Produit.PER);
		assertThatThrownBy(() -> service.choisir(JETON, Produit.PER, ModeGestion.MANDAT, ProfilGestion.DYNAMIQUE))
				.isInstanceOf(ChoixRefuse.class)
				.hasMessage("Votre profil de gestion ne peut pas dépasser votre profil investisseur");
	}

	@Test
	@DisplayName("Changement de profil en assurance vie sans profil client validé : il faut d'abord le revalider")
	void profilRequisPourChangerDeProfil() {
		creer(Produit.AV);
		when(profils.profilValide(CLIENTE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.PRUDENT))
				.isInstanceOf(ProfilRequis.class);
	}

	@Test
	@DisplayName("Gestion sous mandat : taux tout compris (1,5 % par an) et minimum (50 000 €) affichés avant "
			+ "validation ; encours insuffisant : refusé avec le montant minimum")
	void mandatMinimum() {
		Etat etat = creer(Produit.PER);
		assertThat(etat.mandat().tauxToutComprisPct()).isEqualByComparingTo("1.5");
		assertThat(etat.mandat().montantMinimum()).isEqualByComparingTo("50000");
		encours.put("ACTIONS", new BigDecimal("49999.99"));

		assertThatThrownBy(() -> service.choisir(JETON, Produit.PER, ModeGestion.MANDAT, null))
				.isInstanceOf(ChoixRefuse.class)
				.hasMessage("La gestion sous mandat demande un encours d'au moins 50 000,00 € (encours de votre "
						+ "contrat : 49 999,99 €).");

		encours.put("ACTIONS", new BigDecimal("50000.00"));
		assertThat(service.choisir(JETON, Produit.PER, ModeGestion.MANDAT, null).contrat().mode())
				.isEqualTo(ModeGestion.MANDAT);
	}

	@Test
	@DisplayName("Une cliente sous mandat qui demande à sortir du mandat : la sortie est enregistrée sans préavis")
	void sortieDuMandat() {
		creer(Produit.AV);
		encours.put("ACTIONS", new BigDecimal("60000.00"));
		service.choisir(JETON, Produit.AV, ModeGestion.MANDAT, null);

		Etat etat = service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);

		assertThat(etat.contrat().mode()).isEqualTo(ModeGestion.LIBRE);
		assertThat(etat.contrat().historique().get(0).changeLe().toInstant()).isEqualTo(LE.toInstant());
		// Un choix identique n'ajoute rien à l'historique.
		service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);
		assertThat(baseChangements).hasSize(3);
	}

	@Test
	@DisplayName("Une cliente au profil dynamique à 15 ans de la retraite : son allocation respecte les parts minimales "
			+ "de la grille paramétrée ; une modification de la grille s'applique sans changer le code")
	void grilleParHorizon() {
		profil("DYNAMIQUE", 2041);
		creer(Produit.PER);

		Etat etat = service.choisir(JETON, Produit.PER, ModeGestion.PILOTEE, ProfilGestion.DYNAMIQUE);

		assertThat(etat.contrat().contraintes().horizonAnnees()).isEqualTo(15);
		assertThat(etat.contrat().contraintes().partMinNonCotePct()).isEqualByComparingTo("8");
		assertThat(etat.contrat().contraintes().partMinFaibleRisquePct()).isEqualByComparingTo("10");
		assertThat(etat.contrat().contraintes().grilleAValider()).isTrue();
		assertThat(etat.contrat().allocation().stream().filter(Allocation.Ligne::nonCote)
				.map(Allocation.Ligne::partPct).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("8");

		lignesGrille.get(0).modifier(new BigDecimal("30"), new BigDecimal("12"), LE.toOffsetDateTime());

		Etat apres = service.etat(JETON, Produit.PER);
		assertThat(apres.contrat().contraintes().partMinNonCotePct()).isEqualByComparingTo("12");
		assertThat(apres.contrat().allocation().stream().filter(l -> !l.nonCote() && l.niveauRisque() <= 3)
				.map(Allocation.Ligne::partPct).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("30");
	}

	@Test
	@DisplayName("PER à 7 ans de la retraite : la tranche « 5 ans et plus » de la grille s'applique")
	void trancheDeLaGrille() {
		profil("DYNAMIQUE", 2033);
		creer(Produit.PER);

		Etat etat = service.choisir(JETON, Produit.PER, ModeGestion.PILOTEE, ProfilGestion.DYNAMIQUE);

		assertThat(etat.contrat().contraintes().horizonAnnees()).isEqualTo(7);
		assertThat(etat.contrat().contraintes().partMinFaibleRisquePct()).isEqualByComparingTo("25");
	}

	@Test
	@DisplayName("Une cliente au profil dynamique en gestion pilotée qui verse 1 000 € : au moins 80 € sont affectés à "
			+ "des supports non cotés")
	void versementPiloteDynamique() {
		profil("DYNAMIQUE", 2041);
		creer(Produit.AV);
		service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.DYNAMIQUE);
		UUID id = UUID.randomUUID();

		Versement versement = service.verser(JETON, Produit.AV, id, new BigDecimal("1000.00"));

		ArgumentCaptor<DemandeOrdre> demandes = ArgumentCaptor.forClass(DemandeOrdre.class);
		verify(ordres, times(versement.repartition().size())).passer(demandes.capture());
		BigDecimal nonCote = demandes.getAllValues().stream().filter(d -> d.supportArrivee().equals("NONCOTE"))
				.map(DemandeOrdre::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(nonCote).isGreaterThanOrEqualTo(new BigDecimal("80.00"));
		assertThat(demandes.getAllValues()).allSatisfy(d -> assertThat(d.type()).isEqualTo(TypeOrdre.VERSEMENT));
		// Rejouer le même versement redonne les mêmes identifiants d'ordre (le module Ordres ignore le doublon).
		service.verser(JETON, Produit.AV, id, new BigDecimal("1000.00"));
		verify(ordres, times(2)).passer(eq(demandes.getAllValues().get(0)));
	}

	@Test
	@DisplayName("Versement réparti : réservé à la gestion pilotée, montant positif, contrat existant")
	void versementRefuse() {
		assertThatThrownBy(() -> service.verser(JETON, Produit.AV, UUID.randomUUID(), BigDecimal.TEN))
				.isInstanceOf(ContratIntrouvable.class);
		creer(Produit.AV);
		assertThatThrownBy(() -> service.verser(JETON, Produit.AV, UUID.randomUUID(), new BigDecimal("-1")))
				.isInstanceOf(DemandeInvalide.class);
		assertThatThrownBy(() -> service.verser(JETON, Produit.AV, null, BigDecimal.TEN))
				.isInstanceOf(DemandeInvalide.class);
		service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);
		assertThatThrownBy(() -> service.verser(JETON, Produit.AV, UUID.randomUUID(), BigDecimal.TEN))
				.isInstanceOf(VersementImpossible.class);
		verify(ordres, never()).passer(any());
	}

	@Test
	@DisplayName("CU-349 critère 3 : profil devenu prudent après le choix, un versement vers une UC qui n'est plus "
			+ "adaptée est refusé avant tout ordre, contrôlé avec le profil en vigueur")
	void versementVersUcInadaptee() {
		profil("DYNAMIQUE", 2041);
		creer(Produit.AV);
		service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, ProfilGestion.DYNAMIQUE);
		profil("PRUDENT", 2041);
		doThrow(new SupportInadapte()).when(adequation).controler(eq(CLIENTE), eq(Produit.AV), eq("PRUDENT"),
				anyString(), eq("VERSEMENT"));

		assertThatThrownBy(() -> service.verser(JETON, Produit.AV, UUID.randomUUID(), new BigDecimal("1000.00")))
				.isInstanceOf(SupportInadapte.class).hasMessage("Ce support ne correspond pas à votre profil");
		verify(ordres, never()).passer(argThat(d -> d.type() == TypeOrdre.VERSEMENT));
	}

	@Test
	@DisplayName("Choix sans contrat : refusé (404) ; état sans contrat : le profil investisseur et le mandat sont "
			+ "affichés")
	void sansContrat() {
		assertThatThrownBy(() -> service.choisir(JETON, Produit.PER, ModeGestion.LIBRE, null))
				.isInstanceOf(ContratIntrouvable.class);
		Etat etat = service.etat(JETON, Produit.PER);
		assertThat(etat.contrat()).isNull();
		assertThat(etat.profilInvestisseur()).isEqualTo("EQUILIBRE");
		assertThat(etat.contratsDeTest()).isTrue();
	}

	@Test
	@DisplayName("Catalogue sans support non coté : l'allocation est signalée indisponible et le passage en pilotée "
			+ "refusé")
	void allocationIndisponible() {
		when(supports.lister(any(), anyString())).thenReturn(List.of(AllocationTest.support("ACTIONS", 5, false)));
		Etat etat = creer(Produit.AV);
		assertThat(etat.contrat().allocationIndisponible()).contains("non coté");
		service.choisir(JETON, Produit.AV, ModeGestion.LIBRE, null);

		assertThatThrownBy(() -> service.choisir(JETON, Produit.AV, ModeGestion.PILOTEE, null))
				.isInstanceOf(Allocation.AllocationImpossible.class);
	}

}
