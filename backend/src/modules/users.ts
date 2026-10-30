import { Router } from 'express';
import { z } from 'zod';
import { notFound } from '../lib/errors';
import { prisma } from '../lib/prisma';
import { currentUser, requireAuth } from '../middleware/auth';
import { parseBody } from '../middleware/validate';

export const usersRouter = Router();
usersRouter.use(requireAuth);

async function loadMe(userId: string) {
  const user = await prisma.user.findUnique({
    where: { id: userId },
    include: { profile: true, notificationPreferences: true },
  });
  if (!user?.profile || !user.notificationPreferences) throw notFound('Pengguna');
  const { profile, notificationPreferences: n } = user;
  return {
    id: user.id,
    email: user.email,
    hasPassword: user.passwordHash !== null,
    displayName: profile.displayName,
    avatarUrl: profile.avatarUrl,
    language: profile.language,
    dietaryPreferences: profile.dietaryPreferences,
    allergies: profile.allergies,
    dislikedIngredients: profile.dislikedIngredients,
    notifications: {
      expiryEnabled: n.expiryEnabled,
      expiryDaysBefore: n.expiryDaysBefore,
      mealPlanEnabled: n.mealPlanEnabled,
    },
  };
}

usersRouter.get('/me', async (req, res) => {
  res.json(await loadMe(currentUser(req)));
});

usersRouter.put('/me', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(
    z.object({
      displayName: z.string().trim().min(1).max(120).optional(),
      avatarUrl: z.string().url().max(500).nullable().optional(),
      language: z.enum(['id', 'en']).optional(),
    }),
    req,
  );
  await prisma.userProfile.update({ where: { userId }, data: body });
  res.json(await loadMe(userId));
});

const tagList = z.array(z.string().trim().min(1).max(80)).max(30);

usersRouter.put('/preferences', async (req, res) => {
  const userId = currentUser(req);
  const body = parseBody(
    z.object({
      dietaryPreferences: tagList.optional(),
      allergies: tagList.optional(),
      dislikedIngredients: tagList.optional(),
      notifications: z
        .object({
          expiryEnabled: z.boolean().optional(),
          expiryDaysBefore: z.number().int().min(1).max(14).optional(),
          mealPlanEnabled: z.boolean().optional(),
        })
        .optional(),
    }),
    req,
  );
  const { notifications, ...profile } = body;
  await prisma.$transaction([
    prisma.userProfile.update({ where: { userId }, data: profile }),
    prisma.notificationPreference.update({ where: { userId }, data: notifications ?? {} }),
  ]);
  res.json(await loadMe(userId));
});
