package com.example.compteunique.connexion;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// CU-16 (US-11) : compteur d'erreurs consécutives et fin de blocage d'un identifiant saisi
// (table créée par db/migration/V13__blocage_identifiant.sql).
@Entity
@Table(name = "blocage_identifiant")
public class BlocageIdentifiant {

	/** Critère 1 : la troisième erreur consécutive bloque l'identifiant. */
	public static final int ERREURS_MAX = 3;
	public static final Duration DUREE_BLOCAGE = Duration.ofMinutes(5);

	@Id
	@Column(length = 32, updatable = false)
	private String identifiant;

	@Column(name = "erreurs_consecutives", nullable = false)
	private int erreursConsecutives;

	@Column(name = "bloque_jusqu_au")
	private OffsetDateTime bloqueJusquAu;

	protected BlocageIdentifiant() {
	}

	public BlocageIdentifiant(String identifiant) {
		this.identifiant = identifiant;
	}

	public boolean estBloque(OffsetDateTime maintenant) {
		return bloqueJusquAu != null && maintenant.isBefore(bloqueJusquAu);
	}

	/** Critère 6 : à la fin d'un blocage, le compteur repart de zéro. */
	public void leverSiTermine(OffsetDateTime maintenant) {
		if (bloqueJusquAu != null && !estBloque(maintenant)) {
			erreursConsecutives = 0;
			bloqueJusquAu = null;
		}
	}

	/** Une erreur de plus ; renvoie vrai si elle déclenche le blocage (critères 1 et 3). */
	public boolean erreur(OffsetDateTime maintenant) {
		erreursConsecutives++;
		if (erreursConsecutives >= ERREURS_MAX) {
			bloqueJusquAu = maintenant.plus(DUREE_BLOCAGE);
			return true;
		}
		return false;
	}

	/** Temps restant avant la fin du blocage (zéro s'il n'y en a pas). */
	public Duration restant(OffsetDateTime maintenant) {
		return estBloque(maintenant) ? Duration.between(maintenant, bloqueJusquAu) : Duration.ZERO;
	}

	public String getIdentifiant() {
		return identifiant;
	}

	public int getErreursConsecutives() {
		return erreursConsecutives;
	}

	public OffsetDateTime getBloqueJusquAu() {
		return bloqueJusquAu;
	}

}
