package co.edu.uniajc.liga.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Sustituye el verificador real de Google por {@link GoogleTokenVerifierFalso} en las pruebas
 * {@code @SpringBootTest}. {@code @Primary} hace que, de los dos beans de tipo {@code GoogleTokenVerifier}
 * (el real de {@code GoogleAuthConfig} y este), Spring inyecte el doble. Se importa con
 * {@code @Import(GoogleVerificadorFalsoConfig.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GoogleVerificadorFalsoConfig {

    @Bean
    @Primary
    GoogleTokenVerifierFalso googleTokenVerifierFalso() {
        return new GoogleTokenVerifierFalso();
    }
}
