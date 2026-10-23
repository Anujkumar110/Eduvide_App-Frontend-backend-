export const ROLES = {
  STUDENT: 'student',
  TEACHER: 'teacher',
  ADMIN: 'admin',
  MODERATOR: 'moderator',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

export const VERIFICATION_STATUS = {
  PENDING: 'pending',
  VERIFIED: 'verified',
  REJECTED: 'rejected',
  REUPLOAD_REQUESTED: 'reupload_requested',
} as const;

export const CLASS_STATUS = {
  DRAFT: 'draft',
  PUBLISHED: 'published',
  ONGOING: 'ongoing',
  CANCELLED: 'cancelled',
  COMPLETED: 'completed',
  MISSED: 'missed',
} as const;

export const SESSION_STATUS = {
  UPCOMING: 'upcoming',
  ONGOING: 'ongoing',
  COMPLETED: 'completed',
  MISSED: 'missed',
  CANCELLED: 'cancelled',
} as const;

export const ENROLLMENT_STATUS = {
  ACTIVE: 'active',
  COMPLETED: 'completed',
  DROPPED: 'dropped',
} as const;

export const TOKEN_EXPIRY = {
  ACCESS: '15m',
  RESET: 3600,
} as const;

export const UPLOAD_LIMITS = {
  photo: 5 * 1024 * 1024,
  document: 15 * 1024 * 1024,
  chatMedia: 10 * 1024 * 1024,
  video: 100 * 1024 * 1024,
} as const;

export const ALLOWED_MIME = {
  photo: ['image/jpeg', 'image/png', 'image/webp'],
  document: ['image/jpeg', 'image/png', 'application/pdf'],
  chatMedia: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
  video: ['video/mp4', 'video/webm', 'video/ogg'],
} as const;

export const IK_FOLDERS = {
  profilePhotos: '/eduvibe/profile/photos',
  certificates: '/eduvibe/certificates',
  classMedia: '/eduvibe/classes',
  chatMedia: '/eduvibe/chat',
  idProofs: '/eduvibe/id-proofs',
  introVideo: '/eduvibe/profile/intro-video',
} as const;