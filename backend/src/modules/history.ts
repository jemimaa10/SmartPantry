import { Router } from 'express';
import { notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';

export const historyRouter = Router();
historyRouter.use(requireAuth);

historyRouter.get('/', async (req, res) => {
  const scans = await prisma.scanHistory.findMany({
    where: { userId: currentUser(req) },
    orderBy: { createdAt: 'desc' },
    take: 50,
    select: { id: true, ingredients: true, createdAt: true },
  });
  res.json(scans);
});

historyRouter.delete('/:id', async (req, res) => {
  const result = await prisma.scanHistory.deleteMany({ where: { id: req.params.id, userId: currentUser(req) } });
  if (result.count === 0) throw notFound('Riwayat');
  res.status(204).end();
});
