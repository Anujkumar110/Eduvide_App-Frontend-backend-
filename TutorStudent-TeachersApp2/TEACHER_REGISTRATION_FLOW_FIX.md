# Teacher Registration Flow Fix

## Problem
1. Teacher registration was navigating directly to Main Dashboard after register/login
2. Profile completion steps (Step 1 & Step 2) were being skipped
3. Images and videos were not being saved to backend properly

## Solution

### ✅ Changes Made

#### 1. **TeacherRegisterScreen.kt** - Fixed Navigation Flow
**File:** `app/src/main/java/com/example/tutorapp/teacher/TeacherRegisterScreen.kt`

**Changes:**
- Changed navigation from `Routes.MAIN` → `Routes.COMPLETE_PROFILE_1`
- Both email/password registration and Google Sign-In now go through profile completion steps
- Updated `LaunchedEffect` to handle both registration methods properly

```kotlin
// Before: Direct to Main Dashboard
navController.navigate(Routes.MAIN) { ... }

// After: Go to Profile Step 1 first
navController.navigate(Routes.COMPLETE_PROFILE_1) { ... }
```

#### 2. **CreateProfileStep1Screen.kt** - Added Backend API Call
**File:** `app/src/main/java/com/example/tutorapp/teacher/CreateProfileStep1Screen.kt`

**Changes:**
- Added imports: `RetrofitClient`, `TeacherProfileRequest`, `TokenManager`, `CoroutineScope`, `launch`
- Added state variables: `isLoading`, `apiError`
- Modified `saveProfile()` function to:
  1. Save to SharedPreferences (local)
  2. Call backend API: `createTeacherProfile()`
  3. Handle 409 (profile exists) as success
  4. Navigate to Step 2 only after backend success
- Added loading indicator and error message display

**Backend Call:**
```kotlin
val request = TeacherProfileRequest(
    fullName = fullName.trim(),
    email = email.trim(),
    gender = gender,
    age = age.toIntOrNull() ?: 0,
    chargePerHour = chargePerHour.toIntOrNull() ?: 0,
    degree = degree,
    bio = bio.trim(),
    experience = experience.filter { it.isDigit() }.toIntOrNull() ?: 0,
    subjects = cleanSubjects.split(",").filter { it.isNotEmpty() },
    boards = cleanBoards.split(",").filter { it.isNotEmpty() },
    languages = cleanLanguages.split(",").filter { it.isNotEmpty() }
)

val response = RetrofitClient.api.createTeacherProfile(token, request)
```

#### 3. **CreateProfileStep2Screen.kt** - Optimized Media Upload
**File:** `app/src/main/java/com/example/tutorapp/teacher/CreateProfileStep2Screen.kt`

**Changes:**
- Removed redundant `createTeacherProfile()` call (already done in Step 1)
- Focus only on media upload (photo + video)
- Improved error logging with detailed backend response
- Better error messages shown to user

**Media Upload Flow:**
```kotlin
// Photo upload
val photoPart = photoUri?.let { uri ->
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    val file = uriToFile(context, uri, "photo.$ext")
    val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
    MultipartBody.Part.createFormData("profilePhoto", file.name, body)
}

// Video upload
val videoPart = videoUri?.let { uri ->
    val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
    val file = uriToFile(context, uri, "video.mp4")
    val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
    MultipartBody.Part.createFormData("introVideo", file.name, body)
}

val mediaResp = RetrofitClient.api.updateTeacherProfileWithMedia(
    token = token,
    profilePhoto = photoPart,
    introVideo = videoPart,
    bio = bioBody,
    experience = experienceBody,
    pricingHourlyRate = pricingHourlyRateBody,
    pricingCurrency = pricingCurrencyBody
)
```

#### 4. **MainActivity.kt** - Added Navigation Routes
**File:** `app/src/main/java/com/example/tutorapp/MainActivity.kt`

**Changes:**
- Added route for `COMPLETE_PROFILE_1` with SharedPreferences context
- Added route for `COMPLETE_PROFILE_2`

```kotlin
composable(Routes.COMPLETE_PROFILE_1) { 
    CompleteProfileStep1Screen(
        navController = navController,
        prefs = getSharedPreferences("teacher_profile", Context.MODE_PRIVATE)
    ) 
}
composable(Routes.COMPLETE_PROFILE_2) { 
    CompleteProfileStep2Screen(navController = navController) 
}
```

## Complete Registration Flow

### New Flow:
```
Teacher Register Screen
    ↓ (Register button click)
    ↓ (Email/Password OR Google Sign-In)
    ↓ (Backend authentication)
    ↓ (LaunchedEffect detects success)
    ↓
Complete Profile Step 1
    ↓ (Personal info, teaching details)
    ↓ (Save to SharedPreferences)
    ↓ (Call backend: createTeacherProfile)
    ↓ (Backend saves profile data)
    ↓
Complete Profile Step 2
    ↓ (Profile photo upload)
    ↓ (Intro video upload)
    ↓ (Call backend: updateTeacherProfileWithMedia)
    ↓ (Backend saves media files)
    ↓
Main Dashboard (Teacher Home)
```

## Backend Endpoints Used

### 1. Create Teacher Profile (Step 1)
```kotlin
@PUT("teachers/profile")
suspend fun createTeacherProfile(
    @Header("Authorization") token: String,
    @Body request: TeacherProfileRequest
): Response<TeacherProfileResponse>
```

**Saves:**
- Full Name
- Email
- Gender
- Age
- Charge Per Hour
- Degree
- Bio
- Experience
- Subjects
- Boards
- Languages

### 2. Update Teacher Profile with Media (Step 2)
```kotlin
@Multipart
@PUT("teachers/profile")
suspend fun updateTeacherProfileWithMedia(
    @Header("Authorization") token: String,
    @Part profilePhoto: MultipartBody.Part?,
    @Part introVideo: MultipartBody.Part?,
    @Part("bio") bio: RequestBody,
    @Part("experience") experience: RequestBody,
    @Part("pricing[hourlyRate]") pricingHourlyRate: RequestBody,
    @Part("pricing[currency]") pricingCurrency: RequestBody
): Response<TeacherProfileResponse>
```

**Uploads:**
- Profile Photo (image/jpeg, image/png, image/webp)
- Intro Video (video/mp4, max 30 seconds)
- Additional profile data

## Testing Checklist

- [ ] Teacher registration with email/password navigates to Step 1
- [ ] Teacher registration with Google Sign-In navigates to Step 1
- [ ] Step 1 validation works (all required fields)
- [ ] Step 1 saves data to backend successfully
- [ ] Step 1 navigates to Step 2 only after backend success
- [ ] Step 2 photo upload works (camera & gallery)
- [ ] Step 2 video upload works
- [ ] Step 2 saves media to backend successfully
- [ ] Step 2 navigates to Main Dashboard only after media upload success
- [ ] Profile photos appear in backend
- [ ] Profile videos appear in backend
- [ ] Error messages display properly for API failures
- [ ] Loading indicators show during API calls

## Debug Logs

### Step 1 Logs:
```
D/Step1: API Response: code=200, body=TeacherProfileResponse(...)
```

### Step 2 Logs:
```
D/Step2: Photo: 123456 bytes, mime=image/jpeg
D/Step2: Video: 5678901 bytes, mime=video/mp4
D/Step2: Media upload code=200, body=TeacherProfileResponse(...)
```

## Notes
- 409 response (profile already exists) is treated as success
- SharedPreferences cleared after successful Step 2 completion
- `teacher_logged_in` flag set to true only after Step 2 completion
- All backend calls require valid Bearer token from TokenManager
