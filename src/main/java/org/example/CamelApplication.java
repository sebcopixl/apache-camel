package org.example;

import org.apache.camel.main.Main;
import org.example.project_1.SipapDockerActiveMQRouteBuilder;

public class CamelApplication {

    public static void main(String[] args) throws Exception {
        Main main = new Main();

        // ============================================
        // CONFIGURACIÓN DE RUTAS - PATRONES EIP
        // ============================================

        // Rutas básicas (comentar si no se necesitan)
        // main.configure().addRoutesBuilder(new MyRouteBuilder());

        // WIRE TAP PATTERN - Descomenta para probar
        // main.configure().addRoutesBuilder(new WireTapRouteBuilder());

        // CLAIM CHECK PATTERN - Descomenta para probar
        // main.configure().addRoutesBuilder(new ClaimCheckRouteBuilder());

        // EVENT SOURCING + SEDA GAMING PATTERN - Descomenta para probar
        // main.configure().addRoutesBuilder(new EventSourcingGameRouteBuilder());

        // SIMULADOR SIPAP - Sistema de transferencias electrónicas con Docker
        main.configure().addRoutesBuilder(new SipapDockerActiveMQRouteBuilder());

        // Start and keep the application running
        main.run(args);
    }
}