package com.example.compteunique.ouverture;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.compteunique.messagerie.Canal;

public interface EnvoiMessageRepository extends JpaRepository<EnvoiMessage, Long> {

	Optional<EnvoiMessage> findByDossierIdAndCanal(Long dossierId, Canal canal);

	List<EnvoiMessage> findByResultatAndProchaineTentativeLeLessThanEqual(ResultatEnvoi resultat, OffsetDateTime date);

}
