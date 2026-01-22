# 📘 AttendMate – Smart Attendance Tracker (Android)

AttendMate is a modern Android application designed to help students efficiently track attendance, manage timetables, and monitor attendance percentages in real time.  
The app is built using Kotlin, Jetpack Compose, and Firebase, with a focus on clean architecture, reliability, and automation.

---

## 🚀 Features

- Subject-wise attendance tracking
- Timetable management
- Lecture-based attendance records
- Automatic attendance percentage calculation
- Daily and lecture-based notifications
- Firebase Authentication (Email / Google)
- Cloud sync using Firestore
- Responsive UI (Portrait & Landscape)
- Material 3 UI with Jetpack Compose

---

## 🛠 Tech Stack

### Android
- Kotlin
- Jetpack Compose
- Android Studio
- Material 3

### Backend / Cloud
- Firebase Authentication
- Firebase Firestore
- Firebase Cloud Messaging (FCM)

### Architecture
- MVVM (Model–ViewModel)
- Repository Pattern
- Kotlin Coroutines
- StateFlow / LiveData

---

## 📂 Firestore Data Structure

users/{uid}  
└── subjects/{subjectId}  
&nbsp;&nbsp;&nbsp;&nbsp;├── name  
&nbsp;&nbsp;&nbsp;&nbsp;├── totalClasses  
&nbsp;&nbsp;&nbsp;&nbsp;├── attendedClasses  
&nbsp;&nbsp;&nbsp;&nbsp;├── createdAt  
&nbsp;&nbsp;&nbsp;&nbsp;└── attendance/{date_time}  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├── date  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├── startTime  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├── endTime  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├── status (Present / Absent)  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└── createdAt  

---

## 📱 Screens Implemented

- Login & Register
- Dashboard
- Subject Management
- Timetable Setup
- Attendance Marking
- Attendance History
- Notifications



## ⚙ Setup Instructions

1. Clone the repository: git clone https://github.com/kishanpokal/AttendMate2.git
   
2. Open the project in Android Studio

3. Create a Firebase project:
   - Enable Authentication
   - Enable Firestore
   - Add Android app and download google-services.json

4. Place google-services.json inside:
app/

5. Sync Gradle and run the app on an emulator or physical device

---

## 🔐 Permissions Used

- Notifications
- Internet access
- Exact alarms (for scheduled reminders)

---

## 🧪 Minimum Requirements

- Android 8.0 (API 26) or higher
- Internet connection

---

## 📈 Future Enhancements

- Calendar view for attendance
- Export attendance as PDF / CSV
- Multi-user / class sharing
- Web dashboard integration

---

## 👨‍💻 Author

Kishan Pokal  
Android Developer  

GitHub: https://github.com/kishanpokal

---

## 📄 License

This project is licensed under the MIT License.  
You are free to use, modify, and distribute this project.

---

⭐ If you find this project useful, consider giving it a star on GitHub.

