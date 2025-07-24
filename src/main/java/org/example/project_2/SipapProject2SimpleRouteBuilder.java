package org.example.project_2;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.ActiveMQComponent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulador SIPAP Proyecto 2
 * 2 patrones EIP: Dead Letter Channel + Wire Tap
 */
public class SipapProject2SimpleRouteBuilder extends RouteBuilder {

    private Random random = new Random();
    private String[] bancos = {"ITAU", "ATLAS", "FAMILIAR"};
    private String apellidoAlumno = "garcia";
    private AtomicLong transactionIdCounter = new AtomicLong(100000);
    private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void configure() throws Exception {

        // Configurar conexión a ActiveMQ en Docker
        setupDockerActiveMQ();

        // ============================================
        // PATRÓN 1: DEAD LETTER CHANNEL
        // ============================================
        errorHandler(deadLetterChannel("activemq:queue:" + apellidoAlumno + "-DLQ")
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .logStackTrace(true));

        // ============================================
        // GENERADOR DE TRANSFERENCIAS MEJORADO
        // ============================================
        from("timer:transferGenerator?period=4000")
                .routeId("generador-transferencias-v2")
                .process(exchange -> {
                    // Generar datos aleatorios para la transferencia
                    String cuenta = String.valueOf(1000 + random.nextInt(4000));

                    int monto;
                    if (random.nextInt(10) < 2) {
                        monto = 20000000 + random.nextInt(5000000);
                    } else {
                        monto = 1000 + random.nextInt(15000000);
                    }

                    String bancoOrigen = bancos[random.nextInt(bancos.length)];
                    String bancoDestino;

                    // Asegurar que origen y destino sean diferentes
                    do {
                        bancoDestino = bancos[random.nextInt(bancos.length)];
                    } while (bancoOrigen.equals(bancoDestino));

                    // Generar fecha (80% fecha actual, 20% fecha incorrecta para testing)
                    String fecha;
                    if (random.nextInt(10) < 8) {
                        fecha = LocalDate.now().format(dateFormatter);
                    } else {
                        fecha = LocalDate.now().minusDays(1).format(dateFormatter);
                    }

                    // Generar ID único de transacción
                    long idTransaccion = transactionIdCounter.incrementAndGet();

                    // Crear JSON de transferencia mejorado
                    String transferJson = String.format(
                            "{ \"cuenta\": \"%s\", \"monto\": %d, \"banco_origen\": \"%s\", \"banco_destino\": \"%s\", \"fecha\": \"%s\", \"id_transaccion\": %d }",
                            cuenta, monto, bancoOrigen, bancoDestino, fecha, idTransaccion
                    );

                    exchange.getIn().setBody(transferJson);
                    exchange.getIn().setHeader("bancoDestino", bancoDestino);
                    exchange.getIn().setHeader("monto", monto);
                    exchange.getIn().setHeader("idTransaccion", idTransaccion);
                    exchange.getIn().setHeader("fecha", fecha);
                })
                .log("🏦 Generando transferencia: ${body}")

                // PATRÓN 2: WIRE TAP - Auditoría de todas las transacciones generadas
                .wireTap("direct:auditoria")

                // Continuar con validación de monto
                .to("direct:validacion-monto");

        // ============================================
        // PATRÓN 2: WIRE TAP - AUDITORÍA
        // ============================================
        from("direct:auditoria")
                .routeId("auditoria-transferencias")
                .log("📊 AUDITORÍA: Transacción generada - ID: ${header.idTransaccion}, Monto: ${header.monto}")
                .to("activemq:queue:" + apellidoAlumno + "-AUDITORIA");

        // ============================================
        // VALIDACIÓN DE MONTO
        // ============================================
        from("direct:validacion-monto")
                .routeId("validacion-monto")
                .choice()
                .when(header("monto").isGreaterThanOrEqualTo(20000000))
                .log("❌ Monto supera límite: ${header.monto} - ID: ${header.idTransaccion}")
                .process(exchange -> {
                    long idTransaccion = exchange.getIn().getHeader("idTransaccion", Long.class);
                    String rechazoJson = String.format(
                            "{\"id_transaccion\": %d, \"mensaje\": \"El monto supera máximo permitido\"}",
                            idTransaccion
                    );
                    exchange.getIn().setBody(rechazoJson);
                })
                .to("activemq:queue:" + apellidoAlumno + "-RECHAZADOS")
                .otherwise()
                .log("✅ Monto válido: ${header.monto} - ID: ${header.idTransaccion}")
                .to("direct:enrutamiento-banco");

        // ============================================
        // ENRUTAMIENTO POR BANCO
        // ============================================
        from("direct:enrutamiento-banco")
                .routeId("enrutamiento-banco")
                .choice()
                .when(header("bancoDestino").isEqualTo("ITAU"))
                .to("activemq:queue:" + apellidoAlumno + "-ITAU-IN")
                .when(header("bancoDestino").isEqualTo("ATLAS"))
                .to("activemq:queue:" + apellidoAlumno + "-ATLAS-IN")
                .when(header("bancoDestino").isEqualTo("FAMILIAR"))
                .to("activemq:queue:" + apellidoAlumno + "-FAMILIAR-IN");

        // ============================================
        // CONSUMIDORES CON VALIDACIÓN DE FECHA
        // ============================================

        // Consumidor Banco ITAU
        from("activemq:queue:" + apellidoAlumno + "-ITAU-IN")
                .routeId("consumidor-itau-v2")
                .log("🏦 BANCO ITAU procesando: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String fechaActual = LocalDate.now().format(dateFormatter);

                    // Extraer fecha e ID del JSON
                    String fecha = extraerCampo(body, "fecha");
                    String idTransaccion = extraerCampo(body, "id_transaccion");

                    String respuesta;
                    if (fechaActual.equals(fecha)) {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Transferencia procesada exitosamente\"}",
                                idTransaccion
                        );
                        System.out.println("✅ ITAU - Transferencia exitosa: " + respuesta);
                    } else {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Mensaje caducado\"}",
                                idTransaccion
                        );
                        System.out.println("❌ ITAU - Mensaje caducado: " + respuesta);
                    }

                    exchange.getIn().setBody(respuesta);
                })
                .to("activemq:queue:" + apellidoAlumno + "-ITAU-RESPONSE");

        // Consumidor Banco ATLAS
        from("activemq:queue:" + apellidoAlumno + "-ATLAS-IN")
                .routeId("consumidor-atlas-v2")
                .log("🏦 BANCO ATLAS procesando: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String fechaActual = LocalDate.now().format(dateFormatter);

                    String fecha = extraerCampo(body, "fecha");
                    String idTransaccion = extraerCampo(body, "id_transaccion");

                    String respuesta;
                    if (fechaActual.equals(fecha)) {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Transferencia procesada exitosamente\"}",
                                idTransaccion
                        );
                        System.out.println("✅ ATLAS - Transferencia exitosa: " + respuesta);
                    } else {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Mensaje caducado\"}",
                                idTransaccion
                        );
                        System.out.println("❌ ATLAS - Mensaje caducado: " + respuesta);
                    }

                    exchange.getIn().setBody(respuesta);
                })
                .to("activemq:queue:" + apellidoAlumno + "-ATLAS-RESPONSE");

        // Consumidor Banco FAMILIAR
        from("activemq:queue:" + apellidoAlumno + "-FAMILIAR-IN")
                .routeId("consumidor-familiar-v2")
                .log("🏦 BANCO FAMILIAR procesando: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String fechaActual = LocalDate.now().format(dateFormatter);

                    String fecha = extraerCampo(body, "fecha");
                    String idTransaccion = extraerCampo(body, "id_transaccion");

                    String respuesta;
                    if (fechaActual.equals(fecha)) {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Transferencia procesada exitosamente\"}",
                                idTransaccion
                        );
                        System.out.println("✅ FAMILIAR - Transferencia exitosa: " + respuesta);
                    } else {
                        respuesta = String.format(
                                "{\"id_transaccion\": %s, \"mensaje\": \"Mensaje caducado\"}",
                                idTransaccion
                        );
                        System.out.println("❌ FAMILIAR - Mensaje caducado: " + respuesta);
                    }

                    exchange.getIn().setBody(respuesta);
                })
                .to("activemq:queue:" + apellidoAlumno + "-FAMILIAR-RESPONSE");
    }

    private String extraerCampo(String json, String campo) {
        String patron = "\"" + campo + "\": \"?([^,\"\\}]+)\"?";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(patron);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private void setupDockerActiveMQ() throws Exception {
        CamelContext context = getContext();

        // Docker con credenciales
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        connectionFactory.setUserName("artemis");
        connectionFactory.setPassword("artemis");

        ActiveMQComponent activeMQComponent = new ActiveMQComponent();
        activeMQComponent.setConnectionFactory(connectionFactory);

        context.addComponent("activemq", activeMQComponent);

        System.out.println("✅ Conectado a ActiveMQ en Docker (localhost:61616)");
        System.out.println("📊 Web Console disponible en: http://localhost:8161");
        System.out.println("🔐 Usuario: artemis / Password: artemis");
        System.out.println("🎯 PROYECTO 2 SIMPLE - Solo 2 patrones EIP: Dead Letter Channel + Wire Tap");
    }
}