package com.example.compteunique.compte;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.compteunique.connexion.ConnexionService;
import com.example.compteunique.logging.Loggable;

// CU-20 (US-15) : une cliente n'accède qu'aux comptes rattachés à son customer_id.
// Pas de @Transactional sur consulter : l'exception de refus annulerait l'écriture au journal.
@Service
@Loggable
public class CompteService {

	private final ConnexionService connexion;
	private final CompteRepository comptes;
	private final JournalSecuriteRepository journal;

	public CompteService(ConnexionService connexion, CompteRepository comptes, JournalSecuriteRepository journal) {
		this.connexion = connexion;
		this.comptes = comptes;
		this.journal = journal;
	}

	public List<Compte> mesComptes(String jetonSession) {
		return customerId(cliente(jetonSession)).map(comptes::findByCustomerId).orElseGet(List::of);
	}

	public Compte consulter(String jetonSession, String compteDemande) {
		Long cliente = cliente(jetonSession);
		Optional<UUID> customerId = customerId(cliente);
		return customerId
				.flatMap(titulaire -> numero(compteDemande).flatMap(id -> comptes.findByIdAndCustomerId(id, titulaire)))
				.orElseThrow(() -> {
					journal.save(new EvenementSecurite(cliente, customerId.orElse(null), compteDemande,
							OffsetDateTime.now()));
					return new CompteInaccessibleException();
				});
	}

	private Long cliente(String jetonSession) {
		return connexion.idDeLaSession(jetonSession).orElseThrow(ClienteNonConnecteeException::new);
	}

	private Optional<UUID> customerId(Long cliente) {
		return comptes.findByIdentifiantConnexionId(cliente).map(Compte::getCustomerId);
	}

	// Un identifiant qui n'est pas un nombre est traité comme un compte inexistant.
	private static Optional<Long> numero(String compteDemande) {
		return compteDemande.matches("\\d{1,18}") ? Optional.of(Long.valueOf(compteDemande)) : Optional.empty();
	}

}
