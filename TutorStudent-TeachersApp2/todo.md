# EduVibe — TODO & Issues Tracker

---

## ✅ Fixed

| # | Fix | Files Changed |
|---|---|---|
| A | Centralized server config (`AppConfig.kt`) | `AppConfig.kt` (new), `Retrofitclient.kt`, `Signalingclient.kt` |
| B | Added TURN server support to WebRTC | `Webrtcclient.kt` — `IceServerConfig` data class |
| C | Student skips unnecessary audio capture | `Webrtcclient.kt` — `startLocalPreviewOnly()` |
| D | Implemented `ChatPanel` composable (was `TODO()`) | `Liveclassscreen.kt` |
| E | Added `session:teacher-started` backend handler | `sessionHandler.ts` (new), `messageRouter.ts` |
| F | Implemented all stub `SessionViewModel` methods | `Sessionviewmodel.kt` |
| G | `createClass` error now shows snackbar | `SessionsTab.kt` |
| H | Temporarily disabled teacher verification for testing | `class.service.ts` |
| I | **Fixed compilation errors** — `parseScheduleDisplay` scope, destructuring, syntax merge | `Sessionviewmodel.kt` |
| J | **Fixed teacher black screen** — added camera/mic permission request to `CallScreen` | `Callscreen.kt` |
| K | **Fixed student "Join Live" not working** — backend had no `"ongoing"` status, student only fetched `"published"` | `class.model.ts` (added `"ongoing"`), `Sessionviewmodel.kt` (fetches both) |

---

## 🟡 WebRTC Runtime Issues (Will Break in Production)

| # | Issue | Impact | Severity |
|---|---|---|---|
| 4 | **No TURN server** — only Google STUN | Fails when teacher/student on different networks (mobile data vs WiFi, corporate NAT) | High |
| 5 | **Backend room size = 2** (`MAX_ROOM_SIZE = 2`) | Only 1 teacher + 1 student. Third person gets `room-full` error | High |
| 6 | **Backend membership validation disabled** | Anyone can join any room (testing mode bypass) | Medium |
| 7 | **Backend is single-instance only** | No Redis pub/sub — can't scale to multiple server instances | Low (for now) |
| 8 | **`session:teacher-started` only notifies students already in the room** | Students who open the app *after* teacher goes live won't get the notification until polling catches it | Medium |

---

## 🟠 Code Quality / Architecture Issues

| # | Issue | Impact |
|---|---|---|
| 9 | **Two parallel live screen implementations** — `Liveclassscreen.kt` (inline logic) + `CallScreen` + `CallViewModel` (cleaner) | Confusing, duplicated code, harder to maintain |
| 10 | **`Liveclassscreen.kt` has inconsistent token usage** — `TokenManager.getBearerToken` vs `TokenManager.getToken` used inconsistently | Minor confusion |
| 11 | **`CallScreen` shadows `role` parameter** — navigation passes `role` but it's immediately overwritten by `TokenManager.getRole()` | Confusing but works |
| 13 | **`TeacherLiveScreen`** uses `TokenManager.getBearerToken()` while `CallScreen` uses `TokenManager.getToken()` — inconsistent | Minor |

---

## 🟢 Backend Issues

| # | Issue | Impact |
|---|---|---|
| 14 | **Verification check commented out** in `class.service.ts` | Must re-enable before production |
| 17 | **No rate limiting on WebSocket** — spec says 30 msg/10s, not enforced | Low (for now) |
| 18 | **No heartbeat timeout** — server pings but never closes ghost connections | Low |

---

## 📋 Pending Tasks

- [ ] Add TURN server config for production (#4)
- [ ] Increase `MAX_ROOM_SIZE` or implement multi-peer SFU (#5)
- [ ] Re-enable membership validation in `roomManager.ts` (#6)
- [ ] Consolidate `Liveclassscreen.kt` and `CallScreen` into one implementation (#9)
- [ ] Re-enable teacher verification check in backend (#14)
- [ ] Add WebSocket rate limiting (#17)
- [ ] Add heartbeat timeout enforcement (#18)

---

## 🔧 How the Flow Works Now

```
Teacher creates session → POST /api/v1/classes → status: "published" → saved in MongoDB
       ↓
Teacher SessionsTab → GET /api/v1/classes → shows teacher's sessions
       ↓
Teacher clicks "Join" → dialog → "Start Live Class"
       ↓
  Local UI: status → ONGOING
  Backend: PATCH /api/v1/classes/{id} → status: "ongoing" ✅ (now valid enum value)
       ↓
  Navigates to CallScreen → requests camera/mic permissions → starts WebRTC as teacher
       ↓
Student StatusScreen → polls every 10s:
  GET /api/v1/classes?status=published  → scheduled classes
  GET /api/v1/classes?status=ongoing    → live classes ✅
       ↓
Student sees "● LIVE" badge + active "Join Live" button ✅
       ↓
Student clicks "Join Live" → navigates to StudentLiveScreen → requests permissions
       ↓
Student joins room → teacher gets onNewViewer → sends WebRTC offer
       ↓
Student receives offer → sends answer → ICE exchange → P2P connection ✅
       ↓
Video + audio flowing both ways ✅
```
