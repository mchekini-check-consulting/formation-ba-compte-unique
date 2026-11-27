package com.example.compteunique.profil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.compteunique.compte.ClienteNonConnecteeException;
import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.profil.ProfilClientController.EtatVue;
import com.example.compteunique.profil.ProfilClientController.ProfilVue;
import com.example.compteunique.profil.ProfilClientController.Questionnaire;
import com.example.compteunique.profil.ProfilClientController.Refus;
import com.example.compteunique.profil.ProfilClientService.ProfilIncomplet;
import com.example.compteunique.profil.ProfilClientService.Statut;

// CU-10 (US-05) : critères d'acceptation du profil client. Non couverts ici (décision BA du 24/11/2026, périmètre
// « profil seul ») : contrôle gel des avoirs/PPE, validation par un responsable et opérations sur un contrat existant,
// qui viendront avec les stories de souscription (PER-01, AV-01...).
class ProfilClientTest {

	private static final ZonedDateTime AUJOURDHUI = ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();

	private final CompteService comptes = mock(CompteService.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final ProfilKycRepository profils = mock(ProfilKycRepository.class);
	private final ConsentementRepository consentements = mock(ConsentementRepository.class);
	private final PublicationStatutProfil publication = mock(PublicationStatutProfil.class);
	private final DossierRepository dossiers = mock(DossierRepository.class);
	private final ProfilClientService service = new ProfilClientService(comptes, dateMetier, profils, consentements,
			publication, dossiers);
	private final ProfilClientController controller = new ProfilClientController(service);
	private final List<ProfilKyc> base = new ArrayList<>();
	private ZonedDateTime maintenant = AUJOURDHUI;

	@BeforeEach
	void baseEnMemoire() {
		Compte compte = mock(Compte.class);
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(comptes.compteCourant("jeton")).thenReturn(compte);
		when(dateMetier.maintenant()).thenAnswer(appel -> maintenant);
		when(profils.save(any())).thenAnswer(appel -> {
			base.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(profils.findByCustomerIdOrderByVersionDesc(CLIENTE)).thenAnswer(
				appel -> base.stream().sorted(Comparator.comparingInt(ProfilKyc::getVersion).reversed()).toList());
		when(profils.findFirstByCustomerIdOrderByVersionDesc(CLIENTE))
				.thenAnswer(appel -> base.stream().max(Comparator.comparingInt(ProfilKyc::getVersion)));
	}

	private static Reponses completes(Long plafond, String statutMatrimonial, String reactionBaisse) {
		return new Reponses(statutMatrimonial, "SALARIE", "DE_10K_A_100K", "LIVRETS", "RETRAITE", "PLUS_8_ANS",
				reactionBaisse, "JUSQU_A_10", "AUCUNE", plafond, 2026, 30, 2045);
	}

	private ResponseEntity<ProfilVue> valider(Reponses reponses, Boolean consentement) {
		return controller.valider("jeton", new Questionnaire(reponses, consentement));
	}

	private Refus refus(Reponses reponses, Boolean consentement) {
		try {
			valider(reponses, consentement);
		} catch (ProfilIncomplet incomplet) {
			return controller.incomplet(incomplet).getBody();
		}
		throw new AssertionError("le questionnaire aurait dû être refusé");
	}

	@Test
	void sansProfilLeStatutEstACompleterEtLaSouscriptionRefusee() {
		EtatVue etat = controller.etat("jeton");

		assertThat(etat.statut()).isEqualTo(Statut.A_COMPLETER);
		assertThat(etat.profil()).isNull();
		assertThat(service.souscriptionAutorisee(CLIENTE)).isFalse();
	}

	@Test
	void unPlafondDe52000EstAccepteEtLeProfilValideAutoriseLaSouscription() {
		ResponseEntity<ProfilVue> reponse = valider(completes(52_000L, "PACSE", "ATTENDRE"), true);

		assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		ProfilVue profil = reponse.getBody();
		assertThat(profil.version()).isEqualTo(1);
		assertThat(profil.reponses().plafondRetraiteEuros()).isEqualTo(52_000L);
		assertThat(profil.revaliderLe()).isEqualTo(AUJOURDHUI.plusMonths(12).toOffsetDateTime());
		assertThat(controller.etat("jeton").statut()).isEqualTo(Statut.VALIDE);
		assertThat(service.souscriptionAutorisee(CLIENTE)).isTrue();
		verify(consentements).save(any(Consentement.class));
		// CU-382 : « à compléter » devient « validé », publié dans Kafka par la boîte d'envoi
		verify(publication).changer(CLIENTE, Statut.A_COMPLETER, Statut.VALIDE);
	}

	@Test
	void leProfilValideEstLuParLeModuleGestionSeulementTantQu_ilEstValide() {
		assertThat(service.profilValide(CLIENTE)).isEmpty();
		valider(completes(0L, "MARIE", "ATTENDRE"), true);

		assertThat(service.profilValide(CLIENTE)).get().extracting(ProfilKyc::getProfilInvestisseur)
				.isEqualTo("EQUILIBRE");
		maintenant = AUJOURDHUI.plusMonths(13);
		assertThat(service.profilValide(CLIENTE)).isEmpty();
	}

	@Test
	void leProfilInvestisseurSuitLaReactionAUneBaisse() {
		assertThat(valider(completes(0L, "MARIE", "RETIRER"), true).getBody())
				.extracting(ProfilVue::profilInvestisseur, ProfilVue::scoreInvestisseur).containsExactly("PRUDENT", 1);
		assertThat(valider(completes(0L, "MARIE", "ATTENDRE"), true).getBody())
				.extracting(ProfilVue::profilInvestisseur, ProfilVue::scoreInvestisseur).containsExactly("EQUILIBRE", 2);
		assertThat(valider(completes(0L, "MARIE", "INVESTIR"), true).getBody())
				.extracting(ProfilVue::profilInvestisseur, ProfilVue::scoreInvestisseur).containsExactly("DYNAMIQUE", 3);
	}

	@Test
	void unPlafondNegatifOuAbsentEstRefuseAvecLeMessageDuTicket() {
		assertThat(refus(completes(-1L, "MARIE", "ATTENDRE"), true).champs())
				.containsEntry("plafondRetraiteEuros", "Saisissez le montant indiqué sur votre avis d'imposition");
		assertThat(refus(completes(null, "MARIE", "ATTENDRE"), true).champs())
				.containsOnlyKeys("plafondRetraiteEuros");
		assertThat(base).isEmpty();
	}

	@Test
	void unStatutMatrimonialNonRenseigneEstSignaleEtLeProfilResteACompleter() {
		Refus refus = refus(completes(1_000L, null, "ATTENDRE"), true);

		assertThat(refus.message()).isEqualTo(ProfilClientController.MESSAGE_INCOMPLET);
		assertThat(refus.champs()).containsOnlyKeys("statutMatrimonial");
		assertThat(controller.etat("jeton").statut()).isEqualTo(Statut.A_COMPLETER);
		verify(consentements, never()).save(any());
	}

	@Test
	void sansReponseToutesLesQuestionsSontObligatoiresMemePourUneAssuranceVie() {
		Refus refus = refus(null, null);

		assertThat(refus.champs()).containsKeys("plafondRetraiteEuros", "plafondRetraiteAnnee", "tmi",
				"anneeDepartRetraite", "statutMatrimonial", "consentement").hasSize(14);
		assertThatThrownBy(() -> controller.valider("jeton", null)).isInstanceOf(ProfilIncomplet.class);
	}

	@Test
	void desValeursHorsListeSontRefusees() {
		Reponses r = new Reponses("MARIE", "SALARIE", "PLUS_100K", "AUCUN", "AUTRE", "MOINS_3_ANS", "INVESTIR",
				"AUCUNE", "AUCUNE", 10L, 2020, 12, 2200);

		assertThat(refus(r, false).champs()).containsOnlyKeys("plafondRetraiteAnnee", "tmi", "anneeDepartRetraite",
				"consentement");
		assertThat(refus(r, false).champs().get("anneeDepartRetraite")).isEqualTo("Saisissez une année entre 2026 et 2076.");
	}

	@Test
	void uneModificationCreeUneNouvelleVersionEtLAncienneResteConsultable() {
		valider(completes(10_000L, "CELIBATAIRE", "ATTENDRE"), true);
		maintenant = AUJOURDHUI.plusDays(3);
		valider(completes(10_000L, "MARIE", "ATTENDRE"), true);

		List<ProfilVue> versions = controller.versions("jeton");

		assertThat(versions).extracting(ProfilVue::version).containsExactly(2, 1);
		assertThat(versions.get(1).reponses().statutMatrimonial()).isEqualTo("CELIBATAIRE");
		assertThat(versions.get(1).valideLe()).isEqualTo(AUJOURDHUI.toOffsetDateTime());
		assertThat(controller.etat("jeton").profil().reponses().statutMatrimonial()).isEqualTo("MARIE");
	}

	@Test
	void unProfilDePlusDe12MoisEstARevaliderAvecSesReponsesPourPreRemplir() {
		valider(completes(5_000L, "VEUF", "RETIRER"), true);
		maintenant = AUJOURDHUI.plusMonths(12);

		EtatVue etat = controller.etat("jeton");

		assertThat(etat.statut()).isEqualTo(Statut.A_REVALIDER);
		assertThat(etat.profil().reponses().statutMatrimonial()).isEqualTo("VEUF");
		assertThat(service.souscriptionAutorisee(CLIENTE)).isFalse();

		maintenant = AUJOURDHUI.plusMonths(12).minusSeconds(1);
		assertThat(service.statut(CLIENTE)).isEqualTo(Statut.VALIDE);
	}

	@Test
	void sansSessionOuSansCompteLaDemandeEstRefusee() {
		when(comptes.compteCourant("expire")).thenThrow(new ClienteNonConnecteeException());

		assertThatThrownBy(() -> controller.etat("expire")).isInstanceOf(ClienteNonConnecteeException.class);
		assertThat(controller.nonConnectee().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(controller.sansCompte().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void d8PremierPrenomDuDossierEnLectureSeuleSinonRien() {
		Compte compte = comptes.compteCourant("jeton");
		when(compte.getDossierId()).thenReturn(3L);
		Dossier dossier = mock(Dossier.class);
		when(dossiers.findById(3L)).thenReturn(java.util.Optional.of(dossier));

		when(dossier.getPrenoms()).thenReturn(" Marie Claire ");
		assertThat(controller.etat("jeton").prenom()).isEqualTo("Marie");
		when(dossier.getPrenoms()).thenReturn("Jean-Pierre, Louis");
		assertThat(service.prenom("jeton")).contains("Jean-Pierre");
		when(dossier.getPrenoms()).thenReturn("  ");
		assertThat(service.prenom("jeton")).isEmpty();
		when(compte.getDossierId()).thenReturn(null);
		assertThat(controller.etat("jeton").prenom()).isNull();
		verify(dossiers, never()).save(any());
	}

}
