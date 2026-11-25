package com.example.compteunique.evenements;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.springframework.kafka.core.KafkaOperations;
import org.springframework.mock.env.MockEnvironment;

import com.example.compteunique.datemetier.DateMetier;

/**
 * Pour les tests des autres modules (ex. ordres, CU-376) : la vraie boîte d'envoi et le vrai relais de TECH-14, avec
 * une base simulée en mémoire et un Kafka simulé fourni par le test. Topics : cu-epargne.&lt;topic&gt;.
 */
public final class BoiteDEnvoiEnMemoire {

	private final List<EvenementADistribuer> boite = new ArrayList<>();
	private final Evenements evenements;
	private final RelaisEvenements relais;

	public BoiteDEnvoiEnMemoire(DateMetier dateMetier, KafkaOperations<String, String> kafka) {
		EvenementADistribuerRepository aDistribuer = mock(EvenementADistribuerRepository.class);
		when(aDistribuer.save(any())).thenAnswer(appel -> {
			boite.add(appel.getArgument(0));
			return appel.getArgument(0);
		});
		when(aDistribuer.verrouiller()).thenReturn(true);
		when(aDistribuer.enAttente())
				.thenAnswer(appel -> boite.stream().filter(e -> e.getPublieLe() == null).toList());
		this.evenements = new Evenements(aDistribuer, mock(EvenementTraiteRepository.class),
				mock(MessageEnErreurRepository.class), new ConfigurationKafka(environnement()), dateMetier);
		this.relais = new RelaisEvenements(aDistribuer, kafka, dateMetier, 100);
	}

	private static MockEnvironment environnement() {
		MockEnvironment environnement = new MockEnvironment().withProperty(ConfigurationKafka.BOOTSTRAP, "kafka:9092")
				.withProperty(ConfigurationKafka.SUFFIXE_ERREURS, ".dlt")
				.withProperty(ConfigurationKafka.GROUPE_PREFIXE, "cu-epargne");
		for (ConfigurationKafka.Topic topic : ConfigurationKafka.Topic.values()) {
			environnement.setProperty(topic.variable(), "cu-epargne." + topic.name().toLowerCase().replace('_', '-'));
		}
		return environnement;
	}

	public Evenements evenements() {
		return evenements;
	}

	public RelaisEvenements relais() {
		return relais;
	}

	/** Les événements enregistrés, dans l'ordre. */
	public List<EvenementADistribuer> contenu() {
		return boite;
	}

}
