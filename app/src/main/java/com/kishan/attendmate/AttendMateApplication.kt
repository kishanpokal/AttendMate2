package com.kishan.attendmate

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class AttendMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 💾 Enable Firestore Offline Persistence globally
        val db = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        
        try {
            db.firestoreSettings = settings
        } catch (e: Exception) {
            // If it's already configured, ignore the exception
        }
    }
}
