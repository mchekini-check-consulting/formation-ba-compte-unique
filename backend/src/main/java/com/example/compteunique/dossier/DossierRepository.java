package com.example.compteunique.dossier;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.compteunique.dossier.Categories.StatutDossier;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	Optional<Dossier> findByReference(String reference);

	/**
	 * CU-9 : dossiers de la même personne (même pièce OU même identité), hors la demande en cours.
	 * Comparaison après normalisation : majuscules, espaces et tirets ignorés. Pays de délivrance :
	 * les pièces acceptées (CNI, titre de séjour) sont toutes délivrées par la France.
	 */
	@Query(value = """
			select d.* from dossier d
			where d.reference <> :reference
			and ((d.type_piece = :typePiece and upper(translate(d.numero_piece, ' -', '')) = :numeroPiece)
			  or (d.date_naissance = :dateNaissance and d.pays_naissance = :paysNaissance
			      and upper(translate(d.nom_naissance, ' -', '')) = :nom
			      and upper(translate(d.prenoms, ' -', '')) = :prenoms
			      and upper(translate(d.commune_naissance, ' -', '')) = :commune))
			""", nativeQuery = true)
	@SuppressWarnings("PMD.ExcessiveParameterList")
	List<Dossier> memePersonne(@Param("reference") String reference, @Param("typePiece") String typePiece,
			@Param("numeroPiece") String numeroPieceNormalise, @Param("nom") String nomNormalise,
			@Param("prenoms") String prenomsNormalises, @Param("dateNaissance") LocalDate dateNaissance,
			@Param("commune") String communeNormalisee, @Param("paysNaissance") String paysNaissance);

	/** CU-9 : dossiers sans action de la cliente depuis une date (annulation pour inactivité). */
	List<Dossier> findByStatutAndAttenteAnalysteFalseAndDerniereActionLeBefore(StatutDossier statut,
			OffsetDateTime limite);

	/** CU-13 : dossiers dont la création du compte est à (re)lancer automatiquement. */
	List<Dossier> findByStatutAndCreationProchaineLeLessThanEqual(StatutDossier statut, OffsetDateTime date);

}
