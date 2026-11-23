package org.example.laserranitarotaciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El usuario de los tests es parque / secreta1 (application-test.properties). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void sinLoginNoSeEntraPeroLaSaludSi() throws Exception {
        mvc.perform(get("/api/empleados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/salud")).andExpect(status().isOk());
    }

    @Test
    void conElUsuarioDelEnvSeEntra() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\" Parque \",\"clave\":\"secreta1\",\"mantener\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var sesion = json.readTree(respuesta);
        assertThat(sesion.path("usuario").asText()).isEqualTo("parque");
        String token = sesion.path("token").asText();

        mvc.perform(get("/api/empleados").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/empleados").header("Authorization", "Bearer token-trucho")).andExpect(status().isUnauthorized());
    }

    @Test
    void conClaveIncorrectaNo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"parque\",\"clave\":\"otra\"}"))
                .andExpect(status().isBadRequest());
    }
}
