package com.example.compteunique.item;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// GET /items : liste ; POST /items {nom, description} : création (nom obligatoire).
// Exposé par la passerelle : https://api.<env>.check-consulting.net/formation-ba-compte-unique/backend/items
@RestController
@RequestMapping("/items")
public class ItemController {

	private final ItemService items;

	public ItemController(ItemService items) {
		this.items = items;
	}

	@GetMapping
	public List<Item> lister() {
		return items.lister();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Item creer(@RequestBody ItemRequest requete) {
		return items.creer(requete);
	}

}
