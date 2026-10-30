import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import pinoHttp from 'pino-http';
import { env } from './config/env';
import { logger } from './lib/logger';
import { prisma } from './lib/prisma';
import { errorHandler, notFoundHandler } from './middleware/error';
import { globalLimiter } from './middleware/rateLimit';
import { aiRouter } from './modules/ai';
import { authRouter } from './modules/auth';
import { collectionsRouter, favoritesRouter } from './modules/favorites';
import { historyRouter } from './modules/history';
import { ingredientsRouter } from './modules/ingredients';
import { mealPlansRouter } from './modules/mealPlans';
import { pantryRouter } from './modules/pantry';
import { recipesRouter } from './modules/recipes';
import { shoppingListsRouter } from './modules/shoppingLists';
import { usersRouter } from './modules/users';
import { isGeminiConfigured } from './services/gemini';
import { isSpoonacularAvailable } from './services/spoonacular';

export function createApp() {
  const app = express();
  app.use(helmet());
  app.use(
    cors({
      origin: env.CORS_ORIGINS.split(',').map((o) => o.trim()).filter(Boolean),
    }),
  );
  app.use(pinoHttp({ logger, autoLogging: { ignore: (req) => req.url === '/health' } }));
  app.use(express.json({ limit: '1mb' }));
  app.use(globalLimiter);

  app.get('/health', async (_req, res) => {
    await prisma.$queryRaw`SELECT 1`;
    res.json({
      status: 'ok',
      services: { gemini: isGeminiConfigured(), spoonacular: isSpoonacularAvailable(), themealdb: true },
    });
  });

  app.use('/api/auth', authRouter);
  app.use('/api/users', usersRouter);
  app.use('/api/ingredients', ingredientsRouter);
  app.use('/api/pantry', pantryRouter);
  app.use('/api/ai', aiRouter);
  app.use('/api/recipes', recipesRouter);
  app.use('/api/favorites', favoritesRouter);
  app.use('/api/collections', collectionsRouter);
  app.use('/api/shopping-lists', shoppingListsRouter);
  app.use('/api/meal-plans', mealPlansRouter);
  app.use('/api/history', historyRouter);

  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}
