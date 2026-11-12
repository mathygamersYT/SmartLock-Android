package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.UUID

class SimulatorActivity : AppCompatActivity() {
    private val repository = AccessRepository()
    private val auth = FirebaseAuth.getInstance()
    private val subscriptions = mutableListOf<ListenerRegistration>()
    private var admin = false
    private var simulator = false
    private var busy = false
    private var pendingId: String? = null
    private var pendingUid: String? = null
    private lateinit var uidInput: EditText
    private lateinit var submit: Button
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var detail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simulator)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.simulatorRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        pendingId = savedInstanceState?.getString("eventId")
        pendingUid = savedInstanceState?.getString("eventUid")
        uidInput = findViewById(R.id.simUid)
        submit = findViewById(R.id.btnSimulate)
        status = findViewById(R.id.simConnection)
        result = findViewById(R.id.simResult)
        detail = findViewById(R.id.simDetail)
        findViewById<TextView>(R.id.simEmail).text = auth.currentUser?.email.orEmpty()
        submit.setOnClickListener { simulate() }
        findViewById<Button>(R.id.btnExampleActive).setOnClickListener { uidInput.setText("04:A3:9C:21") }
        findViewById<Button>(R.id.btnExampleUnknown).setOnClickListener { uidInput.setText("FF:FF:FF:FF") }
        findViewById<Button>(R.id.btnBackAdmin).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<Button>(R.id.btnSimLogout).setOnClickListener {
            auth.signOut()
            login()
        }
    }

    override fun onStart() {
        super.onStart()
        val actor = auth.currentUser?.uid ?: return login()
        admin = false
        simulator = false
        updateAccess()
        listOf("administradores", "simuladores").forEach { collection ->
            subscriptions += repository.db.collection(collection).document(actor)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    val allowed = snapshot?.metadata?.isFromCache == false && snapshot.getBoolean("activo") == true
                    if (collection == "administradores") admin = allowed else simulator = allowed
                    updateAccess()
                }
        }
    }

    override fun onStop() {
        subscriptions.forEach { it.remove() }
        subscriptions.clear()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("eventId", pendingId)
        outState.putString("eventUid", pendingUid)
        super.onSaveInstanceState(outState)
    }

    private fun updateAccess() {
        submit.isEnabled = (admin || simulator) && !busy
        findViewById<Button>(R.id.btnBackAdmin).visibility = if (admin) View.VISIBLE else View.GONE
        status.text = if (admin || simulator) "Punto de acceso autorizado · Firebase conectado"
            else "Verificando autorización. Se requiere conexión y un rol activo."
        if (!admin && !simulator) {
            result.text = "Esperando autorización"
            detail.text = "UID de esta cuenta: " + auth.currentUser?.uid.orEmpty() +
                "\nEl responsable debe habilitar esta cuenta como simulador."
        }
    }

    private fun simulate() {
        if (busy || (!admin && !simulator)) return
        val uid = AccessPolicy.normalizeUid(uidInput.text.toString())
        if (uid == null) {
            uidInput.error = "Ingresa 8, 14 o 20 dígitos hexadecimales"
            return
        }
        // An ambiguous network failure keeps the same ID for an idempotent retry.
        if (pendingUid != uid || pendingId == null) {
            pendingUid = uid
            pendingId = UUID.randomUUID().toString().replace("-", "")
        }
        val id = pendingId!!
        val started = SystemClock.elapsedRealtime()
        busy = true
        submit.isEnabled = false
        uidInput.isEnabled = false
        findViewById<ProgressBar>(R.id.simProgress).visibility = View.VISIBLE
        result.text = "Validando credencial…"
        detail.text = "Esperando confirmación del servidor."
        repository.simulate(uid, id).addOnCompleteListener { task ->
            busy = false
            if (isDestroyed || isFinishing) return@addOnCompleteListener
            uidInput.isEnabled = true
            findViewById<ProgressBar>(R.id.simProgress).visibility = View.GONE
            updateAccess()
            if (task.isSuccessful && (admin || simulator)) {
                val allowed = task.result["resultado"] == "PERMITIDO"
                result.text = if (allowed) "Acceso permitido" else "Acceso rechazado"
                result.setTextColor(getColor(if (allowed) R.color.success else R.color.danger))
                val reason = when (task.result["motivo"]) {
                    "ACTIVA" -> "Credencial activa · apertura simulada"
                    "REVOCADA" -> "La credencial fue revocada"
                    else -> "Credencial no registrada"
                }
                detail.text = reason + "\nEvento: " + id + "\nConfirmado en Firebase · " +
                    (SystemClock.elapsedRealtime() - started) + " ms\nConsulta este evento en el Android administrador."
                pendingId = null
                pendingUid = null
                submit.text = "Simular otra lectura"
            } else {
                result.text = "No se pudo validar"
                result.setTextColor(getColor(R.color.danger))
                detail.text = "No se concede acceso. Revisa la red, el rol y las reglas de Firebase. " +
                    "Reintenta con el mismo UID para recuperar la confirmación sin duplicar el evento."
                submit.text = "Reintentar validación"
            }
        }
    }

    private fun login() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
