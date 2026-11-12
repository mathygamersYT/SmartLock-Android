package com.example.iot

object AccessPolicy {
    fun decision(exists: Boolean, enabled: Boolean): Pair<String, String> = when {
        !exists -> "RECHAZADO" to "DESCONOCIDA"
        enabled -> "PERMITIDO" to "ACTIVA"
        else -> "RECHAZADO" to "REVOCADA"
    }

    fun normalizeUid(value: String): String? {
        val uid = value.replace(" ", "").replace(":", "").replace("-", "").uppercase(java.util.Locale.ROOT)
        return uid.takeIf { it.length in listOf(8, 14, 20) && it.all { c -> c in '0'..'9' || c in 'A'..'F' } }
    }

    fun maskedUid(uid: String): String = "•••• " + uid.takeLast(4)

    fun maskedEmail(email: String?): String = email?.takeIf { '@' in it }
        ?.let { it.take(1) + "•••@" + it.substringAfter('@') }
        ?: "Cuenta autenticada"

    fun isOnline(lastSeen: Long?, now: Long): Boolean =
        lastSeen != null && now - lastSeen in 0L..60_000L
}
