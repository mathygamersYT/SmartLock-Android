package com.example.iot

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.auth.FirebaseAuth
import java.text.DateFormat

/** Owns the credential form and its presentation; writes stay in the repository. */
class CredentialController(private val activity: AppCompatActivity,
    private val repository: AccessRepository, private val authorized: () -> Boolean) {
    val adapter = CredentialAdapter(::onAction)
    private var documents = emptyList<DocumentSnapshot>()
    private var cached = true
    private var busy = false
    private fun getString(id: Int, vararg args: Any) = activity.getString(id, *args)
    private fun status(message: String) { activity.findViewById<TextView>(R.id.operationStatus).text = message }

    fun clear() { documents = emptyList(); cached = true; adapter.submitList(emptyList()) }
    fun update(rows: List<DocumentSnapshot>, fromCache: Boolean) { documents = rows; cached = fromCache; render() }
    fun render() {
        if (!authorized()) return
        val query = activity.findViewById<EditText>(R.id.cardSearch).text.toString().trim()
        val showDeleted = activity.findViewById<android.widget.CheckBox>(R.id.showDeleted).isChecked
        val visible = documents.filter { (showDeleted || it.getBoolean("eliminada") != true) &&
            (it.id.contains(query, true) || it.getString("nombre").orEmpty().contains(query, true)) }
        adapter.submitList(visible.map { doc -> CredentialRow(doc.id, doc.getString("nombre").orEmpty(),
            doc.getBoolean("habilitada") == true, doc.getBoolean("eliminada") == true,
            !cached && !busy && !doc.metadata.hasPendingWrites(),
            doc.getTimestamp("actualizadaEn")?.toDate()?.let { DateFormat.getDateTimeInstance().format(it) } ?: getString(R.string.no_data)) })
        activity.findViewById<TextView>(R.id.cardCount).text = getString(R.string.card_count, visible.size)
        activity.findViewById<TextView>(R.id.activeCount).text = documents.count {
            it.getBoolean("habilitada") == true && it.getBoolean("eliminada") != true }.toString()
        activity.findViewById<View>(R.id.cardsEmpty).visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        activity.findViewById<Button>(R.id.btnAddCard).isEnabled = !busy && !cached
    }

    fun add() { if (authorized() && !busy && !cached) form(null) }
    private fun onAction(row: CredentialRow, action: String) {
        if (!authorized()) return
        if (action == "VIEW") {
            AlertDialog.Builder(activity).setTitle(row.name)
                .setMessage(getString(R.string.card_detail, AccessPolicy.maskedUid(row.uid), row.updated))
                .setPositiveButton(R.string.close, null).show()
            return
        }
        if (!row.mutable || row.deleted || busy || cached) return
        when (action) {
            "EDIT" -> form(row)
            "DELETE", "TOGGLE" -> AlertDialog.Builder(activity)
                .setTitle(if (action == "DELETE") R.string.delete_card else if (row.enabled) R.string.revoke_access else R.string.enable_access)
                .setMessage(getString(if (action == "DELETE") R.string.delete_confirmation else R.string.change_confirmation, row.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ -> mutate(row.uid,
                    if (action == "DELETE") CardChange.Delete else CardChange.Status(!row.enabled)) }.show()
        }
    }

    private fun form(row: CredentialRow?) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_credential, null)
        val name = view.findViewById<EditText>(R.id.holderInput)
        val uid = view.findViewById<EditText>(R.id.rfidInput)
        name.setText(row?.name.orEmpty())
        // UID is immutable and partially hidden while editing.
        uid.setText(row?.let { AccessPolicy.maskedUid(it.uid) }.orEmpty())
        uid.isEnabled = row == null
        val dialog = AlertDialog.Builder(activity).setTitle(if (row == null) R.string.add_card else R.string.edit_holder)
            .setView(view).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val holder = name.text.toString().trim()
                val rfid = row?.uid ?: AccessPolicy.normalizeUid(uid.text.toString())
                when {
                    holder.length !in 1..80 -> name.error = getString(R.string.name_invalid)
                    rfid == null -> uid.error = getString(R.string.uid_invalid)
                    !authorized() || busy || cached -> { dialog.dismiss(); status(getString(R.string.write_unavailable)) }
                    else -> { dialog.dismiss(); mutate(rfid, if (row == null) CardChange.Create(holder) else CardChange.Edit(holder)) }
                }
            }
        }
        dialog.show()
    }

    private fun mutate(uid: String, change: CardChange) {
        if (!authorized() || busy || cached) return
        val actor = FirebaseAuth.getInstance().currentUser?.uid ?: return
        busy = true; render(); status(getString(R.string.saving))
        repository.db.collection("administradores").document(actor).get(Source.SERVER).continueWithTask { result ->
            check(result.result?.getBoolean("activo") == true) { "ADMIN_REQUIRED" }
            repository.mutateCard(uid, change)
        }.addOnCompleteListener { task ->
            busy = false
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            if (!authorized()) { clear(); return@addOnCompleteListener }
            render()
            status(getString(if (task.isSuccessful) R.string.change_saved else R.string.change_failed))
        }
    }
}
