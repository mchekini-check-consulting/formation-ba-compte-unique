package com.example.compteunique.catalogue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.JournalBatch.Declenchement;
import com.example.compteunique.catalogue.LecteurCatalogue.FichierIllisible;
import com.example.compteunique.catalogue.LecteurCatalogue.Lecture;
import com.example.compteunique.catalogue.LecteurCatalogue.LigneCatalogue;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.evenements.ConfigurationKafka.Topic;
import com.example.compteunique.evenements.Evenements;
import com.example.compteunique.logging.Loggable;

/**
 * CU-381 (TECH-13) : chargement du catalogue des unités de compte et des valeurs liquidatives, pour la date métier
 * (TECH-12). Fichier absent ou illisible : rien n'est modifié, le journal passe en échec et l'équipe est alertée par
 * un log d'erreur (Grafana « Logs métier »). Sinon, les lignes valides sont chargées, les autres rejetées avec leur
 * motif, et un seul événement « catalogue mis à jour » est enregistré.
 */
@Service
@Loggable
public class ChargementCatalogue {

	private static final Logger log = LoggerFactory.getLogger(ChargementCatalogue.class);

	private static final Set<DayOfWeek> WEEK_END = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	static final Pattern NOM_FICHIER = Pattern.compile("catalogue_uc_(\\d{4}-\\d{2}-\\d{2})\\.csv");

	/** Taille maximale d'un fichier déposé (le fichier de test fait 15 ko). */
	static final int TAILLE_MAX = 5_000_000;

	private final SupportRepository supports;
	private final HistoriqueVlRepository historique;
	private final FichierDeposeRepository fichiers;
	private final JournalBatchRepository journaux;
	private final EvenementCatalogueRepository evenements;
	private final DateMetier dateMetier;
	private final Evenements kafka;

	public ChargementCatalogue(SupportRepository supports, HistoriqueVlRepository historique,
			FichierDeposeRepository fichiers, JournalBatchRepository journaux, EvenementCatalogueRepository evenements,
			DateMetier dateMetier, Evenements kafka) {
		this.supports = supports;
		this.historique = historique;
		this.fichiers = fichiers;
		this.journaux = journaux;
		this.evenements = evenements;
		this.dateMetier = dateMetier;
		this.kafka = kafka;
	}

	/** Jours ouvrés : du lundi au vendredi (pas de calendrier des jours fériés dans le prototype). */
	static boolean estOuvre(LocalDate jour) {
		return !WEEK_END.contains(jour.getDayOfWeek());
	}

	/** Vrai si le batch automatique a déjà tourné (terminé ou en échec) pour ce jour métier. */
	@Transactional(readOnly = true)
	public boolean dejaLance(LocalDate jour) {
		return journaux.existsByDateMetierAndDeclenchement(jour, Declenchement.AUTOMATIQUE);
	}

	/**
	 * Exécute le batch pour la date métier du jour. Automatique : une seule fois par jour métier (rien si déjà fait,
	 * résultat null). Manuel (page de test) : autant de fois que voulu ; rejouer le même fichier ne change rien.
	 */
	@Transactional
	public JournalBatch lancer(Declenchement declenchement) {
		journaux.verrouiller();
		OffsetDateTime debut = dateMetier.maintenant().toOffsetDateTime();
		LocalDate jour = debut.toLocalDate();
		if (!estOuvre(jour)) {
			throw new JourNonOuvre();
		}
		if (declenchement == Declenchement.AUTOMATIQUE && dejaLance(jour)) {
			return null;
		}
		String nomAttendu = "catalogue_uc_" + jour + ".csv";
		JournalBatch journal = new JournalBatch(jour, declenchement, nomAttendu, debut);
		var fichier = fichiers.findFirstByDateFichierOrderByIdDesc(jour);
		if (fichier.isEmpty()) {
			return echec(journal, "Fichier absent : " + nomAttendu);
		}
		Lecture lecture;
		try {
			lecture = LecteurCatalogue.lire(fichier.get().getContenu(), jour);
		} catch (FichierIllisible illisible) {
			return echec(journal, "Fichier illisible : " + illisible.getMessage());
		}
		return charger(journal, lecture, debut);
	}

	private JournalBatch charger(JournalBatch journal, Lecture lecture, OffsetDateTime le) {
		Map<String, Support> existants = supports
				.findByCodeIsinIn(lecture.lignes().stream().map(LigneCatalogue::codeIsin).toList())
				.stream()
				.collect(Collectors.toMap(Support::getCodeIsin, Function.identity()));
		int ajoutes = 0;
		int modifies = 0;
		int retires = 0;
		for (LigneCatalogue ligne : lecture.lignes()) {
			Support support = existants.get(ligne.codeIsin());
			if (support == null) {
				support = supports.save(new Support(ligne, le));
				ajoutes++;
			} else {
				boolean etaitActif = support.estActif();
				if (support.mettreAJour(ligne, le)) {
					if (etaitActif && !support.estActif()) {
						retires++;
					} else {
						modifies++;
					}
				}
			}
			if (!historique.existsBySupportAndDateVl(support, ligne.dateVl())) {
				historique.save(new HistoriqueVl(support, ligne.valeurLiquidative(), ligne.dateVl(),
						journal.getNomFichier()));
			}
		}
		List<LigneRejetee> rejets = lecture.rejets().stream()
				.map(rejet -> new LigneRejetee(rejet.numeroLigne(), rejet.codeIsin(), rejet.motif()))
				.toList();
		journal.terminer(ajoutes, modifies, retires, rejets, dateMetier.maintenant().toOffsetDateTime());
		JournalBatch enregistre = journaux.save(journal);
		evenements.save(new EvenementCatalogue(enregistre));
		// CU-382 : publié dans le topic catalogue, clé = date métier (événement de fin de traitement).
		kafka.enregistrer(Topic.CATALOGUE, "CatalogueMisAJour", enregistre.getDateMetier().toString(),
				Map.of("ajoutes", enregistre.getAjoutes(), "modifies", enregistre.getModifies(), "retires",
						enregistre.getRetires(), "rejetees", enregistre.getRejetees()));
		return enregistre;
	}

	private JournalBatch echec(JournalBatch journal, String message) {
		journal.echouer(message, dateMetier.maintenant().toOffsetDateTime());
		MDC.put("layer", "service");
		try {
			// Alerte de l'équipe (Grafana « Logs métier ») : texte fixe, jamais de donnée.
			log.error("chargement du catalogue en echec : catalogue conserve, ordres sans VL du jour reportes");
		} finally {
			MDC.remove("layer");
		}
		return journaux.save(journal);
	}

	/** Dépôt d'un fichier par la page de test ; sa date est lue dans son nom (catalogue_uc_AAAA-MM-JJ.csv). */
	@Transactional
	public FichierDepose deposer(String nomFichier, String contenu) {
		String nom = nomFichier == null ? "" : nomFichier.trim();
		Matcher correspondance = NOM_FICHIER.matcher(nom);
		if (!correspondance.matches()) {
			throw new DepotInvalide("Le fichier doit s'appeler catalogue_uc_AAAA-MM-JJ.csv.");
		}
		LocalDate date;
		try {
			date = LocalDate.parse(correspondance.group(1));
		} catch (DateTimeParseException e) {
			throw new DepotInvalide("La date du nom de fichier n'existe pas.");
		}
		if (contenu == null || contenu.isBlank()) {
			throw new DepotInvalide("Le fichier est vide.");
		}
		if (contenu.length() > TAILLE_MAX) {
			throw new DepotInvalide("Le fichier est trop volumineux (5 Mo au plus).");
		}
		return fichiers.save(new FichierDepose(nom, date, contenu, dateMetier.maintenant().toOffsetDateTime()));
	}

	@Transactional(readOnly = true)
	public List<FichierDepose> fichiersDeposes() {
		return fichiers.findTop20ByOrderByIdDesc();
	}

	@Transactional(readOnly = true)
	public List<JournalBatch> journal() {
		return journaux.findTop20ByOrderByIdDesc();
	}

	/** Lancement demandé un samedi ou un dimanche (date métier) : le batch ne s'exécute pas. */
	public static class JourNonOuvre extends RuntimeException {

		JourNonOuvre() {
			super("La date métier n'est pas un jour ouvré : le batch ne s'exécute pas.");
		}

	}

	/** Fichier déposé mal nommé, vide ou trop volumineux. */
	public static class DepotInvalide extends RuntimeException {

		DepotInvalide(String message) {
			super(message);
		}

	}

}
