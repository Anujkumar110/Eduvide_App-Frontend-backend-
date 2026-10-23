# Intro Video Changes - Edit Profile Fix

## Problem
Edit Profile screen mein intro video properly change/upload nahi ho raha tha. User ko samajh nahi aa raha tha ki:
1. Existing video ko kaise change karein
2. Video remove kaise karein
3. New video kaise upload karein

## Solution

### ✅ Changes Made to EditProfileScreen.kt

#### 1. **Existing Video par Overlay Added**
Jab backend se video already loaded ho, toh ab ek overlay dikhta hai:

```kotlin
// Overlay to indicate clickable
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.3f))
        .clickable { showVideoSourceDialog = true },
    contentAlignment = Alignment.Center
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White)
            Text("Tap to change video", fontSize = 13.sp, color = Color.White)
        }
    }
}
```

**Result:** User ko clear indication milta hai ki video change kar sakte hain.

#### 2. **Improved Video Source Dialog**
Dialog ab zyada user-friendly hai:

**Before:**
```
Select Intro Video
├─ Choose Video
└─ Remove Video (only if introUri exists)
```

**After:**
```
Intro Video
├─ Choose Video (if no video exists)
├─ Change Video (if video already exists)
└─ Remove Video (if video exists)
```

```kotlin
Text(
    text = if (introVideoUri != null || existingVideoUrl != null) 
        "Change Video" 
    else 
        "Choose Video",
    fontSize = 14.sp
)
```

#### 3. **Better Remove Logic**
Ab dono variables (introVideoUri aur existingVideoUrl) clear hote hain:

```kotlin
if (introVideoUri != null || existingVideoUrl != null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { 
                introVideoUri = null
                existingVideoUrl = null  // ✅ Both cleared
                showVideoSourceDialog = false
            }
    ) {
        Icon(Icons.Filled.Delete, tint = Color.Red)
        Text("Remove Video", color = Color.Red)
    }
}
```

## Video Flow

### Scenario 1: No Video Exists
```
User opens Edit Profile
    ↓
No intro video from backend
    ↓
Shows: "Tap to upload intro video" box
    ↓
User clicks box
    ↓
Dialog shows:
  ├─ Choose Video
  └─ Cancel
    ↓
User selects video
    ↓
Shows "New video selected" message
    ↓
User clicks "Save Profile"
    ↓
Video uploads to backend ✅
```

### Scenario 2: Video Already Exists
```
User opens Edit Profile
    ↓
Existing video loaded from backend
    ↓
Shows video player with "Tap to change video" overlay
    ↓
User clicks on video
    ↓
Dialog shows:
  ├─ Change Video
  ├─ Remove Video
  └─ Cancel
    ↓
User chooses:
  
  Option A: Change Video
    → Selects new video
    → Shows "New video selected"
    → Saves to backend
  
  Option B: Remove Video
    → Clears both URIs
    → Shows "Tap to upload" box
    → Saves (no video) to backend
  
  Option C: Keep existing
    → Cancels dialog
    → Existing video remains
```

### Scenario 3: User Uploads New Video
```
User opens Edit Profile
    ↓
No video or existing video
    ↓
User clicks "Change Video"
    ↓
Selects new video from device
    ↓
introVideoUri = new URI
    ↓
Shows green checkmark: "New video selected"
    ↓
User clicks "Save Profile"
    ↓
SessionViewModel.updateTeacherProfileWithMedia()
    ↓
Backend receives:
  ├─ introVideo (MultipartBody.Part)
  └─ All other profile data
    ↓
Backend saves new video
    ↓
Old video replaced ✅
```

## Backend Video Upload

### API Endpoint Used:
```kotlin
@Multipart
@PUT("teachers/profile")
suspend fun updateTeacherProfileWithMedia(
    @Header("Authorization") token: String,
    @Part profilePhoto: MultipartBody.Part?,
    @Part introVideo: MultipartBody.Part?,  // ✅ Video yahan jata hai
    @Part("fullName") fullName: RequestBody,
    @Part("bio") bio: RequestBody,
    // ... other fields
): Response<TeacherProfileResponse>
```

### SessionViewModel Logic:
```kotlin
// Video file banana
val videoPart = if (introVideoUri != null) {
    val file = uriToFile(context, introVideoUri)
    val requestFile = file.asRequestBody("video/mp4".toMediaType())
    MultipartBody.Part.createFormData("introVideo", file.name, requestFile)
} else null

// Backend call
apiService.updateTeacherProfileWithMedia(
    token = token,
    introVideo = videoPart,  // ✅ New video or null
    // ... other fields
)
```

### Remove Video Scenario:
```kotlin
// User clicks "Remove Video"
introVideoUri = null
existingVideoUrl = null

// Save karte time
videoPart = null  // ✅ Backend ko null jayega

// Backend should handle this by:
// 1. Removing existing video URL from database
// 2. Or setting it to null
```

## User Experience Improvements

### Before ❌
- Video change karne ka koi clear indication nahi tha
- Existing video par click karne se kuch nahi hota tha
- "Remove" button sirf tab dikhta tha jab `introVideoUri` set ho
- User confused rehta tha

### After ✅
- Video par clear overlay: "Tap to change video"
- Dialog mein "Change Video" option (dynamic text)
- "Remove Video" option hamesha dikhta hai jab video exists
- User-friendly icons and labels
- Clear visual feedback

## Testing Checklist

- [ ] Edit Profile open karo
- [ ] Verify existing video plays correctly
- [ ] Verify "Tap to change video" overlay dikhta hai
- [ ] Overlay par click karo
- [ ] Dialog mein "Change Video" option verify karo
- [ ] "Remove Video" option verify karo
- [ ] New video select karo
- [ ] "New video selected" message verify karo
- [ ] "Save Profile" click karo
- [ ] Backend mein video update verify karo
- [ ] Profile screen pe navigate karke video check karo
- [ ] Video remove karke save karo
- [ ] Verify video hat gaya

## Build Status
✅ **BUILD SUCCESSFUL** - No compilation errors

## Files Modified
1. `app/src/main/java/com/example/tutorapp/teacher/EditProfileScreen.kt`
   - Added overlay on existing video
   - Improved video source dialog
   - Better remove video logic

## Notes
- Video upload requires valid auth token
- Video size limit: 100MB (backend should enforce)
- Supported formats: MP4, WebM, 3GP
- Backend should handle null video by setting URL to null in database
