package com.example.iot

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in integration check. Uses the account already signed in on the test phone. */
@RunWith(AndroidJUnit4::class)
class LiveFirebaseTest {
    @Test fun createAllowRevokeDenyAndRecoverSameEvent() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("smartLockLive") == "true")
        val repository = AccessRepository()
        val actor = FirebaseAuth.getInstance().currentUser?.uid
        assertNotNull("Inicia sesión con una cuenta administradora antes de probar.", actor)
        fun <T> wait(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 40, TimeUnit.SECONDS)
        val admin = wait(repository.db.collection("administradores").document(actor!!).get(Source.SERVER))
        assertEquals(true, admin.getBoolean("activo"))
        val uid = "F0" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        val card = repository.db.collection("tarjetas").document(uid)
        assertFalse(wait(card.get(Source.SERVER)).exists())
        wait(repository.saveCard(uid, "Prueba de integración Android", true, true))
        assertTrue(wait(card.get(Source.SERVER)).getBoolean("habilitada") == true)
        try {
            wait(repository.saveCard(uid, "Duplicado", true, true))
            fail("El alta duplicada debe rechazarse")
        } catch (_: java.util.concurrent.ExecutionException) {
            assertEquals("Prueba de integración Android", wait(card.get(Source.SERVER)).getString("nombre"))
        }
        val eventId = UUID.randomUUID().toString().replace("-", "")
        assertEquals("PERMITIDO", wait(repository.simulate(uid, eventId))["resultado"])
        val event = repository.db.collection("eventosSimulados").document(eventId)
        val first = wait(event.get(Source.SERVER))
        assertNotNull(first.getTimestamp("fecha"))
        assertEquals("SIMULACION", first.getString("origen"))
        assertEquals("PERMITIDO", wait(repository.simulate(uid, eventId))["resultado"])
        assertEquals(first.getTimestamp("fecha"), wait(event.get(Source.SERVER)).getTimestamp("fecha"))
        wait(repository.saveCard(uid, "Prueba de integración Android", false, false))
        val revoked = wait(card.get(Source.SERVER))
        assertFalse(revoked.getBoolean("habilitada")!!)
        val audit = wait(repository.db.collection("auditoria").document(revoked.getString("ultimaOperacion")!!).get(Source.SERVER))
        assertEquals("REVOCAR", audit.getString("accion"))
        assertEquals(actor, audit.getString("actorUid"))
        val denied = wait(repository.simulate(uid, UUID.randomUUID().toString().replace("-", "")))
        assertEquals("RECHAZADO", denied["resultado"])
        assertEquals("REVOCADA", denied["motivo"])
        var unknown: String
        do { unknown = UUID.randomUUID().toString().replace("-", "").take(8).uppercase() }
        while (wait(repository.db.collection("tarjetas").document(unknown).get(Source.SERVER)).exists())
        val missing = wait(repository.simulate(unknown, UUID.randomUUID().toString().replace("-", "")))
        assertEquals("RECHAZADO", missing["resultado"])
        assertEquals("DESCONOCIDA", missing["motivo"])
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
