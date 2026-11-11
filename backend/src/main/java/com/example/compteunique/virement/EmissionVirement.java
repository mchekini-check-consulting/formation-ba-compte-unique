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

// CU-144 (TECH-05) : émission d'un virement sortant, utilisée par le bouchon de test. Contrôles dans l'ordre :
// montant > 0, IBAN du bénéficiaire, bénéficiaire et libellé, compte existant, compte actif (non clôturé), solde
// suffisant. Puis, dans la même transaction (compte verrouillé) : solde diminué et mouvement daté du jour.
// Jamais d'IBAN, de bénéficiaire ni de libellé dans les logs.
@Service
@Loggable
public class EmissionVirement {

	private final CompteRepository comptes;
	private final MouvementRepository mouvements;

	public EmissionVirement(CompteRepository comptes, MouvementRepository mouvements) {
		this.comptes = comptes;
		this.mouvements = mouvements;
	}

	/** Renvoie le nouveau solde du compte débité, en centimes. */
	@Transactional
	@LogMetier(champs = { "virement.simule" }, message = "virement sortant émis",
			messageEchec = "virement sortant refusé")
	public long emettre(VirementSortant virement) {
		if (virement.montantCentimes() <= 0) {
			throw new MontantInvalideException();
		}
		if (!IbanBeneficiaire.estValide(virement.ibanBeneficiaire())) {
			throw new IbanInvalideException();
		}
		if (!virement.aDesInformationsCompletes()) {
			throw new InformationsVirementIncompletesException();
		}
		Compte compte = comptes.verrouillerParIban(virement.iban()).orElseThrow(CompteIntrouvableException::new);
		OffsetDateTime maintenant = OffsetDateTime.now();
		compte.debiter(virement.montantCentimes(), maintenant);
		mouvements.save(Mouvement.virementSortant(compte.getId(), virement.montantCentimes(), virement.libelle(),
				virement.beneficiaire(), virement.ibanBeneficiaire(), virement.simule(), maintenant));
		return compte.getSoldeCentimes();
	}

}
