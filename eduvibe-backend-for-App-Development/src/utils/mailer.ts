import nodemailer from 'nodemailer';
import { env } from '../config/env';
import { logger } from './logger';

const transporter = nodemailer.createTransport({
  host: env.SMTP_HOST,
  port: parseInt(env.SMTP_PORT),
  secure: false,
  auth: {
    user: env.SMTP_USER,
    pass: env.SMTP_PASS,
  },
});

interface MailOptions {
  to: string;
  subject: string;
  html: string;
}

export const sendEmail = async (opts: MailOptions): Promise<void> => {
  try {
    await transporter.sendMail({ from: env.EMAIL_FROM, ...opts });
    logger.info('Email sent', { to: opts.to, subject: opts.subject });
  } catch (err) {
    logger.error('Email send failed', { error: err, to: opts.to });
    // Don't throw — email failures shouldn't break the request
  }
};

export const passwordResetEmail = (to: string, resetToken: string) =>
  sendEmail({
    to,
    subject: 'Reset your EduVibe password',
    html: `
      <h2>Password Reset</h2>
      <p>Click the link below to reset your password. The link expires in 1 hour.</p>
      <a href="${env.FRONTEND_URL}/reset-password?token=${resetToken}">Reset Password</a>
      <p>If you didn't request this, ignore this email.</p>
    `,
  });

export const teacherApprovedEmail = (to: string, name: string) =>
  sendEmail({
    to,
    subject: 'You\'re verified on EduVibe 🎉',
    html: `
      <h2>Congratulations, ${name}!</h2>
      <p>Your teacher profile has been verified. You can now create and publish classes.</p>
      <a href="${env.FRONTEND_URL}/teacher/dashboard">Go to Dashboard</a>
    `,
  });

export const teacherRejectedEmail = (to: string, name: string, reason: string) =>
  sendEmail({
    to,
    subject: 'EduVibe Verification Update',
    html: `
      <h2>Hi ${name},</h2>
      <p>Unfortunately, your verification was not approved for the following reason:</p>
      <blockquote>${reason}</blockquote>
      <p>You may re-apply after 30 days.</p>
    `,
  });

export const teacherReuploadEmail = (to: string, name: string, reason: string) =>
  sendEmail({
    to,
    subject: 'Action Required — RE-upload Documents',
    html: `
      <h2>Hi ${name},</h2>
      <p>Our team needs you to re-upload your verification documents:</p>
      <blockquote>${reason}</blockquote>
      <a href="${env.FRONTEND_URL}/teacher/onboarding">Re-upload Documents</a>
    `,
  });
