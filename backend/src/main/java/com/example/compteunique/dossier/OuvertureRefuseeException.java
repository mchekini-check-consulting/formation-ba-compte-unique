package com.example.compteunique.dossier;

// CU-9 : nouvelle ouverture refusée pour une personne déjà connue. Seule une ouverture en cours
// donne la référence du dossier existant, pour que la cliente le reprenne.
public class OuvertureRefuseeException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Les messages sont les textes exacts du ticket ; aucun ne révèle le profil ou le dossier existant. */
	public enum Refus {
		/** Critères 1 et 3 : profil existant, identifiant activé. */
		ESPACE_EXISTANT("Vous avez déjà un espace client. Connectez-vous avec votre identifiant."),
		/** Critères 2 et 3 : profil existant, identifiant pas encore activé. */
		ACTIVATION_ATTENDUE("Votre espace n’est pas encore activé. Ouvrez le lien d’activation reçu par SMS."),
		/** Critère 8 : dossier annulé par contrôle ; aucun motif n'est donné. */
		OUVERTURE_ANNULEE("Votre demande d’ouverture en ligne est annulée. Pour ouvrir un compte à la banque, "
				+ "contactez un conseiller."),
		/** Critère 4 : dossier en cours, à reprendre ; aucun nouveau dossier n'est créé. */
		OUVERTURE_EN_COURS("Vous avez déjà une ouverture de compte en cours. Reprenez-la.");

		private final String message;

		Refus(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}
	}

	/** Où reprendre un dossier en cours : saisie de l'identité, ou suivi de l'ouverture du compte. */
	public enum EtapeReprise {
		IDENTITE, OUVERTURE
	}

	private final transient Refus refus;
	private final String referenceReprise;
	private final transient EtapeReprise etapeReprise;

	public OuvertureRefuseeException(Refus refus) {
		this(refus, null, null);
	}

	public OuvertureRefuseeException(Refus refus, String referenceReprise, EtapeReprise etapeReprise) {
		super(refus.name());
		this.refus = refus;
		this.referenceReprise = referenceReprise;
		this.etapeReprise = etapeReprise;
	}

	public Refus getRefus() {
		return refus;
	}

	public String getReferenceReprise() {
		return referenceReprise;
	}

	public EtapeReprise getEtapeReprise() {
		return etapeReprise;
	}

}
