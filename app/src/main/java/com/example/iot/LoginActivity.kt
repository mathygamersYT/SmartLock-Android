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
            email.error = "Ingresa un correo válido"
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
            password.error = if (register) "Usa al menos 6 caracteres" else "Ingresa tu contraseña"
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
                "Si existe una cuenta con ese correo, recibirás instrucciones para recuperar tu contraseña."
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
            return "Falta configurar Firebase Authentication. En la consola del proyecto iot-aki-d467d, abre Authentication, pulsa Comenzar y activa Correo/contraseña."
        }
        return when (error) {
        is FirebaseNetworkException -> "Sin conexión. Revisa tu internet e inténtalo nuevamente."
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento e inténtalo nuevamente."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> "Activa el proveedor Correo/contraseña en Firebase Authentication."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple los requisitos de seguridad del proyecto."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "No se pudo crear la cuenta. Prueba iniciar sesión o recuperar tu contraseña."
            else -> "No se pudo autenticar. Revisa tus datos o recupera tu contraseña."
        }
        else -> "No se pudo completar la solicitud. Inténtalo nuevamente."
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
