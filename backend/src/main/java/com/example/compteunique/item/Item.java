package com.example.compteunique.item;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Ressource générique exposée par /items (table créée par db/migration/V2__item.sql)
@Entity
@Table(name = "item")
public class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String nom;

	@Column(length = 1000)
	private String description;

	@Column(name = "cree_le", nullable = false, updatable = false)
	private OffsetDateTime creeLe;

	protected Item() {
	}

	public Item(String nom, String description) {
		this.nom = nom;
		this.description = description;
	}

	@PrePersist
	void avantCreation() {
		creeLe = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getNom() {
		return nom;
	}

	public String getDescription() {
		return description;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
