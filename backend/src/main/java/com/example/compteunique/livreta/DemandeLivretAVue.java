package com.example.compteunique.livreta;

import java.time.OffsetDateTime;

import com.example.compteunique.livreta.LivretAService.Suivi;
import com.example.compteunique.livreta.LivretAService.Transmission;

/**
 * CU-31 : demande de Livret A vue par la cliente. retard : conseiller alerté (pas de réponse sous 2 jours
 * ouvrés). transmission : identité à transmettre à l'administration fiscale, tant que la demande est en cours.
 */
public record DemandeLivretAVue(String reference, StatutDemandeLivretA statut, String message,
		OffsetDateTime attestationLe, boolean partageDetailAutorise, OffsetDateTime choixPartageLe,
		OffsetDateTime interrogationLe, ReponseFiscale reponseFiscale, boolean retard, String delai,
		Transmission transmission) {

	static final String DELAI = "2 jours ouvrés";
	static final String EN_COURS = "Votre demande est en cours de vérification auprès de l'administration fiscale (délai de 2 jours ouvrés). Aucun versement n'est débité.";
	static final String INDISPONIBLE = "Le service de l'administration fiscale est momentanément indisponible. Votre demande reste en cours de vérification et aucun Livret A n'est ouvert.";
	static final String RETARD = "L'administration fiscale n'a pas encore répondu : votre demande reste en attente et un conseiller a été alerté. Nous vous tiendrons informée.";
	static final String VALIDEE = "Aucun autre Livret A n'a été trouvé : l'ouverture de votre Livret A est validée. Prochaine étape : le versement initial.";
	static final String EXISTANT = "L'administration fiscale indique que vous détenez déjà un Livret A dans un autre établissement. Votre Livret A n'a pas été ouvert.";

	static DemandeLivretAVue de(Suivi suivi) {
		DemandeLivretA demande = suivi.demande();
		boolean retard = demande.getConseillerAlerteLe() != null && demande.estEnCours();
		return new DemandeLivretAVue(demande.getReference(), demande.getStatut(), message(demande, retard),
				demande.getAttestationLe(), demande.isPartageDetailAutorise(), demande.getChoixPartageLe(),
				demande.getInterrogationLe(), demande.getReponseFiscale(), retard, DELAI, suivi.transmission());
	}

	private static String message(DemandeLivretA demande, boolean retard) {
		return switch (demande.getStatut()) {
			case OUVERTURE_VALIDEE -> VALIDEE;
			case LIVRET_A_EXISTANT -> EXISTANT;
			case EN_COURS_DE_VERIFICATION -> {
				if (retard) {
					yield RETARD;
				}
				yield demande.getReponseFiscale() == ReponseFiscale.INDISPONIBLE ? INDISPONIBLE : EN_COURS;
			}
		};
	}

}
