package com.example.compteunique.catalogue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

// CU-381 : journal d'une exécution du batch (compteurs, lignes rejetées avec leur motif, statut).
@Entity
@Table(schema = "catalogue", name = "journal_batch")
class JournalBatch {

	enum Declenchement {
		AUTOMATIQUE, MANUEL
	}

	enum Statut {
		TERMINE, ECHEC
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate dateMetier;

	@Enumerated(EnumType.STRING)
	private Declenchement declenchement;

	private String nomFichier;

	private OffsetDateTime debut;

	private OffsetDateTime fin;

	private int ajoutes;

	private int modifies;

	private int retires;

	private int rejetees;

	@Enumerated(EnumType.STRING)
	private Statut statut;

	private String message;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "journal_id", nullable = false)
	@OrderBy("numeroLigne")
	private List<LigneRejetee> lignesRejetees = new ArrayList<>();

	protected JournalBatch() {
	}

	JournalBatch(LocalDate dateMetier, Declenchement declenchement, String nomFichier, OffsetDateTime debut) {
		this.dateMetier = dateMetier;
		this.declenchement = declenchement;
		this.nomFichier = nomFichier;
		this.debut = debut;
	}

	void terminer(int ajoutes, int modifies, int retires, List<LigneRejetee> rejets, OffsetDateTime fin) {
		this.ajoutes = ajoutes;
		this.modifies = modifies;
		this.retires = retires;
		this.rejetees = rejets.size();
		this.lignesRejetees.addAll(rejets);
		this.statut = Statut.TERMINE;
		this.fin = fin;
	}

	void echouer(String message, OffsetDateTime fin) {
		this.statut = Statut.ECHEC;
		this.message = message;
		this.fin = fin;
	}

	Long getId() {
		return id;
	}

	LocalDate getDateMetier() {
		return dateMetier;
	}

	Declenchement getDeclenchement() {
		return declenchement;
	}

	String getNomFichier() {
		return nomFichier;
	}

	OffsetDateTime getDebut() {
		return debut;
	}

	OffsetDateTime getFin() {
		return fin;
	}

	int getAjoutes() {
		return ajoutes;
	}

	int getModifies() {
		return modifies;
	}

	int getRetires() {
		return retires;
	}

	int getRejetees() {
		return rejetees;
	}

	Statut getStatut() {
		return statut;
	}

	String getMessage() {
		return message;
	}

	List<LigneRejetee> getLignesRejetees() {
		return lignesRejetees;
	}

}
