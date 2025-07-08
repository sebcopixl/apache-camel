package org.example;

import org.apache.camel.builder.RouteBuilder;
import java.util.Random;

/**
 * Simulador del sistema SIPAP - Transferencias electrónicas
 * Simula transferencias entre bancos ITAU, ATLAS, FAMILIAR
 */
public class SipapTransferRouteBuilder extends RouteBuilder {

    private Random random = new Random();
    private String[] bancos = {"ITAU", "ATLAS", "FAMILIAR"};
    private String apellidoAlumno = "garcia"; // Cambia por tu apellido

    @Override
    public void configure() throws Exception {

        // ============================================
        // GENERADOR DE TRANSFERENCIAS (Timer)
        // ============================================
        from("timer:transferGenerator?period=3000")
                .routeId("generador-transferencias")
                .process(exchange -> {
                    // Generar datos aleatorios para la transferencia
                    String cuenta = String.valueOf(1000 + random.nextInt(4000));
                    int monto = 1000 + random.nextInt(4000);
                    String bancoOrigen = bancos[random.nextInt(bancos.length)];
                    String bancoDestino;

                    // Asegurar que origen y destino sean diferentes
                    do {
                        bancoDestino = bancos[random.nextInt(bancos.length)];
                    } while (bancoOrigen.equals(bancoDestino));

                    // Crear JSON de transferencia
                    String transferJson = String.format(
                            "{ \"cuenta\": \"%s\", \"monto\": %d, \"banco_origen\": \"%s\", \"banco_destino\": \"%s\" }",
                            cuenta, monto, bancoOrigen, bancoDestino
                    );

                    exchange.getIn().setBody(transferJson);
                    exchange.getIn().setHeader("bancoDestino", bancoDestino);
                })
                .log("Generando transferencia: ${body}")
                .choice()
                .when(header("bancoDestino").isEqualTo("ITAU"))
                .to("seda:" + apellidoAlumno + "-ITAU-IN")
                .when(header("bancoDestino").isEqualTo("ATLAS"))
                .to("seda:" + apellidoAlumno + "-ATLAS-IN")
                .when(header("bancoDestino").isEqualTo("FAMILIAR"))
                .to("seda:" + apellidoAlumno + "-FAMILIAR-IN");

        // ============================================
        // CONSUMIDORES DE CADA BANCO
        // ============================================

        // Consumidor Banco ITAU
        from("seda:" + apellidoAlumno + "-ITAU-IN")
                .routeId("consumidor-itau")
                .log("BANCO ITAU procesando transferencia: ${body}")
                .process(exchange -> {
                    System.out.println("==== BANCO ITAU ====");
                    System.out.println("Procesando: " + exchange.getIn().getBody());
                    System.out.println("Transacción completada en ITAU");
                    System.out.println("=====================");
                });

        // Consumidor Banco ATLAS
        from("seda:" + apellidoAlumno + "-ATLAS-IN")
                .routeId("consumidor-atlas")
                .log("BANCO ATLAS procesando transferencia: ${body}")
                .process(exchange -> {
                    System.out.println("==== BANCO ATLAS ====");
                    System.out.println("Procesando: " + exchange.getIn().getBody());
                    System.out.println("Transacción completada en ATLAS");
                    System.out.println("======================");
                });

        // Consumidor Banco FAMILIAR
        from("seda:" + apellidoAlumno + "-FAMILIAR-IN")
                .routeId("consumidor-familiar")
                .log("BANCO FAMILIAR procesando transferencia: ${body}")
                .process(exchange -> {
                    System.out.println("==== BANCO FAMILIAR ====");
                    System.out.println("Procesando: " + exchange.getIn().getBody());
                    System.out.println("Transacción completada en FAMILIAR");
                    System.out.println("=========================");
                });
    }
}