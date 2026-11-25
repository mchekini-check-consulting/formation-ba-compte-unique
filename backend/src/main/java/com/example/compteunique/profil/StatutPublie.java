package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.profil.ProfilClientService.Statut;

// CU-382 : dernier statut de profil publié dans Kafka pour une cliente (publication au changement seulement).
@Entity
@Table(schema = "profil_client", name = "statut_publie")
class StatutPublie {

	@Id
	private UUID customerId;

	@Enumerated(EnumType.STRING)
	private Statut statut;

	private OffsetDateTime publieLe;

	protected StatutPublie() {
	}

	StatutPublie(UUID customerId, Statut statut, OffsetDateTime publieLe) {
		this.customerId = customerId;
		this.statut = statut;
		this.publieLe = publieLe;
	}

	void changer(Statut nouveau, OffsetDateTime le) {
		this.statut = nouveau;
		this.publieLe = le;
	}

	UUID getCustomerId() {
		return customerId;
	}

	Statut getStatut() {
		return statut;
	}

	OffsetDateTime getPublieLe() {
		return publieLe;
	}

}
