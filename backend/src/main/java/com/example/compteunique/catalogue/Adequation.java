package com.example.compteunique.catalogue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.compteunique.catalogue.CatalogueService.Produit;
import com.example.compteunique.catalogue.SupportsEligibles.SupportEligible;
import com.example.compteunique.datemetier.DateMetier;
import com.example.compteunique.logging.Loggable;

/**
 * CU-348 (US-PER-21) et CU-349 (PER-03) : fonctions publiques d'adéquation au profil du module Catalogue (ADR-001).
 * <ul>
 * <li>{@link #rapporter} : rapport d'adéquation enregistré à chaque proposition de supports.</li>
 * <li>{@link #controler} : à la validation d'une opération vers une unité de compte, le support est contrôlé de
 * nouveau avec le profil en vigueur ; s'il n'est plus éligible, le refus est enregistré et l'opération refusée.</li>
 * </ul>
 */
@Service
@Loggable
public class Adequation {

	public static final String SUPPORT_INADAPTE = "Ce support ne correspond pas à votre profil";

	private final SupportsEligibles eligibles;
	private final RapportAdequationRepository rapports;
	private final OperationRefuseeRepository refus;
	private final DateMetier dateMetier;

	public Adequation(SupportsEligibles eligibles, RapportAdequationRepository rapports,
			OperationRefuseeRepository refus, DateMetier dateMetier) {
		this.eligibles = eligibles;
		this.rapports = rapports;
		this.refus = refus;
		this.dateMetier = dateMetier;
	}

	/** Enregistre le rapport d'adéquation d'une proposition (profil utilisé, supports proposés). */
	@Transactional
	public void rapporter(UUID customerId, Produit produit, String profil, List<SupportEligible> proposes) {
		rapports.save(new RapportAdequation(customerId, produit, profil,
				proposes.stream().map(SupportEligible::codeIsin).collect(Collectors.joining("|")),
				dateMetier.maintenant().toOffsetDateTime()));
	}

	/**
	 * Refuse ({@link SupportInadapte}) une opération vers un support qui n'est pas éligible au profil en vigueur (vide :
	 * aucun profil validé, donc aucun support). Le refus est enregistré dans sa propre transaction : il reste tracé
	 * alors que l'opération appelante est annulée et n'investit rien.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = SupportInadapte.class)
	public void controler(UUID customerId, Produit produit, String profil, String codeIsin, String typeOperation) {
		boolean eligible = eligibles.lister(produit, profil).stream().anyMatch(s -> s.codeIsin().equals(codeIsin));
		if (!eligible) {
			refus.save(new OperationRefusee(customerId, produit, codeIsin, profil, typeOperation,
					dateMetier.maintenant().toOffsetDateTime()));
			throw new SupportInadapte();
		}
	}

	/** Support inadapté au profil en vigueur (422). */
	public static class SupportInadapte extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SupportInadapte() {
			super(SUPPORT_INADAPTE);
		}

	}

}
