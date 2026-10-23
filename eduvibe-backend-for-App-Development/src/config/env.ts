import dotenv from 'dotenv';

dotenv.config();

export const env = {
  NODE_ENV: process.env.NODE_ENV || 'development',
  PORT: process.env.PORT || '5000',
  MONGO_URI: process.env.MONGO_URI || '',
  JWT_SECRET: process.env.JWT_SECRET || 'secret',
  IK_PUBLIC_KEY: process.env.IK_PUBLIC_KEY || '',
  IK_PRIVATE_KEY: process.env.IK_PRIVATE_KEY || '',
  IK_URL_ENDPOINT: process.env.IK_URL_ENDPOINT || '',
  SMTP_HOST: process.env.SMTP_HOST || 'smtp.gmail.com',
  SMTP_PORT: process.env.SMTP_PORT || '587',
  SMTP_USER: process.env.SMTP_USER || '',
  SMTP_PASS: process.env.SMTP_PASS || '',
  EMAIL_FROM: process.env.EMAIL_FROM || 'EduVibe <no-reply@eduvibe.com>',
  FRONTEND_URL: process.env.FRONTEND_URL || 'http://localhost:3000',
  REDIS_USER: process.env.REDIS_USER || 'default',
  REDIS_HOST: process.env.REDIS_HOST || 'localhost',
  REDIS_PORT: process.env.REDIS_PORT || '6379',
  REDIS_PASSWORD: process.env.REDIS_PASSWORD || '',
  GOOGLE_CLIENT_ID: process.env.GOOGLE_CLIENT_ID || '',
  GOOGLE_CLIENT_SECRET: process.env.GOOGLE_CLIENT_SECRET || '',
};

if (!env.MONGO_URI || !env.JWT_SECRET || !env.IK_PUBLIC_KEY || !env.IK_PRIVATE_KEY || !env.IK_URL_ENDPOINT || !env.REDIS_HOST) {
  console.error('Missing critical environment variables');
  process.exit(1);
}

