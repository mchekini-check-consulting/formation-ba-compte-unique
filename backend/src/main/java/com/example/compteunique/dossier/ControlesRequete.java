package com.example.compteunique.dossier;

import com.example.compteunique.dossier.Categories.ResultatControle;

// Corps de PUT /dossiers/{reference}/controles : résultat des contrôles sanctions et PEP (CUB-8).
public record ControlesRequete(ResultatControle sanctions, ResultatControle pep) {
}
