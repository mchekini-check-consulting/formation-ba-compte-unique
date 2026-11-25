package com.example.compteunique.profil;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.logging.Loggable;

// Registre des consentements créé par CU-10 (profil_client.consentement), ouvert aux autres parcours : CU-32
// (texte d'information du Livret A) y trace l'acceptation de chaque version du texte. Une ligne par choix,
// jamais modifiée.
@Service
@Loggable
public class Consentements {

	private final ConsentementRepository consentements;

	public Consentements(ConsentementRepository consentements) {
		this.consentements = consentements;
	}

	/** Acceptation d'une version du texte, avec la date et l'heure. */
	@Transactional
	public void accepter(UUID customerId, String type, String versionTexte, OffsetDateTime le) {
		consentements.save(new Consentement(customerId, type, true, versionTexte, le));
	}

	/** Vrai si la cliente a déjà accepté cette version du texte. */
	@Transactional(readOnly = true)
	public boolean aAccepte(UUID customerId, String type, String versionTexte) {
		return consentements.existsByCustomerIdAndTypeAndVersionTexteAndChoixTrue(customerId, type, versionTexte);
	}

}
