import { User, IUser } from '../models/user.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { StudentProfile } from '../models/studentProfile.model';
import { AppError } from '../utils/errors';
import { getPagination } from '../utils/pagination';
import { Request } from 'express';

export const getUserById = async (userId: string) => {
  const user = await User.findById(userId);
  if (!user) throw new AppError('User not found', 404);
  return user;
};

export const updateUser = async (userId: string, updateData: Partial<IUser>) => {
  const user = await User.findByIdAndUpdate(userId, updateData, { new: true });
  if (!user) throw new AppError('User not found', 404);
  return user;
};

export const deleteUser = async (userId: string) => {
  const user = await User.findByIdAndUpdate(
    userId,
    { isActive: false },
    { new: true }
  );
  if (!user) throw new AppError('User not found', 404);
  return user;
};

export const listUsers = async (query: Request['query']) => {
  const { page, limit, skip, sort } = getPagination(query);
  const filter: Record<string, unknown> = {
    role: { $ne: 'admin' },
  };

  if (query.isBanned !== undefined) filter.isBanned = query.isBanned === 'true';

  const [users, total] = await Promise.all([
    User.find(filter).sort(sort).skip(skip).limit(limit),
    User.countDocuments(filter),
  ]);

  return { users, pagination: { total, page, limit } };
};

export const getStudent = async (userId: string) => {
  const user = await StudentProfile.findOne({ userId });
  if (!user) throw new AppError('User not found', 404);
  return user;
};

export const getTeacher = async (userId: string) => {
  const user = await TeacherProfile.findOne({ userId });
  if (!user) throw new AppError('User not found', 404);
  return user;
};