package com.abhijit.shutupNstudy.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

// Firestore Models
data class RoomTemplate(
    val focusTime: Long = 1500L,
    val shortBreakTime: Long = 300L,
    val longBreakTime: Long = 900L,
    val longBreakInterval: Long = 4L
)

data class RoomState(
    val status: String = "idle",
    val currentPhase: String = "focus",
    val timerSecondsRemaining: Long = 1500L,
    val currentInterval: Long = 1L,
    val updatedAt: Timestamp? = null,
    val cycleCompleted: Boolean = false,
    val lastAction: String = ""
)

data class StudyRoom(
    val leaderId: String = "",
    val leaderName: String = "",
    val template: RoomTemplate = RoomTemplate(),
    val state: RoomState = RoomState()
)

data class Participant(
    val id: String = "",
    val name: String = "",
    val role: String = "participant",
    val joinedAt: Timestamp? = null,
    val lastActive: Timestamp? = null
)

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private var isInitialized = false
    private lateinit var firestore: FirebaseFirestore

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            // Initializing Firebase App programmatically using keys from google-services.json/web config
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyCe_Au8D7_W_LFHQj1zDdA-DTPgi-x6BrA")
                .setApplicationId("1:545877201348:android:9dec1f283ebdaee1371491")
                .setProjectId("shutupnstudy-1734a")
                .setStorageBucket("shutupnstudy-1734a.firebasestorage.app")
                .build()

            FirebaseApp.initializeApp(context.applicationContext, options)
            firestore = FirebaseFirestore.getInstance()
            isInitialized = true
            Log.d(TAG, "Firebase initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }
    }

    fun getFirestoreInstance(): FirebaseFirestore {
        return firestore
    }

    // Helper for UserId cached in SharedPreferences
    fun getOrGenerateUserId(context: Context): String {
        val prefs = context.getSharedPreferences("shutup_study_prefs", Context.MODE_PRIVATE)
        var uid = prefs.getString("user_id", null)
        if (uid == null) {
            uid = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            prefs.edit().putString("user_id", uid).apply()
        }
        return uid
    }

    // Helper for cached Username
    fun getCachedUsername(context: Context): String {
        val prefs = context.getSharedPreferences("shutup_study_prefs", Context.MODE_PRIVATE)
        return prefs.getString("username", "") ?: ""
    }

    fun cacheUsername(context: Context, name: String) {
        val prefs = context.getSharedPreferences("shutup_study_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("username", name.trim()).apply()
    }

    // Check if a room exists
    fun checkRoomExists(roomId: String, onSuccess: (Boolean, StudyRoom?) -> Unit, onFailure: (Exception) -> Unit) {
        val docRef = firestore.collection("sessions").document(roomId.uppercase())
        docRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val room = snapshot.toObject(StudyRoom::class.java)
                onSuccess(true, room)
            } else {
                onSuccess(false, null)
            }
        }.addOnFailureListener { e ->
            onFailure(e)
        }
    }

    // Create a new room
    fun createRoom(
        context: Context,
        username: String,
        focusMin: Long,
        shortBreakMin: Long,
        longBreakMin: Long,
        intervals: Long,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = getOrGenerateUserId(context)
        val newRoomId = UUID.randomUUID().toString().replace("-", "").substring(0, 6).uppercase()
        val template = RoomTemplate(
            focusTime = focusMin * 60,
            shortBreakTime = shortBreakMin * 60,
            longBreakTime = longBreakMin * 60,
            longBreakInterval = intervals
        )
        val state = RoomState(
            status = "idle",
            currentPhase = "focus",
            timerSecondsRemaining = focusMin * 60,
            currentInterval = 1,
            updatedAt = Timestamp.now()
        )
        val roomData = mapOf(
            "createdAt" to FieldValue.serverTimestamp(),
            "leaderId" to userId,
            "leaderName" to username.trim(),
            "template" to mapOf(
                "focusTime" to template.focusTime,
                "shortBreakTime" to template.shortBreakTime,
                "longBreakTime" to template.longBreakTime,
                "longBreakInterval" to template.longBreakInterval
            ),
            "state" to mapOf(
                "status" to state.status,
                "currentPhase" to state.currentPhase,
                "timerSecondsRemaining" to state.timerSecondsRemaining,
                "currentInterval" to state.currentInterval,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        firestore.collection("sessions").document(newRoomId).set(roomData)
            .addOnSuccessListener {
                onSuccess(newRoomId)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    // Listen to study room changes
    fun observeRoom(roomId: String): Flow<StudyRoom?> = callbackFlow {
        val docRef = firestore.collection("sessions").document(roomId.uppercase())
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val room = snapshot.toObject(StudyRoom::class.java)
                trySend(room)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    // Join room as participant
    fun joinRoom(roomId: String, userId: String, name: String, isLeader: Boolean) {
        val participantRef = firestore.collection("sessions").document(roomId.uppercase())
            .collection("participants").document(userId)

        val data = mapOf(
            "name" to name.trim(),
            "role" to if (isLeader) "leader" else "participant",
            "joinedAt" to FieldValue.serverTimestamp(),
            "lastActive" to FieldValue.serverTimestamp()
        )
        participantRef.set(data)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to register participant: $e")
            }
    }

    // Update participant heartbeat
    fun updateHeartbeat(roomId: String, userId: String) {
        val participantRef = firestore.collection("sessions").document(roomId.uppercase())
            .collection("participants").document(userId)

        participantRef.update("lastActive", FieldValue.serverTimestamp())
            .addOnFailureListener { e ->
                Log.e(TAG, "Heartbeat failed: $e")
            }
    }

    // Leave room
    fun leaveRoom(roomId: String, userId: String) {
        val participantRef = firestore.collection("sessions").document(roomId.uppercase())
            .collection("participants").document(userId)
        participantRef.delete()
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove participant: $e")
            }
    }

    // Delete room (Leader actions)
    fun deleteRoom(roomId: String, userId: String, onComplete: () -> Unit) {
        // First delete participant
        leaveRoom(roomId, userId)
        // Then delete room document
        val docRef = firestore.collection("sessions").document(roomId.uppercase())
        docRef.delete().addOnCompleteListener {
            onComplete()
        }
    }

    // Observe participants
    fun observeParticipants(roomId: String): Flow<List<Participant>> = callbackFlow {
        val collectionRef = firestore.collection("sessions").document(roomId.uppercase())
            .collection("participants")

        val listener = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = Timestamp.now().seconds
                val list = mutableListOf<Participant>()
                for (doc in snapshot.documents) {
                    val p = doc.toObject(Participant::class.java)?.copy(id = doc.id)
                    if (p != null) {
                        // Filter out inactive participants (no heartbeat in 2 minutes / 120s)
                        val lastActiveSecs = p.lastActive?.seconds ?: now
                        if (now - lastActiveSecs < 120) {
                            list.add(p)
                        }
                    }
                }
                // Sort by role so leader is at the top
                list.sortByDescending { it.role }
                trySend(list)
            }
        }
        awaitClose { listener.remove() }
    }

    // Leader updates state in DB
    fun updateTimerState(roomId: String, status: String, remainingSeconds: Long, overrideMap: Map<String, Any> = emptyMap()) {
        val docRef = firestore.collection("sessions").document(roomId.uppercase())
        val updates = mutableMapOf<String, Any>(
            "state.status" to status,
            "state.timerSecondsRemaining" to remainingSeconds,
            "state.updatedAt" to FieldValue.serverTimestamp()
        )
        for ((key, value) in overrideMap) {
            updates[key] = value
        }
        docRef.update(updates)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update timer state in Firestore: $e")
            }
    }
}
