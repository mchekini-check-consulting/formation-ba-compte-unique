package com.example.compteunique.livreta;

import java.time.LocalDate;

/** CU-296 : détail du Livret A détenu ailleurs, communiqué par l'administration fiscale si la cliente l'a autorisé. */
public record DetailLivretA(String etablissement, String agence, LocalDate dateOuverture) {
}
