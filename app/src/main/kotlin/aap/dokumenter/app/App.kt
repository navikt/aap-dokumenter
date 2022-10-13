package aap.dokumenter.app

import aap.dokumenter.app.saf.SafClient
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.ktor.config.loadConfig
import java.util.concurrent.TimeUnit

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::dok).start(wait = true)
}

fun Application.dok() {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) { registry = prometheus }

    val config = loadConfig<Config>()

    val jwkProvider: JwkProvider = JwkProviderBuilder(config.oauth.jwksUrl)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt {
            realm = "Hent dokumenter"
            verifier(jwkProvider, config.oauth.issuer)
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, "Ikke tilgang") }
            validate { cred ->
                // Check if this app is the audience
                if (cred.audience.contains(config.azure.clientId)) {
                    JWTPrincipal(cred.payload)
                } else return@validate null
            }
        }
    }

    val safClient = SafClient(config)

    routing {
        route("/api") {
            authenticate {
                get("/dokumenter/{personident}") {
                    val token = call.request.authorization()?.removePrefix("Bearer ")
                        ?: return@get call.respondText("Ingen adgang", status = HttpStatusCode.Unauthorized)

                    val personident = call.parameters.getOrFail("personident")

                    val safResponse = safClient.hentDokumenter(personident, token)

                    if (safResponse.data?.dokumentoversiktBruker != null) {
                        call.respond(safResponse.data.dokumentoversiktBruker)
                    } else {
                        call.respond(HttpStatusCode.NotFound, safResponse.errors ?: "unknown reason")
                    }
                }
            }
        }

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

