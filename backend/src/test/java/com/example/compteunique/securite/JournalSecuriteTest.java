package com.example.compteunique.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.compte.JournalSecuriteRepository;

// CU-48 : écriture du journal (adresse IP, panne), purge à 6 mois ; CU-18 : réponses sans cache.
class JournalSecuriteTest {

	private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-11-06T10:00:00Z");

	private final JournalSecuriteRepository depot = mock(JournalSecuriteRepository.class);
	private final JournalSecurite journal = new JournalSecurite(depot);

	@AfterEach
	void sansDemandeEnCours() {
		RequestContextHolder.resetRequestAttributes();
	}

	private static EvenementSecurite connexion() {
		return EvenementSecurite.authentification(EvenementSecurite.CONNEXION_REUSSIE, "12345678", 1L, null,
				MAINTENANT);
	}

	@Test
	void lEvenementRecoitLAdresseIpDeLaDemandeEnCours() {
		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr("203.0.113.7");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requete));
		EvenementSecurite evenement = connexion();

		journal.enregistrer(evenement);

		assertThat(evenement.getAdresseIp()).isEqualTo("203.0.113.7");
		verify(depot).saveAndFlush(evenement);
	}

	@Test
	void horsDemandeHttpLAdresseResteVide() {
		EvenementSecurite evenement = connexion();
		journal.enregistrer(evenement);
		assertThat(evenement.getAdresseIp()).isNull();
		assertThat(JournalSecurite.adresseIpCourante()).isEmpty();
	}

	@Test
	void unJournalQuiNePeutPasEtreEcritRefuseLAction() {
		when(depot.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("base arrêtée"));
		assertThrows(JournalIndisponibleException.class, () -> journal.enregistrer(connexion()));
	}

	@Test
	void lesChampsTropLongsSontTronquesALaTailleDesColonnes() {
		EvenementSecurite evenement = EvenementSecurite.authentification(EvenementSecurite.DEMANDE_REINITIALISATION,
				"x".repeat(50), null, null, MAINTENANT);
		evenement.depuis("y".repeat(60));
		assertThat(evenement.getIdentifiantSaisi()).hasSize(32);
		assertThat(evenement.getAdresseIp()).hasSize(45);
		assertThat(evenement.getResultat()).isEqualTo(EvenementSecurite.REUSSITE);
	}

	@Test
	void laPurgeSupprimeCeQuiAPlusDeSixMois() {
		when(depot.purgerAvant(any())).thenReturn(4);
		PurgeJournalSecurite purge = new PurgeJournalSecurite(depot,
				Clock.fixed(Instant.parse("2026-11-06T03:30:00Z"), ZoneOffset.UTC));

		assertThat(purge.purger()).isEqualTo(4);

		verify(depot).purgerAvant(OffsetDateTime.parse("2026-05-06T03:30:00Z"));
		assertThat(new PurgeJournalSecurite(depot)).isNotNull();
	}

	@Test
	void aucuneReponseNEstGardeeEnCache() throws Exception {
		InterceptorRegistry registre = new InterceptorRegistry() {

			@Override
			public org.springframework.web.servlet.config.annotation.InterceptorRegistration addInterceptor(
					HandlerInterceptor interceptor) {
				MockHttpServletResponse reponse = new MockHttpServletResponse();
				try {
					assertThat(interceptor.preHandle(new MockHttpServletRequest(), reponse, new Object())).isTrue();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
				assertThat(reponse.getHeader("Cache-Control")).isEqualTo("no-store");
				return super.addInterceptor(interceptor);
			}

		};
		new SansCacheConfig().addInterceptors(registre);
	}

}
