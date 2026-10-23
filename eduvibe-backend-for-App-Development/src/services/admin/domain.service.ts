import { Request } from 'express';
import { Domain } from '../../models/domain.model';
import { AuditLog } from '../../models/auditLog.model';
import { AppError } from '../../utils/errors';
import { getPagination } from '../../utils/pagination';

const logAction = async (
  adminId: string,
  action: string,
  targetId: string,
  metadata: Record<string, unknown> = {}
) => {
  await AuditLog.create({ adminId, action, targetId, targetType: 'domain', metadata });
};

// ─── Create ──────────────────────────────────────────────────────────────────

export const createDomain = async (
  adminId: string,
  body: {
    name: string;
    description: string;
    parentId?: string | null;
    pricing?: { baseRate?: number; currency?: string };
  }
) => {
  const { name, description, parentId = null, pricing = {} } = body;

  if (parentId) {
    const parent = await Domain.findById(parentId);
    if (!parent) throw new AppError('Parent domain not found', 404);
    if (parent.parentId) throw new AppError('Nesting beyond one level is not allowed', 400);
  }

  const domain = await Domain.create({
    name,
    description,
    parentId: parentId || null,
    pricing: {
      baseRate: pricing.baseRate ?? 0,
      currency: pricing.currency ?? 'INR',
    },
  });

  await logAction(adminId, 'create_domain', String(domain._id), { name });
  return domain;
};

// ─── List ─────────────────────────────────────────────────────────────────────

export const listDomains = async (query: Request['query']) => {
  const { page, limit, skip, sort } = getPagination(query);

  const filter: Record<string, unknown> = {};
  if (query.isActive !== undefined) filter.isActive = query.isActive === 'true';
  // When `tree=true`, return only top-level domains (parentId is null)
  if (query.tree === 'true') filter.parentId = null;

  const [domains, total] = await Promise.all([
    Domain.find(filter).sort(sort).skip(skip).limit(limit).populate('parentId', 'name'),
    Domain.countDocuments(filter),
  ]);

  return { domains, pagination: { total, page, limit } };
};

// ─── Get by ID ────────────────────────────────────────────────────────────────

export const getDomainById = async (id: string) => {
  const domain = await Domain.findById(id).populate('parentId', 'name');
  if (!domain) throw new AppError('Domain not found', 404);

  const subDomains = await Domain.find({ parentId: id, isActive: true }).sort('name');
  return { domain, subDomains };
};

// ─── Update ───────────────────────────────────────────────────────────────────

export const updateDomain = async (
  adminId: string,
  id: string,
  body: {
    name?: string;
    description?: string;
    isActive?: boolean;
    pricing?: { baseRate?: number; currency?: string };
  }
) => {
  const domain = await Domain.findById(id);
  if (!domain) throw new AppError('Domain not found', 404);

  if (body.name !== undefined) domain.name = body.name;
  if (body.description !== undefined) domain.description = body.description;
  if (body.isActive !== undefined) domain.isActive = body.isActive;
  if (body.pricing?.baseRate !== undefined) domain.pricing.baseRate = body.pricing.baseRate;
  if (body.pricing?.currency !== undefined) domain.pricing.currency = body.pricing.currency;

  await domain.save();
  await logAction(adminId, 'update_domain', id, { changes: body });
  return domain;
};

// ─── Deactivate (soft-delete) ─────────────────────────────────────────────────

export const deactivateDomain = async (adminId: string, id: string) => {
  const domain = await Domain.findByIdAndUpdate(id, { isActive: false }, { new: true });
  if (!domain) throw new AppError('Domain not found', 404);

  // Also deactivate all sub-domains
  await Domain.updateMany({ parentId: id }, { isActive: false });

  await logAction(adminId, 'deactivate_domain', id, { name: domain.name });
  return domain;
};

// ─── Sub-domains ──────────────────────────────────────────────────────────────

export const listSubDomains = async (parentId: string, query: Request['query']) => {
  const { page, limit, skip, sort } = getPagination(query);

  const parent = await Domain.findById(parentId);
  if (!parent) throw new AppError('Parent domain not found', 404);

  const filter: Record<string, unknown> = { parentId };
  if (query.isActive !== undefined) filter.isActive = query.isActive === 'true';

  const [subDomains, total] = await Promise.all([
    Domain.find(filter).sort(sort).skip(skip).limit(limit),
    Domain.countDocuments(filter),
  ]);

  return { subDomains, pagination: { total, page, limit } };
};

// ─── Update pricing ───────────────────────────────────────────────────────────

export const updateDomainPricing = async (
  adminId: string,
  id: string,
  pricing: { baseRate: number; currency?: string }
) => {
  const domain = await Domain.findById(id);
  if (!domain) throw new AppError('Domain not found', 404);

  domain.pricing.baseRate = pricing.baseRate;
  if (pricing.currency) domain.pricing.currency = pricing.currency;

  await domain.save();
  await logAction(adminId, 'update_domain_pricing', id, { pricing });
  return domain;
};
