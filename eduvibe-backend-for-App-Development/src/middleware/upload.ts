import multer, { FileFilterCallback } from 'multer';
import { Request, Response, NextFunction } from 'express';
import { AppError } from '../utils/errors';
import { UPLOAD_LIMITS, ALLOWED_MIME } from '../constants';

import path from 'path';
import fs from 'fs';

const FIELD_MIME_PROFILE: Record<string, keyof typeof ALLOWED_MIME> = {
  profilePhoto: 'photo',
  introVideo: 'video',
  idProof: 'document',
  chatMedia: 'chatMedia',
};

const tempUploadsDir = path.join(__dirname, '../temp_uploads');
if (!fs.existsSync(tempUploadsDir)) {
  fs.mkdirSync(tempUploadsDir, { recursive: true });
}

// export const createUploader = (profile: 'photo' | 'document' | 'chatMedia' | 'video') =>
//   multer({
//     storage: multer.diskStorage({
//       destination: tempUploadsDir,
//       filename: (req, file, cb) => {
//         cb(null, `${Date.now()}-${file.originalname}`);
//       }
//     }),
//     limits: {
//       fileSize: UPLOAD_LIMITS[profile],
//       // files: 1,
//     },
//     fileFilter: (_req: Request, file: Express.Multer.File, cb: FileFilterCallback) => {
//       const allowed = ALLOWED_MIME[profile] as readonly string[];
//       if (!allowed.includes(file.mimetype)) {
//         return cb(new AppError(`File type ${file.mimetype} not allowed`, 400));
//       }
//       cb(null, true);
//     },
//   });

// export const photoUploader = createUploader('photo');
// export const documentUploader = createUploader('document');
// export const chatMediaUploader = createUploader('chatMedia');
// export const videoUploader = createUploader('video');
export const createFieldsUploader = (
  fields: { name: string; profile: 'photo' | 'document' | 'chatMedia' | 'video' }[]
) => {
  const maxSize = Math.max(...fields.map(f => UPLOAD_LIMITS[f.profile]));
  const fieldMimeMap = Object.fromEntries(fields.map(f => [f.name, ALLOWED_MIME[f.profile]]));

  return multer({
    storage: multer.diskStorage({
      destination: tempUploadsDir,
      filename: (_req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`),
    }),
    limits: { fileSize: maxSize },
    fileFilter: (_req, file, cb) => {
      const allowed = fieldMimeMap[file.fieldname] as readonly string[] | undefined;
      if (!allowed) return cb(new AppError(`Unexpected field: ${file.fieldname}`, 400));
      if (!allowed.includes(file.mimetype)) {
        return cb(new AppError(`File type ${file.mimetype} not allowed for ${file.fieldname}`, 400));
      }
      cb(null, true);
    },
  });
};

// Usage on the teacher profile route:
export const teacherProfileUploader = createFieldsUploader([
  { name: 'profilePhoto', profile: 'photo' },
  { name: 'introVideo', profile: 'video' },
  { name: 'idProof', profile: 'document' },
]);

// export const validateMagicBytes = async (
//   req: Request, _res: Response, next: NextFunction
// ) => {
//   if (!req.file && !req.files) return next();

//   try {
//     // Wait, since we moved to diskStorage, req.file.buffer is undefined. We need to read from path or use fileTypeFromFile.
//     const { fileTypeFromFile } = await import('file-type');

//     // Check single file
//     if (req.file) {
//       const type = await fileTypeFromFile(req.file.path);
//       const isAllowedPhoto = ALLOWED_MIME.photo.includes((type?.mime as any));
//       const isAllowedDoc = ALLOWED_MIME.document.includes((type?.mime as any));
//       const isAllowedChat = ALLOWED_MIME.chatMedia.includes((type?.mime as any));
//       const isAllowedVideo = ALLOWED_MIME.video.includes((type?.mime as any));

//       if (!type || (!isAllowedPhoto && !isAllowedDoc && !isAllowedChat && !isAllowedVideo)) {
//         if (fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
//         return next(new AppError('Invalid file content', 400));
//       }
//     }

//     // Check multiple files
//     if (req.files) {
//       const files = req.files as { [fieldname: string]: Express.Multer.File[] };
//       for (const fieldName in files) {
//         for (const file of files[fieldName]) {
//           const type = await fileTypeFromFile(file.path);
//           const isAllowedPhoto = ALLOWED_MIME.photo.includes((type?.mime as any));
//           const isAllowedDoc = ALLOWED_MIME.document.includes((type?.mime as any));
//           const isAllowedChat = ALLOWED_MIME.chatMedia.includes((type?.mime as any));
//           const isAllowedVideo = ALLOWED_MIME.video.includes((type?.mime as any));

//           if (!type || (!isAllowedPhoto && !isAllowedDoc && !isAllowedChat && !isAllowedVideo)) {
//             // Delete all uploaded files if one fails
//             for (const field in files) {
//               for (const f of files[field]) {
//                 if (fs.existsSync(f.path)) fs.unlinkSync(f.path);
//               }
//             }
//             return next(new AppError(`Invalid file content in field ${fieldName}`, 400));
//           }
//         }
//       }
//     }

//     next();
//   } catch (error) {
//     if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
//     if (req.files) {
//       const files = req.files as { [fieldname: string]: Express.Multer.File[] };
//       for (const fieldName in files) {
//         for (const file of files[fieldName]) {
//           if (fs.existsSync(file.path)) fs.unlinkSync(file.path);
//         }
//       }
//     }
//     next(new AppError('Error validating file content', 500));
//   }
// };

export const validateMagicBytes = async (
  req: Request, _res: Response, next: NextFunction
) => {
  if (!req.file && !req.files) return next();

  const { fileTypeFromFile } = await import('file-type');

  // Helper to collect all files for cleanup
  const allFiles = (): Express.Multer.File[] => {
    const single = req.file ? [req.file] : [];
    const multi = req.files
      ? Object.values(req.files as Record<string, Express.Multer.File[]>).flat()
      : [];
    return [...single, ...multi];
  };

  const cleanup = () =>
    allFiles().forEach(f => { if (fs.existsSync(f.path)) fs.unlinkSync(f.path); });

  try {
    // Single file — req.file has no fieldname context for profile,
    // so fall back to checking all allowed types
    if (req.file) {
      const type = await fileTypeFromFile(req.file.path);
      const allAllowed = Object.values(ALLOWED_MIME).flat();
      if (!type || !allAllowed.includes(type.mime as any)) {
        cleanup();
        return next(new AppError('Invalid file content', 400));
      }
    }

    // Multiple files — we know the field name, so validate precisely
    if (req.files) {
      const files = req.files as Record<string, Express.Multer.File[]>;
      for (const fieldName in files) {
        const profile = FIELD_MIME_PROFILE[fieldName];
        const allowed = profile
          ? ALLOWED_MIME[profile] as readonly string[]
          : Object.values(ALLOWED_MIME).flat(); // unknown field: fall back to all

        for (const file of files[fieldName]) {
          const type = await fileTypeFromFile(file.path);
          if (!type || !allowed.includes(type.mime as any)) {
            cleanup();
            return next(new AppError(`Invalid file content in field "${fieldName}"`, 400));
          }
        }
      }
    }

    next();
  } catch (error) {
    cleanup();
    next(new AppError('Error validating file content', 500));
  }
};