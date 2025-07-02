package org.example;

import org.apache.camel.builder.RouteBuilder;

/**
 * Route Builder class that defines Camel routes
 */
public class MyRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Simple timer route that runs every 5 seconds
        from("timer:hello?period=5000")
                .routeId("timer-route")
                .setBody(constant("Hello from Apache Camel! Current time: ${date:now:yyyy-MM-dd HH:mm:ss}"))
                .log("${body}")
                .to("log:org.example?level=INFO");

        // File monitoring route - watches for files in input directory
        from("file:input?noop=true")
                .routeId("file-processing-route")
                .log("Processing file: ${header.CamelFileName}")
                .setBody(simple("File processed: ${header.CamelFileName} at ${date:now:yyyy-MM-dd HH:mm:ss}"))
                .to("file:output");
    }
}