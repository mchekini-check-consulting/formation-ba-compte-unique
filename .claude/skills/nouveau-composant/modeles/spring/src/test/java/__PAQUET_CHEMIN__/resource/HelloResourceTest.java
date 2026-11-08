package __PAQUET__.resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HelloResource.class)
class HelloResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void helloRepondHelloWorld() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string("Hello, World!"));
    }
}
