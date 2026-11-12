package com.example.bouchonsidentite.fiscal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.bouchonsidentite.commun.Textes;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.DetailLivretA;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.Interrogation;
import com.example.bouchonsidentite.fiscal.FiscalRequetes.ReponseFiscale;
import com.example.bouchonsidentite.logging.Loggable;

// CU-295 : bouchon de l'administration fiscale (fichier FICOBA) pour le contrôle de multidétention du
// Livret A (CU-31). Aucun appel réel. Le cas de test choisi dans le panneau de test donne la réponse :
//   SANS_LIVRET_A   -> « aucun autre Livret A » (critère 1)
//   DETENU_AILLEURS -> « Livret A existant », avec un détail fictif si la cliente l'autorise (critères 2 et 3)
//   SANS_REPONSE    -> pas de réponse : la demande reste en attente (critère 4)
//   INDISPONIBLE    -> service indisponible (critère 6)
// Chaque appel est tracé : date, référence de la demande, résultat ; jamais l'identité (critère 9).
@Service
@Loggable
public class AdministrationFiscaleService {

	static final DetailLivretA DETAIL_FICTIF = new DetailLivretA("Banque Exemple", "Agence Paris Centre",
			LocalDate.of(2015, 3, 2));
	static final Set<String> SEXES = Set.of("F", "M");

	private final InterrogationFiscaleRepository interrogations;
	private final Clock horloge;

	public AdministrationFiscaleService(InterrogationFiscaleRepository interrogations, Clock horloge) {
		this.interrogations = interrogations;
		this.horloge = horloge;
	}

	public ReponseFiscale interroger(Interrogation demande) {
		if (demande == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "demande obligatoire");
		}
		String demandeId = Textes.obligatoire(demande.demandeId(), "demandeId");
		Textes.obligatoire(demande.nom(), "nom");
		Textes.obligatoire(demande.prenoms(), "prenoms");
		Textes.obligatoire(demande.lieuNaissance(), "lieuNaissance");
		String sexe = Textes.obligatoire(demande.sexe(), "sexe").toUpperCase(Locale.ROOT);
		if (!SEXES.contains(sexe)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sexe : F ou M");
		}
		if (demande.dateNaissance() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateNaissance obligatoire");
		}
		CasFiscal cas = demande.casDeTest() == null ? CasFiscal.SANS_LIVRET_A : demande.casDeTest();
		return switch (cas) {
			case SANS_LIVRET_A -> tracer(demandeId, cas, ResultatFiscal.AUCUN_LIVRET_A, null,
					"Aucun autre Livret A");
			case DETENU_AILLEURS -> tracer(demandeId, cas, ResultatFiscal.LIVRET_A_EXISTANT,
					demande.partageDetailAutorise() ? DETAIL_FICTIF : null, "Livret A existant");
			case SANS_REPONSE -> tracer(demandeId, cas, ResultatFiscal.SANS_REPONSE, null,
					"Pas encore de réponse de l'administration fiscale");
			case INDISPONIBLE -> tracer(demandeId, cas, ResultatFiscal.INDISPONIBLE, null,
					"Service de l'administration fiscale indisponible");
		};
	}

	private ReponseFiscale tracer(String demandeId, CasFiscal cas, ResultatFiscal resultat, DetailLivretA detail,
			String message) {
		InterrogationFiscale trace = interrogations.save(new InterrogationFiscale(UUID.randomUUID().toString(),
				demandeId, cas, resultat, detail != null, OffsetDateTime.now(horloge)));
		return new ReponseFiscale(trace.getInterrogationId(), resultat, detail, message, trace.getAppeleLe());
	}

}
