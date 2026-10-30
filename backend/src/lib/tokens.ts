import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { unauthorized } from './errors';

export const hashToken = (token: string) => crypto.createHash('sha256').update(token).digest('hex');
export const randomToken = () => crypto.randomBytes(48).toString('base64url');

export function signAccessToken(userId: string): string {
  return jwt.sign({}, env.JWT_ACCESS_SECRET, {
    subject: userId,
    expiresIn: env.JWT_ACCESS_TTL_SECONDS,
    algorithm: 'HS256',
  });
}

function verify(token: string, secret: string): string {
  try {
    const payload = jwt.verify(token, secret, { algorithms: ['HS256'] });
    if (typeof payload === 'string' || !payload.sub) throw new Error('bad payload');
    return payload.sub;
  } catch {
    throw unauthorized();
  }
}

export const verifyAccessToken = (token: string) => verify(token, env.JWT_ACCESS_SECRET);
export const verifyRefreshToken = (token: string) => verify(token, env.JWT_REFRESH_SECRET);

export function signRefreshToken(userId: string): { token: string; expiresAt: Date } {
  const expiresAt = new Date(Date.now() + env.JWT_REFRESH_TTL_DAYS * 86_400_000);
  const token = jwt.sign({ jti: crypto.randomUUID() }, env.JWT_REFRESH_SECRET, {
    subject: userId,
    expiresIn: env.JWT_REFRESH_TTL_DAYS * 86_400,
    algorithm: 'HS256',
  });
  return { token, expiresAt };
}
