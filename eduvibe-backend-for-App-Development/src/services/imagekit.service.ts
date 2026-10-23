import ImageKit from 'imagekit';
import { env } from '../config/env';
import { IK_FOLDERS } from '../constants';

const ik = new ImageKit({
  publicKey: env.IK_PUBLIC_KEY,
  privateKey: env.IK_PRIVATE_KEY,
  urlEndpoint: env.IK_URL_ENDPOINT,
});

export interface UploadResult {
  url: string;   // public CDN URL (do NOT store for private docs)
  fileId: string;   // for deletion
  filePath: string;   // for signed URL generation
}

// Core upload — buffer goes straight to ImageKit
export const uploadToImageKit = async (
  fileData: Buffer | string,
  fileName: string,
  folder: keyof typeof IK_FOLDERS,
  // options?: any
): Promise<UploadResult> => {
  const res = await ik.upload({
    file: fileData,
    fileName: `${Date.now()}-${fileName}`,
    folder: IK_FOLDERS[folder],
    useUniqueFileName: true,
    // ...options,
  });
  return { url: res.url, fileId: res.fileId, filePath: res.filePath };
};

// Always call when user replaces a file — prevents orphaned storage
export const deleteFromImageKit = async (fileId: string): Promise<void> => {
  await ik.deleteFile(fileId);
};

// Signed URL for private/admin-only documents (default: 1-hour expiry)
export const getSignedUrl = (filePath: string, expiry = 3600): string => {
  return ik.url({ path: filePath, signed: true, expireSeconds: expiry });
};

// Signed auth params for direct frontend upload (video)
export const getUploadAuthParams = () => ik.getAuthenticationParameters();
