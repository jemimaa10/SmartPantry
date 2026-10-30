import rateLimit from 'express-rate-limit';
import { env } from '../config/env';

const limiter = (windowMs: number, limit: number, message: string) =>
  rateLimit({
    windowMs,
    limit,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => env.NODE_ENV === 'test',
    message: { error: { code: 'RATE_LIMITED', message } },
  });

export const globalLimiter = limiter(60_000, 300, 'Terlalu banyak permintaan. Coba lagi sebentar.');
export const authLimiter = limiter(15 * 60_000, 30, 'Terlalu banyak percobaan masuk. Coba lagi nanti.');
export const aiLimiter = limiter(60_000, 15, 'Terlalu banyak permintaan AI. Coba lagi sebentar.');
