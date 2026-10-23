import { Request, Response, NextFunction } from 'express';
import { AppError } from '../utils/errors';
import { sendSuccess, sendError, sendPaginated } from '../utils/response';
import * as userService from '../services/user.service';

export const userController = {
    listUsers: async (req: Request, res: Response) => {
        try {
            const { users, pagination } = await userService.listUsers(req.query);
            sendPaginated(res, 'Users fetched', users, pagination);
        } catch (err) {
            sendError(res, 'Users not found', 404);
        }
    },

    getStudent: async (req: Request, res: Response) => {
        try {
            const user = await userService.getStudent(req.params.id as string);
            sendSuccess(res, 'Student fetched', user);
        } catch (err) {
            sendError(res, "Student not found", 404);
        }
    },

    getTeacher: async (req: Request, res: Response) => {
        try {
            const user = await userService.getTeacher(req.params.id as string);
            sendSuccess(res, 'Teacher fetched', user);
        } catch (err) {
            sendError(res, "Teacher not found", 404);
        }
    },

    updateUser: async (req: Request, res: Response) => {
        try {
            const user = await userService.updateUser(req.params.id as string, req.body);
            sendSuccess(res, 'User updated', user);
        } catch (err) {
            sendError(res, "User not found", 404);
        }
    },

    deleteUser: async (req: Request, res: Response) => {
        try {
            const user = await userService.deleteUser(req.params.id as string);

            sendSuccess(res, 'User deleted', user);
        } catch (err) {
            sendError(res, "User not found", 404);
        }
    },
}