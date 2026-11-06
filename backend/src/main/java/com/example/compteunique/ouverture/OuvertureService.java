package com.example.compteunique.ouverture;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.connexion.IdentifiantIndisponibleException;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.signature.ConventionSigneeEvent;

// CU-13 (US-08) : création du compte dès que la convention est signée, par le système, sans
// action de la cliente ; puis envoi de l'identifiant (e-mail) et du lien d'activation (SMS).
// Une création en échec est relancée automatiquement (RelancesAutomatiques) jusqu'à 3 fois, à
// 1 minute d'intervalle, puis le dossier passe à « Création à relancer ».
@Service
@Loggable
public class OuvertureService {

	/** Délai minimal entre deux renvois demandés par la cliente. */
	static final Duration DELAI_RENVOI = Duration.ofSeconds(60);

	private static final Logger log = LoggerFactory.getLogger(OuvertureService.class);

	private final DossierRepository dossiers;
	private final CompteRepository comptes;
	private final EnvoiMessageRepository envois;
	private final CreationCompte creation;
	private final EnvoisIdentifiants envoisIdentifiants;
	private final Clock horloge;

	@Autowired
	public OuvertureService(DossierRepository dossiers, CompteRepository comptes, EnvoiMessageRepository envois,
			CreationCompte creation, EnvoisIdentifiants envoisIdentifiants) {
		this(dossiers, comptes, envois, creation, envoisIdentifiants, Clock.systemDefaultZone());
	}

	OuvertureService(DossierRepository dossiers, CompteRepository comptes, EnvoiMessageRepository envois,
			CreationCompte creation, EnvoisIdentifiants envoisIdentifiants, Clock horloge) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.envois = envois;
		this.creation = creation;
		this.envoisIdentifiants = envoisIdentifiants;
		this.horloge = horloge;
	}

	/** Déclenché une fois la signature enregistrée (CU-12). */
	@TransactionalEventListener
	public void conventionSignee(ConventionSigneeEvent evenement) {
		ouvrir(evenement.dossierId());
	}

	/** Crée le compte puis envoie identifiant et lien ; une erreur est enregistrée pour être relancée. */
	public void ouvrir(Long dossierId) {
		try {
			if (creation.creer(dossierId)) {
				envoisIdentifiants.envoyerTout(dossierId);
			}
		} catch (IdentifiantIndisponibleException e) {
			log.warn("Création du compte en échec : identifiants indisponibles");
			creation.echec(dossierId, true);
		} catch (RuntimeException e) {
			log.warn("Création du compte en échec : {}", e.getClass().getSimpleName());
			creation.echec(dossierId, false);
		}
	}

	/** Relance manuelle d'un dossier « Création à relancer », avec le même customer_id. */
	public OuvertureReponse relancer(String reference) {
		Dossier dossier = dossier(reference);
		if (dossier.getStatut() != StatutDossier.CREATION_A_RELANCER) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "création non à relancer");
		}
		creation.relancer(dossier.getId());
		ouvrir(dossier.getId());
		return etat(reference);
	}

	/** Bouton « Renvoyer mon identifiant » (EMAIL) ou « Renvoyer mon lien » (SMS). */
	public OuvertureReponse renvoyer(String reference, Canal canal) {
		Dossier dossier = dossier(reference);
		EnvoiMessage envoi = envois.findByDossierIdAndCanal(dossier.getId(), canal)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "aucun envoi"));
		if (envoi.getDernierEssaiLe().plus(DELAI_RENVOI).isAfter(OffsetDateTime.now(horloge))) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "renvoi possible dans 60 secondes");
		}
		envoisIdentifiants.renvoyer(dossier.getId(), canal);
		return etat(reference);
	}

	/** Écran de confirmation : statut, IBAN masqué, adresse et numéro masqués, résultat des envois. */
	public OuvertureReponse etat(String reference) {
		Dossier dossier = dossier(reference);
		return new OuvertureReponse(dossier.getStatut(),
				comptes.findByDossierId(dossier.getId()).map(Compte::getIbanMasque).orElse(null),
				Masquage.email(dossier.getEmail()), Masquage.mobile(dossier.getMobile()),
				resultat(dossier.getId(), Canal.EMAIL), resultat(dossier.getId(), Canal.SMS));
	}

	private ResultatEnvoi resultat(Long dossierId, Canal canal) {
		return envois.findByDossierIdAndCanal(dossierId, canal).map(EnvoiMessage::getResultat).orElse(null);
	}

	private Dossier dossier(String reference) {
		return dossiers.findByReference(reference)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dossier inconnu"));
	}

}
