import { Response } from 'express';

export const sendSuccess = (
  res: Response,
  message: string,
  data: unknown = null,
  statusCode = 200
): Response => {
  return res.status(statusCode).json({ success: true, message, data });
};

export const sendError = (
  res: Response,
  message: string,
  statusCode = 500,
  errors?: unknown
): Response => {
  const body: Record<string, unknown> = { success: false, message };
  if (errors !== undefined) body.errors = errors;
  return res.status(statusCode).json(body);
};

export const sendPaginated = (
  res: Response,
  message: string,
  data: unknown[],
  pagination: { total: number; page: number; limit: number }
): Response => {
  return res.status(200).json({
    success: true,
    message,
    data,
    pagination: {
      ...pagination,
      pages: Math.ceil(pagination.total / pagination.limit),
    },
  });
};
