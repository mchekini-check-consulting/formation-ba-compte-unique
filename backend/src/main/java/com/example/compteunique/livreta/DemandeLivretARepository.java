package com.example.compteunique.livreta;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandeLivretARepository extends JpaRepository<DemandeLivretA, Long> {

	/** Dernière demande de la cliente. */
	Optional<DemandeLivretA> findFirstByCustomerIdOrderByCreeeLeDesc(UUID customerId);

	/** Demande de la cliente, seulement si elle lui appartient. */
	Optional<DemandeLivretA> findByReferenceAndCustomerId(String reference, UUID customerId);

	Optional<DemandeLivretA> findByReference(String reference);

	/** Contrôle interne : demandes rattachées aux dossiers de la même personne. */
	List<DemandeLivretA> findByDossierIdIn(Collection<Long> dossierIds);

	/** Tâche quotidienne : demandes encore en cours de vérification, sans alerte du conseiller. */
	List<DemandeLivretA> findByStatutAndConseillerAlerteLeIsNull(StatutDemandeLivretA statut);

}
