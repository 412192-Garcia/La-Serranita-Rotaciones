package org.example.laserranitarotaciones;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.laserranitarotaciones.model.dto.NuevoUsuarioDto;
import org.example.laserranitarotaciones.repository.UsuarioRepository;
import org.example.laserranitarotaciones.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioService usuarios;
    @Autowired
    UsuarioRepository repositorio;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
        usuarios.crear(new NuevoUsuarioDto("Pau", "Pau", "secreta1"));
    }

    @Test
    void sinLoginNoSeEntraPeroLaSaludSi() throws Exception {
        mvc.perform(get("/api/empleados")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/salud")).andExpect(status().isOk());
    }

    @Test
    void conUsuarioYClaveSeEntra() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\" PAU \",\"clave\":\"secreta1\",\"mantener\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode sesion = json.readTree(respuesta);
        assertThat(sesion.path("usuario").path("usuario").asText()).isEqualTo("pau");
        String token = sesion.path("token").asText();

        mvc.perform(get("/api/empleados").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/empleados").header("Authorization", "Bearer token-trucho")).andExpect(status().isUnauthorized());
    }

    @Test
    void conClaveIncorrectaNo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"pau\",\"clave\":\"otra\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unUsuarioBorradoNoSigueEntrandoConSuToken() throws Exception {
        var otro = usuarios.crear(new NuevoUsuarioDto("beto", "Beto", "secreta2"));
        String token = json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"beto\",\"clave\":\"secreta2\"}")).andReturn().getResponse().getContentAsString()).path("token").asText();
        repositorio.deleteById(otro.id());
        mvc.perform(get("/api/empleados").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }
}
