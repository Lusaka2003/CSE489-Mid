Project Overview
----------------
Smart Landmarks is an Android app for CSE 489 Lab Exam v5. It talks to the faculty REST API at
https://labs.anontech.info/cse489/exm3/api.php to list, map, visit, create, soft-delete, and restore
geo-tagged landmarks. Visit scoring is computed on the server; the app only submits GPS and then
polls a background job for the distance.

Features Implemented
--------------------
- Bottom navigation with four tabs: Map, Landmarks, Activity, Add/View
- Landmark fetch with title, image, and score
- OpenStreetMap view centered on Bangladesh; marker color scales from low (red) to high (green) score
- Marker tap shows title, image, score, visit count, and a Visit action
- Visit uses current GPS, POST visit_landmark, then WorkManager polls get_job_status until done
- Landmarks list with image/title/score, sort by score, and minimum-score filter
- Activity tab: visit time, landmark name, distance; pending and offline-queued rows included
- Add landmark with title, lat/lon, GPS autofill, Photo Picker, and CameraX capture (multipart form-data)
- Soft delete from the detail sheet; restore from Add/View
- Room cache so the list/map still work offline
- Offline visit queue that drains when connectivity returns
- Toasts/Snackbars for success, dialogs for errors, 403 key handling

API Usage
---------
Base: https://labs.anontech.info/cse489/exm3/api.php?key=YOUR_KEY

- GET  action=get_landmarks
- POST action=visit_landmark     JSON body {landmark_id, user_lat, user_lon} -> {job_id, status}
- GET  action=get_job_status&job_id=...
- POST action=create_landmark    multipart form-data: title, lat, lon, image
- POST action=delete_landmark    form field landmark_id
- POST action=restore_landmark   form field landmark_id

Paste your semester API key on first launch (Settings gear), or put LANDMARK_API_KEY in local.properties.

Offline Strategy
----------------
Room is the source of truth. The UI observes Room, never the live network. A successful fetch upserts
landmarks and marks missing rows inactive (soft-deleted). If the device is offline, cached rows are
shown. Visit taps without network insert a pending_visits row. LandmarkSyncWorker (WorkManager) has
a CONNECTED constraint, retries with exponential backoff, drains the queue, then polls job_id values
until status is done and writes distance back into the visits table.

Architecture Used
-----------------
Repository + single source of truth:
UI (Jetpack Compose) -> LandmarkViewModel -> LandmarkRepository -> Room / Retrofit / WorkManager

No Hilt; a small AppContainer in LandmarkApplication holds Retrofit, Room, and the repository.

Challenges Faced
----------------
- visit_landmark is asynchronous; the distance is not in the first response, so the UI cannot block
- Scores on the public sample data are often large negatives, so marker color is relative (min-max)
  rather than hardcoded 40/80 thresholds
- create_landmark must be multipart; JSON leaves $_FILES empty on the PHP server
- Jobs must survive process death, so polling lives in WorkManager, not a Compose LaunchedEffect

How to run
----------
1. Open this folder in Android Studio (SDK 35, JDK 17)
2. Enter your API key in the app or in local.properties as LANDMARK_API_KEY=...
3. Run on a device/emulator with Google Play (location) and internet
4. Grant location permission when visiting or auto-filling GPS
