package com.example.compteunique.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.catalogue.LecteurCatalogue.LigneCatalogue;

// CU-381 : un support (unité de compte) du catalogue, un par code ISIN. Mis à jour à chaque chargement.
@Entity
@Table(schema = "catalogue", name = "support")
class Support {

	static final String ACTIF = "actif";
	static final String RETIRE = "retire";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String codeIsin;
	private String nom;
	private String type;
	private String categorie;
	private String produitsOuverts;
	private short niveauRisque;
	private boolean cote;
	private BigDecimal fraisSupportPct;
	private String statut;
	private BigDecimal valeurLiquidative;
	private LocalDate dateVl;
	private String devise;
	private String periodiciteVl;
	private String label;
	private String societeGestion;
	@Column(name = "mis_a_jour_le")
	private OffsetDateTime misAJourLe;

	protected Support() {
	}

	Support(LigneCatalogue ligne, OffsetDateTime le) {
		this.codeIsin = ligne.codeIsin();
		copier(ligne);
		this.misAJourLe = le;
	}

	/** Applique la ligne du fichier ; vrai si une caractéristique ou la valeur liquidative a changé. */
	boolean mettreAJour(LigneCatalogue ligne, OffsetDateTime le) {
		if (identique(ligne)) {
			return false;
		}
		copier(ligne);
		this.misAJourLe = le;
		return true;
	}

	boolean estActif() {
		return ACTIF.equals(statut);
	}

	private void copier(LigneCatalogue ligne) {
		this.nom = ligne.nom();
		this.type = ligne.type();
		this.categorie = ligne.categorie();
		this.produitsOuverts = ligne.produitsOuverts();
		this.niveauRisque = (short) ligne.niveauRisque();
		this.cote = ligne.cote();
		this.fraisSupportPct = ligne.fraisSupportPct();
		this.statut = ligne.statut();
		this.valeurLiquidative = ligne.valeurLiquidative();
		this.dateVl = ligne.dateVl();
		this.devise = ligne.devise();
		this.periodiciteVl = ligne.periodiciteVl();
		this.label = ligne.label();
		this.societeGestion = ligne.societeGestion();
	}

	// Les décimaux sont comparés par valeur (25.4 = 25.400000 relu en base).
	private boolean identique(LigneCatalogue ligne) {
		return Objects.equals(nom, ligne.nom()) && Objects.equals(type, ligne.type())
				&& Objects.equals(categorie, ligne.categorie())
				&& Objects.equals(produitsOuverts, ligne.produitsOuverts()) && niveauRisque == ligne.niveauRisque()
				&& cote == ligne.cote() && memeValeur(fraisSupportPct, ligne.fraisSupportPct())
				&& Objects.equals(statut, ligne.statut())
				&& memeValeur(valeurLiquidative, ligne.valeurLiquidative()) && Objects.equals(dateVl, ligne.dateVl())
				&& Objects.equals(devise, ligne.devise()) && Objects.equals(periodiciteVl, ligne.periodiciteVl())
				&& Objects.equals(label, ligne.label()) && Objects.equals(societeGestion, ligne.societeGestion());
	}

	private static boolean memeValeur(BigDecimal a, BigDecimal b) {
		return a != null && b != null && a.compareTo(b) == 0;
	}

	Long getId() {
		return id;
	}

	String getCodeIsin() {
		return codeIsin;
	}

	String getNom() {
		return nom;
	}

	String getType() {
		return type;
	}

	String getCategorie() {
		return categorie;
	}

	String getProduitsOuverts() {
		return produitsOuverts;
	}

	int getNiveauRisque() {
		return niveauRisque;
	}

	boolean isCote() {
		return cote;
	}

	BigDecimal getFraisSupportPct() {
		return fraisSupportPct;
	}

	String getStatut() {
		return statut;
	}

	BigDecimal getValeurLiquidative() {
		return valeurLiquidative;
	}

	LocalDate getDateVl() {
		return dateVl;
	}

	String getDevise() {
		return devise;
	}

	String getPeriodiciteVl() {
		return periodiciteVl;
	}

	String getLabel() {
		return label;
	}

	String getSocieteGestion() {
		return societeGestion;
	}

}
