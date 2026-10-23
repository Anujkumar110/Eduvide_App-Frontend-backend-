# Teacher Profile & Notifications Update

## Overview
This document describes the implementation of teacher profile settings and notifications, matching the student-side functionality.

## What Was Implemented

### 1. Teacher Profile Settings (Like Student)

#### New Files Created:

1. **TeacherProfileRepository.kt** (`app/src/main/java/com/example/tutorapp/repository/TeacherProfileRepository.kt`)
   - Repository layer for teacher profile API calls
   - `getProfile(token)` - Fetches teacher profile from backend
   - `updateProfile(...)` - Updates teacher profile with all fields including photo upload
   - Handles multipart form data for subjects, boards, and languages (padded arrays like backend expects)

2. **TeacherProfileViewModel.kt** (`app/src/main/java/com/example/tutorapp/viewmodel/TeacherProfileViewModel.kt`)
   - ViewModel for teacher profile management
   - `loadProfile(context)` - Loads profile with role validation (teacher only)
   - `updateProfile(...)` - Updates profile with full validation
   - State management via `TeacherProfileUiState` data class
   - Error handling and loading states

3. **TeacherProfileEditScreen.kt** (`app/src/main/java/com/example/tutorapp/teacher/TeacherProfileEditScreen.kt`)
   - Full-featured profile edit screen matching student's ProfileEditScreen
   - **Features:**
     - Profile photo upload (camera/gallery) with permissions handling
     - Full name and email fields
     - Gender selection (radio buttons)
     - Age dropdown (18-70)
     - Degree input with dropdown suggestions
     - Charge per hour field
     - Experience dropdown
     - Bio textarea (300 char limit)
     - Multi-select chips for:
       - Subjects (9 options)
       - Boards (6 options)
       - Languages (8 options)
     - Save button with loading state
     - Error message display

### 2. Teacher Notifications (Already Working)

The teacher notifications screen was already implemented and matches the student functionality:

- **NotificationsScreen.kt** (`app/src/main/java/com/example/tutorapp/teacher/NotificationsScreen.kt`)
  - Fetches notifications from backend API
  - Shows unread count badge
  - Mark all as read functionality
  - Individual notification tap marks as read
  - Time formatting (min ago, hr ago, Yesterday, days ago)
  - AlertDialog for full message view
  - Loading, empty, and error states

### 3. Navigation Updates

#### MainActivity.kt
- Added new route: `"teacher_profile_edit"` → `TeacherProfileEditScreen`
- Existing routes maintained for backward compatibility

#### ProfileScreen.kt
- Updated to show TWO edit buttons:
  1. **"Edit Profile (Full)"** → Navigates to new `TeacherProfileEditScreen` (backend-connected)
  2. **"Edit Personal Info"** → Navigates to existing `PersonalInfoScreen` (SharedPreferences-only)

#### SettingTab.kt
- Updated edit icon (pencil) on profile banner to navigate to `TeacherProfileEditScreen`
- Previously navigated to `Routes.EDIT_PROFILE`, now uses new screen

## Architecture

### MVVM Pattern (Same as Student)
```
TeacherProfileEditScreen (UI)
        ↓
TeacherProfileViewModel (State Management)
        ↓
TeacherProfileRepository (API Calls)
        ↓
ApiService (Retrofit Backend)
```

### Data Flow

1. **Load Profile:**
   - Screen calls `viewModel.loadProfile(context)`
   - ViewModel validates role (teacher)
   - Repository calls `api.getTeacherProfile(token)`
   - Response updates `uiState.profile`
   - Screen pre-fills form fields

2. **Update Profile:**
   - User edits fields and taps "Save Changes"
   - Screen calls `viewModel.updateProfile(...)`
   - ViewModel validates role and token
   - Repository creates multipart form data
   - API call to `updateTeacherProfileWithMedia`
   - On success: navigates back to dashboard
   - On error: shows error message

## Key Features

### Profile Photo Upload
- Camera capture with permission handling
- Gallery selection with permission handling
- Shows local preview immediately
- Uploads as multipart form data (JPEG)
- Falls back to backend photo if no local change

### Form Validation
- Role check (teacher only)
- Token validation
- Required field checks (name, email)
- Proper error messages

### Multi-Select Chips
- Subjects: 9 options (Maths, Science, English, SST, Hindi, Biology, Physics, Chemistry, Computer)
- Boards: 6 options (CBSE, ICSE, State Board, IB, IGCSE, A-Level)
- Languages: 8 options (English, Hindi, Punjabi, Tamil, Telugu, Bengali, Marathi, Gujarati)
- Visual feedback (blue background when selected)
- Grid layout (3 columns)

## Backend Integration

### API Endpoints Used:
- `GET /teachers/profile` - Fetch teacher profile
- `PUT /teachers/profile` (multipart) - Update teacher profile with media

### Expected Response Format:
```kotlin
data class TeacherProfileResponse(
    val success: Boolean,
    val message: String,
    val data: TeacherProfileData?
)

data class TeacherProfileData(
    val id: String?,
    val userId: String?,
    val fullName: String?,
    val email: String?,
    val gender: String?,
    val age: Int?,
    val bio: String?,
    val subjects: List<String>?,
    val experience: Int?,
    val pricing: PricingData?,
    val qualifications: List<QualificationData>?,
    val profilePhoto: String?,
    val introVideo: String?,
    val isLive: Boolean?
)
```

## Testing

### Build Status:
✅ **BUILD SUCCESSFUL** - All Kotlin code compiles without errors

### Warnings (Non-Critical):
- Some deprecated Icon usage (AutoMirrored recommended)
- Divider renamed to HorizontalDivider
- These are cosmetic and don't affect functionality

### How to Test:

1. **Login as Teacher:**
   - Open app → Teacher login → Enter credentials
   - Should navigate to Teacher Dashboard

2. **Access Profile Settings:**
   - Go to Settings tab (bottom navigation)
   - Tap on profile banner OR edit icon (pencil)
   - Should open `TeacherProfileEditScreen`

3. **Edit Profile:**
   - Update any field (name, email, bio, etc.)
   - Select subjects, boards, languages
   - Tap camera icon to change photo
   - Tap "Save Changes"
   - Should show loading indicator
   - On success: navigate back to dashboard
   - On error: show error message

4. **View Profile:**
   - Go to Settings tab
   - Tap "Profile" or profile banner
   - Should show all updated fields
   - Tap "Edit Profile (Full)" to go to edit screen
   - Tap "Edit Personal Info" for SharedPreferences-only edit

5. **Check Notifications:**
   - Go to Settings tab
   - Tap "Notifications" (should show unread badge if any)
   - Should load notifications from backend
   - Tap any notification to see full message
   - "Mark all read" button should work
   - Individual tap should mark as read

## Comparison with Student Side

| Feature | Student | Teacher |
|---------|---------|---------|
| Profile Screen | `ProfileEditScreen` | `TeacherProfileEditScreen` ✅ |
| ViewModel | `ProfileViewModel` | `TeacherProfileViewModel` ✅ |
| Repository | `ProfileRepository` | `TeacherProfileRepository` ✅ |
| Photo Upload | ✅ Camera/Gallery | ✅ Camera/Gallery |
| Multi-select Chips | Goals, Subjects | Subjects, Boards, Languages |
| Backend Integration | ✅ Multipart PUT | ✅ Multipart PUT |
| Notifications | ✅ Full implementation | ✅ Full implementation |
| Mark All Read | ✅ | ✅ |
| Unread Badge | ✅ | ✅ |

## Files Modified

1. `MainActivity.kt` - Added new route
2. `ProfileScreen.kt` - Added two edit buttons
3. `SettingTab.kt` - Updated edit icon navigation

## Files Created

1. `repository/TeacherProfileRepository.kt`
2. `viewmodel/TeacherProfileViewModel.kt`
3. `teacher/TeacherProfileEditScreen.kt`

## Next Steps (Optional Enhancements)

1. **Profile Video Upload:** Add intro video upload support in edit screen
2. **Profile Stats:** Connect student/session/review counts to backend
3. **Live Status Toggle:** Implement Go Live switch in Settings tab
4. **Avatar Colors:** Random avatar colors for teachers without photos
5. **Validation:** Add more field validations (email format, age range, etc.)

## Notes

- Teacher notifications were already fully implemented and working
- Old `PersonalInfoScreen` still available for quick edits (SharedPreferences only)
- New `TeacherProfileEditScreen` connects to backend for persistent storage
- Both screens can coexist; user chooses which to use
- Build compiles successfully with only warnings (no errors)
