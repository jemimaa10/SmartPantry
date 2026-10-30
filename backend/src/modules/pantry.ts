import { Router } from 'express';
import { z } from 'zod';
import { ingredientSearchText } from '../data/catalog';
import { addDays, formatDate, parseDate, today } from '../lib/dates';
import { AppError, notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody, parseQuery } from '../middleware/validate';
import { normalizeIngredients } from '../services/normalizer';

export const pantryRouter = Router();
pantryRouter.use(requireAuth);

const include = { ingredient: { include: { category: true } } } as const;
type PantryRow = Awaited<ReturnType<typeof loadRows>>[number];

function loadRows(userId: string) {
  return prisma.pantryItem.findMany({
    where: { userId },
    include,
    orderBy: [{ expiresAt: { sort: 'asc', nulls: 'last' } }, { createdAt: 'desc' }],
  });
}

type ExpiryStatus = 'FRESH' | 'EXPIRING_SOON' | 'EXPIRING_TODAY' | 'EXPIRED' | 'NONE';

export function expiryStatus(expiresAt: Date | null, soonDays: number, now = today()): ExpiryStatus {
  if (!expiresAt) return 'NONE';
  if (expiresAt < now) return 'EXPIRED';
  if (expiresAt.getTime() === now.getTime()) return 'EXPIRING_TODAY';
  return expiresAt <= addDays(now, soonDays) ? 'EXPIRING_SOON' : 'FRESH';
}

function present(row: PantryRow, soonDays: number) {
  return {
    id: row.id,
    ingredientId: row.ingredientId,
    name: row.ingredient.nameId,
    nameEn: row.ingredient.nameEn,
    category: {
      slug: row.ingredient.category.slug,
      nameId: row.ingredient.category.nameId,
      nameEn: row.ingredient.category.nameEn,
    },
    quantity: row.quantity,
    unit: row.unit,
    expiresAt: row.expiresAt ? formatDate(row.expiresAt) : null,
    expiryStatus: expiryStatus(row.expiresAt, soonDays),
    notes: row.notes,
  };
}

async function soonDaysFor(userId: string): Promise<number> {
  const prefs = await prisma.notificationPreference.findUnique({ where: { userId } });
  return prefs?.expiryDaysBefore ?? 3;
}

/** The pantry in English, as used for recipe matching. */
export async function pantrySnapshot(userId: string) {
  const [rows, soonDays] = await Promise.all([loadRows(userId), soonDaysFor(userId)]);
  const fresh = rows.filter((r) => expiryStatus(r.expiresAt, soonDays) !== 'EXPIRED');
  return {
    pantry: fresh.map((r) => r.ingredient.nameEn),
    expiring: fresh
      .filter((r) => ['EXPIRING_SOON', 'EXPIRING_TODAY'].includes(expiryStatus(r.expiresAt, soonDays)))
      .map((r) => r.ingredient.nameEn),
  };
}

/** Finds the catalog entry for a free-typed name, creating a private one if needed. */
async function resolveIngredient(userId: string, name: string, categorySlug?: string) {
  const [english = name.trim().toLowerCase()] = await normalizeIngredients([name]);
  const known = await prisma.ingredient.findFirst({
    where: { nameEn: english, OR: [{ createdById: null }, { createdById: userId }] },
    orderBy: { scope: 'asc' },
  });
  if (known) return known;

  const category = await prisma.ingredientCategory.findUnique({ where: { slug: categorySlug ?? 'other' } });
  if (!category) throw new AppError(400, 'UNKNOWN_CATEGORY', 'Kategori tidak dikenal.');
  return prisma.ingredient.create({
    data: {
      nameEn: english.slice(0, 120),
      nameId: name.trim().slice(0, 120),
      aliases: [],
      searchText: ingredientSearchText(english, name.trim()),
      categoryId: category.id,
      createdById: userId,
      scope: userId,
    },
  });
}

const itemFields = {
  quantity: z.number().positive().max(1_000_000).nullable().optional(),
  unit: z.string().trim().min(1).max(30).optional(),
  expiresAt: z.iso.date().nullable().optional(),
  notes: z.string().trim().max(500).nullable().optional(),
};

const createItem = z
  .object({
    ingredientId: z.uuid().optional(),
    name: z.string().trim().min(1).max(120).optional(),
    categorySlug: z.string().trim().max(60).optional(),
    ...itemFields,
  })
  .refine((v) => v.ingredientId || v.name, { message: 'Isi nama bahan.' });

async function addItem(userId: string, input: z.output<typeof createItem>) {
  const ingredient = input.ingredientId
    ? await prisma.ingredient.findFirst({
        where: { id: input.ingredientId, OR: [{ createdById: null }, { createdById: userId }] },
      })
    : await resolveIngredient(userId, input.name ?? '', input.categorySlug);
  if (!ingredient) throw notFound('Bahan');

  const expiresAt = input.expiresAt ? parseDate(input.expiresAt) : null;
  const where = { userId_ingredientId: { userId, ingredientId: ingredient.id } };
  const existing = await prisma.pantryItem.findUnique({ where });

  if (existing) {
    // Re-adding merges quantities and keeps the earlier date, the safer one to act on.
    const earliest =
      existing.expiresAt && expiresAt
        ? new Date(Math.min(existing.expiresAt.getTime(), expiresAt.getTime()))
        : (existing.expiresAt ?? expiresAt);
    return prisma.pantryItem.update({
      where,
      data: {
        quantity:
          existing.quantity !== null && input.quantity
            ? existing.quantity + input.quantity
            : (input.quantity ?? existing.quantity),
        unit: input.unit ?? existing.unit,
        expiresAt: earliest,
        notes: input.notes ?? existing.notes,
      },
      include,
    });
  }

  return prisma.pantryItem.create({
    data: {
      userId,
      ingredientId: ingredient.id,
      quantity: input.quantity ?? null,
      unit: input.unit ?? ingredient.defaultUnit,
      expiresAt,
      notes: input.notes ?? null,
    },
    include,
  });
}

pantryRouter.get('/', async (req, res) => {
  const userId = currentUser(req);
  const { q, category } = parseQuery(
    z.object({ q: z.string().trim().max(80).optional(), category: z.string().trim().max(60).optional() }),
    req,
  );
  const [rows, soonDays] = await Promise.all([loadRows(userId), soonDaysFor(userId)]);
  const needle = q?.toLowerCase();
  const filtered = rows.filter(
    (r) =>
      (!category || r.ingredient.category.slug === category) &&
      (!needle ||
        r.ingredient.nameId.toLowerCase().includes(needle) ||
        r.ingredient.nameEn.includes(needle)),
  );
  res.json(filtered.map((r) => present(r, soonDays)));
});

pantryRouter.get('/expiring', async (req, res) => {
  const userId = currentUser(req);
  const [rows, soonDays] = await Promise.all([loadRows(userId), soonDaysFor(userId)]);
  const items = rows.map((r) => present(r, soonDays));
  res.json({
    expiringSoon: items.filter((i) => i.expiryStatus === 'EXPIRING_SOON'),
    expiringToday: items.filter((i) => i.expiryStatus === 'EXPIRING_TODAY'),
    expired: items.filter((i) => i.expiryStatus === 'EXPIRED'),
  });
});

pantryRouter.post('/', async (req, res) => {
  const userId = currentUser(req);
  const row = await addItem(userId, parseBody(createItem, req));
  res.status(201).json(present(row, await soonDaysFor(userId)));
});

/** Saves the reviewed result of a scan in one call. */
pantryRouter.post('/bulk', async (req, res) => {
  const userId = currentUser(req);
  const { items } = parseBody(z.object({ items: z.array(createItem).min(1).max(50) }), req);
  const soonDays = await soonDaysFor(userId);
  const saved = [];
  for (const item of items) saved.push(present(await addItem(userId, item), soonDays));
  res.status(201).json(saved);
});

pantryRouter.put('/:id', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(z.object(itemFields), req);
  const owned = await prisma.pantryItem.findFirst({ where: { id: req.params.id, userId } });
  if (!owned) throw notFound('Bahan');
  const { expiresAt, ...rest } = body;
  const row = await prisma.pantryItem.update({
    where: { id: owned.id },
    data: { ...rest, ...(expiresAt !== undefined ? { expiresAt: expiresAt ? parseDate(expiresAt) : null } : {}) },
    include,
  });
  res.json(present(row, await soonDaysFor(userId)));
});

pantryRouter.delete('/:id', async (req, res) => {
  const result = await prisma.pantryItem.deleteMany({ where: { id: req.params.id, userId: currentUser(req) } });
  if (result.count === 0) throw notFound('Bahan');
  res.status(204).end();
});
