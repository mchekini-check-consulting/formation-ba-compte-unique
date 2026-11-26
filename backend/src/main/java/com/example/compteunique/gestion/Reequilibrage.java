package com.example.compteunique.gestion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// CU-338 : rééquilibrage au passage en gestion pilotée, par arbitrages internes gratuits au prochain cours.
@Entity
@Table(schema = "gestion", name = "reequilibrage")
public class Reequilibrage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String numeroContrat;

	private OffsetDateTime demandeLe;

	private BigDecimal montantArbitre;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@JoinColumn(name = "reequilibrage_id", nullable = false)
	private List<ReequilibrageOrdre> ordres = new ArrayList<>();

	protected Reequilibrage() {
	}

	Reequilibrage(String numeroContrat, OffsetDateTime demandeLe) {
		this.numeroContrat = numeroContrat;
		this.demandeLe = demandeLe;
		this.montantArbitre = BigDecimal.ZERO.setScale(2);
	}

	void ajouter(UUID ordreId, String depart, String arrivee, BigDecimal montant) {
		ordres.add(new ReequilibrageOrdre(ordreId, depart, arrivee, montant));
		montantArbitre = montantArbitre.add(montant);
	}

	public OffsetDateTime getDemandeLe() {
		return demandeLe;
	}

	public BigDecimal getMontantArbitre() {
		return montantArbitre;
	}

	public List<ReequilibrageOrdre> getOrdres() {
		return ordres;
	}

}
