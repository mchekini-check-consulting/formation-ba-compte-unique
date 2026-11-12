package com.example.compteunique.livreta;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;

// CU-31 : délai de réponse de l'administration fiscale en jours ouvrés (du lundi au vendredi).
// Les jours fériés ne sont pas pris en compte dans le prototype.
final class JoursOuvres {

	private JoursOuvres() {
	}

	static OffsetDateTime ajouter(OffsetDateTime depart, int jours) {
		OffsetDateTime date = depart;
		int restants = jours;
		while (restants > 0) {
			date = date.plusDays(1);
			if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
				restants--;
			}
		}
		return date;
	}

}
