package com.example.bouchonsidentite;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BouchonsIdentiteApplication {

	public static void main(String[] args) {
		SpringApplication.run(BouchonsIdentiteApplication.class, args);
	}

	// Horloge des dates de vérification et d'expiration (remplaçable dans les tests)
	@Bean
	Clock horloge() {
		return Clock.systemDefaultZone();
	}

}
