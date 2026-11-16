package com.example.iot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

data class CredentialRow(val uid: String, val name: String, val enabled: Boolean,
    val deleted: Boolean, val mutable: Boolean, val updated: String)

class CredentialAdapter(private val action: (CredentialRow, String) -> Unit) :
    ListAdapter<CredentialRow, CredentialAdapter.Holder>(object : DiffUtil.ItemCallback<CredentialRow>() {
        override fun areItemsTheSame(a: CredentialRow, b: CredentialRow) = a.uid == b.uid
        override fun areContentsTheSame(a: CredentialRow, b: CredentialRow) = a == b
    }) {
    class Holder(view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.row_credential, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        val view = holder.itemView
        view.findViewById<TextView>(R.id.credentialName).text = row.name
        view.findViewById<TextView>(R.id.credentialUid).text = AccessPolicy.maskedUid(row.uid)
        view.findViewById<TextView>(R.id.credentialState).setText(when {
            row.deleted -> R.string.credential_deleted
            row.enabled -> R.string.credential_active
            else -> R.string.credential_revoked
        })
        mapOf(R.id.viewCredential to "VIEW", R.id.editCredential to "EDIT",
            R.id.toggleCredential to "TOGGLE", R.id.deleteCredential to "DELETE").forEach { (id, command) ->
            view.findViewById<Button>(id).apply {
                isEnabled = command == "VIEW" || (row.mutable && !row.deleted)
                setOnClickListener { action(row, command) }
            }
        }
        view.findViewById<Button>(R.id.toggleCredential).setText(
            if (row.enabled) R.string.revoke_access else R.string.enable_access)
    }
}
