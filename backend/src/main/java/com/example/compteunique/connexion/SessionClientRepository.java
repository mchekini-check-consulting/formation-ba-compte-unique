package com.example.compteunique.connexion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionClientRepository extends JpaRepository<SessionClient, Long> {

	Optional<SessionClient> findByJetonHache(String jetonHache);

}
