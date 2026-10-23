import { Worker, Job } from 'bullmq';
import { redisConnection } from '../config/redis';
import { logger } from '../utils/logger';
import { UPLOAD_JOB_TYPES } from '../queues/upload.queue';
import { notificationQueue, NOTIFICATION_JOB_TYPES, NOTIFICATION_EVENTS } from '../queues/notification.queue';
import { TeacherProfile, StudentProfile, Class } from '../models';
import { uploadToImageKit, deleteFromImageKit } from '../services/imagekit.service';
import fs from 'fs';
import util from 'util';

const unlinkAsync = util.promisify(fs.unlink);

interface UploadJobData {
  userId: string;
  role: 'student' | 'teacher';
  filePath: string;
  originalName: string;
  mimeType: string;
  documentType?: 'idProof' | 'certificate';
  qualificationId?: string; // used if updating a specific qualification's certificate
  classId?: string; // used for video
}

export const uploadWorker = new Worker(
  'uploadQueue',
  async (job: Job<UploadJobData>) => {
    logger.info(`Processing upload job ${job.id}`, { type: job.name, userId: job.data.userId });

    const { userId, role, filePath, originalName } = job.data;

    try {
      if (!fs.existsSync(filePath)) {
        throw new Error(`File not found at path: ${filePath}`);
      }

      const buffer = fs.readFileSync(filePath);

      let uploadResult;

      if (job.name === UPLOAD_JOB_TYPES.PROFILE_PHOTO) {
        uploadResult = await uploadToImageKit(
          buffer,
          originalName,
          'profilePhotos',
          // {
          //   transformation: {
          //     post: [
          //       {
          //         type: "resize",
          //         width: 400,
          //         height: 400,
          //       },
          //     ],
          //   },
          // }
        );

        if (role === 'teacher') {
          const profile = await TeacherProfile.findOne({ userId });
          if (profile?.profilePhotoFileId) {
            await deleteFromImageKit(profile.profilePhotoFileId).catch(err => logger.warn('IK delete failed', { err }));
          }
          await TeacherProfile.findOneAndUpdate(
            { userId },
            { profilePhoto: uploadResult.url, profilePhotoFileId: uploadResult.fileId }
          );
        } else if (role === 'student') {
          const profile = await StudentProfile.findOne({ userId });
          if (profile?.profilePhotoFileId) {
            await deleteFromImageKit(profile.profilePhotoFileId).catch(err => logger.warn('IK delete failed', { err }));
          }
          await StudentProfile.findOneAndUpdate(
            { userId },
            { profilePhoto: uploadResult.url, profilePhotoFileId: uploadResult.fileId }
          );
        }
      } else if (job.name === UPLOAD_JOB_TYPES.DOCUMENT) {
        uploadResult = await uploadToImageKit(
          buffer,
          originalName,
          'certificates'
        );

        const profile = await TeacherProfile.findOne({ userId });
        if (profile) {
          if (job.data.documentType === 'idProof') {
            if (profile.verification?.idProofFileId) {
              await deleteFromImageKit(profile.verification.idProofFileId).catch(err => logger.warn('IK delete failed', { err }));
            }
            await TeacherProfile.findOneAndUpdate(
              { userId },
              {
                'verification.idProofPath': uploadResult.filePath,
                'verification.idProofFileId': uploadResult.fileId,
              }
            );
          } else if (job.data.documentType === 'certificate' && job.data.qualificationId) {
            // we don't know the exact schema for qualifications in the MVP right now
            // but normally we would update the array element matching the qualificationId
            await TeacherProfile.findOneAndUpdate(
              { userId, 'qualifications._id': job.data.qualificationId },
              {
                'qualifications.$.certificateUrl': uploadResult.url,
              }
            );
          }
        }
      } else if (job.name === UPLOAD_JOB_TYPES.VIDEO) {
        uploadResult = await uploadToImageKit(
          buffer,
          originalName,
          'classMedia'
        );

        if (job.data.classId) {
          const cls = await Class.findById(job.data.classId);
          if (cls) {
            if (cls.videoFileId) {
              await deleteFromImageKit(cls.videoFileId).catch(err => logger.warn('IK delete failed', { err }));
            }
            await Class.findByIdAndUpdate(job.data.classId, {
              videoUrl: uploadResult.url,
              videoFileId: uploadResult.fileId,
            });
          }
        }
      } else if (job.name === UPLOAD_JOB_TYPES.INTRO_VIDEO) {
        uploadResult = await uploadToImageKit(
          buffer,
          originalName,
          'introVideo'
        );

        const profile = await TeacherProfile.findOne({ userId });
        if (profile) {
          if (profile.introVideoFileId) {
            await deleteFromImageKit(profile.introVideoFileId).catch(err => logger.warn('IK delete failed', { err }));
          }
          await TeacherProfile.findOneAndUpdate(
            { userId },
            {
              introVideo: uploadResult.url,
              introVideoFileId: uploadResult.fileId,
            }
          );
        }
      }

      // Cleanup temp file
      await unlinkAsync(filePath);

      // Notify user
      await notificationQueue.add(NOTIFICATION_JOB_TYPES.SEND_NOTIFICATION, {
        userId,
        type: NOTIFICATION_EVENTS.UPLOAD_COMPLETE,
        title: 'Upload Successful',
        message: `Your upload for ${originalName} was completed successfully.`,
        metadata: { jobId: job.id, url: uploadResult?.url }
      });

      logger.info(`Completed upload job ${job.id}`);
      return { success: true, url: uploadResult?.url };
    } catch (error: any) {
      logger.error(`Failed upload job ${job.id}`, { error: error.message });

      // Cleanup temp file if it exists and it's the last attempt
      if (job.attemptsMade >= job.opts.attempts! - 1) {
        if (fs.existsSync(filePath)) {
          await unlinkAsync(filePath).catch(() => { });
        }
      }
      throw error;
    }
  },
  {
    connection: redisConnection as any,
    drainDelay: 60 * 1000
  }
);

uploadWorker.on('failed', (job, err) => {
  logger.error(`Upload job ${job?.id} failed with error: ${err.message}`);
});
