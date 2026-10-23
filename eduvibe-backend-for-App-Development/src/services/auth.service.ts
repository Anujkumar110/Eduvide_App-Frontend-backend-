import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { User } from '../models/user.model';
import { TeacherProfile } from '../models/teacherProfile.model';
import { StudentProfile } from '../models/studentProfile.model';
import { OAuth2Client } from 'google-auth-library';
import axios from 'axios';
import { env } from '../config/env';
import { AppError } from '../utils/errors';
import { Role } from '../constants';
import { passwordResetEmail } from '../utils/mailer';
import { firebaseAuth } from '../config/firebase';
import { createWallet } from './business/wallet.service';

export interface SignupInput {
  email: string;
  password: string;
  role: string;
  fullName: string;
  gender?: 'male' | 'female' | 'other';
  age?: number;
}

export interface LoginInput {
  email: string;
  role: Role;
  password: string;
}

const googleClient = new OAuth2Client(env.GOOGLE_CLIENT_ID);

const generateTokens = (userId: string, loggedInAs: Role, email: string) => {
  const accessToken = jwt.sign({ userId, loggedInAs, email }, env.JWT_SECRET);
  return { accessToken };
};

const formatUser = (user: any, role: string) => {
  const obj = typeof user.toObject === 'function' ? user.toObject() : { ...user };
  delete obj.passwordHash;
  return { ...obj, role };
};

export const signup = async (input: SignupInput) => {
  // ✅ Validation
  if (!input.email) throw new AppError('Email is required', 400);
  if (!input.role) throw new AppError('Role is required', 400);
  if (!input.fullName) throw new AppError('Full name is required', 400);

  const existing = await User.findOne({ email: input.email });

  if (existing) {
    const incomingRole = input.role as Role;

    if (existing.role.includes(incomingRole)) {
      throw new AppError('Email already registered with this role', 409);
    }

    const allowedRoles: Role[] = ['student', 'teacher'];
    if (!allowedRoles.includes(incomingRole) || !existing.role.every(r => allowedRoles.includes(r))) {
      throw new AppError('Cannot add this role to an existing account', 403);
    }

    if (!existing.passwordHash) {
      throw new AppError('Please log in with Google to add a new role', 401);
    }

    // ✅ Password check sirf existing user ke liye
    if (!input.password) throw new AppError('Password is required to add a new role', 400);

    const valid = await bcrypt.compare(input.password, existing.passwordHash as string);
    if (!valid) throw new AppError('Invalid password for existing account', 401);

    existing.role.push(incomingRole);
    await existing.save();

    if (incomingRole === 'teacher') {
      await TeacherProfile.create({
        userId: existing._id,
        fullName: input.fullName,
        email: input.email,
        ...(input.gender && { gender: input.gender }),
        ...(input.age !== undefined && { age: input.age }),
      });
    } else {
      await StudentProfile.create({ userId: existing._id, fullName: input.fullName });
    }

    const { accessToken } = generateTokens(existing._id.toString(), incomingRole, existing.email);
    return { accessToken, user: formatUser(existing, incomingRole) };
  }

  // ✅ Naye user ke liye password required
  if (!input.password) throw new AppError('Password is required', 400);

  const passwordHash = await bcrypt.hash(input.password, 12);
  const user = await User.create({
    email: input.email,
    passwordHash,
    role: [input.role],
  });

  if (input.role === 'teacher') {
    await TeacherProfile.create({
      userId: user._id,
      fullName: input.fullName,
      email: input.email,
      ...(input.gender && { gender: input.gender }),
      ...(input.age !== undefined && { age: input.age }),
    });
  } else {
    await StudentProfile.create({ userId: user._id, fullName: input.fullName });
  }

  await createWallet(user._id.toString());

  const { accessToken } = generateTokens(user._id.toString(), input.role as Role, user.email);
  return { accessToken, user: formatUser(user, input.role) };
};

export const login = async (input: LoginInput) => {
  const user = await User.findOne({ email: input.email }).select('+passwordHash').lean();
  if (!user) throw new AppError('Invalid email or password', 401);
  if (!user.role.includes(input.role))
    throw new AppError(`You are not authorized to login as a ${input.role}`, 401);
  if (user.isBanned) throw new AppError('Account suspended. Contact support.', 403);
  if (!user.isActive) throw new AppError('Account deactivated', 403);

  if (!user.passwordHash) {
    throw new AppError('Invalid email or password', 401);
  }

  const valid = await bcrypt.compare(input.password, user.passwordHash as string);
  if (!valid) throw new AppError('Invalid email or password', 401);

  await User.findByIdAndUpdate(user._id, { lastLoginAt: new Date() });

  const { accessToken } = generateTokens(user._id.toString(), input.role as Role, user.email);
  const { passwordHash: _, ...safeUser } = user;
  return { accessToken, user: { ...safeUser, role: input.role } };
};

export const googleAuth = async (token: string, role: Role) => {
  let email, name;
  try {
    const response = await axios.get('https://www.googleapis.com/oauth2/v3/userinfo', {
      headers: { Authorization: `Bearer ${token}` },
    });
    email = response.data.email;
    name = response.data.name;
  } catch (error) {
    throw new AppError('Invalid Google access token', 401);
  }

  if (!email) throw new AppError('Google account has no email', 400);

  let user = await User.findOne({ email }).select('+passwordHash').lean();

  if (user) {
    if (user.isBanned) throw new AppError('Account suspended. Contact support.', 403);
    if (!user.isActive) throw new AppError('Account deactivated', 403);

    await User.findByIdAndUpdate(user._id, { lastLoginAt: new Date() });

    const { accessToken } = generateTokens(user._id.toString(), role, user.email);
    const { passwordHash: _, ...safeUser } = user;
    return { accessToken, user: { ...safeUser, role } };
  }

  if (!role) throw new AppError('Account not found. Please sign up first.', 404);
  if (role !== 'student' && role !== 'teacher') throw new AppError('Invalid role specified during sign up', 400);

  const newUser = await User.create({
    email,
    authProvider: 'google',
    role: [role],
  });

  if (role === 'teacher') {
    await TeacherProfile.create({ userId: newUser._id, fullName: name || 'Google User', email });
  } else {
    await StudentProfile.create({ userId: newUser._id, fullName: name || 'Google User' });
  }

  await createWallet(newUser._id.toString());

  const { accessToken } = generateTokens(newUser._id.toString(), role, newUser.email);
  return { accessToken, user: formatUser(newUser, role) };
};

export const logout = async (userId: string) => {};

export const forgotPassword = async (email: string) => {
  const user = await User.findOne({ email });
  if (!user) return;

  const token = crypto.randomBytes(32).toString('hex');
  await passwordResetEmail(email, token);
};

export const resetPassword = async (token: string, newPassword: string) => {
  throw new AppError('Reset password is not fully implemented after removing Redis', 400);
};

export const googleLogin = async (payload: any, role: Role) => {
  const { email, name, sub: googleId, picture } = payload;
  const user = await User.findOne({ email });
  if (!user) {
    const newUser = await User.create({ email, role: [role] });
    await StudentProfile.create({ userId: newUser._id, fullName: name, profilePhoto: picture });
    const { accessToken } = generateTokens(newUser._id.toString(), role, newUser.email);
    return { accessToken, user: formatUser(newUser, role) };
  }
  const { accessToken } = generateTokens(user._id.toString(), role, user.email);
  return { accessToken, user: formatUser(user, role) };
};

export const firebaseAuthentication = async (token: string, role: Role): Promise<{ accessToken: string; user: any }> => {
  let email, name;
  try {
    const decoded = await firebaseAuth.verifyIdToken(token);
    email = decoded.email;
    name = decoded.name;
  } catch (error) {
    throw new AppError('Invalid Firebase token', 401);
  }

  let user = await User.findOne({ email });
  if (user) {
    if (user.isBanned) throw new AppError('Account suspended. Contact support.', 403);
    if (!user.isActive) throw new AppError('Account deactivated', 403);

    await User.findByIdAndUpdate(user._id, { lastLoginAt: new Date() });

    const { accessToken } = generateTokens(user._id.toString(), role, user.email);
    const { passwordHash: _, ...safeUser } = user;
    return { accessToken, user: { ...safeUser, role } };
  }

  if (!role) throw new AppError('Invalid role specified during sign up', 400);
  if (role !== 'student' && role !== 'teacher') throw new AppError('Invalid role specified during sign up', 400);

  const newUser = await User.create({
    email,
    authProvider: 'firebase',
    role: [role],
  });

  if (role === 'teacher') {
    await TeacherProfile.create({ userId: newUser._id, fullName: name || 'Firebase User', email });
  } else {
    await StudentProfile.create({ userId: newUser._id, fullName: name || 'Firebase User' });
  }

  await createWallet(newUser._id.toString());

  const { accessToken } = generateTokens(newUser._id.toString(), role, newUser.email);
  return { accessToken, user: formatUser(newUser, role) };
};

export const verifyGoogleToken = async (token: string, role: Role) => {
  let email, name;
  try {
    const ticket = await googleClient.verifyIdToken({
      idToken: token,
      audience: env.GOOGLE_CLIENT_ID,
    });
    const payload = ticket.getPayload();
    if (!payload) throw new AppError('Invalid Google token', 401);
    email = payload.email;
    name = payload.name;
  } catch (error) {
    throw new AppError('Invalid Google token', 401);
  }

  if (!email) throw new AppError('Google account has no email', 400);

  let user = await User.findOne({ email }).select('+passwordHash').lean();

  if (user) {
    if (user.isBanned) throw new AppError('Account suspended. Contact support.', 403);
    if (!user.isActive) throw new AppError('Account deactivated', 403);

    if (role) {
      const allowedRoles: Role[] = ['student', 'teacher'];

      if (!allowedRoles.includes(role) || !user.role.every(r => allowedRoles.includes(r))) {
        throw new AppError('Cannot add this role to an existing account', 403);
      }

      if (user.role.includes(role)) {
        throw new AppError('Account already registered with this role', 409);
      }

      const updated = await User.findByIdAndUpdate(
        user._id,
        { $addToSet: { role }, lastLoginAt: new Date() },
        { new: true }
      ).lean();

      if (role === 'teacher') {
        await TeacherProfile.create({ userId: user._id, fullName: name || 'Google User', email });
      } else {
        await StudentProfile.create({ userId: user._id, fullName: name || 'Google User' });
      }

      const { accessToken } = generateTokens(user._id.toString(), role, user.email);
      const { passwordHash: _, ...safeUser } = updated!;
      return { accessToken, user: { ...safeUser, role } };
    }

    await User.findByIdAndUpdate(user._id, { lastLoginAt: new Date() });
    const { accessToken } = generateTokens(user._id.toString(), role, user.email);
    const { passwordHash: _, ...safeUser } = user;
    return { accessToken, user: { ...safeUser, role } };
  }

  if (!role) throw new AppError('Account not found. Please sign up first.', 404);
  if (role !== 'student' && role !== 'teacher') throw new AppError('Invalid role specified during sign up', 400);

  const newUser = await User.create({
    email,
    authProvider: 'google',
    role: [role],
  });

  if (role === 'teacher') {
    await TeacherProfile.create({ userId: newUser._id, fullName: name || 'Google User', email });
  } else {
    await StudentProfile.create({ userId: newUser._id, fullName: name || 'Google User' });
  }

  const { accessToken } = generateTokens(newUser._id.toString(), role, newUser.email);
  return { accessToken, user: formatUser(newUser, role) };
};