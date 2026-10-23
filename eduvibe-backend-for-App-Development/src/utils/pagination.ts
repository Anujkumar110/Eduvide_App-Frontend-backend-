import { Request } from 'express';

export interface PaginationOptions {
  page: number;
  limit: number;
  skip: number;
  sort: string;
}

export const getPagination = (query: Request['query']): PaginationOptions => {
  const page = Math.max(1, parseInt(query.page as string) || 1);
  const limit = Math.min(100, Math.max(1, parseInt(query.limit as string) || 20));
  const skip = (page - 1) * limit;
  const sort = (query.sort as string) || '-createdAt';
  return { page, limit, skip, sort };
};
