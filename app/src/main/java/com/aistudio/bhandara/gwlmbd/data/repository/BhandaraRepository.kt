package com.aistudio.bhandara.gwlmbd.data.repository

import android.util.Log
import com.aistudio.bhandara.gwlmbd.data.model.AdminLog
import com.aistudio.bhandara.gwlmbd.data.model.AppUpdateConfig
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.Meme
import com.aistudio.bhandara.gwlmbd.data.model.Report
import com.aistudio.bhandara.gwlmbd.data.model.SavedBhandara
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BhandaraRepository {
    private val tag = "BhandaraRepo"
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // In-memory cache for offline resiliency
    private val cachedBhandaras = mutableListOf<Bhandara>()
    private val cachedMemes = mutableListOf<Meme>()

    // Default regional memes if Firestore has none yet
    private val defaultMemes = listOf(
        Meme(id = "def1", text = "भंडारे को हमारा राष्ट्रीय भोजन घोषित किया जाना चाहिए 😂", city = "all", active = true),
        Meme(id = "def2", text = "शीतला माता की जय 🙏", city = "all", active = true),
        Meme(id = "def3", text = "जहाँ भंडारे का तंबू दिखा, समझो किस्मत खुल गई! 😋", city = "all", active = true),
        Meme(id = "def4", text = "भिंड वालों के लिए — दंदरौआ सरकार की जय 🙏", city = "bhind", active = true),
        Meme(id = "def5", text = "दंदरौआ धाम के मालपुए और भंडारे का कोई मुकाबला नहीं! 😂", city = "bhind", active = true),
        Meme(id = "def6", text = "मुरैना वालों के लिए — पटिया वाले बाबा की जय 🙏", city = "morena", active = true),
        Meme(id = "def7", text = "मुरैना की गजक मीठी और यहाँ का भंडारा अमृत! 😂", city = "morena", active = true),
        Meme(id = "def8", text = "ग्वालियर वालों के लिए — अचलेश्वर महादेव की कृपा से भंडारा ही भंडारा 🙏", city = "gwalior", active = true),
        Meme(id = "def9", text = "महाराज बाड़े की सैर और भंडारे की गरमा-गरम पूड़ी! 😂", city = "gwalior", active = true),
        Meme(id = "def10", text = "किला ग्वालियर का और भंडारा पूरे चंबल का! 🚩", city = "gwalior", active = true)
    )

    // ==========================================
    // PUBLIC BHANDARA QUERIES (Approved & Active)
    // ==========================================

    suspend fun getActiveBhandaras(city: String): List<Bhandara> {
        return try {
            val query = firestore.collection("bhandaras")
                .whereEqualTo("approvalStatus", "approved")
                .whereEqualTo("status", "active")
                .orderBy("createdAt", Query.Direction.DESCENDING)

            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Bhandara::class.java)?.copy(id = doc.id)
            }.filter { bhandara ->
                val matchesCity = if (city.isBlank() || city.equals("all", ignoreCase = true)) true
                else bhandara.city.equals(city, ignoreCase = true) || bhandara.getCityDisplayName().equals(city, ignoreCase = true)

                matchesCity && !bhandara.isExpired()
            }

            cachedBhandaras.clear()
            cachedBhandaras.addAll(list)
            list
        } catch (e: Exception) {
            Log.e(tag, "getActiveBhandaras error: ${e.message}", e)
            // Return cached data if offline
            if (cachedBhandaras.isNotEmpty()) {
                cachedBhandaras.filter { bhandara ->
                    val matchesCity = if (city.isBlank() || city.equals("all", ignoreCase = true)) true
                    else bhandara.city.equals(city, ignoreCase = true)
                    matchesCity && !bhandara.isExpired()
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getTodayBhandaras(city: String): List<Bhandara> {
        val allActive = getActiveBhandaras(city)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return allActive.filter { b ->
            b.date == todayStr || isDateToday(b.date)
        }
    }

    suspend fun getUpcomingBhandaras(city: String): List<Bhandara> {
        val allActive = getActiveBhandaras(city)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return allActive.filter { b ->
            b.date > todayStr
        }
    }

    suspend fun searchBhandaras(queryText: String, city: String): List<Bhandara> {
        val q = queryText.trim().lowercase()
        val allActive = getActiveBhandaras(city)
        if (q.isBlank()) return allActive

        return allActive.filter { b ->
            b.name.lowercase().contains(q) ||
            b.area.lowercase().contains(q) ||
            b.landmark.lowercase().contains(q) ||
            b.address.lowercase().contains(q) ||
            b.description.lowercase().contains(q) ||
            b.city.lowercase().contains(q)
        }
    }

    suspend fun getBhandaraById(id: String): Bhandara? {
        return try {
            val doc = firestore.collection("bhandaras").document(id).get().await()
            doc.toObject(Bhandara::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            Log.e(tag, "getBhandaraById error: ${e.message}")
            cachedBhandaras.find { it.id == id }
        }
    }

    // ==========================================
    // USER CONTRIBUTIONS
    // ==========================================

    suspend fun addBhandara(bhandara: Bhandara): String {
        // Direct publication: new submissions are approved immediately for all users!
        val submission = bhandara.copy(
            approvalStatus = "approved",
            status = "active",
            createdAt = if (bhandara.createdAt > 0L) bhandara.createdAt else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        var newId = "local_${System.currentTimeMillis()}"
        try {
            val ref = firestore.collection("bhandaras").add(submission).await()
            newId = ref.id
        } catch (e: Exception) {
            Log.e(tag, "Firestore addBhandara error (handled): ${e.message}", e)
        }

        val created = submission.copy(id = newId)
        cachedBhandaras.add(0, created)
        return newId
    }

    suspend fun getUserSubmittedBhandaras(auth0UserId: String): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras")
                .whereEqualTo("createdByAuth0Id", auth0UserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.toObject(Bhandara::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            Log.e(tag, "getUserSubmittedBhandaras error: ${e.message}")
            emptyList()
        }
    }

    // ==========================================
    // SAVED BHANDARAS
    // ==========================================

    suspend fun saveBhandara(bhandaraId: String, userId: String) {
        val existing = firestore.collection("saved_bhandaras")
            .whereEqualTo("bhandaraId", bhandaraId)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        if (existing.isEmpty) {
            val saved = SavedBhandara(
                bhandaraId = bhandaraId,
                userId = userId,
                savedAt = System.currentTimeMillis()
            )
            firestore.collection("saved_bhandaras").add(saved).await()
        }
    }

    suspend fun unsaveBhandara(bhandaraId: String, userId: String) {
        val existing = firestore.collection("saved_bhandaras")
            .whereEqualTo("bhandaraId", bhandaraId)
            .whereEqualTo("userId", userId)
            .get()
            .await()

        for (doc in existing.documents) {
            firestore.collection("saved_bhandaras").document(doc.id).delete().await()
        }
    }

    suspend fun isBhandaraSaved(bhandaraId: String, userId: String): Boolean {
        if (userId.isBlank()) return false
        val existing = firestore.collection("saved_bhandaras")
            .whereEqualTo("bhandaraId", bhandaraId)
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return !existing.isEmpty
    }

    suspend fun getSavedBhandaras(userId: String): List<Bhandara> {
        return try {
            val savedDocs = firestore.collection("saved_bhandaras")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val ids = savedDocs.documents.mapNotNull { it.getString("bhandaraId") }
            if (ids.isEmpty()) return emptyList()

            val bhandaraList = mutableListOf<Bhandara>()
            for (id in ids) {
                val b = getBhandaraById(id)
                if (b != null) {
                    bhandaraList.add(b)
                }
            }
            bhandaraList
        } catch (e: Exception) {
            Log.e(tag, "getSavedBhandaras error: ${e.message}")
            emptyList()
        }
    }

    // ==========================================
    // REPORTS
    // ==========================================

    suspend fun reportBhandara(report: Report): String {
        val doc = report.copy(
            createdAt = System.currentTimeMillis(),
            status = "pending"
        )
        val ref = firestore.collection("reports").add(doc).await()
        return ref.id
    }

    suspend fun getUserReports(reportedBy: String): List<Report> {
        return try {
            val snapshot = firestore.collection("reports")
                .whereEqualTo("reportedBy", reportedBy)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(Report::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==========================================
    // MEME SYSTEM
    // ==========================================

    suspend fun getMemes(city: String): List<Meme> {
        return try {
            val snapshot = firestore.collection("memes")
                .get()
                .await()

            val dbMemes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Meme::class.java)?.copy(id = doc.id)
            }.filter { it.active && it.isActive }

            val combined = mutableListOf<Meme>()
            combined.addAll(cachedMemes)
            for (m in dbMemes) {
                if (combined.none { it.id == m.id || (it.text == m.text && it.city == m.city) }) {
                    combined.add(m)
                }
            }
            if (combined.isEmpty()) {
                combined.addAll(defaultMemes)
            }

            cachedMemes.clear()
            cachedMemes.addAll(combined)

            combined.filter { m ->
                m.city.equals("all", ignoreCase = true) || m.city.equals(city, ignoreCase = true) || city.isBlank() || city.equals("all", ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(tag, "getMemes error (handled): ${e.message}", e)
            val fallback = if (cachedMemes.isNotEmpty()) cachedMemes else defaultMemes
            fallback.filter { m ->
                m.city.equals("all", ignoreCase = true) || m.city.equals(city, ignoreCase = true) || city.isBlank() || city.equals("all", ignoreCase = true)
            }
        }
    }

    // ==========================================
    // ADMIN FUNCTIONS (SECURE & SAFE)
    // ==========================================

    suspend fun getPendingBhandaras(): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras")
                .whereEqualTo("approvalStatus", "pending")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(Bhandara::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            Log.e(tag, "getPendingBhandaras error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getExpiredBhandaras(): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.toObject(Bhandara::class.java)?.copy(id = doc.id)
            }.filter { it.isExpired() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun approveBhandara(id: String, adminId: String) {
        firestore.collection("bhandaras").document(id)
            .update(
                mapOf(
                    "approvalStatus" to "approved",
                    "status" to "active",
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        logAdminAction(adminId, "approve", id, "Bhandara approved by admin")
    }

    suspend fun rejectBhandara(id: String, adminId: String) {
        firestore.collection("bhandaras").document(id)
            .update(
                mapOf(
                    "approvalStatus" to "rejected",
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        logAdminAction(adminId, "reject", id, "Bhandara rejected by admin")
    }

    suspend fun archiveBhandara(id: String, adminId: String) {
        firestore.collection("bhandaras").document(id)
            .update(
                mapOf(
                    "status" to "expired",
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        logAdminAction(adminId, "archive", id, "Bhandara archived by admin")
    }

    suspend fun updateBhandaraByAdmin(bhandara: Bhandara, adminId: String) {
        firestore.collection("bhandaras").document(bhandara.id)
            .set(bhandara.copy(updatedAt = System.currentTimeMillis()))
            .await()
        logAdminAction(adminId, "edit", bhandara.id, "Bhandara edited by admin")
    }

    suspend fun getAllMemesForAdmin(): List<Meme> {
        return try {
            val snapshot = firestore.collection("memes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { it.toObject(Meme::class.java)?.copy(id = it.id) }
            if (list.isEmpty()) defaultMemes else list
        } catch (e: Exception) {
            defaultMemes
        }
    }

    suspend fun addMeme(meme: Meme, adminId: String? = null): String {
        val submission = meme.copy(
            active = true,
            isActive = true,
            createdAt = if (meme.createdAt > 0L) meme.createdAt else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        var newId = "local_${System.currentTimeMillis()}"
        try {
            val ref = firestore.collection("memes").add(submission).await()
            newId = ref.id
            if (!adminId.isNullOrBlank()) {
                try {
                    logAdminAction(adminId, "meme_add", ref.id, "Meme added: ${meme.text}")
                } catch (e: Exception) {
                    Log.w(tag, "logAdminAction failed (handled): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Firestore addMeme error (handled): ${e.message}", e)
        }

        val finalMeme = submission.copy(id = newId)
        cachedMemes.add(0, finalMeme)
        return newId
    }

    suspend fun toggleMeme(memeId: String, active: Boolean, adminId: String? = null) {
        try {
            firestore.collection("memes").document(memeId)
                .update("active", active, "isActive", active, "updatedAt", System.currentTimeMillis())
                .await()
            if (!adminId.isNullOrBlank()) {
                logAdminAction(adminId, "meme_toggle", memeId, "Meme active status set to $active")
            }
        } catch (e: Exception) {
            Log.e(tag, "toggleMeme error (handled): ${e.message}")
        }
    }

    suspend fun deleteMeme(memeId: String, adminId: String? = null) {
        try {
            firestore.collection("memes").document(memeId).delete().await()
            if (!adminId.isNullOrBlank()) {
                logAdminAction(adminId, "meme_delete", memeId, "Meme deleted")
            }
        } catch (e: Exception) {
            Log.e(tag, "deleteMeme error (handled): ${e.message}")
        }
        cachedMemes.removeAll { it.id == memeId }
    }

    suspend fun getAllReports(): List<Report> {
        return try {
            val snapshot = firestore.collection("reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(Report::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateReportStatus(reportId: String, status: String, adminId: String) {
        firestore.collection("reports").document(reportId)
            .update("status", status)
            .await()
        logAdminAction(adminId, "report_update", reportId, "Report marked as $status")
    }

    // ==========================================
    // APP UPDATE SYSTEM
    // ==========================================

    suspend fun getAppUpdateConfig(): AppUpdateConfig? {
        return try {
            val doc = firestore.collection("app_config").document("app_update").get().await()
            if (doc.exists()) {
                doc.toObject(AppUpdateConfig::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "getAppUpdateConfig error (handled): ${e.message}")
            null
        }
    }

    suspend fun setAppUpdateConfig(config: AppUpdateConfig, adminId: String? = null) {
        try {
            val data = config.copy(updatedAt = System.currentTimeMillis())
            firestore.collection("app_config").document("app_update")
                .set(data)
                .await()
            if (!adminId.isNullOrBlank()) {
                logAdminAction(adminId, "update_config", "app_update", "Updated version to v${config.latestVersionName} (${config.latestVersionCode})")
            }
        } catch (e: Exception) {
            Log.e(tag, "setAppUpdateConfig error: ${e.message}", e)
            throw e
        }
    }

    suspend fun logAdminAction(adminId: String, action: String, targetId: String, details: String) {
        try {
            val log = AdminLog(
                adminId = adminId,
                action = action,
                targetId = targetId,
                timestamp = System.currentTimeMillis(),
                details = details
            )
            firestore.collection("admin_logs").add(log).await()
        } catch (e: Exception) {
            Log.e(tag, "logAdminAction failed: ${e.message}")
        }
    }

    private fun isDateToday(dateStr: String): Boolean {
        if (dateStr.isBlank()) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return false
            val calToday = Calendar.getInstance()
            val calDate = Calendar.getInstance().apply { time = date }

            calToday.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) &&
            calToday.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }
}
