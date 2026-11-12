package com.example.bouchonsidentite.fiscal;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-295 critère 9 : trace d'une interrogation (table créée par db/migration/V4__interrogations_fiscales.sql).
// Aucune donnée d'identité n'est conservée.
@Entity
@Table(name = "interrogation_fiscale")
public class InterrogationFiscale {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "interrogation_id", nullable = false, length = 64, unique = true)
	private String interrogationId;

	@Column(name = "demande_id", nullable = false, length = 64)
	private String demandeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "cas_de_test", nullable = false, length = 20)
	private CasFiscal casDeTest;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ResultatFiscal resultat;

	@Column(name = "detail_communique", nullable = false)
	private boolean detailCommunique;

	@Column(name = "appele_le", nullable = false)
	private OffsetDateTime appeleLe;

	protected InterrogationFiscale() {
	}

	public InterrogationFiscale(String interrogationId, String demandeId, CasFiscal casDeTest,
			ResultatFiscal resultat, boolean detailCommunique, OffsetDateTime appeleLe) {
		this.interrogationId = interrogationId;
		this.demandeId = demandeId;
		this.casDeTest = casDeTest;
		this.resultat = resultat;
		this.detailCommunique = detailCommunique;
		this.appeleLe = appeleLe;
	}

	public Long getId() {
		return id;
	}

	public String getInterrogationId() {
		return interrogationId;
	}

	public String getDemandeId() {
		return demandeId;
	}

	public CasFiscal getCasDeTest() {
		return casDeTest;
	}

	public ResultatFiscal getResultat() {
		return resultat;
	}

	public boolean isDetailCommunique() {
		return detailCommunique;
	}

	public OffsetDateTime getAppeleLe() {
		return appeleLe;
	}

}
