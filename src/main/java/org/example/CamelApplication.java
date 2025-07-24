package org.example;

import org.apache.camel.main.Main;
// import org.example.project_1.SipapDockerActiveMQRouteBuilder;
import org.example.project_2.SipapProject2SimpleRouteBuilder;

public class CamelApplication {

    public static void main(String[] args) throws Exception {
        Main main = new Main();

        // ============================================
        // CONFIGURACIÓN DE RUTAS - PATRONES EIP
        // ============================================

        // Rutas básicas
        // main.configure().addRoutesBuilder(new MyRouteBuilder());

        // WIRE TAP PATTERN
        // main.configure().addRoutesBuilder(new WireTapRouteBuilder());

        // CLAIM CHECK PATTERN
        // main.configure().addRoutesBuilder(new ClaimCheckRouteBuilder());

        // EVENT SOURCING + SEDA GAMING PATTERN
        // main.configure().addRoutesBuilder(new EventSourcingGameRouteBuilder());

        // ============================================
        // PROYECTO 1 - Versión básica
        // ============================================
        // main.configure().addRoutesBuilder(new SipapDockerActiveMQRouteBuilder());


        // ============================================
        // PROYECTO 2 - 2 patrones EIP
        // ============================================
        main.configure().addRoutesBuilder(new SipapProject2SimpleRouteBuilder());

        main.run(args);
    }
}