package org.example;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;

/**
 * Claim Check Pattern Implementation
 *
 * Caso de uso: Sistema de procesamiento de documentos legales
 * - Problema: PDFs grandes (50MB+) saturan la red y memoria
 * - Solución: Almacenar documentos temporalmente, procesar solo metadatos
 * - Beneficio: Flujo eficiente con recuperación bajo demanda
 */
public class ClaimCheckRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // ========================================
        // CLAIM CHECK PATTERN - FLUJO PRINCIPAL
        // ========================================

        from("timer:document-upload?period=15000") // Simula subida de documentos cada 15 segundos
                .routeId("document-processing-main")
                .process(exchange -> {
                    // Simula un documento grande (contrato legal)
                    String largeDocument = generateLargeDocument();
                    exchange.getIn().setBody(largeDocument);
                    exchange.getIn().setHeader("DocumentType", "Legal_Contract");
                    exchange.getIn().setHeader("ClientId", "CLIENT-" + System.currentTimeMillis() % 1000);
                    exchange.getIn().setHeader("DocumentSize", largeDocument.length());
                })
                .log("📄 [MAIN] Recibido documento grande: ${header.DocumentType} para ${header.ClientId} (${header.DocumentSize} chars)")

                // *** CLAIM CHECK STEP 1: STORE - Almacenar documento grande ***
                .to("direct:claim-check-store")

                // En este punto, el body ya NO contiene el documento grande,
                // solo contiene el "claim ticket" (referencia)
                .log("🎫 [MAIN] Documento almacenado, procesando con claim ticket: ${body}")

                // *** IMPORTANTE: Preservar el claim ticket en un header ***
                .setHeader("PreservedClaimTicket", simple("${body}"))

                // Procesamiento rápido con solo metadatos y referencia
                .to("direct:document-validation")
                .to("direct:metadata-processing")
                .to("direct:notification-service")

                // *** CLAIM CHECK STEP 2: Restaurar claim ticket y RETRIEVE ***
                .setBody(simple("${header.PreservedClaimTicket}"))
                .to("direct:claim-check-retrieve")
                .log("📋 [MAIN] Documento recuperado para finalización: ${header.DocumentType}")

                // Procesamiento final con documento completo
                .to("direct:final-document-processing");

        // ========================================
        // CLAIM CHECK STORE - Almacenar documento grande
        // ========================================

        from("direct:claim-check-store")
                .routeId("claim-check-store")
                .log("💾 [STORE] Almacenando documento grande en repositorio temporal...")
                .process(exchange -> {
                    // Extraer el documento grande del body
                    String largeDocument = exchange.getIn().getBody(String.class);

                    // Generar un claim ticket único
                    String claimTicket = "CLAIM-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);

                    // Almacenar el documento en "repositorio temporal" (simulado con header)
                    exchange.getIn().setHeader("StoredDocument", largeDocument);
                    exchange.getIn().setHeader("ClaimTicket", claimTicket);

                    // *** PUNTO CLAVE: Reemplazar el body grande con solo la referencia ***
                    exchange.getIn().setBody(claimTicket);

                    // Metadatos para el procesamiento ligero
                    exchange.getIn().setHeader("StorageTimestamp", java.time.LocalDateTime.now().toString());
                    exchange.getIn().setHeader("StorageLocation", "temp://storage/" + claimTicket);
                })
                .to("file:output/claim-storage?fileName=${header.ClaimTicket}.metadata")
                .log("✅ [STORE] Documento almacenado con ticket: ${body}");

        // ========================================
        // CLAIM CHECK RETRIEVE - Recuperar documento
        // ========================================

        from("direct:claim-check-retrieve")
                .routeId("claim-check-retrieve")
                .log("🔍 [RETRIEVE] Recuperando documento con claim ticket: ${body}")
                .process(exchange -> {
                    String claimTicket = exchange.getIn().getBody(String.class);

                    // Verificar que tenemos un ticket válido
                    if (claimTicket != null && claimTicket.startsWith("CLAIM-")) {
                        // Recuperar el documento desde el "almacén temporal"
                        String storedDocument = exchange.getIn().getHeader("StoredDocument", String.class);

                        if (storedDocument != null) {
                            // *** PUNTO CLAVE: Restaurar el documento original al body ***
                            exchange.getIn().setBody(storedDocument);
                            exchange.getIn().setHeader("DocumentRetrieved", true);
                            exchange.getIn().setHeader("RetrievalTimestamp", java.time.LocalDateTime.now().toString());
                        } else {
                            exchange.getIn().setBody("ERROR: Document not found for ticket " + claimTicket);
                            exchange.getIn().setHeader("DocumentRetrieved", false);
                        }
                    } else {
                        exchange.getIn().setBody("ERROR: Invalid claim ticket");
                        exchange.getIn().setHeader("DocumentRetrieved", false);
                    }
                })
                .choice()
                .when(header("DocumentRetrieved").isEqualTo(true))
                .log("✅ [RETRIEVE] Documento recuperado exitosamente (${header.DocumentSize} chars)")
                .otherwise()
                .log("❌ [RETRIEVE] Error al recuperar documento: ${body}")
                .end();

        // ========================================
        // RUTAS DE PROCESAMIENTO LIGERO (con claim ticket)
        // ========================================

        // Validación rápida con solo metadatos
        from("direct:document-validation")
                .routeId("document-validation")
                .log("🔍 [VALIDATION] Validando metadatos del documento ${header.DocumentType}")
                .process(exchange -> {
                    // Validación rápida basada en metadatos, no en contenido
                    String clientId = exchange.getIn().getHeader("ClientId", String.class);
                    boolean isValid = clientId != null && clientId.startsWith("CLIENT-");
                    exchange.getIn().setHeader("DocumentValid", isValid);
                })
                .choice()
                .when(header("DocumentValid").isEqualTo(true))
                .log("✅ [VALIDATION] Documento válido para ${header.ClientId}")
                .otherwise()
                .log("❌ [VALIDATION] Documento inválido")
                .end();

        // Procesamiento de metadatos
        from("direct:metadata-processing")
                .routeId("metadata-processing")
                .log("📊 [METADATA] Procesando metadatos del documento...")
                .process(exchange -> {
                    // Procesar solo metadatos, muy rápido
                    exchange.getIn().setHeader("ProcessingStatus", "METADATA_PROCESSED");
                    exchange.getIn().setHeader("EstimatedProcessingTime", "2_minutes");
                })
                .to("file:output/metadata?fileName=metadata-${header.ClaimTicket}.json");

        // Servicio de notificaciones
        from("direct:notification-service")
                .routeId("notification-service")
                .log("📧 [NOTIFICATION] Enviando notificación a ${header.ClientId}")
                .process(exchange -> {
                    // Preservar el body original y crear mensaje de notificación
                    String originalBody = exchange.getIn().getBody(String.class);
                    String claimTicket = exchange.getIn().getHeader("ClaimTicket", String.class);
                    String clientId = exchange.getIn().getHeader("ClientId", String.class);
                    String docType = exchange.getIn().getHeader("DocumentType", String.class);

                    String notificationMessage = String.format(
                            "Estimado %s, su documento %s está siendo procesado. Ticket: %s",
                            clientId, docType, claimTicket);

                    // Guardar el mensaje para el archivo, pero mantener el body original
                    exchange.getIn().setHeader("NotificationMessage", notificationMessage);
                    // NO modificar el body - mantener el claim ticket
                })
                .setBody(simple("${header.NotificationMessage}"))
                .to("file:output/notifications?fileName=notification-${header.ClientId}.txt")
                .setBody(simple("${header.PreservedClaimTicket}")); // Restaurar claim ticket

        // ========================================
        // PROCESAMIENTO FINAL (con documento completo)
        // ========================================

        from("direct:final-document-processing")
                .routeId("final-document-processing")
                .choice()
                .when(header("DocumentRetrieved").isEqualTo(true))
                .log("🎯 [FINAL] Iniciando procesamiento final del documento completo...")
                .process(exchange -> {
                    // Aquí podríamos hacer procesamiento pesado del documento completo
                    String document = exchange.getIn().getBody(String.class);
                    String processedDocument = "PROCESSED: " + document;
                    exchange.getIn().setBody(processedDocument);
                })
                .to("file:output/processed-documents?fileName=final-${header.ClaimTicket}.txt")
                .log("🎉 [FINAL] Documento procesado completamente para ${header.ClientId}")
                .otherwise()
                .log("⚠️ [FINAL] No se pudo procesar - documento no recuperado")
                .end();

        // ========================================
        // EJEMPLO ADICIONAL: Claim Check con múltiples recuperaciones
        // ========================================

        from("timer:multi-retrieve-demo?period=30000")
                .routeId("multi-retrieve-demo")
                .setBody(constant("Demo: Multiple retrievals of same document"))
                .setHeader("DemoClaimTicket", constant("CLAIM-DEMO-123"))
                .setHeader("StoredDocument", constant("This is a demo document stored multiple times"))

                .log("🔄 [DEMO] Demostrando múltiples recuperaciones del mismo ticket")

                // Primera recuperación
                .setBody(simple("${header.DemoClaimTicket}"))
                .to("direct:claim-check-retrieve")
                .log("1️⃣ [DEMO] Primera recuperación completada")

                // Segunda recuperación del mismo documento
                .setBody(simple("${header.DemoClaimTicket}"))
                .to("direct:claim-check-retrieve")
                .log("2️⃣ [DEMO] Segunda recuperación completada")

                .log("✨ [DEMO] Demo de múltiples recuperaciones terminado");
    }

    /**
     * Simula un documento grande (contrato legal con mucho texto)
     */
    private String generateLargeDocument() {
        StringBuilder doc = new StringBuilder();
        doc.append("=== CONTRATO LEGAL ===\n");
        doc.append("Fecha: ").append(java.time.LocalDateTime.now()).append("\n");
        doc.append("Partes: Cliente XYZ y Empresa ABC\n\n");

        // Simular documento grande con contenido repetitivo
        for (int i = 0; i < 100; i++) {
            doc.append("Cláusula ").append(i + 1).append(": ")
                    .append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                    .append("Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. ")
                    .append("Ut enim ad minim veniam, quis nostrud exercitation ullamco. ")
                    .append("Duis aute irure dolor in reprehenderit in voluptate velit esse. ")
                    .append("Excepteur sint occaecat cupidatat non proident sunt in culpa. ")
                    .append("Muy importante clausula legal número ").append(i + 1).append(".\n\n");
        }

        doc.append("=== FIN DEL CONTRATO ===\n");
        doc.append("Total de cláusulas: 100\n");
        doc.append("Documento generado automáticamente para demostración del patrón Claim Check.\n");

        return doc.toString();
    }
}