package com.example.compteunique.livreta;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificatClotureLivretARepository extends JpaRepository<CertificatClotureLivretA, Long> {

	/** Dernier certificat déposé pour la demande. */
	Optional<CertificatClotureLivretA> findFirstByDemandeIdOrderByIdDesc(Long demandeId);

}
