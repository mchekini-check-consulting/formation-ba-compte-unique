package com.example.compteunique.virement;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.MontantInvalideException;
import com.example.compteunique.compte.Mouvement;
import com.example.compteunique.compte.MouvementRepository;
import com.example.compteunique.logging.LogMetier;
import com.example.compteunique.logging.Loggable;

// CU-11 (US-06) : réception d'un virement instantané. Seul chemin d'entrée d'un virement, utilisé par le
// bouchon de test (CU-143) : compte actif -> solde augmenté et mouvement daté du jour ; compte clôturé ->
// virement renvoyé à l'émetteur (AC04), solde inchangé. Jamais d'IBAN, d'émetteur ni de libellé dans les logs.
@Service
@Loggable
public class ReceptionVirement {

	/** Résultat d'un virement reçu : crédité sur le compte, ou renvoyé à l'émetteur avec un motif SEPA. */
	public enum Resultat {
		CREDITE, RENVOYE
	}

	private final CompteRepository comptes;
	private final MouvementRepository mouvements;
	private final RetourVirementRepository retours;

	public ReceptionVirement(CompteRepository comptes, MouvementRepository mouvements,
			RetourVirementRepository retours) {
		this.comptes = comptes;
		this.mouvements = mouvements;
		this.retours = retours;
	}

	@Transactional
	@LogMetier(champs = { "virement.simule", "resultat" }, message = "virement entrant reçu",
			messageEchec = "virement entrant refusé")
	public Resultat recevoir(VirementEntrant virement) {
		if (virement.montantCentimes() <= 0) {
			throw new MontantInvalideException();
		}
		if (!virement.aDesInformationsCompletes()) {
			throw new InformationsVirementIncompletesException();
		}
		Compte compte = comptes.verrouillerParIban(virement.iban()).orElseThrow(CompteIntrouvableException::new);
		OffsetDateTime maintenant = OffsetDateTime.now();
		if (!compte.isActif()) {
			retours.save(new RetourVirement(compte.getId(), virement, RetourVirement.COMPTE_CLOTURE, maintenant));
			return Resultat.RENVOYE;
		}
		compte.crediter(virement.montantCentimes(), maintenant);
		mouvements.save(new Mouvement(compte.getId(), virement.montantCentimes(), virement.libelle(),
				virement.emetteur(), virement.simule(), maintenant));
		return Resultat.CREDITE;
	}

}
