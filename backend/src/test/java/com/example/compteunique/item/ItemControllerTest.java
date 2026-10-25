package com.example.compteunique.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class ItemControllerTest {

	private final ItemService items = mock(ItemService.class);
	private final ItemController controller = new ItemController(items);

	@Test
	void listerDelegueAuService() {
		List<Item> attendus = List.of(new Item("a", null));
		when(items.lister()).thenReturn(attendus);
		assertEquals(attendus, controller.lister());
	}

	@Test
	void creerDelegueAuService() {
		ItemRequest requete = new ItemRequest("Premier", "description");
		Item cree = new Item("Premier", "description");
		when(items.creer(requete)).thenReturn(cree);
		assertEquals(cree, controller.creer(requete));
	}

}
