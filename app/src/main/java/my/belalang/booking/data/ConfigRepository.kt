package my.belalang.booking.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ConfigLoadResult(val configs: Map<String, StepConfig>, val error: String?)

/** Membaca koleksi `api_configs` dari Cloud Firestore. Tiada nilai API di-hardcode. */
class ConfigRepository(private val context: Context) {

    suspend fun load(): ConfigLoadResult {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return ConfigLoadResult(
                emptyMap(),
                "Firebase belum dikonfigurasi: letak app/google-services.json dan bina semula.",
            )
        }
        return try {
            val snap = FirebaseFirestore.getInstance().collection(COLLECTION).get().awaitResult()
            val map = snap.documents.associate { doc ->
                doc.id to StepConfig.fromMap(doc.id, doc.data ?: emptyMap())
            }
            ConfigLoadResult(map, null)
        } catch (e: Exception) {
            ConfigLoadResult(emptyMap(), "Gagal baca Firestore: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val COLLECTION = "api_configs"
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}
