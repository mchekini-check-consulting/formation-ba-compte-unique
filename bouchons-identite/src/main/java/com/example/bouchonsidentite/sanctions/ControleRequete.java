package com.example.bouchonsidentite.sanctions;

import java.time.LocalDate;

// Corps de POST /sanctions-pep/controles : identité saisie à l'étape « Mon identité » (US-01).
public record ControleRequete(String nomNaissance, String prenoms, LocalDate dateNaissance, String nationalite) {
}
