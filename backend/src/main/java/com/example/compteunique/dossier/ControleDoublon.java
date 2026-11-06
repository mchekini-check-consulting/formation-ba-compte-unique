package com.example.compteunique.dossier;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.compteunique.compte.Compte;
import com.example.compteunique.compte.CompteRepository;
import com.example.compteunique.compte.EvenementSecurite;
import com.example.compteunique.connexion.IdentifiantConnexion;
import com.example.compteunique.connexion.IdentifiantConnexionRepository;
import com.example.compteunique.connexion.StatutIdentifiant;
import com.example.compteunique.dossier.Categories.StatutDossier;
import com.example.compteunique.dossier.OuvertureRefuseeException.EtapeReprise;
import com.example.compteunique.dossier.OuvertureRefuseeException.Refus;
import com.example.compteunique.logging.Loggable;
import com.example.compteunique.securite.JournalSecurite;

// CU-9 (US-04) : une personne (même pièce OU même identité) n'a qu'un seul profil client et qu'un
// seul dossier d'ouverture en cours, et une ouverture annulée par contrôle ne recommence pas en
// ligne. Priorité : profil existant, puis dossier annulé par contrôle, puis dossier en cours.
// Ne s'applique qu'à l'ouverture de compte : la souscription d'un produit par un client existant
// ne passe pas par ici (critère 13). Rien n'est modifié sur le profil ou le dossier existant.
@Service
@Loggable
public class ControleDoublon {

	/** Dossier en cours : la personne doit le reprendre. */
	static final Set<StatutDossier> EN_COURS = Set.of(StatutDossier.EN_COURS_DE_VERIFICATION, StatutDossier.ACCEPTE,
			StatutDossier.CONVENTION_SIGNEE, StatutDossier.CREATION_A_RELANCER);

	private final DossierRepository dossiers;
	private final CompteRepository comptes;
	private final IdentifiantConnexionRepository identifiants;
	private final JournalSecurite journal;

	public ControleDoublon(DossierRepository dossiers, CompteRepository comptes,
			IdentifiantConnexionRepository identifiants, JournalSecurite journal) {
		this.dossiers = dossiers;
		this.comptes = comptes;
		this.identifiants = identifiants;
		this.journal = journal;
	}

	/**
	 * Refuse l'ouverture (OuvertureRefuseeException) si la personne est déjà connue ; l'événement
	 * est journalisé sans numéro de pièce ni identité (critère 15).
	 */
	public void verifier(String reference, IdentiteRequete identite, OffsetDateTime maintenant) {
		List<Dossier> memePersonne = dossiers.memePersonne(reference, identite.typePiece().name(),
				normaliser(identite.numeroPiece()), normaliser(identite.nomNaissance()), normaliser(identite.prenoms()),
				identite.dateNaissance(), normaliser(identite.communeNaissance()), identite.paysNaissance().name());
		Optional<Refus> profil = refusProfil(memePersonne);
		if (profil.isPresent()) {
			refuser(new OuvertureRefuseeException(profil.get()), maintenant);
		}
		if (memePersonne.stream().anyMatch(ControleDoublon::annuleParControle)) {
			refuser(new OuvertureRefuseeException(Refus.OUVERTURE_ANNULEE), maintenant);
		}
		Optional<Dossier> enCours = memePersonne.stream()
				.filter(dossier -> EN_COURS.contains(dossier.getStatut()))
				.max(Comparator.comparing(Dossier::getDerniereActionLe));
		if (enCours.isPresent()) {
			Dossier existant = enCours.get();
			EtapeReprise etape = existant.getStatut() == StatutDossier.EN_COURS_DE_VERIFICATION
					? EtapeReprise.IDENTITE
					: EtapeReprise.OUVERTURE;
			journal.enregistrer(EvenementSecurite.dossier(EvenementSecurite.REPRISE_DOSSIER,
					Refus.OUVERTURE_EN_COURS.name(), maintenant));
			throw new OuvertureRefuseeException(Refus.OUVERTURE_EN_COURS, existant.getReference(), etape);
		}
	}

	private void refuser(OuvertureRefuseeException refus, OffsetDateTime maintenant) {
		journal.enregistrer(EvenementSecurite.dossier(EvenementSecurite.DOUBLON_DETECTE, refus.getRefus().name(),
				maintenant));
		throw refus;
	}

	/**
	 * Critères 1 à 3 : profil client existant = compte créé, statut ACTIF ; message selon que
	 * l'identifiant est activé ou non. Un identifiant activé l'emporte.
	 */
	private Optional<Refus> refusProfil(List<Dossier> memePersonne) {
		List<Long> identifiantsDesProfils = memePersonne.stream()
				.filter(dossier -> dossier.getStatut() == StatutDossier.COMPTE_OUVERT)
				.flatMap(dossier -> comptes.findByDossierId(dossier.getId()).stream())
				.filter(compte -> Compte.ACTIF.equals(compte.getStatut()))
				.map(Compte::getIdentifiantConnexionId)
				.toList();
		if (identifiantsDesProfils.isEmpty()) {
			return Optional.empty();
		}
		boolean active = identifiantsDesProfils.stream()
				.flatMap(id -> identifiants.findById(id).stream())
				.map(IdentifiantConnexion::getStatut)
				.anyMatch(StatutIdentifiant.ACTIF::equals);
		return Optional.of(active ? Refus.ESPACE_EXISTANT : Refus.ACTIVATION_ATTENDUE);
	}

	private static boolean annuleParControle(Dossier dossier) {
		return dossier.getStatut() == StatutDossier.ANNULE && dossier.getMotifAnnulation() != null
				&& dossier.getMotifAnnulation().estBloquant();
	}

	/** Majuscules, espaces et tirets ignorés (« Marie-Claire » = « marie claire »). */
	static String normaliser(String valeur) {
		return valeur.toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
	}

}
