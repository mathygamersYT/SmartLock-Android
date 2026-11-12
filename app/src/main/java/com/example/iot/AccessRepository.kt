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
                val (result, reason) = AccessPolicy.decision(credential.exists(), credential.getBoolean("habilitada") == true)
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

    fun saveCard(uid: String, name: String, enabled: Boolean, create: Boolean): Task<Void> {
        val actor = FirebaseAuth.getInstance().currentUser?.uid
            ?: return com.google.android.gms.tasks.Tasks.forException(IllegalStateException("Sesión cerrada"))
        val card = db.collection("tarjetas").document(uid)
        val audit = db.collection("auditoria").document()
        return db.runTransaction { transaction ->
            val existing = transaction.get(card)
            check(if (create) !existing.exists() else existing.exists()) {
                if (create) "La tarjeta ya existe" else "La tarjeta ya no existe"
            }
            val data = hashMapOf<String, Any>(
                "nombre" to name, "habilitada" to enabled,
                "actualizadaEn" to FieldValue.serverTimestamp(),
                "actualizadaPor" to actor, "ultimaOperacion" to audit.id
            )
            if (create) data["fechaCreacion"] = FieldValue.serverTimestamp()
            if (create) transaction.set(card, data) else transaction.update(card, data)
            transaction.set(audit, mapOf(
                "actorUid" to actor, "tarjetaUid" to uid,
                "accion" to if (create) "ALTA" else if (enabled) "HABILITAR" else "REVOCAR",
                "fecha" to FieldValue.serverTimestamp()
            ))
            null
        }
    }
}
