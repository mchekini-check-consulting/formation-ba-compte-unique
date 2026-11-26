package com.example.compteunique.gestion;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.compteunique.catalogue.CatalogueService.Produit;

interface ParametreMandatRepository extends JpaRepository<ParametreMandat, Produit> {

}
