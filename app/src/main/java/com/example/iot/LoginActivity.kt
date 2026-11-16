package com.example.iot

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class LoginActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var actions: List<Button>
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val root = findViewById<View>(R.id.loginRoot)
        val padding = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        email = findViewById(R.id.etEmail)
        password = findViewById(R.id.etPassword)
        // Never persist the password in the activity's saved view state.
        password.isSaveEnabled = false
        status = findViewById(R.id.tvAuthStatus)
        progress = findViewById(R.id.authProgress)
        val signIn = findViewById<Button>(R.id.btnLogin)
        val register = findViewById<Button>(R.id.btnRegister)
        val reset = findViewById<Button>(R.id.btnResetPassword)
        actions = listOf(signIn, register, reset)
        signIn.setOnClickListener { authenticate(false) }
        register.setOnClickListener { authenticate(true) }
        reset.setOnClickListener { resetPassword() }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) openMain()
    }

    private fun validEmail(): String? {
        val value = email.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            email.error = getString(R.string.ui_ingresa_un_correo_valido)
            email.requestFocus()
            return null
        }
        return value
    }

    private fun authenticate(register: Boolean) {
        if (busy) return
        val address = validEmail() ?: return
        val secret = password.text.toString()
        if (secret.isEmpty() || (register && secret.length < 6)) {
            password.error = if (register) getString(R.string.ui_usa_al_menos_6_caracteres) else getString(R.string.ui_ingresa_tu_contrasena)
            password.requestFocus()
            return
        }
        setBusy(true)
        val task = if (register) auth.createUserWithEmailAndPassword(address, secret)
            else auth.signInWithEmailAndPassword(address, secret)
        task.addOnCompleteListener(this) {
            setBusy(false)
            if (it.isSuccessful) {
                password.text.clear()
                openMain()
            } else {
                status.text = errorMessage(it.exception)
            }
        }
    }

    private fun resetPassword() {
        if (busy) return
        val address = validEmail() ?: return
        setBusy(true)
        auth.sendPasswordResetEmail(address).addOnCompleteListener(this) {
            setBusy(false)
            status.text = if (it.isSuccessful)
                getString(R.string.ui_si_existe_una_cuenta_con_ese_correo_recibiras_instrucci)
            else errorMessage(it.exception)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        actions.forEach { it.isEnabled = !value }
        email.isEnabled = !value
        password.isEnabled = !value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        if (value) status.text = ""
    }

    private fun errorMessage(error: Exception?): String {
        val details = error?.message.orEmpty()
        if (details.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
            return getString(R.string.ui_falta_configurar_firebase_authentication_en_la_consola_)
        }
        return when (error) {
        is FirebaseNetworkException -> getString(R.string.ui_sin_conexion_revisa_tu_internet_e_intentalo_nuevamente)
        is FirebaseTooManyRequestsException -> getString(R.string.ui_demasiados_intentos_espera_un_momento_e_intentalo_nueva)
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> getString(R.string.ui_activa_el_proveedor_correo_contrasena_en_firebase_authe)
            "ERROR_WEAK_PASSWORD" -> getString(R.string.ui_la_contrasena_no_cumple_los_requisitos_de_seguridad_del)
            "ERROR_EMAIL_ALREADY_IN_USE" -> getString(R.string.ui_no_se_pudo_crear_la_cuenta_prueba_iniciar_sesion_o_recu)
            else -> getString(R.string.ui_no_se_pudo_autenticar_revisa_tus_datos_o_recupera_tu_co)
        }
        else -> getString(R.string.ui_no_se_pudo_completar_la_solicitud_intentalo_nuevamente)
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
