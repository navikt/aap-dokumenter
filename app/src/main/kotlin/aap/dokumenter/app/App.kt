package aap.dokumenter.app

import aap.dokumenter.app.saf.SafClient
import aap.dokumenter.app.saf.Variantformat
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.aap.ktor.config.loadConfig
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val secureLog = LoggerFactory.getLogger("secureLog")

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::dok).start(wait = true)
}

fun Application.dok() {
    val config = loadConfig<Config>()
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) { registry = prometheus }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    val jwkProvider: JwkProvider =
        JwkProviderBuilder(config.oauth.jwksUrl).cached(10, 24, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    install(Authentication) {
        jwt {
            realm = "Hent dokumenter"
            verifier(jwkProvider, config.oauth.issuer)
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, "Ikke tilgang") }
            validate { cred ->
                when (config.azure.clientId) {
                    in cred.audience -> JWTPrincipal(cred.payload)
                    else -> null
                }
            }
        }
    }

    val safClient = SafClient(config)

    routing {
        route("/api") {
            authenticate {
                get("/dokumenter/{personident}") {
                    val token = call.request.getAccessToken()

                    val personident = call.parameters.getOrFail("personident")
                    val safResponse = safClient.hentDokumenter(personident, token)

                    safResponse.data?.let { data ->
                        call.respond(data.dokumentoversiktBruker)
                    } ?: safResponse.errors?.let { errors ->
                        secureLog.error("Response fra Saf(t) inneholder feil: $errors")
                        call.respond(HttpStatusCode.BadRequest, errors)
                    }
                }

                get("/dokumenter/{personident}/pdf") {
                    val token = call.request.getAccessToken()

                    val personident = call.parameters.getOrFail("personident")
                    val safResponse = safClient.hentDokumenter(personident, token)

                    val pdfListe: List<ByteArray> =
                        safResponse.data?.dokumentoversiktBruker?.journalposter?.flatMap { journalpost ->
                            journalpost.dokumenter?.flatMap { dokumentInfo ->
                                dokumentInfo.dokumentvarianter.mapNotNull { dokumentvariant ->
                                    val response = safClient.hentPdf(
                                        journalpostId = journalpost.journalpostId,
                                        dokumentInfoId = dokumentInfo.dokumentInfoId,
                                        variantformat = dokumentvariant.variantformat,
                                        saksbehandlerToken = token,
                                    )
                                    if (response.status.isSuccess()) response.body<ByteArray>() else null
                                }
                            }.orEmpty()
                        }.orEmpty()

                    call.respond(pdfListe)
                }

                get("/pdf/{journalpostId}/{dokumentInfoId}/{variantformat}") {
                    val token = call.request.getAccessToken()

                    val journalpostId = call.parameters.getOrFail("journalpostId")
                    val dokumentInfoId = call.parameters.getOrFail("dokumentInfoId")
                    val variantformat = enumValueOf<Variantformat>(call.parameters.getOrFail("variantformat"))

                    val response = safClient.hentPdf(
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoId,
                        variantformat = variantformat,
                        saksbehandlerToken = token,
                    )

                    fun dokument(pdf: ByteArray) = Dokument(
                        data = pdf,
                        journalpostId = journalpostId,
                        dokumentInfoId = dokumentInfoId,
                        variantformat = variantformat,
                        contentType = response.contentType()?.contentType,
                        contentDisposition = response.headers["Content-Disposition"]
                    )

                    when (val status = response.status) {
                        HttpStatusCode.OK -> call.respond(status, dokument(response.body()))
                        HttpStatusCode.Unauthorized -> call.respond(status, "Saf(t) kunne ikke autorisere bruker")
                        HttpStatusCode.Forbidden -> call.respond(status, "Saf(t) kunne ikke gi bruker tilgang til PDF")
                        HttpStatusCode.NotFound -> call.respond(status, "Saf(t) fant ingen dokumenter")
                        else -> call.respond(status, "Saf(t) returnerte for aap-dokumenter en ukjent status")
                    }
                }
            }
        }

        route("/actuator") {
            get("/metrics") { call.respondText(prometheus.scrape()) }
            get("/live") { call.respondText("vedtak") }
            get("/ready") { call.respondText("vedtak") }
        }
    }
}

private fun ApplicationRequest.getAccessToken() =
    authorization()?.removePrefix("Bearer ") ?: error("Auth er feilkonfigurert")


data class Dokument(
    val data: ByteArray,
    val journalpostId: String,
    val dokumentInfoId: String,
    val variantformat: Variantformat,
    val contentType: String?,
    val contentDisposition: String?,
)
