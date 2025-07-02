package org.example;

import org.apache.camel.builder.RouteBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event Sourcing + SEDA Pattern Implementation
 *
 * Caso de uso: Sistema de eventos para Battle Royale Game
 * - Eventos: Kills, Deaths, Powerups, Zone Damage, Wins
 * - Procesamiento: Estadísticas en tiempo real, achievements, leaderboards
 * - SEDA: Procesamiento asíncrono por etapas con colas internas
 */
public class EventSourcingGameRouteBuilder extends RouteBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure() throws Exception {

        // ========================================
        // EVENT SOURCING + SEDA PATTERN - GAME EVENTS
        // ========================================

        // Generador de eventos de juego (simula eventos reales)
        from("timer:game-events?period=3000")
                .routeId("game-event-generator")
                .process(exchange -> {
                    // Generar evento de juego aleatorio
                    GameEvent event = generateRandomGameEvent();
                    String eventJson = objectMapper.writeValueAsString(event);

                    exchange.getIn().setBody(eventJson);
                    exchange.getIn().setHeader("EventType", event.getEventType());
                    exchange.getIn().setHeader("PlayerId", event.getPlayerId());
                    exchange.getIn().setHeader("EventId", event.getEventId());
                    exchange.getIn().setHeader("Timestamp", event.getTimestamp());
                })
                .log("🎮 [EVENT-GEN] Evento generado: ${header.EventType} por ${header.PlayerId}")

                // *** POINT CLAVE: ENVÍO A SEDA QUEUE PARA PROCESAMIENTO ASÍNCRONO ***
                .to("seda:game-event-processing?size=1000&concurrentConsumers=3");

        // ========================================
        // SEDA STAGE 1: PROCESAMIENTO PRINCIPAL DE EVENTOS
        // ========================================

        from("seda:game-event-processing")
                .routeId("seda-stage1-event-processing")
                .log("📥 [SEDA-1] Procesando evento: ${header.EventType} - ${header.EventId}")

                // Almacenamiento del evento (Event Sourcing)
                .to("direct:event-store")

                // Distribuir a diferentes colas SEDA según tipo de evento
                .choice()
                .when(header("EventType").isEqualTo("KILL"))
                .to("seda:kill-processing?size=500&concurrentConsumers=2")
                .when(header("EventType").isEqualTo("POWERUP"))
                .to("seda:powerup-processing?size=300&concurrentConsumers=1")
                .when(header("EventType").isEqualTo("ZONE_DAMAGE"))
                .to("seda:zone-processing?size=200&concurrentConsumers=1")
                .when(header("EventType").isEqualTo("WIN"))
                .to("seda:win-processing?size=100&concurrentConsumers=2")
                .otherwise()
                .to("seda:general-processing?size=200&concurrentConsumers=1")
                .end()

                // Enviar a cola de estadísticas generales
                .to("seda:stats-processing?size=800&concurrentConsumers=2");

        // ========================================
        // EVENT STORE (Event Sourcing Core)
        // ========================================

        from("direct:event-store")
                .routeId("event-store")
                .log("💾 [EVENT-STORE] Almacenando evento: ${header.EventId}")
                .process(exchange -> {
                    // Simular almacenamiento en event store
                    String eventData = exchange.getIn().getBody(String.class);
                    exchange.getIn().setHeader("StoredEventId", exchange.getIn().getHeader("EventId"));
                    exchange.getIn().setHeader("EventSequence", System.currentTimeMillis());
                })
                .to("file:output/event-store?fileName=event-${header.EventId}.json")
                .log("✅ [EVENT-STORE] Evento almacenado: ${header.StoredEventId}");

        // ========================================
        // SEDA STAGE 2: PROCESAMIENTO ESPECÍFICO POR TIPO
        // ========================================

        // Procesamiento de Kills
        from("seda:kill-processing")
                .routeId("seda-stage2-kill-processing")
                .log("⚔️ [KILL-PROC] Procesando kill de ${header.PlayerId}")
                .process(exchange -> {
                    // Lógica específica para kills
                    exchange.getIn().setHeader("KillProcessed", true);
                    exchange.getIn().setHeader("XpGained", 100);
                })
                .to("seda:achievement-check?size=200&concurrentConsumers=1")
                .to("file:output/kills?fileName=kill-${header.EventId}.json");

        // Procesamiento de Powerups
        from("seda:powerup-processing")
                .routeId("seda-stage2-powerup-processing")
                .log("⭐ [POWERUP-PROC] Procesando powerup de ${header.PlayerId}")
                .process(exchange -> {
                    exchange.getIn().setHeader("PowerupProcessed", true);
                    exchange.getIn().setHeader("PowerType", "HEALTH_BOOST");
                })
                .to("file:output/powerups?fileName=powerup-${header.EventId}.json");

        // Procesamiento de Zone Damage
        from("seda:zone-processing")
                .routeId("seda-stage2-zone-processing")
                .log("🌀 [ZONE-PROC] Procesando daño de zona para ${header.PlayerId}")
                .process(exchange -> {
                    exchange.getIn().setHeader("ZoneProcessed", true);
                    exchange.getIn().setHeader("DamageAmount", 25);
                })
                .to("file:output/zone-damage?fileName=zone-${header.EventId}.json");

        // Procesamiento de Wins
        from("seda:win-processing")
                .routeId("seda-stage2-win-processing")
                .log("🏆 [WIN-PROC] ¡Victoria procesada para ${header.PlayerId}!")
                .process(exchange -> {
                    exchange.getIn().setHeader("WinProcessed", true);
                    exchange.getIn().setHeader("XpGained", 1000);
                    exchange.getIn().setHeader("RankingPoints", 50);
                })
                .to("seda:achievement-check?size=200&concurrentConsumers=1")
                .to("seda:leaderboard-update?size=100&concurrentConsumers=1")
                .to("file:output/wins?fileName=win-${header.EventId}.json");

        // ========================================
        // SEDA STAGE 3: PROCESAMIENTO AVANZADO
        // ========================================

        // Sistema de Achievements
        from("seda:achievement-check")
                .routeId("seda-stage3-achievement-check")
                .log("🎖️ [ACHIEVEMENT] Verificando logros para ${header.PlayerId}")
                .process(exchange -> {
                    // Simular verificación de achievements
                    String eventType = exchange.getIn().getHeader("EventType", String.class);
                    boolean achievementUnlocked = ThreadLocalRandom.current().nextBoolean();

                    if (achievementUnlocked) {
                        exchange.getIn().setHeader("AchievementUnlocked", true);
                        exchange.getIn().setHeader("AchievementName", "KILLER_INSTINCT");
                        exchange.getIn().setBody("🎉 ¡LOGRO DESBLOQUEADO! " + exchange.getIn().getBody());
                    }
                })
                .choice()
                .when(header("AchievementUnlocked").isEqualTo(true))
                .log("🎉 [ACHIEVEMENT] ¡Logro desbloqueado! ${header.AchievementName} para ${header.PlayerId}")
                .to("file:output/achievements?fileName=achievement-${header.EventId}.json")
                .to("seda:notification-system?size=300&concurrentConsumers=1")
                .end();

        // Actualización de Leaderboards
        from("seda:leaderboard-update")
                .routeId("seda-stage3-leaderboard-update")
                .log("📊 [LEADERBOARD] Actualizando ranking para ${header.PlayerId}")
                .process(exchange -> {
                    // Simular actualización de leaderboard
                    int newRanking = ThreadLocalRandom.current().nextInt(1, 1000);
                    exchange.getIn().setHeader("NewRanking", newRanking);
                    exchange.getIn().setHeader("LeaderboardUpdated", true);
                })
                .to("file:output/leaderboard?fileName=ranking-${header.EventId}.json");

        // ========================================
        // SEDA STAGE 4: ESTADÍSTICAS Y NOTIFICACIONES
        // ========================================

        // Procesamiento de Estadísticas
        from("seda:stats-processing")
                .routeId("seda-stage4-stats-processing")
                .log("📈 [STATS] Actualizando estadísticas para ${header.PlayerId}")
                .process(exchange -> {
                    // Simular cálculo de estadísticas
                    exchange.getIn().setHeader("StatsUpdated", true);
                    exchange.getIn().setHeader("TotalEvents", System.currentTimeMillis() % 1000);
                })
                .to("file:output/player-stats?fileName=stats-${header.PlayerId}-${date:now:HHmmss}.json");

        // Sistema de Notificaciones
        from("seda:notification-system")
                .routeId("seda-stage4-notifications")
                .log("📱 [NOTIFY] Enviando notificación a ${header.PlayerId}")
                .process(exchange -> {
                    String notification = String.format(
                            "🎮 Notification for %s: %s",
                            exchange.getIn().getHeader("PlayerId"),
                            exchange.getIn().getBody()
                    );
                    exchange.getIn().setBody(notification);
                })
                .to("file:output/notifications?fileName=notify-${header.EventId}.txt");

        // ========================================
        // PROCESAMIENTO GENERAL
        // ========================================

        from("seda:general-processing")
                .routeId("seda-stage2-general-processing")
                .log("🔧 [GENERAL] Procesamiento general para evento ${header.EventType}")
                .to("file:output/general-events?fileName=general-${header.EventId}.json");

        // ========================================
        // MONITOREO DE COLAS SEDA
        // ========================================

        from("timer:seda-monitor?period=10000")
                .routeId("seda-queue-monitor")
                .process(exchange -> {
                    // Simular monitoreo de colas
                    exchange.getIn().setBody("🔍 SEDA Queue Monitor - All queues operational");
                })
                .log("${body}")
                .to("file:output/monitoring?fileName=seda-monitor-${date:now:HHmmss}.log");
    }

    /**
     * Genera eventos de juego aleatorios para simular actividad real
     */
    private GameEvent generateRandomGameEvent() {
        String[] eventTypes = {"KILL", "POWERUP", "ZONE_DAMAGE", "WIN"};
        String[] playerIds = {"Player_Alpha", "Player_Beta", "Player_Gamma", "Player_Delta", "Player_Omega"};
        String[] weapons = {"AK47", "M4A1", "AWP", "GRENADE", "KNIFE"};
        String[] powerups = {"HEALTH_KIT", "ARMOR", "SPEED_BOOST", "DAMAGE_BOOST"};

        String eventType = eventTypes[ThreadLocalRandom.current().nextInt(eventTypes.length)];
        String playerId = playerIds[ThreadLocalRandom.current().nextInt(playerIds.length)];

        GameEvent event = new GameEvent();
        event.setEventId("EVT-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000));
        event.setEventType(eventType);
        event.setPlayerId(playerId);
        event.setTimestamp(System.currentTimeMillis());

        // Datos específicos según tipo de evento
        Map<String, Object> eventData = new HashMap<>();
        switch (eventType) {
            case "KILL":
                eventData.put("weapon", weapons[ThreadLocalRandom.current().nextInt(weapons.length)]);
                eventData.put("victim", playerIds[ThreadLocalRandom.current().nextInt(playerIds.length)]);
                eventData.put("headshot", ThreadLocalRandom.current().nextBoolean());
                break;
            case "POWERUP":
                eventData.put("powerupType", powerups[ThreadLocalRandom.current().nextInt(powerups.length)]);
                eventData.put("location", "Zone_" + ThreadLocalRandom.current().nextInt(1, 10));
                break;
            case "ZONE_DAMAGE":
                eventData.put("damage", ThreadLocalRandom.current().nextInt(10, 50));
                eventData.put("newHealth", ThreadLocalRandom.current().nextInt(1, 100));
                break;
            case "WIN":
                eventData.put("playersRemaining", 1);
                eventData.put("matchDuration", ThreadLocalRandom.current().nextInt(900, 1800)); // 15-30 min
                eventData.put("kills", ThreadLocalRandom.current().nextInt(1, 20));
                break;
        }

        event.setEventData(eventData);
        return event;
    }

    /**
     * Clase para representar eventos de juego
     */
    public static class GameEvent {
        private String eventId;
        private String eventType;
        private String playerId;
        private long timestamp;
        private Map<String, Object> eventData;

        // Getters y Setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public Map<String, Object> getEventData() { return eventData; }
        public void setEventData(Map<String, Object> eventData) { this.eventData = eventData; }
    }
}