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
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import no.nav.aap.ktor.client.HttpClientAzureAdTokenProvider
import org.slf4j.LoggerFactory

private val secureLog = LoggerFactory.getLogger("secureLog")

class SafClient(private val config: Config) {
    private val tokenProvider = HttpClientAzureAdTokenProvider(config.azure, config.saf.scope)
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout)
        install(HttpRequestRetry)
        install(Logging) {
            level = LogLevel.BODY
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

    suspend fun hentDokumenter(personident: String) : SafResponse {
        val token = tokenProvider.getToken()
        val request = httpClient.post(config.saf.host) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(request(personident))
        }
        return request.body()
    }
}

fun request(personident: String) : String = """
query {
  dokumentoversiktBruker(brukerId: {id: "$personident", type: FNR}, foerste: 3) {
    journalposter {
      journalpostId
      tittel
      journalposttype
      journalstatus
      tema
      dokumenter {
        dokumentInfoId
        tittel
      }
    }
  }
}    
"""

data class SafResponse(
    val journalposter: String,
)
