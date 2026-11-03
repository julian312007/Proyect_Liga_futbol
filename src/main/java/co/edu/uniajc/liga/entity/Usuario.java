package co.edu.uniajc.liga.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Usuario de la API (ampliación a las 9 entidades de la propuesta). Se identifica con su cuenta de
 * Google; no tiene contraseña ni nombre de usuario propios. No se relaciona con ninguna otra entidad.
 *
 * <p>Corresponde a la tabla {@code usuario} de la migración {@code V2__usuario.sql}. Con
 * {@code ddl-auto=validate}, los nombres, tipos y longitudes de las columnas deben coincidir
 * con la migración; por eso cada {@code @Column} declara su {@code length} de forma explícita.
 *
 * <p>Solo {@code @Getter/@Setter}, nunca {@code @Data}: {@code equals/hashCode/toString} generados
 * romperían con relaciones {@code LAZY} y {@code toString} imprimiría datos personales.
 */
@Getter
@Setter
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    /**
     * Identificador estable de la cuenta de Google (claim {@code sub}). Nulo hasta el primer ingreso:
     * el administrador inicial se crea solo con su correo y se enlaza al ingresar. Es la clave con la que
     * se reconoce al usuario; el correo puede cambiar en Google, el {@code sub} no.
     */
    @Column(name = "google_sub", unique = true, length = 255)
    private String googleSub;

    /** Correo electrónico (verificado por Google). Se guarda normalizado: sin espacios y en minúsculas. */
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    /** Nombre para mostrar. Se toma de Google al crear la cuenta y el propio usuario puede editarlo. */
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Foto de perfil de Google (opcional). Se refresca en cada ingreso. */
    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol = Rol.USER;

    /** Un usuario inactivo no puede ingresar ni usar un token que aún no haya vencido. */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Último ingreso con Google; nulo si todavía no ha ingresado nunca. */
    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = Instant.now();
        }
    }
}
