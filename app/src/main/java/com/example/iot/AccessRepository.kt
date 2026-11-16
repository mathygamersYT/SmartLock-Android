package com.example.iot

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AccessRepository {
    val db = FirebaseFirestore.getInstance()

    // Transactions retry concurrent revocations. Rules independently validate the decision.
    fun simulate(uid: String, eventId: String): Task<Map<String, Any>> {
        val actor = FirebaseAuth.getInstance().currentUser?.uid
            ?: return com.google.android.gms.tasks.Tasks.forException(IllegalStateException("Sesión cerrada"))
        val event = db.collection("eventosSimulados").document(eventId)
        val card = db.collection("tarjetas").document(uid)
        return db.runTransaction { tx ->
            val existing = tx.get(event)
            if (existing.exists()) {
                check(existing.getString("actorUid") == actor && existing.getString("uidRFID") == uid)
                existing.data!!
            } else {
                val credential = tx.get(card)
                val (result, reason) = AccessPolicy.decision(credential.exists(), credential.getBoolean("habilitada") == true, credential.getBoolean("eliminada") == true)
                val data = mapOf<String, Any>(
                    "actorUid" to actor, "uidRFID" to uid, "resultado" to result,
                    "motivo" to reason, "origen" to "SIMULACION",
                    "dispositivoId" to actor,
                    "fecha" to FieldValue.serverTimestamp()
                )
                tx.set(event, data)
                data
            }
        }
    }


    // Compatibility for the existing integration test and callers.
    fun saveCard(uid: String, name: String, enabled: Boolean, create: Boolean): Task<Void> =
        mutateCard(uid, if (create) CardChange.Create(name) else CardChange.Status(enabled))

    fun mutateCard(uid: String, change: CardChange): Task<Void> {
        require(AccessPolicy.normalizeUid(uid) == uid)
        val actor = FirebaseAuth.getInstance().currentUser?.uid
            ?: return com.google.android.gms.tasks.Tasks.forException(IllegalStateException("Sesión cerrada"))
        val card = db.collection("tarjetas").document(uid)
        val audit = db.collection("auditoria").document()
        return db.runTransaction { tx ->
            val old = tx.get(card)
            val create = change is CardChange.Create
            check(if (create) !old.exists() else old.exists()) { "CARD_EXISTS_OR_MISSING" }
            check(create || old.getBoolean("eliminada") != true) { "CARD_DELETED" }
            val data = hashMapOf<String, Any>(
                "actualizadaEn" to FieldValue.serverTimestamp(), "actualizadaPor" to actor,
                "ultimaOperacion" to audit.id, "eliminada" to false
            )
            val action = when (change) {
                is CardChange.Create -> {
                    require(change.name.trim().length in 1..80)
                    data["nombre"] = change.name.trim()
                    data["habilitada"] = true
                    data["fechaCreacion"] = FieldValue.serverTimestamp()
                    "ALTA"
                }
                is CardChange.Edit -> {
                    require(change.name.trim().length in 1..80)
                    // Do not override a concurrent revocation when editing a name.
                    data["nombre"] = change.name.trim()
                    "EDITAR"
                }
                is CardChange.Status -> {
                    data["habilitada"] = change.enabled
                    if (change.enabled) "HABILITAR" else "REVOCAR"
                }
                CardChange.Delete -> {
                    data["habilitada"] = false
                    data["eliminada"] = true
                    "ELIMINAR"
                }
            }
            if (create) tx.set(card, data) else tx.update(card, data)
            tx.set(audit, mapOf("actorUid" to actor, "tarjetaUid" to uid,
                "accion" to action, "fecha" to FieldValue.serverTimestamp()))
            null
        }
    }
}

sealed class CardChange {
    data class Create(val name: String) : CardChange()
    data class Edit(val name: String) : CardChange()
    data class Status(val enabled: Boolean) : CardChange()
    data object Delete : CardChange()
}
