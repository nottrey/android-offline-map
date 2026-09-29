# Offline Map & Travel Log (Android)

> **Academic Context:** Developed as a high school final capstone project for the Software Engineering major (*הנדסת תוכנה*).

An offline-first Android application designed to give travelers custom Point of Interest (POI) organization, photo-backed travel journaling, and intelligent proximity alerts—even without an active cellular or data connection.

---

## Motivation & Problem Statement

Existing offline mapping applications (such as MAPS.ME or Google Maps) often fall short in location management—specifically lacking intuitive, multi-category POI organization for travel planning. 

This project was built to bridge that gap by combining:
1. **Full Offline Autonomy:** Downloading and inspecting offline regional map tiles.
2. **Categorized POI Management:** Grouping waypoints by personal categories (e.g., Nature, Culture, Dining, Custom).
3. **Visual Travel Diary:** Attaching personal photographs and detailed metadata directly to map locations.

---

## Key Features

* **Offline Regional Navigation:** Download regional map tiles for viewing and navigation in zero-connectivity environments.
* **Categorized POIs & Visual Travel Log:** Pin locations, organize them into custom categories, and attach personal photographs—creating a localized, searchable travel diary.
* **Hybrid Cloud Sync:** Custom waypoints and media created offline are saved locally and seamlessly synced to **Firebase Cloud Firestore** and **Firebase Storage** as soon as an internet connection is re-established.
* **Background Geofencing Alerts:** Runs a background tracking service that monitors user position and sends push notifications whenever the user comes within **500 meters** of a saved waypoint.

---

## Technical Architecture & Engineering Decisions

### Architectural Shift: Google Maps SDK → MapBox SDK
* **Engine Choice:** The initial prototype used the *Google Maps SDK*, but was migrated to the **MapBox SDK** due to MapBox's superior support for offline region tile downloads and custom map rendering.

### Multi-Tier Data Layer
* **Local Database (SQLite):** Manages offline map region metadata, download queues, and local state.
* **Internal Storage (JSON):** Stores region metadata and user-added custom marker structures locally on the device.
* **Remote Backend (Firebase):** Syncs POI objects and image assets to Cloud Firestore and Firebase Storage when online.

---

## Tech Stack

* **Language:** Java (Android SDK)
* **Mapping Engine:** MapBox SDK & MapBox Search API
* **Local Storage:** SQLite (Map Management), JSON Serialization (Marker Data)
* **Cloud Infrastructure:** Firebase Cloud Firestore, Firebase Storage
* **System Services:** Location Services, Geofencing, Background Services, NotificationManager
