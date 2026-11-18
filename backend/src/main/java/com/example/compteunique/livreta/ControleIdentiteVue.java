package com.example.compteunique.livreta;

import com.example.compteunique.livreta.IdentiteLivretAService.IdentiteAControler;
import com.example.compteunique.livreta.IdentiteLivretAService.Suivi;

/**
 * CU-28 : contrôle d'identité vu par la cliente. utilisable : validé et pas encore utilisé par une demande.
 * aControler : identité à envoyer au contrôle sanctions/PPE, tant que le résultat est attendu. Critère 8 : en cas
 * de gel des avoirs, le message ne donne pas la raison.
 */
public record ControleIdentiteVue(String reference, StatutControleIdentite statut, String message,
		boolean utilisable, IdentiteAControler aControler) {

	static final String EN_COURS = "Vérification de votre identité en cours.";
	static final String VALIDE = "Votre identité est confirmée. Étape suivante : l'éligibilité.";
	static final String BLOQUE = "Votre demande ne peut pas aboutir en ligne. Un conseiller reviendra vers vous.";
	static final String ATTENTE_RESPONSABLE = "Votre demande est en cours d'examen par nos services. Elle reprendra dès sa validation.";
	static final String EN_ATTENTE = "La vérification de votre identité n'a pas pu aboutir pour le moment. Votre demande est en attente et un conseiller a été alerté.";

	static ControleIdentiteVue de(Suivi suivi) {
		ControleIdentiteLivretA controle = suivi.controle();
		return new ControleIdentiteVue(controle.getReference(), controle.getStatut(), message(controle.getStatut()),
				controle.permetUneDemande(), suivi.aControler());
	}

	static String message(StatutControleIdentite statut) {
		return switch (statut) {
			case EN_COURS -> EN_COURS;
			case VALIDE -> VALIDE;
			case BLOQUE -> BLOQUE;
			case ATTENTE_RESPONSABLE -> ATTENTE_RESPONSABLE;
			case EN_ATTENTE -> EN_ATTENTE;
		};
	}

}
