package aap.dokumenter.app

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::dok).start(wait = true)
}

fun Application.dok() {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = prometheus }

    routing {
        route("/actuator") {

            get("/metrics") {
                call.respondText(prometheus.scrape())
            }

            get("/live") {
                call.respondText("vedtak")
            }

            get("/ready") {
                call.respondText("vedtak")
            }
        }
    }
}

