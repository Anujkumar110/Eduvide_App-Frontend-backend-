import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';
import { User } from '../models/user.model';
import { connectDB } from '../config/db';
import { logger } from '../utils/logger';
import { ROLES } from '../constants';

const ADMIN_EMAIL = 'admin@eduvibe.com';
const ADMIN_PASSWORD = 'Admin@123';

export const seedAdmin = async () => {
  try {
    await connectDB();

    const existingAdmin = await User.findOne({ email: ADMIN_EMAIL });
    if (existingAdmin) {
      logger.info('Admin user already exists');
      process.exit(0);
    }

    const passwordHash = await bcrypt.hash(ADMIN_PASSWORD, 12);

    await User.create({
      email: ADMIN_EMAIL,
      passwordHash,
      role: [ROLES.ADMIN],
      isActive: true,
      isBanned: false,
    });

    logger.info('Admin user seeded successfully');
    logger.info(`Email: ${ADMIN_EMAIL}`);
    logger.info(`Password: ${ADMIN_PASSWORD}`);

    process.exit(0);
  } catch (error) {
    logger.error('Error seeding admin:', error);
    process.exit(1);
  }
};

seedAdmin();
