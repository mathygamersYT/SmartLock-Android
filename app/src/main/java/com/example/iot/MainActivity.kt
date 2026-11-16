package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.core.widget.addTextChangedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val repository = AccessRepository()
    private val auth = FirebaseAuth.getInstance()
    private val subscriptions = mutableListOf<ListenerRegistration>()
    private var adminSubscription: ListenerRegistration? = null
    private var authorized = false
    private var device: DocumentSnapshot? = null
    private var deviceFromCache = true
    private var physicalEvents = emptyList<DocumentSnapshot>()
    private var simulatedEvents = emptyList<DocumentSnapshot>()
    private var eventFilter = 0
    private lateinit var content: View
    private lateinit var accessStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var credentials: CredentialController
    private val eventAdapter = RecordAdapter { row ->
        AlertDialog.Builder(this).setTitle(row.title).setMessage(row.dialog).setPositiveButton(R.string.close, null).show()
    }
    private val auditAdapter = RecordAdapter()
    private lateinit var addCard: Button
    private lateinit var refresh: Button
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            renderDevice()
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.adminContent)
        accessStatus = findViewById(R.id.accessStatus)
        operationStatus = findViewById(R.id.operationStatus)
        credentials = CredentialController(this, repository) { authorized }
        listOf(R.id.cardList to credentials.adapter, R.id.eventList to eventAdapter, R.id.auditList to auditAdapter)
            .forEach { (id, listAdapter) ->
                findViewById<RecyclerView>(id).apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = listAdapter
                }
            }
        findViewById<android.widget.CheckBox>(R.id.showDeleted).setOnCheckedChangeListener { _, _ -> credentials.render() }
        addCard = findViewById(R.id.btnAddCard)
        refresh = findViewById(R.id.btnRefreshAccess)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.sessionEmail).text = AccessPolicy.maskedEmail(auth.currentUser?.email)
        findViewById<TextView>(R.id.accountUid).text = getString(R.string.ui_uid_de_cuenta) + auth.currentUser?.uid.orEmpty()
        findViewById<Button>(R.id.btnSimulator).setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }
        findViewById<Button>(R.id.btnPairSimulator).setOnClickListener { manageSimulator() }
        findViewById<EditText>(R.id.cardSearch).addTextChangedListener { credentials.render() }
        val filter = findViewById<Spinner>(R.id.eventFilter)
        filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.ui_todos), getString(R.string.ui_permitidos), getString(R.string.ui_rechazados), getString(R.string.ui_simulados), getString(R.string.ui_fisicos)))
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                eventFilter = position
                renderEvents()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            openLogin()
        }
        addCard.setOnClickListener { credentials.add() }
        refresh.setOnClickListener { watchAdministrator() }
        listOf(getString(R.string.ui_resumen) to R.id.summarySection, getString(R.string.ui_tarjetas) to R.id.cardsSection, getString(R.string.ui_historial) to R.id.historySection, getString(R.string.ui_auditoria) to R.id.auditSection)
            .zip(listOf(R.id.tabSummary, R.id.tabCards, R.id.tabHistory, R.id.tabAudit))
            .forEach { (section, buttonId) ->
                findViewById<Button>(buttonId).setOnClickListener {
                    listOf(R.id.summarySection, R.id.cardsSection, R.id.historySection, R.id.auditSection).forEach {
                        findViewById<View>(it).visibility = if (it == section.second) View.VISIBLE else View.GONE
                    }
                    listOf(R.id.tabSummary, R.id.tabCards, R.id.tabHistory, R.id.tabAudit).forEach {
                        findViewById<Button>(it).isSelected = it == buttonId
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) { openLogin(); return }
        watchAdministrator()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        adminSubscription?.remove()
        adminSubscription = null
        stopData()
        super.onStop()
    }

    private fun watchAdministrator() {
        val user = auth.currentUser ?: return openLogin()
        adminSubscription?.remove()
        stopData()
        accessStatus.text = getString(R.string.ui_verificando_permiso_de_administrador)
        adminSubscription = repository.db.collection("administradores").document(user.uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null) {
                    stopData()
                    accessStatus.text = readError(error)
                } else if (snapshot == null || snapshot.metadata.isFromCache) {
                    stopData()
                    accessStatus.text = getString(R.string.ui_conecta_a_internet_para_verificar_tu_autorizacion)
                } else if (snapshot.getBoolean("activo") == true) {
                    accessStatus.text = getString(R.string.ui_administrador_autorizado)
                    findViewById<View>(R.id.rolePanel).visibility = View.GONE
                    if (!authorized) {
                        authorized = true
                        content.visibility = View.VISIBLE
                        listenData()
                    }
                } else {
                    stopData()
                    accessStatus.text = getString(R.string.ui_tu_cuenta_no_tiene_permiso_de_administrador_solicita_la)
                }
            }
    }

    private fun stopData() {
        authorized = false
        subscriptions.forEach { it.remove() }
        subscriptions.clear()
        content.visibility = View.GONE
        findViewById<View>(R.id.rolePanel).visibility = View.VISIBLE
        credentials.clear()
        eventAdapter.submitList(emptyList())
        auditAdapter.submitList(emptyList())
        physicalEvents = emptyList()
        simulatedEvents = emptyList()
        device = null
    }

    private fun listenData() {
        subscriptions += repository.db.collection("dispositivos").document("puerta-principal")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!authorized) return@addSnapshotListener
                if (error != null) {
                    device = null
                    findViewById<TextView>(R.id.deviceStatus).text = readError(error)
                    findViewById<TextView>(R.id.doorStatus).text = getString(R.string.ui_puerta_sin_datos)
                } else {
                    device = snapshot
                    deviceFromCache = snapshot?.metadata?.isFromCache != false
                    renderDevice()
                }
            }
        subscriptions += repository.db.collection("tarjetas").orderBy("nombre").limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!authorized) return@addSnapshotListener
                val cardsCached = snapshot?.metadata?.isFromCache != false
                credentials.update(snapshot?.documents.orEmpty(), cardsCached)
                findViewById<TextView>(R.id.cardsSync).text = if (error != null) readError(error)
                    else if (cardsCached) getString(R.string.ui_lista_guardada_sin_confirmacion_del_servidor) else getString(R.string.ui_credenciales_actualizadas_en_firebase)
            }
        listOf("eventos", "eventosSimulados").forEach { collection ->
            subscriptions += repository.db.collection(collection).orderBy("fecha", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (!authorized) return@addSnapshotListener
                    if (collection == "eventos") physicalEvents = snapshot?.documents.orEmpty()
                    else simulatedEvents = snapshot?.documents.orEmpty()
                    renderEvents()
                    findViewById<TextView>(R.id.historySync).text = if (error != null) readError(error)
                        else if (snapshot?.metadata?.isFromCache != false) getString(R.string.ui_historial_guardado_sin_confirmar_con_el_servidor)
                        else getString(R.string.ui_hasta_50_eventos_por_origen_hora_local_del_telefono)
                }
        }
        subscriptions += repository.db.collection("auditoria").orderBy("fecha", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!authorized) return@addSnapshotListener
                findViewById<TextView>(R.id.auditEmpty).apply {
                    visibility = if (error != null || snapshot == null || snapshot.isEmpty) View.VISIBLE else View.GONE
                    text = if (error != null) readError(error) else getString(R.string.audit_empty)
                }
                auditAdapter.submitList(if (error != null) emptyList() else snapshot?.documents.orEmpty().map { doc ->
                    val action = when (doc.getString("accion")) {
                        "ALTA" -> getString(R.string.ui_credencial_registrada)
                        "EDITAR" -> getString(R.string.ui_titular_actualizado)
                        "ELIMINAR" -> getString(R.string.ui_credencial_eliminada)
                        "REVOCAR" -> getString(R.string.ui_acceso_revocado)
                        else -> getString(R.string.ui_credencial_habilitada)
                    }
                    RecordRow(doc.id, action, AccessPolicy.maskedUid(doc.getString("tarjetaUid").orEmpty()),
                        formatDate(doc.getTimestamp("fecha")?.toDate()) + getString(R.string.ui_nresponsable) + doc.getString("actorUid").orEmpty().take(12))
                })
            }
    }

    private fun renderEvents() {
        if (!authorized) return
        val all = (physicalEvents + simulatedEvents).sortedByDescending { it.getTimestamp("fecha")?.seconds ?: 0 }
        val filtered = all.filter { doc -> when (eventFilter) {
            1 -> doc.getString("resultado") == "PERMITIDO"
            2 -> doc.getString("resultado") == "RECHAZADO"
            3 -> doc.reference.parent.id == "eventosSimulados"
            4 -> doc.reference.parent.id == "eventos"
            else -> true
        } }
        findViewById<View>(R.id.eventsEmpty).visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        eventAdapter.submitList(filtered.map { doc ->
            val allowed = doc.getString("resultado") == "PERMITIDO"
            val simulated = doc.reference.parent.id == "eventosSimulados"
            val title = if (allowed) getString(R.string.ui_acceso_permitido) else getString(R.string.ui_acceso_rechazado)
            val origin = getString(if (simulated) R.string.origin_simulated else R.string.origin_physical)
            RecordRow(doc.reference.path, title, origin + " · " + AccessPolicy.maskedUid(doc.getString("uidRFID").orEmpty()),
                formatDate(doc.getTimestamp("fecha")?.toDate()), getString(R.string.event_detail,
                    doc.id, doc.getString("resultado").orEmpty(), doc.getString("motivo").orEmpty(),
                    AccessPolicy.maskedUid(doc.getString("uidRFID").orEmpty()),
                    doc.getString("dispositivoId").orEmpty(), formatDate(doc.getTimestamp("fecha")?.toDate())))
        })
        findViewById<TextView>(R.id.eventCount).text = all.size.toString()
        findViewById<TextView>(R.id.latestAttempt).text = all.firstOrNull()?.let {
            (if (it.getString("resultado") == "PERMITIDO") getString(R.string.ui_ultimo_intento_permitido) else getString(R.string.ui_ultimo_intento_rechazado)) + "\n" +
                formatDate(it.getTimestamp("fecha")?.toDate()) + " · " +
                (if (it.reference.parent.id == "eventosSimulados") getString(R.string.origin_simulated) else getString(R.string.ui_dispositivo_fisico))
        } ?: getString(R.string.ui_sin_intentos_todavia_abre_el_simulador_en_el_segundo_an)
    }

    private fun renderDevice() {
        if (!authorized) return
        val snapshot = device
        val seen = snapshot?.getTimestamp("ultimaConexion")?.toDate()
        val online = !deviceFromCache && AccessPolicy.isOnline(seen?.time, System.currentTimeMillis())
        findViewById<TextView>(R.id.deviceStatus).text = when {
            snapshot == null || !snapshot.exists() -> getString(R.string.ui_sin_datos_del_arduino)
            deviceFromCache -> getString(R.string.ui_sin_conexion_a_firebase_datos_guardados)
            online -> getString(R.string.ui_arduino_conectado)
            else -> getString(R.string.ui_arduino_sin_comunicacion)
        }
        val door = when (snapshot?.getString("puerta")) {
            "ABIERTA" -> getString(R.string.ui_abierta)
            "CERRADA" -> getString(R.string.ui_cerrada)
            else -> getString(R.string.no_data)
        }
        findViewById<TextView>(R.id.doorStatus).text = getString(R.string.door_state, door) + if (!online) getString(R.string.ui_estado_sin_confirmar) else ""
        findViewById<TextView>(R.id.lastSeen).text = getString(R.string.ui_ultimo_reporte) + formatDate(seen)
        findViewById<TextView>(R.id.lastSync).text = getString(R.string.ui_ultima_sincronizacion_de_tarjetas) +
            formatDate(snapshot?.getTimestamp("ultimaSincronizacionTarjetas")?.toDate())
    }

    private fun manageSimulator() {
        val form = layoutInflater.inflate(R.layout.dialog_simulator, null)
        val account = form.findViewById<EditText>(R.id.simulatorAccountInput)
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.ui_vincular_punto_de_acceso))
            .setMessage(getString(R.string.ui_copia_el_uid_que_aparece_al_iniciar_sesion_en_el_otro_t))
            .setView(form).setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.ui_revocar), null).setPositiveButton(getString(R.string.ui_habilitar), null).create()
        dialog.setOnShowListener {
            fun save(active: Boolean) {
                val uid = account.text.toString().trim()
                if (!uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    account.error = getString(R.string.ui_copia_un_uid_de_cuenta_valido)
                    return
                }
                if (!authorized) return
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                repository.db.collection("simuladores").document(uid).set(mapOf(
                    "activo" to active, "habilitadoPor" to auth.currentUser!!.uid,
                    "fecha" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )).addOnCompleteListener { task ->
                    if (isDestroyed || isFinishing) return@addOnCompleteListener
                    if (task.isSuccessful) {
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle(if (active) getString(R.string.ui_punto_de_acceso_habilitado) else getString(R.string.ui_punto_de_acceso_revocado))
                            .setMessage(getString(R.string.ui_el_permiso_fue_confirmado_en_firebase)).setPositiveButton(getString(R.string.ui_aceptar), null).show()
                    } else {
                        account.error = getString(R.string.ui_no_se_confirmo_el_cambio_revisa_conexion_y_permisos)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(true) }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(false) }
        }
        dialog.show()
    }

    private fun readError(error: FirebaseFirestoreException): String = when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> getString(R.string.ui_acceso_denegado_revisa_el_administrador_autorizado_y_la)
        FirebaseFirestoreException.Code.UNAVAILABLE -> getString(R.string.ui_no_se_puede_conectar_a_firebase_revisa_tu_conexion)
        else -> getString(R.string.ui_no_se_pudieron_cargar_los_datos_vuelve_a_verificar_el_a)
    }

    private fun formatDate(date: Date?): String = date?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) } ?: getString(R.string.no_data)
    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
