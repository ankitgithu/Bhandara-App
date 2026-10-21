package com.aistudio.bhandara.gwlmbd.data.repository

import android.util.Log
import com.aistudio.bhandara.gwlmbd.data.model.AdminLog
import com.aistudio.bhandara.gwlmbd.data.model.AppUpdateConfig
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.Meme
import com.aistudio.bhandara.gwlmbd.data.model.Report
import com.aistudio.bhandara.gwlmbd.data.model.SavedBhandara
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BhandaraRepository {
    private val tag = "BhandaraRepo"
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // In-memory cache for offline resiliency
    private val cachedBhandaras = mutableListOf<Bhandara>()
    private val cachedAllActiveBhandaras = mutableListOf<Bhandara>()
    private val cachedMemes = mutableListOf<Meme>()

    fun getCachedBhandarasForCity(city: String): List<Bhandara> {
        return cachedAllActiveBhandaras.filter { it.matchesCity(city) && !it.isExpired() }
            .sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }
    }

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
    // BACKWARD-COMPATIBLE FIELD MAPPER
    // ==========================================

    /**
     * Converts a Firestore document into a Bhandara object safely supporting multiple schema variations.
     */
    fun fromDocument(doc: DocumentSnapshot): Bhandara {
        val data = doc.data ?: return doc.toObject(Bhandara::class.java)?.copy(id = doc.id) ?: Bhandara(id = doc.id)

        val name = (data["name"] ?: data["bhandaraName"] ?: data["title"] ?: "").toString()
        val city = (data["city"] ?: data["district"] ?: data["cityDisplayName"] ?: "gwalior").toString()
        val area = (data["area"] ?: data["location"] ?: "").toString()
        val landmark = (data["landmark"] ?: "").toString()
        val description = (data["description"] ?: data["details"] ?: "").toString()
        val address = (data["address"] ?: data["fullAddress"] ?: "").toString()

        val latitude = when (val lat = data["latitude"] ?: data["lat"]) {
            is Number -> lat.toDouble()
            is String -> lat.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        val longitude = when (val lng = data["longitude"] ?: data["lng"] ?: data["lon"]) {
            is Number -> lng.toDouble()
            is String -> lng.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        val mapsUrl = (data["mapsUrl"] ?: "").toString()
        val date = (data["date"] ?: data["eventDate"] ?: "").toString()
        val startTime = (data["startTime"] ?: data["time"] ?: "").toString()
        val endTime = (data["endTime"] ?: "").toString()

        val startDateTime = parseTimestamp(data["startDateTime"])
        val endDateTime = parseTimestamp(data["endDateTime"])
        val createdAt = parseTimestamp(data["createdAt"])
        val updatedAt = parseTimestamp(data["updatedAt"])

        val contactName = (data["contactName"] ?: data["organizerName"] ?: "").toString()
        val contactPhone = (data["contactPhone"] ?: data["phone"] ?: data["mobile"] ?: "").toString()
        val createdBy = (data["createdBy"] ?: "अनाम भक्त").toString()
        val createdByAuth0Id = (data["createdByAuth0Id"] ?: "").toString()

        val status = (data["status"] ?: "active").toString()
        val approvalStatus = (data["approvalStatus"] ?: if (data["approved"] == false) "pending" else "approved").toString()

        return Bhandara(
            id = doc.id,
            name = name,
            city = city,
            area = area,
            landmark = landmark,
            description = description,
            address = address,
            latitude = latitude,
            longitude = longitude,
            mapsUrl = mapsUrl,
            date = date,
            startTime = startTime,
            endTime = endTime,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            contactName = contactName,
            contactPhone = contactPhone,
            createdBy = createdBy,
            createdByAuth0Id = createdByAuth0Id,
            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
            updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis(),
            approvalStatus = approvalStatus,
            status = status
        )
    }

    private fun parseTimestamp(raw: Any?): Long {
        return when (raw) {
            is Number -> raw.toLong()
            is Timestamp -> raw.toDate().time
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    // ==========================================
    // PUBLIC BHANDARA QUERIES (Safe & Resilient)
    // ==========================================

    /**
     * Retrieves all active Bhandaras for public display.
     * Guaranteed NO composite index requirement and backward-compatible with all documents.
     */
    suspend fun getActiveBhandaras(city: String): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras").get().await()
            val allDocs = snapshot.documents.map { doc -> fromDocument(doc) }

            val activeList = allDocs.filter { bhandara ->
                val isRejected = bhandara.approvalStatus.equals("rejected", ignoreCase = true)
                val isArchived = bhandara.status.equals("expired", ignoreCase = true) ||
                                 bhandara.status.equals("deleted", ignoreCase = true) ||
                                 bhandara.status.equals("archived", ignoreCase = true)
                !isRejected && !isArchived && !bhandara.isExpired()
            }

            val expiredList = allDocs.filter { it.isExpired() }
            val filteredList = activeList.filter { it.matchesCity(city) }
                .sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }

            // Development Debug Logging (as specified in requirements)
            val projectId = try { firestore.app.options.projectId } catch (_: Exception) { "bhandara-2f5ce" }
            Log.d("BhandaraDebug", "Firebase project: $projectId")
            Log.d("BhandaraDebug", "Bhandara collection: /bhandaras")
            Log.d("BhandaraDebug", "Documents fetched: ${allDocs.size} (IDs: ${allDocs.map { it.id }})")
            Log.d("BhandaraDebug", "Active Bhandaras: ${activeList.size}")
            Log.d("BhandaraDebug", "Expired Bhandaras: ${expiredList.size}")
            Log.d("BhandaraDebug", "District filter: '$city' -> Matched: ${filteredList.size}")

            cachedAllActiveBhandaras.clear()
            cachedAllActiveBhandaras.addAll(activeList)

            cachedBhandaras.clear()
            cachedBhandaras.addAll(filteredList)
            filteredList
        } catch (e: Exception) {
            Log.e(tag, "getActiveBhandaras error: ${e.message}", e)
            if (cachedBhandaras.isNotEmpty()) {
                cachedBhandaras.filter { it.matchesCity(city) && !it.isExpired() }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Realtime flow listener for active Bhandaras.
     */
    fun listenToActiveBhandaras(city: String): Flow<List<Bhandara>> = callbackFlow {
        val listener = firestore.collection("bhandaras")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listenToActiveBhandaras error: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val allDocs = snapshot.documents.map { doc -> fromDocument(doc) }
                    val allActive = allDocs.filter { bhandara ->
                        val isRejected = bhandara.approvalStatus.equals("rejected", ignoreCase = true)
                        val isArchived = bhandara.status.equals("expired", ignoreCase = true) ||
                                         bhandara.status.equals("deleted", ignoreCase = true)
                        !isRejected && !isArchived && !bhandara.isExpired()
                    }
                    cachedAllActiveBhandaras.clear()
                    cachedAllActiveBhandaras.addAll(allActive)

                    val activeList = allActive.filter { it.matchesCity(city) }
                        .sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }

                    cachedBhandaras.clear()
                    cachedBhandaras.addAll(activeList)
                    trySend(activeList)
                }
            }
        awaitClose { listener.remove() }
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
            if (doc.exists()) {
                fromDocument(doc)
            } else {
                cachedBhandaras.find { it.id == id }
            }
        } catch (e: Exception) {
            Log.e(tag, "getBhandaraById error: ${e.message}")
            cachedBhandaras.find { it.id == id }
        }
    }

    // ==========================================
    // USER CONTRIBUTIONS
    // ==========================================

    suspend fun addBhandara(bhandara: Bhandara): String {
        // Direct publication: new submissions are approved and active immediately for all users!
        val startMillis = if (bhandara.startDateTime > 0L) bhandara.startDateTime else System.currentTimeMillis()
        val endMillis = if (bhandara.endDateTime > 0L) bhandara.endDateTime else Bhandara.parseEventEndMillis(bhandara.date, bhandara.endTime)

        val submission = bhandara.copy(
            approvalStatus = "approved",
            status = "active",
            startDateTime = startMillis,
            endDateTime = endMillis,
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
            val snapshot = firestore.collection("bhandaras").get().await()
            snapshot.documents.map { fromDocument(it) }
                .filter { it.createdByAuth0Id == auth0UserId }
                .sortedByDescending { it.createdAt }
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
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(Report::class.java)?.copy(id = it.id) }
                .sortedByDescending { it.createdAt }
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

    /**
     * Returns all active Bhandaras in Firestore for Admin Panel.
     * Shows all 4 existing records without accidental filtering!
     */
    suspend fun getAllActiveBhandarasForAdmin(): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras").get().await()
            val allDocs = snapshot.documents.map { fromDocument(it) }

            val active = allDocs.filter { bhandara ->
                val isRejected = bhandara.approvalStatus.equals("rejected", ignoreCase = true)
                val isArchived = bhandara.status.equals("expired", ignoreCase = true) ||
                                 bhandara.status.equals("deleted", ignoreCase = true) ||
                                 bhandara.status.equals("archived", ignoreCase = true)
                !isRejected && !isArchived && !bhandara.isExpired()
            }.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }

            Log.d("BhandaraDebug", "getAllActiveBhandarasForAdmin: Found ${active.size} active bhandaras")
            active
        } catch (e: Exception) {
            Log.e(tag, "getAllActiveBhandarasForAdmin error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllExpiredBhandarasForAdmin(): List<Bhandara> {
        return try {
            val snapshot = firestore.collection("bhandaras").get().await()
            val allDocs = snapshot.documents.map { fromDocument(it) }

            allDocs.filter {
                (it.isExpired() ||
                it.status.equals("expired", ignoreCase = true) ||
                it.status.equals("archived", ignoreCase = true)) &&
                !it.status.equals("deleted", ignoreCase = true)
            }.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }
        } catch (e: Exception) {
            Log.e(tag, "getAllExpiredBhandarasForAdmin error: ${e.message}")
            emptyList()
        }
    }

    // Backward compatibility for existing Admin calls
    suspend fun getPendingBhandaras(): List<Bhandara> {
        return getAllActiveBhandarasForAdmin()
    }

    suspend fun getExpiredBhandaras(): List<Bhandara> {
        return getAllExpiredBhandarasForAdmin()
    }

    suspend fun deleteBhandara(id: String, adminId: String) {
        // Step 1: Attempt to establish Firebase Auth session safely without throwing
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            Log.w(tag, "FirebaseAuth anonymous sign-in skipped: ${e.message}")
        }

        var deletionSuccessful = false

        // Step 2: Try direct document deletion from Firestore across all possible collection name variations
        val collections = listOf("bhandaras", "Bhandara", "bhandara")
        for (col in collections) {
            try {
                firestore.collection(col).document(id).delete().await()
                deletionSuccessful = true
                Log.d(tag, "Hard delete succeeded on $col for $id")
            } catch (e: Exception) {
                Log.w(tag, "Hard delete attempt on $col for $id failed: ${e.message}")
            }
        }

        // Step 3: If hard delete didn't succeed (e.g. blocked by Firestore rules), mark document as deleted
        // so it immediately vanishes from all user screens, admin lists, and real-time listeners
        if (!deletionSuccessful) {
            val softDeleteData = mapOf(
                "status" to "deleted",
                "approvalStatus" to "deleted",
                "isDeleted" to true,
                "deletedAt" to System.currentTimeMillis(),
                "deletedBy" to adminId,
                "updatedAt" to System.currentTimeMillis()
            )
            for (col in collections) {
                try {
                    firestore.collection(col).document(id).update(softDeleteData).await()
                    deletionSuccessful = true
                    Log.d(tag, "Soft delete succeeded on $col for $id")
                } catch (e: Exception) {
                    Log.w(tag, "Soft delete attempt on $col for $id failed: ${e.message}")
                }
            }
        }

        // Step 4: Always purge from in-memory caches immediately
        cachedBhandaras.removeAll { it.id == id }
        cachedAllActiveBhandaras.removeAll { it.id == id }

        try {
            logAdminAction(adminId, "delete", id, "Bhandara permanently deleted by admin ($id)")
        } catch (e: Exception) {
            Log.w(tag, "logAdminAction failed: ${e.message}")
        }

        if (!deletionSuccessful) {
            throw Exception("डेटाबेस से हटाने की अनुमति नहीं मिली। कृपया Firebase Console में Rules चेक करें।")
        }
    }

    fun listenToAllActiveBhandarasForAdmin(): Flow<List<Bhandara>> = callbackFlow {
        val listener = firestore.collection("bhandaras")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listenToAllActiveBhandarasForAdmin error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val allDocs = snapshot.documents.map { fromDocument(it) }
                    val active = allDocs.filter { bhandara ->
                        val isRejected = bhandara.approvalStatus.equals("rejected", ignoreCase = true)
                        val isArchived = bhandara.status.equals("expired", ignoreCase = true) ||
                                         bhandara.status.equals("deleted", ignoreCase = true) ||
                                         bhandara.status.equals("archived", ignoreCase = true)
                        !isRejected && !isArchived && !bhandara.isExpired()
                    }.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }
                    trySend(active)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenToAllExpiredBhandarasForAdmin(): Flow<List<Bhandara>> = callbackFlow {
        val listener = firestore.collection("bhandaras")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listenToAllExpiredBhandarasForAdmin error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val allDocs = snapshot.documents.map { fromDocument(it) }
                    val expired = allDocs.filter {
                        (it.isExpired() ||
                         it.status.equals("expired", ignoreCase = true) ||
                         it.status.equals("archived", ignoreCase = true)) &&
                        !it.status.equals("deleted", ignoreCase = true)
                    }.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.startDateTime }
                    trySend(expired)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenToReports(): Flow<List<Report>> = callbackFlow {
        val listener = firestore.collection("reports")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listenToReports error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Report::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.createdAt }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenToMemesForAdmin(): Flow<List<Meme>> = callbackFlow {
        val listener = firestore.collection("memes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listenToMemesForAdmin error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(Meme::class.java)?.copy(id = it.id) }
                        .sortedByDescending { it.createdAt }
                    trySend(if (list.isEmpty()) defaultMemes else list)
                }
            }
        awaitClose { listener.remove() }
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
            val snapshot = firestore.collection("memes").get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(Meme::class.java)?.copy(id = it.id) }
                .sortedByDescending { it.createdAt }
            if (list.isEmpty()) defaultMemes else list
        } catch (e: Exception) {
            defaultMemes
        }
    }

    suspend fun addMeme(meme: Meme, adminId: String? = null): String {
        if (adminId.isNullOrBlank()) {
            throw SecurityException("मीम केवल अधिकृत व्यवस्थापक (Admin) ही जोड़ सकते हैं।")
        }
        val submission = meme.copy(
            active = true,
            isActive = true,
            createdAt = if (meme.createdAt > 0L) meme.createdAt else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val ref = firestore.collection("memes").add(submission).await()
        if (!adminId.isNullOrBlank()) {
            try {
                logAdminAction(adminId, "meme_add", ref.id, "Meme added: ${meme.text}")
            } catch (e: Exception) {
                Log.w(tag, "logAdminAction failed (handled): ${e.message}")
            }
        }

        val finalMeme = submission.copy(id = ref.id)
        cachedMemes.add(0, finalMeme)
        return ref.id
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
            val snapshot = firestore.collection("reports").get().await()
            snapshot.documents.mapNotNull { it.toObject(Report::class.java)?.copy(id = it.id) }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateReportStatus(reportId: String, status: String, adminId: String) {
        firestore.collection("reports").document(reportId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
        logAdminAction(adminId, "report_update", reportId, "Report status set to $status")
    }

    suspend fun deleteReport(reportId: String, adminId: String) {
        firestore.collection("reports").document(reportId).delete().await()
        try {
            logAdminAction(adminId, "report_delete", reportId, "Report deleted by admin")
        } catch (_: Exception) {}
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
            val ist = TimeZone.getTimeZone("Asia/Kolkata")
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }
            val date = sdf.parse(dateStr.trim()) ?: return false
            val calToday = Calendar.getInstance(ist)
            val calDate = Calendar.getInstance(ist).apply { time = date }

            calToday.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) &&
            calToday.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR)
        } catch (e: Exception) {
            false
        }
    }
}
