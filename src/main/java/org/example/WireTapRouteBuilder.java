package org.example;

import org.apache.camel.builder.RouteBuilder;

/**
 * Wire Tap Pattern Implementation
 *
 * Caso de uso: Sistema de procesamiento de pedidos de e-commerce
 * - Flujo principal: procesar pedido y confirmar al cliente
 * - Wire Tap: enviar copia para auditoría y análisis de ventas
 */
public class WireTapRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // ========================================
        // WIRE TAP PATTERN - RUTA PRINCIPAL
        // ========================================

        from("timer:order-simulation?period=10000") // Simula llegada de pedidos cada 10 segundos
                .routeId("main-order-processing")
                .setBody(simple("{'orderId': '${random(1000,9999)}', 'product': 'Laptop', 'amount': ${random(500,2000)}, 'customer': 'Customer-${random(100,999)}', 'timestamp': '${date:now:yyyy-MM-dd HH:mm:ss}'}"))
                .setHeader("OrderId", simple("ORDER-${random(1000,9999)}"))
                .log("📦 [MAIN FLOW] Recibido nuevo pedido: ${header.OrderId}")

                // *** AQUÍ APLICAMOS EL WIRE TAP PATTERN ***
                // Enviamos una copia del mensaje a rutas secundarias SIN afectar el flujo principal
                .wireTap("direct:audit-log")           // Copia para auditoría
                .wireTap("direct:sales-analytics")     // Copia para análisis de ventas
                .wireTap("direct:inventory-check")     // Copia para verificación de inventario

                // El flujo principal continúa normalmente
                .log("💳 [MAIN FLOW] Procesando pago para pedido: ${header.OrderId}")
                .delay(1000) // Simula tiempo de procesamiento
                .log("✅ [MAIN FLOW] Pedido ${header.OrderId} confirmado y enviado al cliente")
                .to("file:output/confirmed-orders?fileName=${header.OrderId}-confirmed.json");

        // ========================================
        // RUTAS SECUNDARIAS (WIRE TAP DESTINATIONS)
        // ========================================

        // Ruta de Auditoría - registra todos los pedidos para compliance
        from("direct:audit-log")
                .routeId("audit-logging")
                .log("📋 [AUDIT] Registrando pedido para auditoría: ${header.OrderId}")
                .setHeader("AuditTimestamp", simple("${date:now:yyyy-MM-dd HH:mm:ss}"))
                .to("file:output/audit?fileName=audit-${header.OrderId}.log");

        // Ruta de Análisis de Ventas - procesa datos para reportes
        from("direct:sales-analytics")
                .routeId("sales-analytics")
                .log("📊 [ANALYTICS] Analizando datos de venta: ${header.OrderId}")
                .process(exchange -> {
                    // Simula procesamiento de analytics
                    String body = exchange.getIn().getBody(String.class);
                    String analytics = "ANALYTICS_PROCESSED: " + body;
                    exchange.getIn().setBody(analytics);
                })
                .to("file:output/analytics?fileName=analytics-${header.OrderId}.json");

        // Ruta de Verificación de Inventario - actualiza stock
        from("direct:inventory-check")
                .routeId("inventory-management")
                .log("📦 [INVENTORY] Verificando stock para: ${header.OrderId}")
                .delay(500) // Simula consulta a base de datos de inventario
                .setBody(simple("INVENTORY_UPDATE: Product reserved for ${header.OrderId} at ${date:now:HH:mm:ss}"))
                .to("file:output/inventory?fileName=inventory-${header.OrderId}.txt");

        // ========================================
        // RUTA ADICIONAL: Wire Tap con Filtros
        // ========================================

        // Ejemplo avanzado: Wire Tap condicional solo para pedidos grandes
        from("timer:premium-orders?period=15000")
                .routeId("premium-order-processing")
                .setBody(simple("{'orderId': 'PREMIUM-${random(1000,9999)}', 'amount': ${random(2000,5000)}, 'type': 'premium'}"))
                .setHeader("OrderAmount", simple("${random(2000,5000)}"))
                .log("💎 [PREMIUM] Procesando pedido premium: ${body}")

                // Wire Tap condicional: solo envía a VIP processing si el monto > 3000
                .choice()
                .when(simple("${header.OrderAmount} > 3000"))
                .wireTap("direct:vip-processing")
                .end()

                .log("✨ [PREMIUM] Pedido premium completado")
                .to("file:output/premium-orders?fileName=premium-${date:now:HHmmss}.json");

        // Procesamiento VIP (solo para pedidos > 3000)
        from("direct:vip-processing")
                .routeId("vip-processing")
                .log("👑 [VIP] Activando procesamiento VIP para pedido de alta valor")
                .setBody(simple("VIP_PROCESSING: High-value order detected - Amount: ${header.OrderAmount}"))
                .to("file:output/vip?fileName=vip-order-${date:now:HHmmss}.txt");
    }
}