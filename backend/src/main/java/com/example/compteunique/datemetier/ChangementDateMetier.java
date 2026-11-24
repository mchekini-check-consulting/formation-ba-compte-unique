package com.example.compteunique.datemetier;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-380 critère 5 : un changement de date métier tracé (ancienne date, nouvelle date, auteur, heure).
// Journal en ajout seul : jamais modifié ni supprimé.
@Entity
@Table(schema = "date_metier", name = "journal_changement")
public class ChangementDateMetier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ancienne_date", nullable = false)
	private OffsetDateTime ancienneDate;

	@Column(name = "nouvelle_date", nullable = false)
	private OffsetDateTime nouvelleDate;

	@Column(nullable = false, length = 100)
	private String auteur;

	@Column(name = "change_le", nullable = false)
	private OffsetDateTime changeLe;

	protected ChangementDateMetier() {
	}

	ChangementDateMetier(OffsetDateTime ancienneDate, OffsetDateTime nouvelleDate, String auteur,
			OffsetDateTime changeLe) {
		this.ancienneDate = ancienneDate;
		this.nouvelleDate = nouvelleDate;
		this.auteur = auteur;
		this.changeLe = changeLe;
	}

	public Long getId() {
		return id;
	}

	public OffsetDateTime getAncienneDate() {
		return ancienneDate;
	}

	public OffsetDateTime getNouvelleDate() {
		return nouvelleDate;
	}

	public String getAuteur() {
		return auteur;
	}

	public OffsetDateTime getChangeLe() {
		return changeLe;
	}

}
