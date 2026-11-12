package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
    private var busy = false
    private var device: DocumentSnapshot? = null
    private var deviceFromCache = true
    private var cardDocuments = emptyList<DocumentSnapshot>()
    private var physicalEvents = emptyList<DocumentSnapshot>()
    private var simulatedEvents = emptyList<DocumentSnapshot>()
    private var cardsCached = true
    private var eventFilter = "Todos"
    private lateinit var content: View
    private lateinit var accessStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var cards: LinearLayout
    private lateinit var events: LinearLayout
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
        cards = findViewById(R.id.cardList)
        events = findViewById(R.id.eventList)
        addCard = findViewById(R.id.btnAddCard)
        refresh = findViewById(R.id.btnRefreshAccess)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.sessionEmail).text = AccessPolicy.maskedEmail(auth.currentUser?.email)
        findViewById<TextView>(R.id.accountUid).text = "UID de cuenta: " + auth.currentUser?.uid.orEmpty()
        findViewById<Button>(R.id.btnSimulator).setOnClickListener {
            startActivity(Intent(this, SimulatorActivity::class.java))
        }
        findViewById<Button>(R.id.btnPairSimulator).setOnClickListener { manageSimulator() }
        findViewById<EditText>(R.id.cardSearch).addTextChangedListener { renderCards() }
        val filter = findViewById<Spinner>(R.id.eventFilter)
        filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Todos", "Permitidos", "Rechazados", "Simulados", "Físicos"))
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                eventFilter = parent?.getItemAtPosition(position).toString()
                renderEvents()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            openLogin()
        }
        addCard.setOnClickListener { showAddCard() }
        refresh.setOnClickListener { watchAdministrator() }
        listOf("Resumen" to R.id.summarySection, "Tarjetas" to R.id.cardsSection, "Historial" to R.id.historySection, "Auditoría" to R.id.auditSection)
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
        accessStatus.text = "Verificando permiso de administrador…"
        adminSubscription = repository.db.collection("administradores").document(user.uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null) {
                    stopData()
                    accessStatus.text = readError(error)
                } else if (snapshot == null || snapshot.metadata.isFromCache) {
                    stopData()
                    accessStatus.text = "Conecta a internet para verificar tu autorización."
                } else if (snapshot.getBoolean("activo") == true) {
                    accessStatus.text = "Administrador autorizado"
                    findViewById<View>(R.id.rolePanel).visibility = View.GONE
                    if (!authorized) {
                        authorized = true
                        content.visibility = View.VISIBLE
                        listenData()
                    }
                } else {
                    stopData()
                    accessStatus.text = "Tu cuenta no tiene permiso de administrador. Solicita la habilitación al responsable del proyecto."
                }
            }
    }

    private fun stopData() {
        authorized = false
        subscriptions.forEach { it.remove() }
        subscriptions.clear()
        content.visibility = View.GONE
        findViewById<View>(R.id.rolePanel).visibility = View.VISIBLE
        cards.removeAllViews()
        events.removeAllViews()
        findViewById<LinearLayout>(R.id.auditList).removeAllViews()
        cardDocuments = emptyList()
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
                    findViewById<TextView>(R.id.doorStatus).text = "Puerta: sin datos"
                } else {
                    device = snapshot
                    deviceFromCache = snapshot?.metadata?.isFromCache != false
                    renderDevice()
                }
            }
        subscriptions += repository.db.collection("tarjetas").orderBy("nombre").limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!authorized) return@addSnapshotListener
                cardDocuments = snapshot?.documents.orEmpty()
                cardsCached = snapshot?.metadata?.isFromCache != false
                renderCards()
                findViewById<TextView>(R.id.cardsSync).text = if (error != null) readError(error)
                    else if (cardsCached) "Lista guardada · sin confirmación del servidor" else "Credenciales actualizadas en Firebase"
            }
        listOf("eventos", "eventosSimulados").forEach { collection ->
            subscriptions += repository.db.collection(collection).orderBy("fecha", Query.Direction.DESCENDING).limit(50)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (!authorized) return@addSnapshotListener
                    if (collection == "eventos") physicalEvents = snapshot?.documents.orEmpty()
                    else simulatedEvents = snapshot?.documents.orEmpty()
                    renderEvents()
                    findViewById<TextView>(R.id.historySync).text = if (error != null) readError(error)
                        else if (snapshot?.metadata?.isFromCache != false) "Historial guardado · sin confirmar con el servidor"
                        else "Hasta 50 eventos por origen · hora local del teléfono"
                }
        }
        subscriptions += repository.db.collection("auditoria").orderBy("fecha", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (!authorized) return@addSnapshotListener
                val list = findViewById<LinearLayout>(R.id.auditList)
                list.removeAllViews()
                if (error != null) { label(list, readError(error)); return@addSnapshotListener }
                if (snapshot == null || snapshot.isEmpty) label(list, "Aún no hay cambios administrativos.")
                snapshot?.documents?.forEach { doc ->
                    val action = when (doc.getString("accion")) { "ALTA" -> "Credencial registrada"; "REVOCAR" -> "Acceso revocado"; else -> "Credencial habilitada" }
                    val row = tile(list)
                    label(row, action, 18f)
                    label(row, AccessPolicy.maskedUid(doc.getString("tarjetaUid").orEmpty()) + " · " + formatDate(doc.getTimestamp("fecha")?.toDate()))
                    label(row, "Responsable: " + doc.getString("actorUid").orEmpty().take(12))
                }
            }
    }

    private fun renderCards() {
        if (!authorized) return
        cards.removeAllViews()
        val query = findViewById<EditText>(R.id.cardSearch).text.toString().trim()
        val documents = cardDocuments.filter { it.id.contains(query, true) || it.getString("nombre").orEmpty().contains(query, true) }
        if (documents.isEmpty()) label(cards, if (query.isEmpty()) "Aún no hay credenciales. Agrega la primera para comenzar." else "Sin coincidencias para esa búsqueda.")
        documents.forEach { document ->
            val name = document.getString("nombre").orEmpty()
            val enabled = document.getBoolean("habilitada") == true
            val row = tile(cards)
            label(row, name, 20f)
            label(row, AccessPolicy.maskedUid(document.id) + if (enabled) " · Activa" else " · Revocada")
            row.addView(Button(this).apply {
                text = "Ver credencial"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(name)
                        .setMessage("UID: " + document.id + "\nEstado: " + (if (enabled) "Activa" else "Revocada") +
                            "\nActualización: " + formatDate(document.getTimestamp("actualizadaEn")?.toDate()))
                        .setPositiveButton("Cerrar", null).show()
                }
            })
            row.addView(Button(this).apply {
                text = if (enabled) "Revocar acceso" else "Habilitar acceso"
                isEnabled = !cardsCached && !document.metadata.hasPendingWrites() && !busy
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(text)
                        .setMessage("¿Confirmas el cambio para " + name + "? Se aplicará a los nuevos intentos después de confirmar en Firebase.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Confirmar") { _, _ -> saveCard(document.id, name, !enabled, false) }.show()
                }
            })
        }
        findViewById<TextView>(R.id.cardCount).text = documents.size.toString() + " credenciales mostradas · máximo 100"
        findViewById<TextView>(R.id.activeCount).text = cardDocuments.count { it.getBoolean("habilitada") == true }.toString()
    }

    private fun renderEvents() {
        if (!authorized) return
        events.removeAllViews()
        val all = (physicalEvents + simulatedEvents).sortedByDescending { it.getTimestamp("fecha")?.seconds ?: 0 }
        val filtered = all.filter { doc ->
            when (eventFilter) {
                "Permitidos" -> doc.getString("resultado") == "PERMITIDO"
                "Rechazados" -> doc.getString("resultado") == "RECHAZADO"
                "Simulados" -> doc.reference.parent.id == "eventosSimulados"
                "Físicos" -> doc.reference.parent.id == "eventos"
                else -> true
            }
        }
        if (filtered.isEmpty()) label(events, "No hay eventos para este filtro. Realiza una lectura desde el punto de acceso.")
        filtered.forEach { doc ->
            val allowed = doc.getString("resultado") == "PERMITIDO"
            val simulated = doc.reference.parent.id == "eventosSimulados"
            val row = tile(events)
            label(row, if (allowed) "Acceso permitido" else "Acceso rechazado", 19f)
            label(row, (if (simulated) "SIMULACIÓN" else "DISPOSITIVO FÍSICO") + " · " + AccessPolicy.maskedUid(doc.getString("uidRFID").orEmpty()))
            label(row, formatDate(doc.getTimestamp("fecha")?.toDate()))
            row.setOnClickListener {
                AlertDialog.Builder(this).setTitle(if (simulated) "Detalle del intento simulado" else "Detalle del evento físico")
                    .setMessage("Evento: " + doc.id + "\nResultado: " + doc.getString("resultado").orEmpty() +
                        "\nMotivo: " + doc.getString("motivo").orEmpty() + "\nUID: " + AccessPolicy.maskedUid(doc.getString("uidRFID").orEmpty()) +
                        "\nOrigen: " + doc.getString("dispositivoId").orEmpty() + "\nFecha local: " + formatDate(doc.getTimestamp("fecha")?.toDate()))
                    .setPositiveButton("Cerrar", null).show()
            }
        }
        findViewById<TextView>(R.id.eventCount).text = all.size.toString()
        findViewById<TextView>(R.id.latestAttempt).text = all.firstOrNull()?.let {
            (if (it.getString("resultado") == "PERMITIDO") "Último intento permitido" else "Último intento rechazado") + "\n" +
                formatDate(it.getTimestamp("fecha")?.toDate()) + " · " + (if (it.reference.parent.id == "eventosSimulados") "Simulación" else "Dispositivo físico")
        } ?: "Sin intentos todavía. Abre el simulador en el segundo Android."
    }

    private fun tile(parent: LinearLayout): LinearLayout {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.outline))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        card.addView(row)
        parent.addView(card)
        return row
    }
    private fun renderDevice() {
        if (!authorized) return
        val snapshot = device
        val seen = snapshot?.getTimestamp("ultimaConexion")?.toDate()
        val online = !deviceFromCache && AccessPolicy.isOnline(seen?.time, System.currentTimeMillis())
        findViewById<TextView>(R.id.deviceStatus).text = when {
            snapshot == null || !snapshot.exists() -> "Sin datos del Arduino"
            deviceFromCache -> "Sin conexión a Firebase · datos guardados"
            online -> "Arduino conectado"
            else -> "Arduino sin comunicación"
        }
        val door = when (snapshot?.getString("puerta")) {
            "ABIERTA" -> "Abierta"
            "CERRADA" -> "Cerrada"
            else -> "Sin datos"
        }
        findViewById<TextView>(R.id.doorStatus).text = "Puerta: $door" + if (!online) " · estado sin confirmar" else ""
        findViewById<TextView>(R.id.lastSeen).text = "Último reporte: " + formatDate(seen)
        findViewById<TextView>(R.id.lastSync).text = "Última sincronización de tarjetas: " +
            formatDate(snapshot?.getTimestamp("ultimaSincronizacionTarjetas")?.toDate())
    }

    private fun showAddCard() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val name = EditText(this).apply { hint = "Nombre del titular"; maxLines = 1; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        val uid = EditText(this).apply { hint = "UID RFID hexadecimal"; maxLines = 1; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; isSaveEnabled = false }
        form.addView(name)
        form.addView(uid)
        label(form, "Ingresa el UID leído por el RC522 (4, 7 o 10 bytes).")
        val dialog = AlertDialog.Builder(this).setTitle("Agregar tarjeta").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = AccessPolicy.normalizeUid(uid.text.toString())
                val holder = name.text.toString().trim()
                when {
                    holder.isEmpty() || holder.length > 80 -> name.error = "Ingresa un nombre de 1 a 80 caracteres"
                    value == null -> uid.error = "Usa 8, 14 o 20 dígitos hexadecimales"
                    else -> { saveCard(value, holder, true, true); dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun manageSimulator() {
        val account = EditText(this).apply {
            hint = "UID de la cuenta del segundo Android"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Vincular punto de acceso")
            .setMessage("Copia el UID que aparece al iniciar sesión en el otro teléfono. Esta cuenta podrá simular lecturas; no administrar credenciales.")
            .setView(account).setNegativeButton("Cancelar", null)
            .setNeutralButton("Revocar", null).setPositiveButton("Habilitar", null).create()
        dialog.setOnShowListener {
            fun save(active: Boolean) {
                val uid = account.text.toString().trim()
                if (!uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    account.error = "Copia un UID de cuenta válido"
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
                        AlertDialog.Builder(this).setTitle(if (active) "Punto de acceso habilitado" else "Punto de acceso revocado")
                            .setMessage("El permiso fue confirmado en Firebase.").setPositiveButton("Aceptar", null).show()
                    } else {
                        account.error = "No se confirmó el cambio. Revisa conexión y permisos."
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

    private fun saveCard(uid: String, name: String, enabled: Boolean, create: Boolean) {
        if (!authorized || busy) return
        busy = true
        addCard.isEnabled = false
        renderCards()
        operationStatus.text = "Verificando autorización y guardando…"
        val user = auth.currentUser
        if (user == null) { busy = false; openLogin(); return }
        repository.db.collection("administradores").document(user.uid).get(Source.SERVER)
            .continueWithTask { task ->
                val admin = task.result
                check(admin?.getBoolean("activo") == true) { "Administrador no autorizado" }
                repository.saveCard(uid, name, enabled, create)
            }.addOnCompleteListener { task ->
                busy = false
                if (isDestroyed || isFinishing) return@addOnCompleteListener
                addCard.isEnabled = true
                renderCards()
                if (authorized) operationStatus.text = if (task.isSuccessful)
                    "Cambio confirmado en Firebase. El simulador ya puede validar el nuevo estado; el hardware debe sincronizarlo."
                else when (task.exception?.message) {
                    "La tarjeta ya existe" -> "La tarjeta ya está registrada. Puedes habilitarla desde la lista."
                    else -> "No se guardó el cambio. Revisa tu conexión y permisos; vuelve a intentarlo."
                }
            }
    }

    private fun readError(error: FirebaseFirestoreException): String = when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Acceso denegado. Revisa el administrador autorizado y las reglas de Firestore."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "No se puede conectar a Firebase. Revisa tu conexión."
        else -> "No se pudieron cargar los datos. Vuelve a verificar el acceso."
    }

    private fun label(parent: LinearLayout, text: String, size: Float = 15f) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(8), 0, dp(8))
            setTextIsSelectable(false)
        })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatDate(date: Date?): String = date?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it) } ?: "Sin datos"
    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
