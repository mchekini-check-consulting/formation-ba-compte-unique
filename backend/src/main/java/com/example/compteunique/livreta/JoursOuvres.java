package com.example.compteunique.livreta;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;

// CU-31 : délai de réponse de l'administration fiscale en jours ouvrés (du lundi au vendredi).
// Les jours fériés ne sont pas pris en compte dans le prototype.
final class JoursOuvres {

	private static final Set<DayOfWeek> WEEK_END = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private JoursOuvres() {
	}

	static OffsetDateTime ajouter(OffsetDateTime depart, int jours) {
		OffsetDateTime date = depart;
		int restants = jours;
		while (restants > 0) {
			date = date.plusDays(1);
			if (!WEEK_END.contains(date.getDayOfWeek())) {
				restants--;
			}
		}
		return date;
	}

}
