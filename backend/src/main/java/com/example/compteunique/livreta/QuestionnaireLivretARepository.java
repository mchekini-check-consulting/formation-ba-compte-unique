package com.example.compteunique.livreta;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionnaireLivretARepository extends JpaRepository<QuestionnaireLivretA, Long> {

	/** Dernier questionnaire de la cliente. */
	Optional<QuestionnaireLivretA> findFirstByCustomerIdOrderByReponduLeDescIdDesc(UUID customerId);

	Optional<QuestionnaireLivretA> findByReference(String reference);

}
