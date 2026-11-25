package com.example.compteunique.datemetier;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.logging.Loggable;

/**
 * CU-380 (TECH-12) : la « date du jour » des modules de placement (ADR-001, section 2, point 6). Ces modules ne
 * lisent jamais l'horloge du serveur : ils appellent {@link #maintenant()} ou {@link #aujourdhui()}.
 * <p>
 * En production (app.test.date-metier-modifiable = false, helm.yaml), c'est l'heure réelle de Paris et rien ne
 * peut la changer. En test, la testeuse la décale ; l'heure continue ensuite d'avancer, et chaque changement est
 * tracé.
 */
@Service
@Loggable
public class DateMetier {

	public static final ZoneId PARIS = ZoneId.of("Europe/Paris");

	static final int AUTEUR_LONGUEUR_MAX = 100;

	private final ReglageDateMetierRepository reglages;
	private final ChangementDateMetierRepository journal;
	private final boolean modifiable;
	private final Clock horlogeReelle;

	@Autowired
	public DateMetier(ReglageDateMetierRepository reglages, ChangementDateMetierRepository journal,
			@Value("${app.test.date-metier-modifiable:false}") boolean modifiable) {
		this(reglages, journal, modifiable, Clock.system(PARIS));
	}

	DateMetier(ReglageDateMetierRepository reglages, ChangementDateMetierRepository journal, boolean modifiable,
			Clock horlogeReelle) {
		this.reglages = reglages;
		this.journal = journal;
		this.modifiable = modifiable;
		this.horlogeReelle = horlogeReelle;
	}

	/** État renvoyé à l'écran de test : date métier, simulée ou non, modifiable ou non dans cet environnement. */
	public record Etat(ZonedDateTime dateMetier, boolean simulee, boolean modifiable) {
	}

	/** Date et heure métier, à l'heure de Paris, à la seconde (CU-413 : 12:00 réglé se lit 12:00:00). */
	@Transactional(readOnly = true)
	public ZonedDateTime maintenant() {
		return reel().plusSeconds(decalageSecondes());
	}

	/**
	 * CU-413 : seul endroit où une heure stockée (relue de la base en UTC, par exemple) est ramenée à l'heure de
	 * Paris, heure d'été comprise.
	 */
	public static OffsetDateTime heureDeParis(OffsetDateTime heure) {
		return heure.atZoneSameInstant(PARIS).toOffsetDateTime();
	}

	/** Jour métier, à Paris. */
	@Transactional(readOnly = true)
	public LocalDate aujourdhui() {
		return maintenant().toLocalDate();
	}

	@Transactional(readOnly = true)
	public Etat etat() {
		long decalage = decalageSecondes();
		return new Etat(reel().plusSeconds(decalage), decalage != 0, modifiable);
	}

	/** CU-380 : la testeuse avance (ou recule) la date métier ; refusé en production. */
	@Transactional
	public Etat changer(LocalDateTime nouvelleDate, String auteur) {
		verifierModifiable();
		if (nouvelleDate == null) {
			throw new ChangementInvalide("La nouvelle date est obligatoire.");
		}
		ZonedDateTime reel = reel();
		return appliquer(reel, nouvelleDate.truncatedTo(ChronoUnit.SECONDS).atZone(PARIS), auteur);
	}

	/** Revient à l'heure réelle de Paris ; refusé en production (où l'on y est toujours). */
	@Transactional
	public Etat revenirALaDateReelle(String auteur) {
		verifierModifiable();
		ZonedDateTime reel = reel();
		return appliquer(reel, reel, auteur);
	}

	/** Les 100 derniers changements, du plus récent au plus ancien ; refusé en production. */
	@Transactional(readOnly = true)
	public List<ChangementDateMetier> journal() {
		verifierModifiable();
		return journal.findTop100ByOrderByIdDesc();
	}

	private Etat appliquer(ZonedDateTime reel, ZonedDateTime nouvelle, String auteur) {
		String nom = auteurValide(auteur);
		ZonedDateTime ancienne = reel.plusSeconds(decalageSecondes());
		long decalage = Duration.between(reel, nouvelle).toSeconds();
		ReglageDateMetier reglage = reglages.findById(ReglageDateMetier.ID_UNIQUE)
				.orElseGet(() -> new ReglageDateMetier(0, reel.toOffsetDateTime()));
		reglage.decaler(decalage, reel.toOffsetDateTime());
		reglages.save(reglage);
		journal.save(new ChangementDateMetier(ancienne.toOffsetDateTime(), reel.plusSeconds(decalage).toOffsetDateTime(),
				nom, reel.toOffsetDateTime()));
		return new Etat(reel.plusSeconds(decalage), decalage != 0, modifiable);
	}

	private void verifierModifiable() {
		if (!modifiable) {
			throw new DateMetierNonModifiable();
		}
	}

	private static String auteurValide(String auteur) {
		String nom = auteur == null ? "" : auteur.trim();
		if (nom.isEmpty()) {
			throw new ChangementInvalide("Indiquez votre nom : chaque changement de date est tracé.");
		}
		if (nom.length() > AUTEUR_LONGUEUR_MAX) {
			throw new ChangementInvalide("Le nom ne doit pas dépasser " + AUTEUR_LONGUEUR_MAX + " caractères.");
		}
		return nom;
	}

	/** En production, jamais de décalage, même si une ligne de réglage traînait en base. */
	private long decalageSecondes() {
		if (!modifiable) {
			return 0;
		}
		return reglages.findById(ReglageDateMetier.ID_UNIQUE).map(ReglageDateMetier::getDecalageSecondes).orElse(0L);
	}

	// CU-413 : heure réelle à la seconde. Avec les fractions, l'écart calculé en secondes entières était arrondi
	// vers le bas : 23:30 saisi se relisait 23:29:59.
	private ZonedDateTime reel() {
		return ZonedDateTime.now(horlogeReelle).withZoneSameInstant(PARIS).truncatedTo(ChronoUnit.SECONDS);
	}

	/** Changement demandé dans un environnement où la date métier est fixe (preprod, prod). */
	public static class DateMetierNonModifiable extends RuntimeException {

		DateMetierNonModifiable() {
			super("La date métier ne peut pas être modifiée dans cet environnement.");
		}

	}

	/** Demande de changement incomplète (date ou auteur manquant). */
	public static class ChangementInvalide extends RuntimeException {

		ChangementInvalide(String message) {
			super(message);
		}

	}

}
