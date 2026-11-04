package com.example.compteunique.ouverture;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.GenerateurIban;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionService;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;

// CU-13 : compte (IBAN de CU-88, solde 0 €, customer_id, ACTIF) et identifiant de connexion
// (8 chiffres, EN_ATTENTE_ACTIVATION, sans mot de passe) enregistrés dans une seule
// transaction : une erreur annule tout, sans compte, identifiant ni IBAN partiel.
// REQUIRES_NEW : appelé aussi juste après la validation de la signature.
@Service
@Loggable
public class CreationCompte {

	private final DossierRepository dossiers;
	private final CompteRepository comptes;
	private final GenerateurIban generateurIban;
	private final IdentifiantConnexionService identifiants;
	private final Clock horloge;

	@Autowired
	public CreationCompte(DossierRepository dossiers, CompteRepository comptes, GenerateurIban generateurIban,
			IdentifiantConnexionService identifiants) {
		this(dossiers, comptes, generateurIban, identifiants, Clock.systemDefaultZone());
	}

	CreationCompte(DossierRepository dossiers, CompteRepository comptes, GenerateurIban generateurIban,
			IdentifiantConnexionService identifiants, Clock horloge) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.generateurIban = generateurIban;
		this.identifiants = identifiants;
		this.horloge = horloge;
	}

	/** Renvoie false si le compte existait déjà (création relancée : sans doublon). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean creer(Long dossierId) {
		Dossier dossier = dossiers.findById(dossierId).orElseThrow();
		if (comptes.findByDossierId(dossierId).isPresent()) {
			return false;
		}
		if (dossier.getStatut() != StatutDossier.CONVENTION_SIGNEE) {
			throw new IllegalStateException("convention non signée");
		}
		OffsetDateTime maintenant = OffsetDateTime.now(horloge);
		IdentifiantConnexion identifiant = identifiants.creer();
		comptes.save(new Compte(dossier.getId(), dossier.getCustomerId(), generateurIban.generer(),
				identifiant.getId(), maintenant));
		dossier.compteOuvert();
		return true;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void relancer(Long dossierId) {
		dossiers.findById(dossierId).orElseThrow().relancerCreation(OffsetDateTime.now(horloge));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void echec(Long dossierId, boolean definitif) {
		dossiers.findById(dossierId).orElseThrow().creationEchouee(OffsetDateTime.now(horloge), definitif);
	}

}
