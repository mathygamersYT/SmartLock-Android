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
    private val nfcReader by lazy { NfcUidReader(this) }
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
        findViewById<TextView>(R.id.simEmail).text = AccessPolicy.maskedEmail(auth.currentUser?.email)
        submit.setOnClickListener { simulate() }
        findViewById<Button>(R.id.btnReadAccessNfc).setOnClickListener {
            if (!busy && (admin || simulator)) nfcReader.read { uid ->
                if (!busy && (admin || simulator)) {
                    uidInput.setText(uid)
                    uidInput.error = null
                    result.text = getString(R.string.nfc_captured)
                    detail.text = getString(R.string.nfc_validate_hint)
                }
            }
        }
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
        findViewById<Button>(R.id.btnReadAccessNfc).isEnabled = (admin || simulator) && !busy
        if (!admin && !simulator) nfcReader.stop()
        findViewById<Button>(R.id.btnBackAdmin).visibility = if (admin) View.VISIBLE else View.GONE
        status.text = if (admin || simulator) getString(R.string.ui_punto_de_acceso_autorizado_firebase_conectado)
            else getString(R.string.ui_verificando_autorizacion_se_requiere_conexion_y_un_rol_)
        if ((admin || simulator) && result.text.toString() == getString(R.string.ui_esperando_autorizacion)) {
            result.text = getString(R.string.ui_listo_para_validar)
            detail.text = getString(R.string.ui_ingresa_un_uid_y_pulsa_simular_lectura)
        }
        if (!admin && !simulator && !busy && result.text.toString() != getString(R.string.ui_no_se_pudo_validar)) {
            result.text = getString(R.string.ui_esperando_autorizacion)
            detail.text = getString(R.string.ui_uid_de_esta_cuenta) + auth.currentUser?.uid.orEmpty() +
                getString(R.string.ui_nel_responsable_debe_habilitar_esta_cuenta_como_simulad)
        }
    }

    private fun simulate() {
        if (busy || (!admin && !simulator)) return
        val uid = AccessPolicy.normalizeUid(uidInput.text.toString())
        if (uid == null) {
            uidInput.error = getString(R.string.ui_ingresa_8_14_o_20_digitos_hexadecimales)
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
        findViewById<Button>(R.id.btnReadAccessNfc).isEnabled = false
        submit.isEnabled = false
        uidInput.isEnabled = false
        findViewById<ProgressBar>(R.id.simProgress).visibility = View.VISIBLE
        result.text = getString(R.string.ui_validando_credencial)
        detail.text = getString(R.string.ui_esperando_confirmacion_del_servidor)
        repository.simulate(uid, id).addOnCompleteListener { task ->
            busy = false
            if (isDestroyed || isFinishing) return@addOnCompleteListener
            uidInput.isEnabled = true
            findViewById<ProgressBar>(R.id.simProgress).visibility = View.GONE
            updateAccess()
            if (task.isSuccessful && (admin || simulator)) {
                val allowed = task.result["resultado"] == "PERMITIDO"
                result.text = if (allowed) getString(R.string.ui_acceso_permitido) else getString(R.string.ui_acceso_rechazado)
                result.setTextColor(getColor(if (allowed) R.color.success else R.color.danger))
                val reason = when (task.result["motivo"]) {
                    "ACTIVA" -> getString(R.string.ui_credencial_activa_apertura_simulada)
                    "REVOCADA" -> getString(R.string.ui_la_credencial_fue_revocada)
                    "ELIMINADA" -> getString(R.string.ui_la_credencial_fue_eliminada)
                    else -> getString(R.string.ui_credencial_no_registrada)
                }
                detail.text = reason + getString(R.string.ui_nevento) + id + getString(R.string.ui_nconfirmado_en_firebase) +
                    (SystemClock.elapsedRealtime() - started) + getString(R.string.ui_ms_nconsulta_este_evento_en_el_android_administrador)
                pendingId = null
                pendingUid = null
                submit.text = getString(R.string.ui_simular_otra_lectura)
            } else {
                result.text = getString(R.string.ui_no_se_pudo_validar)
                result.setTextColor(getColor(R.color.danger))
                detail.text = getString(R.string.ui_no_se_concede_acceso_revisa_la_red_el_rol_y_las_reglas_) +
                    getString(R.string.ui_reintenta_con_el_mismo_uid_para_recuperar_la_confirmaci)
                submit.text = getString(R.string.ui_reintentar_validacion)
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
