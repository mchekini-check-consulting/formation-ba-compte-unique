package com.example.compteunique.decouverte;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.compteunique.decouverte.DocumentsProduits.Document;
import com.example.compteunique.decouverte.DocumentsProduits.Produit;

interface ConsultationDocumentRepository extends JpaRepository<ConsultationDocument, Long> {

	/** Dernière ouverture de cette version du document : c'est elle que la case cochée complète. */
	Optional<ConsultationDocument> findFirstByCustomerIdAndProduitAndDocumentAndVersionDocumentOrderByIdDesc(
			UUID customerId, Produit produit, Document document, String versionDocument);

	/** Case cochée sur une ouverture de cette version du document. */
	boolean existsByCustomerIdAndProduitAndDocumentAndVersionDocumentAndCocheLeIsNotNull(UUID customerId,
			Produit produit, Document document, String versionDocument);

}
