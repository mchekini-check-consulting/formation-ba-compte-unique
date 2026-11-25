package com.example.compteunique.evenements;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface MessageEnErreurRepository extends JpaRepository<MessageEnErreur, Long> {

	List<MessageEnErreur> findTop50ByOrderByIdDesc();

}
