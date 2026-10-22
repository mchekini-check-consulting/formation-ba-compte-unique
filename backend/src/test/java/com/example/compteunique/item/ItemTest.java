package com.example.compteunique.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ItemTest {

	@Test
	void dateDeCreationFixeeALEnregistrement() {
		Item item = new Item("nom", "description");
		assertNull(item.getId());
		assertNull(item.getCreeLe());
		item.avantCreation();
		assertNotNull(item.getCreeLe());
		assertEquals("description", item.getDescription());
	}

}
