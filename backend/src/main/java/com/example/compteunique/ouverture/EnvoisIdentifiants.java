package com.example.compteunique.ouverture;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.JetonService;
import com.example.compteunique.connexion.TypeJeton;
import com.example.compteunique.dossier.Dossier;
import com.example.compteunique.dossier.DossierRepository;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.messagerie.Canal;
import com.example.compteunique.messagerie.Messagerie;

// CU-13 : envoi de l'identifiant par e-mail et du lien d'activation par SMS. Les deux envois
// sont indépendants (chacun dans sa transaction) : l'échec de l'un n'empêche pas l'autre.
// Aucun des deux messages ne contient de donnée d'identité ; l'e-mail n'a pas de lien et le
// SMS ne contient pas l'identifiant.
@Service
@Loggable
public class EnvoisIdentifiants {

	static final String OBJET_EMAIL = "Votre identifiant de connexion Hi-Tech Bank";

	private static final Logger log = LoggerFactory.getLogger(EnvoisIdentifiants.class);

	private final DossierRepository dossiers;
	private final CompteRepository comptes;
	private final IdentifiantConnexionRepository identifiants;
	private final JetonService jetons;
	private final Messagerie messagerie;
	private final EnvoiMessageRepository envois;
	private final TransactionTemplate transaction;
	private final Clock horloge;

	@Autowired
	public EnvoisIdentifiants(DossierRepository dossiers, CompteRepository comptes,
			IdentifiantConnexionRepository identifiants, JetonService jetons, Messagerie messagerie,
			EnvoiMessageRepository envois, PlatformTransactionManager transactions) {
		this(dossiers, comptes, identifiants, jetons, messagerie, envois, transactions, Clock.systemDefaultZone());
	}

	@SuppressWarnings("PMD.ExcessiveParameterList")
	EnvoisIdentifiants(DossierRepository dossiers, CompteRepository comptes,
			IdentifiantConnexionRepository identifiants, JetonService jetons, Messagerie messagerie,
			EnvoiMessageRepository envois, PlatformTransactionManager transactions, Clock horloge) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.identifiants = identifiants;
		this.jetons = jetons;
		this.messagerie = messagerie;
		this.envois = envois;
		this.transaction = new TransactionTemplate(transactions);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.horloge = horloge;
	}

	public void envoyerTout(Long dossierId) {
		envoyer(dossierId, Canal.EMAIL);
		envoyer(dossierId, Canal.SMS);
	}

	/** Un envoi (ou une relance) ; renvoie son résultat. Pour le SMS, un nouveau lien remplace l'ancien. */
	public ResultatEnvoi envoyer(Long dossierId, Canal canal) {
		return transaction.execute(statut -> {
			EnvoiMessage envoi = envois.findByDossierIdAndCanal(dossierId, canal)
					.orElseGet(() -> envois.save(new EnvoiMessage(dossierId, canal)));
			envoi.tentative(remettre(dossierId, canal), OffsetDateTime.now(horloge));
			return envoi.getResultat();
		});
	}

	/** Renvoi demandé par la cliente (bouton « Renvoyer ») ou nouveau lien demandé (CU-14). */
	public ResultatEnvoi renvoyer(Long dossierId, Canal canal) {
		transaction.executeWithoutResult(statut -> envois.findByDossierIdAndCanal(dossierId, canal)
				.ifPresent(EnvoiMessage::reinitialiser));
		return envoyer(dossierId, canal);
	}

	private boolean remettre(Long dossierId, Canal canal) {
		try {
			Dossier dossier = dossiers.findById(dossierId).orElseThrow();
			Compte compte = comptes.findByDossierId(dossierId).orElseThrow();
			IdentifiantConnexion identifiant = identifiants.findById(compte.getIdentifiantConnexionId()).orElseThrow();
			if (canal == Canal.EMAIL) {
				return messagerie.envoyerEmail(dossier.getEmail(), OBJET_EMAIL, texteEmail(identifiant.getIdentifiant()));
			}
			String lien = jetons.lien(TypeJeton.ACTIVATION, jetons.generer(identifiant.getId(), TypeJeton.ACTIVATION));
			return messagerie.envoyerSms(dossier.getMobile(), texteSms(lien));
		} catch (RuntimeException e) {
			// Ni destinataire ni contenu dans le log.
			log.warn("Envoi {} non remis : {}", canal, e.getClass().getSimpleName());
			return false;
		}
	}

	static String texteEmail(String identifiant) {
		return """
				Bonjour,

				Votre compte courant Hi-Tech Bank est ouvert.

				Votre identifiant de connexion est : %s

				Saisissez-le dans le champ « Identifiant » de la page « Connexion à votre espace », \
				avec le mot de passe que vous allez définir grâce au lien d'activation reçu par SMS.

				Hi-Tech Bank""".formatted(identifiant);
	}

	static String texteSms(String lien) {
		return "Hi-Tech Bank : activez votre espace en définissant votre mot de passe : " + lien
				+ " (lien valable 24 h, à usage unique).";
	}

}
