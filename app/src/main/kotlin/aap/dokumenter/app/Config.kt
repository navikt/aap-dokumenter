package aap.dokumenter.app

import no.nav.aap.ktor.client.AzureConfig
import java.net.URL

data class Config(
    val saf: SafConfig,
    val azure: AzureConfig,
)

data class SafConfig(
    val host: URL,
    val scope: String,
)
