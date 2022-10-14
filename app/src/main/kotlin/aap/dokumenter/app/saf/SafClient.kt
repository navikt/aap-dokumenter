package aap.dokumenter.app.saf

import aap.dokumenter.app.Config
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import no.nav.aap.ktor.client.HttpClientAdBehalfOfTokenProvider
import org.slf4j.LoggerFactory
import java.util.*

private val secureLog = LoggerFactory.getLogger("secureLog")

class SafClient(private val config: Config) {
    private val tokenProvider = HttpClientAdBehalfOfTokenProvider(config.azure, config.saf.scope)
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) = secureLog.info(message)
            }
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    suspend fun hentDokumenter(
        personident: String,
        saksbehandlerToken: String,
        antall: Int = Int.MAX_VALUE,
    ): DokumentoversiktBrukerResponse {
        val obo = tokenProvider.getBehalfOfToken(saksbehandlerToken)
        val callId = UUID.randomUUID()
        val response = httpClient.post("${config.saf.host}/graphql") {
            contentType(ContentType.Application.Json)
            bearerAuth(obo)
            header("Nav-Callid", callId)
            setBody(
                HentDokumentoversiktBruker(
                    query = SafQueries.dokumentoversiktBruker(),
                    variables = Variables(
                        brukerId = BrukerId(personident, BrukerIdType.FNR),
                        tema = listOf(Tema.AAP, Tema.SYK, Tema.SYM),
                        foerste = antall,
                        etter = "0",
                    ),
                )
            )
        }
        return response.body()
    }

    suspend fun hentPdf(
        journalpostId: String,
        dokumentInfoId: String,
        variantformat: Variantformat,
        saksbehandlerToken: String,
    ): HttpResponse {
        val obo = tokenProvider.getBehalfOfToken(saksbehandlerToken)
        val callId = UUID.randomUUID()
        val url = "${config.saf.host}/graphql/rest/hentdokument/$journalpostId/$dokumentInfoId/$variantformat"
        return httpClient.get(url) {
            bearerAuth(obo)
            header("Nav-Callid", callId)
        }
    }
}
