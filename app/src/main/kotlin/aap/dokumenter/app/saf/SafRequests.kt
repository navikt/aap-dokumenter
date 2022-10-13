package aap.dokumenter.app.saf

object SafQueries {
    fun dokumentoversiktBruker() = HentDokumentoversiktBruker::class.java
        .getResource("/dokumentoversiktBruker.graphql")
        ?.readText()
        ?.replace("[\n\r]".toRegex(), "")
        ?: error("Fant ikke graphql query ressursen")
}

data class HentDokumentoversiktBruker(
    val query: String,
    val variables: Variables,
)

data class Variables(
    val brukerId: BrukerId,
    val tema: List<Tema>?,
    val foerste: Int,
    val etter: String?,
)

data class BrukerId(val id: String, val type: BrukerIdType = BrukerIdType.FNR)
enum class BrukerIdType { FNR }
