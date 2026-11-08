package __PAQUET__.resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point de départ : GET / répond « Hello, World! » (via le gateway : /__SLUG__/). Couches : resource (HTTP) →
 * service (règles métier) → repository (données).
 */
@RestController
public class HelloResource {

    @GetMapping("/")
    public String hello() {
        return "Hello, World!";
    }
}
