package com.example.iot

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Checks actual inflated XML and recycled row permissions without touching Firebase. */
@RunWith(AndroidJUnit4::class)
class CrudLayoutTest {
    @Test fun nfcRecordsRoundTripAndRejectDifferentPhysicalIdentity() {
        val original = NfcCredentialRecord.create("04A39C21")
        val decoded = android.nfc.NdefMessage(android.nfc.NdefMessage(arrayOf(original)).toByteArray()).records.single()
        assertTrue(NfcCredentialRecord.matches(decoded, "04A39C21"))
        assertFalse(NfcCredentialRecord.matches(decoded, "FFFFFFFF"))
        assertFalse(NfcCredentialRecord.matches(android.nfc.NdefRecord.createTextRecord("es", "04A39C21"), "04A39C21"))
    }
    @Test fun credentialRowsExposeCrudAndDisableDeletedOrOfflineWrites() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_IoT)
            val parent = FrameLayout(context)
            var selected = ""
            val adapter = CredentialAdapter { _, command -> selected = command }
            adapter.submitList(listOf(CredentialRow("04A39C21", "Titular", true, false, true, "")))
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)
            assertTrue(holder.itemView.findViewById<Button>(R.id.editCredential).isEnabled)
            holder.itemView.findViewById<Button>(R.id.deleteCredential).performClick()
            assertEquals("DELETE", selected)
            val deletedAdapter = CredentialAdapter { _, _ -> fail("Deleted credential must not mutate") }
            deletedAdapter.submitList(listOf(CredentialRow("04A39C21", "Titular", false, true, true, "")))
            deletedAdapter.onBindViewHolder(holder, 0)
            assertFalse(holder.itemView.findViewById<Button>(R.id.editCredential).isEnabled)
            assertFalse(holder.itemView.findViewById<Button>(R.id.toggleCredential).isEnabled)
            assertFalse(holder.itemView.findViewById<Button>(R.id.deleteCredential).isEnabled)
            assertEquals(context.getString(R.string.credential_deleted),
                holder.itemView.findViewById<TextView>(R.id.credentialState).text.toString())
            val offlineAdapter = CredentialAdapter { _, _ -> }
            offlineAdapter.submitList(listOf(CredentialRow("04A39C21", "Titular", true, false, false, "")))
            offlineAdapter.onBindViewHolder(holder, 0)
            assertFalse(holder.itemView.findViewById<Button>(R.id.editCredential).isEnabled)
            assertTrue(holder.itemView.findViewById<Button>(R.id.viewCredential).isEnabled)
        }
    }

    @Test fun formsAndAllThreeListsAreDefinedInXml() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_IoT)
            val inflater = LayoutInflater.from(context)
            val form = inflater.inflate(R.layout.dialog_credential, FrameLayout(context), false)
            assertNotNull(form.findViewById<EditText>(R.id.holderInput))
            assertNotNull(form.findViewById<Button>(R.id.btnReadCredentialNfc))
            assertNotNull(form.findViewById<Button>(R.id.btnWriteCredentialNfc))
            assertFalse(form.findViewById<EditText>(R.id.rfidInput).isSaveEnabled)
            val screen = inflater.inflate(R.layout.activity_main, FrameLayout(context), false)
            listOf(R.id.cardList, R.id.eventList, R.id.auditList).forEach {
                assertTrue(screen.findViewById<android.view.View>(it) is androidx.recyclerview.widget.RecyclerView)
            }
        }
    }
}
