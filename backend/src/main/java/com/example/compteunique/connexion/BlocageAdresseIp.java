package com.example.compteunique.connexion;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-48 : fin de blocage d'une adresse IP (table créée par db/migration/V14__blocage_adresse_ip.sql).
@Entity
@Table(name = "blocage_adresse_ip")
public class BlocageAdresseIp {

	@Id
	@Column(name = "adresse_ip", length = 45, updatable = false)
	private String adresseIp;

	@Column(name = "bloque_jusqu_au", nullable = false)
	private OffsetDateTime bloqueJusquAu;

	protected BlocageAdresseIp() {
	}

	public BlocageAdresseIp(String adresseIp, OffsetDateTime bloqueJusquAu) {
		this.adresseIp = adresseIp;
		this.bloqueJusquAu = bloqueJusquAu;
	}

	public boolean estBloquee(OffsetDateTime maintenant) {
		return maintenant.isBefore(bloqueJusquAu);
	}

	public void bloquerJusquAu(OffsetDateTime fin) {
		this.bloqueJusquAu = fin;
	}

	public String getAdresseIp() {
		return adresseIp;
	}

	public OffsetDateTime getBloqueJusquAu() {
		return bloqueJusquAu;
	}

}
