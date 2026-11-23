package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.config.JwtService;
import org.example.laserranitarotaciones.model.dto.CambioClaveDto;
import org.example.laserranitarotaciones.model.dto.LoginDto;
import org.example.laserranitarotaciones.model.dto.NuevoUsuarioDto;
import org.example.laserranitarotaciones.model.dto.SesionDto;
import org.example.laserranitarotaciones.model.dto.UsuarioDto;
import org.example.laserranitarotaciones.model.entity.Usuario;
import org.example.laserranitarotaciones.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    static final int CLAVE_MINIMA = 6;

    private final UsuarioRepository repositorio;
    private final PasswordEncoder cifrador;
    private final JwtService jwt;

    /** Entrar: si el usuario y la contraseña coinciden, un token. El mismo mensaje para los dos errores. */
    @Transactional(readOnly = true)
    public SesionDto entrar(LoginDto dto) {
        String nombre = normalizar(dto.usuario());
        Usuario u = repositorio.findByUsuario(nombre)
                .filter(x -> dto.clave() != null && cifrador.matches(dto.clave(), x.getClave()))
                .orElseThrow(() -> new ValidacionException("Usuario o contraseña incorrectos."));
        return new SesionDto(jwt.generar(u, dto.mantener()), aDto(u));
    }

    @Transactional(readOnly = true)
    public UsuarioDto buscar(Long id) {
        return aDto(repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese usuario no existe.")));
    }

    @Transactional(readOnly = true)
    public List<UsuarioDto> listar() {
        return repositorio.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(UsuarioService::aDto).toList();
    }

    @Transactional
    public UsuarioDto crear(NuevoUsuarioDto dto) {
        String usuario = normalizar(dto.usuario());
        if (usuario.isEmpty()) throw new ValidacionException("Falta el usuario.");
        if (!usuario.matches("[a-z0-9._-]{3,40}")) {
            throw new ValidacionException("El usuario va de 3 a 40 letras o números, sin espacios.");
        }
        if (repositorio.findByUsuario(usuario).isPresent()) throw new ValidacionException("Ya existe el usuario " + usuario + ".");
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        validarClave(dto.clave());
        Usuario u = new Usuario();
        u.setUsuario(usuario);
        u.setNombre(nombre);
        u.setClave(cifrador.encode(dto.clave()));
        return aDto(repositorio.save(u));
    }

    /** La propia: pide la de ahora. */
    @Transactional
    public void cambiarPropia(Long id, CambioClaveDto dto) {
        Usuario u = repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese usuario no existe."));
        if (dto.actual() == null || !cifrador.matches(dto.actual(), u.getClave())) {
            throw new ValidacionException("La contraseña actual no es esa.");
        }
        validarClave(dto.nueva());
        u.setClave(cifrador.encode(dto.nueva()));
    }

    /** La de otro (por si se la olvidó): no pide la de ahora. */
    @Transactional
    public void cambiarDeOtro(Long id, CambioClaveDto dto) {
        Usuario u = repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese usuario no existe."));
        validarClave(dto.nueva());
        u.setClave(cifrador.encode(dto.nueva()));
    }

    /** No se puede borrar a uno mismo (así siempre queda alguien que puede entrar). */
    @Transactional
    public void eliminar(Long id, Long quienBorra) {
        if (id.equals(quienBorra)) throw new ValidacionException("No podés borrar tu propio usuario.");
        repositorio.delete(repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Ese usuario no existe.")));
    }

    /** El primero, si no hay ninguno (lo usa el arranque). */
    @Transactional
    public boolean crearPrimeroSiNoHay(String usuario, String clave, String nombre) {
        if (repositorio.count() > 0) return false;
        crear(new NuevoUsuarioDto(usuario, nombre, clave));
        return true;
    }

    private static void validarClave(String clave) {
        if (clave == null || clave.length() < CLAVE_MINIMA) {
            throw new ValidacionException("La contraseña tiene que tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
        if (clave.length() > 72) throw new ValidacionException("La contraseña es muy larga.");
    }

    private static String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    private static UsuarioDto aDto(Usuario u) {
        return new UsuarioDto(u.getId(), u.getUsuario(), u.getNombre());
    }
}
