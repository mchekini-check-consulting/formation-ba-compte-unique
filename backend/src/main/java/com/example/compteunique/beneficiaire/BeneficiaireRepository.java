package com.example.compteunique.beneficiaire;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaireRepository extends JpaRepository<Beneficiaire, Long> {

	/** CU-146 : bénéficiaires d'une cliente au statut donné, du plus récent au plus ancien. */
	List<Beneficiaire> findByCustomerIdAndStatutOrderByAjouteLeDescIdDesc(UUID customerId, String statut);

	long countByCustomerIdAndStatut(UUID customerId, String statut);

	boolean existsByCustomerIdAndIbanAndStatut(UUID customerId, String iban, String statut);

	/** CU-146 critère 17 : un bénéficiaire seulement s'il appartient à la cliente. */
	Optional<Beneficiaire> findByIdAndCustomerIdAndStatut(Long id, UUID customerId, String statut);

}
