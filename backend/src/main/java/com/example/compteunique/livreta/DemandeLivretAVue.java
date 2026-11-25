package com.example.compteunique.livreta;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.compteunique.livreta.CertificatClotureLivretA.Decision;
import com.example.compteunique.livreta.LivretAService.Suivi;
import com.example.compteunique.livreta.LivretAService.Transmission;

/**
 * CU-31 : demande de Livret A vue par la cliente. retard : conseiller alerté (pas de réponse sous 2 jours
 * ouvrés). transmission : identité à transmettre à l'administration fiscale, tant que la demande est en cours.
 * CU-296 : detail du livret existant (null = « établissement non communiqué »), suite choisie, date limite de dépôt
 * du certificat de clôture, dernier certificat déposé et délai de vérification annoncé.
 */
public record DemandeLivretAVue(String reference, StatutDemandeLivretA statut, String message,
		OffsetDateTime attestationLe, boolean partageDetailAutorise, OffsetDateTime choixPartageLe,
		OffsetDateTime interrogationLe, ReponseFiscale reponseFiscale, boolean retard, String delai,
		Transmission transmission, DetailLivretA detail, SuiteLivretAExistant suiteChoisie,
		LocalDate dateLimiteCertificat, CertificatVue certificat, String delaiVerification) {

	/** CU-296 : dernier certificat de clôture déposé (sans le fichier). decision null = à vérifier. */
	public record CertificatVue(String nomFichier, OffsetDateTime deposeLe, Decision decision, String motifRefus) {

		static CertificatVue de(CertificatClotureLivretA certificat) {
			return certificat == null ? null
					: new CertificatVue(certificat.getNomFichier(), certificat.getDeposeLe(),
							certificat.getDecision(), certificat.getMotifRefus());
		}
	}

	static final String DELAI = "2 jours ouvrés";
	static final String DELAI_VERIFICATION = "48 heures ouvrées";
	static final String EN_COURS = "Votre demande est en cours de vérification auprès de l'administration fiscale (délai de 2 jours ouvrés). Aucun versement n'est débité.";
	static final String INDISPONIBLE = "Le service de l'administration fiscale est momentanément indisponible. Votre demande reste en cours de vérification et aucun Livret A n'est ouvert.";
	static final String RETARD = "L'administration fiscale n'a pas encore répondu : votre demande reste en attente et un conseiller a été alerté. Nous vous tiendrons informée.";
	static final String VALIDEE = "Aucun autre Livret A n'a été trouvé : l'ouverture de votre Livret A est validée. Prochaine étape : le versement initial.";
	static final String EXISTANT = "L'administration fiscale indique que vous détenez déjà un Livret A dans un autre établissement. Votre Livret A n'a pas été ouvert. Pour continuer, choisissez de clôturer vous-même votre ancien livret ou de renoncer à l'ouverture.";
	// CU-296 : textes proposés, à valider par la BA (point à valider de l'US).
	static final String EN_ATTENTE_CERTIFICAT = "Clôturez votre ancien Livret A auprès de votre banque, puis déposez ici le certificat de clôture qu'elle vous remettra, au plus tard le %s. Passé cette date, votre demande sera annulée.";
	static final String CERTIFICAT_REFUSE = "Votre certificat de clôture a été refusé : %s. Vous pouvez déposer un nouveau certificat jusqu'au %s.";
	static final String CERTIFICAT_A_VERIFIER = "Votre certificat de clôture a bien été reçu. Un conseiller le vérifie sous 48 heures ouvrées : vous serez informée du résultat ici.";
	static final String CERTIFICAT_VALIDE = "Votre certificat de clôture a été validé : l'ouverture de votre Livret A reprend. Prochaine étape : le versement initial.";
	static final String ANNULEE = "Votre demande d'ouverture de Livret A a été annulée : aucun certificat de clôture validé n'a été reçu avant le %s. Vous pouvez faire une nouvelle demande.";
	static final String RENONCEE = "Vous avez renoncé à l'ouverture de votre Livret A : votre demande est annulée. Vous pouvez faire une nouvelle demande à tout moment.";

	static DemandeLivretAVue de(Suivi suivi) {
		DemandeLivretA demande = suivi.demande();
		boolean retard = demande.getConseillerAlerteLe() != null && demande.estEnCours();
		return new DemandeLivretAVue(demande.getReference(), demande.getStatut(),
				message(demande, retard, suivi.certificat()), demande.getAttestationLe(),
				demande.isPartageDetailAutorise(), demande.getChoixPartageLe(), demande.getInterrogationLe(),
				demande.getReponseFiscale(), retard, DELAI, suivi.transmission(), demande.getDetail(),
				demande.getSuiteChoisie(), demande.getDateLimiteCertificat(), CertificatVue.de(suivi.certificat()),
				DELAI_VERIFICATION);
	}

	private static String message(DemandeLivretA demande, boolean retard, CertificatClotureLivretA certificat) {
		String dateLimite = ClotureLivretAService.jour(demande.getDateLimiteCertificat());
		return switch (demande.getStatut()) {
			case OUVERTURE_VALIDEE -> demande.getSuiteChoisie() == SuiteLivretAExistant.CLOTURE_PAR_CLIENTE
					? CERTIFICAT_VALIDE
					: VALIDEE;
			case LIVRET_A_EXISTANT -> EXISTANT;
			case EN_ATTENTE_CERTIFICAT -> certificat != null && certificat.getDecision() == Decision.REFUSE
					? CERTIFICAT_REFUSE.formatted(certificat.getMotifRefus(), dateLimite)
					: EN_ATTENTE_CERTIFICAT.formatted(dateLimite);
			case CERTIFICAT_A_VERIFIER -> CERTIFICAT_A_VERIFIER;
			case ANNULEE -> ANNULEE.formatted(dateLimite);
			case RENONCEE -> RENONCEE;
			case EN_COURS_DE_VERIFICATION -> {
				if (retard) {
					yield RETARD;
				}
				yield demande.getReponseFiscale() == ReponseFiscale.INDISPONIBLE ? INDISPONIBLE : EN_COURS;
			}
		};
	}

}
