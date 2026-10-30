import bcrypt from 'bcryptjs';
import { Router } from 'express';
import { OAuth2Client } from 'google-auth-library';
import nodemailer from 'nodemailer';
import { z } from 'zod';
import { env } from '../config/env';
import { AppError, notConfigured, unauthorized } from '../lib/errors';
import { logger } from '../lib/logger';
import { prisma } from '../lib/prisma';
import {
  hashToken,
  randomToken,
  signAccessToken,
  signRefreshToken,
  verifyRefreshToken,
} from '../lib/tokens';
import { authLimiter } from '../middleware/rateLimit';
import { parseBody } from '../middleware/validate';

export const authRouter = Router();
authRouter.use(authLimiter);

const BCRYPT_ROUNDS = 12;
const RESET_TTL_MS = 30 * 60_000;
const email = z.string().trim().toLowerCase().email('Format email tidak valid.').max(191);
const password = z.string().min(8, 'Kata sandi minimal 8 karakter.').max(72, 'Kata sandi maksimal 72 karakter.');

async function issueSession(userId: string) {
  const { token, expiresAt } = signRefreshToken(userId);
  await prisma.refreshToken.create({ data: { userId, tokenHash: hashToken(token), expiresAt } });
  return { accessToken: signAccessToken(userId), refreshToken: token, expiresIn: env.JWT_ACCESS_TTL_SECONDS };
}

const newUserRelations = (displayName: string, avatarUrl?: string) => ({
  profile: {
    create: { displayName, avatarUrl, dietaryPreferences: [], allergies: [], dislikedIngredients: [] },
  },
  notificationPreferences: { create: {} },
  shoppingLists: { create: { name: 'Daftar Belanja' } },
});

authRouter.post('/register', async (req, res) => {
  const body = parseBody(z.object({ name: z.string().trim().min(1).max(120), email, password }), req);
  const existing = await prisma.user.findUnique({ where: { email: body.email } });
  if (existing) throw new AppError(409, 'EMAIL_TAKEN', 'Email ini sudah terdaftar.');

  const user = await prisma.user.create({
    data: {
      email: body.email,
      passwordHash: await bcrypt.hash(body.password, BCRYPT_ROUNDS),
      ...newUserRelations(body.name),
    },
  });
  res.status(201).json(await issueSession(user.id));
});

authRouter.post('/login', async (req, res) => {
  const body = parseBody(z.object({ email, password: z.string().min(1).max(72) }), req);
  const user = await prisma.user.findUnique({ where: { email: body.email } });
  const valid = user?.passwordHash && (await bcrypt.compare(body.password, user.passwordHash));
  if (!user || !valid) throw unauthorized('Email atau kata sandi salah.');
  res.json(await issueSession(user.id));
});

authRouter.post('/google', async (req, res) => {
  if (!env.GOOGLE_CLIENT_ID) throw notConfigured('Masuk dengan Google');
  const { idToken } = parseBody(z.object({ idToken: z.string().min(1) }), req);

  const client = new OAuth2Client(env.GOOGLE_CLIENT_ID);
  const ticket = await client
    .verifyIdToken({ idToken, audience: env.GOOGLE_CLIENT_ID })
    .catch(() => {
      throw unauthorized('Token Google tidak valid.');
    });
  const payload = ticket.getPayload();
  if (!payload?.sub || !payload.email || !payload.email_verified) {
    throw unauthorized('Akun Google belum terverifikasi.');
  }

  const googleEmail = payload.email.toLowerCase();
  const user =
    (await prisma.user.findUnique({ where: { googleId: payload.sub } })) ??
    (await prisma.user.findUnique({ where: { email: googleEmail } }));

  if (user && !user.googleId) {
    // Email ownership was never proven at registration. Drop the old password and
    // sessions so someone who pre-registered this address loses access on linking.
    await prisma.$transaction([
      prisma.user.update({ where: { id: user.id }, data: { googleId: payload.sub, passwordHash: null } }),
      prisma.refreshToken.updateMany({ where: { userId: user.id, revokedAt: null }, data: { revokedAt: new Date() } }),
    ]);
  }

  const id = user
    ? user.id
    : (
        await prisma.user.create({
          data: {
            email: googleEmail,
            googleId: payload.sub,
            ...newUserRelations(payload.name ?? googleEmail.split('@')[0] ?? 'Pengguna', payload.picture),
          },
        })
      ).id;
  res.json(await issueSession(id));
});

authRouter.post('/refresh', async (req, res) => {
  const { refreshToken } = parseBody(z.object({ refreshToken: z.string().min(1) }), req);
  const userId = verifyRefreshToken(refreshToken);
  const tokenHash = hashToken(refreshToken);

  const stored = await prisma.refreshToken.findUnique({ where: { tokenHash } });
  if (!stored || stored.userId !== userId || stored.expiresAt <= new Date()) throw unauthorized();
  if (stored.revokedAt) {
    // A rotated token being replayed suggests theft, so end every session for this user.
    await prisma.refreshToken.updateMany({ where: { userId, revokedAt: null }, data: { revokedAt: new Date() } });
    throw unauthorized();
  }

  const revoked = await prisma.refreshToken.updateMany({
    where: { id: stored.id, revokedAt: null },
    data: { revokedAt: new Date() },
  });
  if (revoked.count === 0) throw unauthorized();
  res.json(await issueSession(userId));
});

authRouter.post('/logout', async (req, res) => {
  const { refreshToken } = parseBody(z.object({ refreshToken: z.string().min(1) }), req);
  await prisma.refreshToken.updateMany({
    where: { tokenHash: hashToken(refreshToken), revokedAt: null },
    data: { revokedAt: new Date() },
  });
  res.status(204).end();
});

async function sendResetEmail(to: string, token: string): Promise<void> {
  if (!env.SMTP_HOST) {
    if (env.NODE_ENV === 'production') throw notConfigured('Pengiriman email');
    logger.info({ to, token }, 'SMTP not configured; development reset token');
    return;
  }
  const transport = nodemailer.createTransport({
    host: env.SMTP_HOST,
    port: env.SMTP_PORT,
    auth: env.SMTP_USER ? { user: env.SMTP_USER, pass: env.SMTP_PASS } : undefined,
  });
  await transport.sendMail({
    from: env.SMTP_FROM,
    to,
    subject: 'Kode reset kata sandi SmartPantry AI',
    text: `Tempel kode ini di aplikasi untuk mengatur ulang kata sandi. Berlaku 30 menit.\n\n${token}\n\nAbaikan email ini jika kamu tidak memintanya.`,
  });
}

authRouter.post('/forgot-password', async (req, res) => {
  const body = parseBody(z.object({ email }), req);
  const user = await prisma.user.findUnique({ where: { email: body.email } });
  if (user) {
    const token = randomToken();
    await prisma.passwordResetToken.create({
      data: { userId: user.id, tokenHash: hashToken(token), expiresAt: new Date(Date.now() + RESET_TTL_MS) },
    });
    await sendResetEmail(user.email, token);
  }
  // The reply is identical either way so this endpoint cannot reveal who has an account.
  res.json({ message: 'Jika email terdaftar, kode reset telah dikirim.' });
});

authRouter.post('/reset-password', async (req, res) => {
  const body = parseBody(z.object({ token: z.string().min(1), password }), req);
  const stored = await prisma.passwordResetToken.findUnique({ where: { tokenHash: hashToken(body.token) } });
  if (!stored || stored.usedAt || stored.expiresAt <= new Date()) {
    throw new AppError(400, 'INVALID_RESET_TOKEN', 'Kode reset tidak valid atau sudah kedaluwarsa.');
  }
  const passwordHash = await bcrypt.hash(body.password, BCRYPT_ROUNDS);
  await prisma.$transaction([
    prisma.user.update({ where: { id: stored.userId }, data: { passwordHash } }),
    prisma.passwordResetToken.update({ where: { id: stored.id }, data: { usedAt: new Date() } }),
    prisma.refreshToken.updateMany({
      where: { userId: stored.userId, revokedAt: null },
      data: { revokedAt: new Date() },
    }),
  ]);
  res.json({ message: 'Kata sandi berhasil diperbarui.' });
});
