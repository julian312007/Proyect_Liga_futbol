package co.edu.uniajc.liga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API del Sistema de Gestión de una Liga de Fútbol.
 *
 * <p>La clase vive en el paquete raíz {@code co.edu.uniajc.liga} a propósito: así Spring
 * detecta controladores, servicios, repositorios y entidades de todos los subpaquetes sin
 * necesidad de {@code @ComponentScan}, {@code @EntityScan} ni {@code @EnableJpaRepositories}.
 */
@SpringBootApplication
public class LigaFutbolApplication {

    public static void main(String[] args) {
        SpringApplication.run(LigaFutbolApplication.class, args);
    }
}
