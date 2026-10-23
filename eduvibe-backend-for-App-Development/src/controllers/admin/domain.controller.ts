import { Request, Response, NextFunction } from 'express';
import * as domainService from '../../services/admin/domain.service';
import { sendSuccess, sendPaginated } from '../../utils/response';

export const createDomain = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const domain = await domainService.createDomain(req.user!.userId, req.body);
    sendSuccess(res, 'Domain created', domain, 201);
  } catch (err) {
    next(err);
  }
};

export const listDomains = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { domains, pagination } = await domainService.listDomains(req.query);
    sendPaginated(res, 'Domains fetched', domains, pagination);
  } catch (err) {
    next(err);
  }
};

export const getDomain = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const result = await domainService.getDomainById(String(req.params.id));
    sendSuccess(res, 'Domain fetched', result);
  } catch (err) {
    next(err);
  }
};

export const updateDomain = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const domain = await domainService.updateDomain(req.user!.userId, String(req.params.id), req.body);
    sendSuccess(res, 'Domain updated', domain);
  } catch (err) {
    next(err);
  }
};

export const deactivateDomain = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const domain = await domainService.deactivateDomain(req.user!.userId, String(req.params.id));
    sendSuccess(res, 'Domain deactivated', domain);
  } catch (err) {
    next(err);
  }
};

export const listSubDomains = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const { subDomains, pagination } = await domainService.listSubDomains(String(req.params.id), req.query);
    sendPaginated(res, 'Sub-domains fetched', subDomains, pagination);
  } catch (err) {
    next(err);
  }
};

export const updateDomainPricing = async (req: Request, res: Response, next: NextFunction) => {
  try {
    const domain = await domainService.updateDomainPricing(req.user!.userId, String(req.params.id), req.body);
    sendSuccess(res, 'Domain pricing updated', domain);
  } catch (err) {
    next(err);
  }
};
