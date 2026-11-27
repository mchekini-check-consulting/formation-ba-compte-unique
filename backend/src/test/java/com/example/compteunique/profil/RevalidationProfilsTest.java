package com.example.compteunique.profil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteService;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.profil.ProfilClientService.Statut;

// CU-415 : le passage de « validé » à « à revalider » est enregistré (statut et événement, même transaction) par la
// tâche quotidienne, ou à la lecture du profil si la tâche n'est pas encore passée, une seule fois.
class RevalidationProfilsTest {

	/** Profil validé le 25/11/2026 : à revalider à partir du 25/11/2027. */
	private static final ZonedDateTime VALIDE_LE = ZonedDateTime.of(2026, 11, 25, 15, 28, 0, 0, DateMetier.PARIS);
	private static final ZonedDateTime APRES_ECHEANCE = ZonedDateTime.of(2027, 11, 26, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();

	private final StatutPublieRepository publies = mock(StatutPublieRepository.class);
	private final ProfilKycRepository profils = mock(ProfilKycRepository.class);
	private final Evenements evenements = mock(Evenements.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final CompteService comptes = mock(CompteService.class);
	private final PublicationStatutProfil publication = new PublicationStatutProfil(publies, evenements, dateMetier);
	private final ProfilClientService service = new ProfilClientService(comptes, dateMetier, profils,
			mock(ConsentementRepository.class), publication, mock(DossierRepository.class));
	private final Map<UUID, StatutPublie> base = new HashMap<>();
	private ZonedDateTime maintenant = APRES_ECHEANCE;

	@BeforeEach
	void profilValideLe25Novembre2026() {
		ProfilKyc profil = mock(ProfilKyc.class);
		when(profil.getRevaliderLe()).thenReturn(VALIDE_LE.plusYears(1).toOffsetDateTime());
		when(profils.findFirstByCustomerIdOrderByVersionDesc(CLIENTE)).thenReturn(Optional.of(profil));
		Compte compte = mock(Compte.class);
		when(compte.getCustomerId()).thenReturn(CLIENTE);
		when(comptes.compteCourant("jeton")).thenReturn(compte);
		when(dateMetier.maintenant()).thenAnswer(appel -> maintenant);
		when(dateMetier.aujourdhui()).thenAnswer(appel -> maintenant.toLocalDate());
		base.put(CLIENTE, new StatutPublie(CLIENTE, Statut.VALIDE, VALIDE_LE.toOffsetDateTime()));
		when(publies.verrouiller(any())).thenAnswer(appel -> Optional.ofNullable(base.get(appel.getArgument(0))));
		when(publies.save(any())).thenAnswer(appel -> {
			StatutPublie statut = appel.getArgument(0);
			base.put(statut.getCustomerId(), statut);
			return statut;
		});
		// Même règle que la requête : dernière version échue, « à revalider » pas encore enregistré.
		when(publies.aRevaliderNonPublies(any())).thenAnswer(appel -> {
			OffsetDateTime date = appel.getArgument(0);
			boolean echu = !date.isBefore(VALIDE_LE.plusYears(1).toOffsetDateTime());
			return echu && base.get(CLIENTE).getStatut() != Statut.A_REVALIDER ? List.of(CLIENTE) : List.of();
		});
	}

	private void verifierUnSeulEvenement() {
		verify(evenements, times(1)).enregistrer(Topic.PROFIL_CLIENT, "StatutProfilModifie", CLIENTE.toString(),
				Map.of("statut", "A_REVALIDER", "ancienStatut", "VALIDE"));
		verify(evenements, times(1)).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Critère 1 : étant donné un profil validé le 25/11/2026 et une date métier avancée au 26/11/2027, "
			+ "quand la tâche quotidienne s'exécute, alors « à revalider » est enregistré et un seul événement "
			+ "VALIDE → A_REVALIDER est créé, clé = identifiant client, sans réponse du questionnaire")
	void critere1TacheQuotidienne() {
		assertThat(publication.revaliderLesProfilsEchus()).isEqualTo(1);

		assertThat(base.get(CLIENTE).getStatut()).isEqualTo(Statut.A_REVALIDER);
		assertThat(base.get(CLIENTE).getPublieLe()).isEqualTo(APRES_ECHEANCE.toOffsetDateTime());
		verifierUnSeulEvenement();
		verify(publies).verrouillerLaTache();
	}

	@Test
	@DisplayName("Critère 2 : étant donné la même tâche lancée une seconde fois le même jour, alors aucun nouvel "
			+ "événement n'est publié pour ce profil")
	void critere2SecondPassageSansEvenement() {
		publication.revaliderLesProfilsEchus();

		assertThat(publication.revaliderLesProfilsEchus()).isZero();

		verifierUnSeulEvenement();
	}

	@Test
	@DisplayName("Critère 3 : étant donné un profil échu et la tâche pas encore passée, quand la cliente ouvre son "
			+ "profil, alors il est affiché « à revalider », le statut est enregistré et l'événement publié une fois")
	void critere3LectureDuProfil() {
		assertThat(service.etat("jeton").statut()).isEqualTo(Statut.A_REVALIDER);
		assertThat(service.etat("jeton").statut()).isEqualTo(Statut.A_REVALIDER);
		assertThat(service.souscriptionAutorisee(CLIENTE)).isFalse();

		assertThat(base.get(CLIENTE).getStatut()).isEqualTo(Statut.A_REVALIDER);
		verifierUnSeulEvenement();
		assertThat(publication.revaliderLesProfilsEchus()).isZero();
		verifierUnSeulEvenement();
	}

	@Test
	@DisplayName("Avant l'échéance, ni la tâche ni la lecture du profil ne changent le statut")
	void avantEcheance() {
		maintenant = VALIDE_LE.plusYears(1).minusMinutes(1);

		assertThat(publication.revaliderLesProfilsEchus()).isZero();
		assertThat(service.etat("jeton").statut()).isEqualTo(Statut.VALIDE);

		verify(evenements, never()).enregistrer(any(), anyString(), anyString(), any());
		assertThat(base.get(CLIENTE).getStatut()).isEqualTo(Statut.VALIDE);
	}

	@Test
	@DisplayName("Tâche quotidienne : une fois par jour métier, à partir de l'heure prévue (1 h), selon la date métier")
	void uneFoisParJourMetier() {
		PublicationStatutProfil espion = mock(PublicationStatutProfil.class);
		TacheRevalidationProfils tache = new TacheRevalidationProfils(espion, dateMetier, 1);

		maintenant = APRES_ECHEANCE.withHour(0).withMinute(30);
		tache.verifier();
		verify(espion, never()).revaliderLesProfilsEchus();

		maintenant = APRES_ECHEANCE.withHour(1);
		tache.verifier();
		maintenant = APRES_ECHEANCE.withHour(23);
		tache.verifier();
		verify(espion, times(1)).revaliderLesProfilsEchus();

		maintenant = APRES_ECHEANCE.plusDays(1).withHour(2);
		tache.verifier();
		verify(espion, times(2)).revaliderLesProfilsEchus();
	}

	@Test
	@DisplayName("Une erreur de la tâche est journalisée sans arrêter les vérifications suivantes")
	void erreurDeLaTache() {
		PublicationStatutProfil enPanne = mock(PublicationStatutProfil.class);
		when(enPanne.revaliderLesProfilsEchus()).thenThrow(new IllegalStateException("base indisponible"))
				.thenReturn(1);
		TacheRevalidationProfils tache = new TacheRevalidationProfils(enPanne, dateMetier, 1);

		tache.verifier();
		tache.verifier();

		verify(enPanne, times(2)).revaliderLesProfilsEchus();
	}

	@Test
	@DisplayName("En test, le bouton lance la tâche à la main et renvoie le nombre de profils passés « à revalider »")
	void boutonDeTest() {
		ProfilTestController routes = new ProfilTestController(publication);

		assertThat(routes.lancerRevalidation().profilsARevalider()).isEqualTo(1);
		assertThat(routes.lancerRevalidation().profilsARevalider()).isZero();
		verifierUnSeulEvenement();
	}

}
