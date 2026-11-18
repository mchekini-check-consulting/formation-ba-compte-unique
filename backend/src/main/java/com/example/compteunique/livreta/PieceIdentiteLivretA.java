package com.example.compteunique.livreta;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.compteunique.dossier.Categories.TypePiece;
import com.example.compteunique.livreta.ResultatsPiece.Document;
import com.example.compteunique.livreta.ResultatsPiece.Selfie;

// CU-29 : vérification d'une nouvelle pièce d'identité à l'ouverture d'un Livret A (table créée par
// db/migration/V24__piece_identite_livret_a.sql).
@Entity
@Table(name = "piece_identite_livret_a")
public class PieceIdentiteLivretA {

	/** Seuil de correspondance du selfie (Stripe Identity), en %. */
	static final int SEUIL_SELFIE = 90;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64, unique = true)
	private String reference;

	@Column(name = "dossier_id", nullable = false)
	private Long dossierId;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatutPieceLivretA statut;

	@Enumerated(EnumType.STRING)
	@Column(name = "type_piece", nullable = false, length = 30)
	private TypePiece typePiece;

	@Column(name = "numero_piece", nullable = false, length = 30)
	private String numeroPiece;

	@Column(name = "date_expiration", nullable = false)
	private LocalDate dateExpiration;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultat_document", length = 30)
	private Document resultatDocument;

	@Column(name = "tentatives_document", nullable = false)
	private int tentativesDocument;

	@Column(name = "document_le")
	private OffsetDateTime documentLe;

	@Enumerated(EnumType.STRING)
	@Column(name = "resultat_selfie", length = 30)
	private Selfie resultatSelfie;

	@Column(name = "score_selfie")
	private Integer scoreSelfie;

	@Column(name = "tentatives_selfie", nullable = false)
	private int tentativesSelfie;

	@Column(name = "selfie_le")
	private OffsetDateTime selfieLe;

	@Column(name = "conseiller_alerte_le")
	private OffsetDateTime conseillerAlerteLe;

	@Column(name = "valide_le")
	private OffsetDateTime valideLe;

	@Column(name = "cree_le", nullable = false)
	private OffsetDateTime creeLe;

	protected PieceIdentiteLivretA() {
	}

	/** Nouvelle pièce saisie par la cliente : l'analyse Stripe Identity est demandée par l'écran. */
	public PieceIdentiteLivretA(Long dossierId, UUID customerId, TypePiece typePiece, String numeroPiece,
			LocalDate dateExpiration, OffsetDateTime maintenant) {
		this.reference = UUID.randomUUID().toString();
		this.dossierId = dossierId;
		this.customerId = customerId;
		this.statut = StatutPieceLivretA.PIECE_A_VERIFIER;
		this.typePiece = typePiece;
		this.numeroPiece = numeroPiece;
		this.dateExpiration = dateExpiration;
		this.creeLe = maintenant;
	}

	/**
	 * Résultat de l'analyse de la pièce. Vérifiée : selfie attendu ; à reprendre ou erreur technique : la cliente
	 * recommence ; rejetée : demande arrêtée ; sans réponse : en attente et conseiller alerté.
	 */
	void recevoirDocument(Document recu, int essais, OffsetDateTime maintenant) {
		resultatDocument = recu;
		tentativesDocument = essais;
		documentLe = maintenant;
		switch (recu) {
			case VERIFIE -> statut = StatutPieceLivretA.SELFIE_A_FAIRE;
			case REJETE -> statut = StatutPieceLivretA.BLOQUE;
			case SANS_REPONSE -> mettreEnAttente(maintenant);
			case A_REPRENDRE, ERREUR_TECHNIQUE -> statut = StatutPieceLivretA.PIECE_A_VERIFIER;
		}
	}

	/**
	 * Résultat du selfie. Correspondance (score d'au moins 90 %) : validée ; score inférieur ou photo d'écran :
	 * demande arrêtée ; sans réponse : en attente et conseiller alerté.
	 */
	void recevoirSelfie(Selfie recu, Integer score, int essais, OffsetDateTime maintenant) {
		resultatSelfie = recu;
		scoreSelfie = score;
		tentativesSelfie = essais;
		selfieLe = maintenant;
		switch (recu) {
			case CORRESPONDANCE -> {
				statut = StatutPieceLivretA.VALIDE;
				valideLe = maintenant;
			}
			case NON_CORRESPONDANCE, ECHEC_VERIFICATION_PRESENCE -> statut = StatutPieceLivretA.BLOQUE;
			case SANS_REPONSE -> mettreEnAttente(maintenant);
		}
	}

	private void mettreEnAttente(OffsetDateTime maintenant) {
		statut = StatutPieceLivretA.EN_ATTENTE;
		conseillerAlerteLe = maintenant;
	}

	/** Démarche arrêtée ou en attente d'un conseiller : elle ne peut pas être reprise en ligne. */
	boolean suspend() {
		return statut == StatutPieceLivretA.BLOQUE || statut == StatutPieceLivretA.EN_ATTENTE;
	}

	public String getReference() {
		return reference;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public StatutPieceLivretA getStatut() {
		return statut;
	}

	public TypePiece getTypePiece() {
		return typePiece;
	}

	public String getNumeroPiece() {
		return numeroPiece;
	}

	public LocalDate getDateExpiration() {
		return dateExpiration;
	}

	public Document getResultatDocument() {
		return resultatDocument;
	}

	public int getTentativesDocument() {
		return tentativesDocument;
	}

	public OffsetDateTime getDocumentLe() {
		return documentLe;
	}

	public Selfie getResultatSelfie() {
		return resultatSelfie;
	}

	public Integer getScoreSelfie() {
		return scoreSelfie;
	}

	public int getTentativesSelfie() {
		return tentativesSelfie;
	}

	public OffsetDateTime getSelfieLe() {
		return selfieLe;
	}

	public OffsetDateTime getConseillerAlerteLe() {
		return conseillerAlerteLe;
	}

	public OffsetDateTime getValideLe() {
		return valideLe;
	}

	public OffsetDateTime getCreeLe() {
		return creeLe;
	}

}
