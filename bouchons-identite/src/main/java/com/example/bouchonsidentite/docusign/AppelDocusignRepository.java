package com.example.bouchonsidentite.docusign;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppelDocusignRepository extends JpaRepository<AppelDocusign, Long> {

	Optional<AppelDocusign> findFirstByEnveloppeIdAndOperation(String enveloppeId, OperationDocusign operation);

}
