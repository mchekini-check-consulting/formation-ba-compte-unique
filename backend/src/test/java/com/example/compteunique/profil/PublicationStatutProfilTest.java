package com.example.compteunique.profil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.profil.ProfilClientService.Statut;

// CU-382 (TECH-14), critère 8 : événement « statut du profil modifié » publié au changement de statut, avec
// l'identifiant client comme clé, sans aucune réponse du questionnaire.
class PublicationStatutProfilTest {

	private static final ZonedDateTime MAINTENANT = ZonedDateTime.of(2027, 11, 25, 10, 0, 0, 0, DateMetier.PARIS);
	private static final UUID CLIENTE = UUID.randomUUID();

	private final StatutPublieRepository publies = mock(StatutPublieRepository.class);
	private final Evenements evenements = mock(Evenements.class);
	private final DateMetier dateMetier = mock(DateMetier.class);
	private final PublicationStatutProfil publication = new PublicationStatutProfil(publies, evenements, dateMetier);
	private final Map<UUID, StatutPublie> base = new HashMap<>();

	@BeforeEach
	void baseEnMemoire() {
		when(dateMetier.maintenant()).thenReturn(MAINTENANT);
		when(publies.findById(any())).thenAnswer(appel -> Optional.ofNullable(base.get(appel.getArgument(0))));
		when(publies.save(any())).thenAnswer(appel -> {
			StatutPublie statut = appel.getArgument(0);
			base.put(statut.getCustomerId(), statut);
			return statut;
		});
	}

	@Test
	@DisplayName("Première validation : « à compléter » devient « validé », l'événement est enregistré avec "
			+ "l'identifiant client comme clé et seulement le statut")
	void premiereValidation() {
		publication.changer(CLIENTE, Statut.A_COMPLETER, Statut.VALIDE);

		verify(evenements).enregistrer(Topic.PROFIL_CLIENT, "StatutProfilModifie", CLIENTE.toString(),
				Map.of("statut", "VALIDE", "ancienStatut", "A_COMPLETER"));
		assertThat(base.get(CLIENTE).getStatut()).isEqualTo(Statut.VALIDE);
		assertThat(base.get(CLIENTE).getPublieLe()).isEqualTo(MAINTENANT.toOffsetDateTime());
	}

	@Test
	@DisplayName("Nouvelle version d'un profil déjà « validé » : pas de changement de statut, pas d'événement")
	void memeStatut() {
		base.put(CLIENTE, new StatutPublie(CLIENTE, Statut.VALIDE, MAINTENANT.toOffsetDateTime()));

		publication.changer(CLIENTE, Statut.VALIDE, Statut.VALIDE);

		verify(evenements, never()).enregistrer(any(), anyString(), anyString(), any());
	}

	@Test
	@DisplayName("Critère 8 : étant donné un profil qui passe de « validé » à « à revalider », quand le changement "
			+ "est détecté, alors l'événement est enregistré une seule fois, sans réponse du questionnaire")
	void passageARevalider() {
		base.put(CLIENTE, new StatutPublie(CLIENTE, Statut.VALIDE, MAINTENANT.toOffsetDateTime()));
		when(publies.aRevaliderNonPublies(MAINTENANT.toOffsetDateTime())).thenReturn(List.of(CLIENTE), List.of());

		publication.signalerRevalidations();
		publication.signalerRevalidations();

		verify(evenements).enregistrer(eq(Topic.PROFIL_CLIENT), eq("StatutProfilModifie"), eq(CLIENTE.toString()),
				eq(Map.of("statut", "A_REVALIDER", "ancienStatut", "VALIDE")));
		assertThat(base.get(CLIENTE).getStatut()).isEqualTo(Statut.A_REVALIDER);
	}

}
