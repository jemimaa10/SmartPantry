import type { Request } from 'express';
import type { z } from 'zod';
import { AppError } from '../lib/errors';

function parse<S extends z.ZodType>(schema: S, value: unknown): z.output<S> {
  const result = schema.safeParse(value);
  if (result.success) return result.data;
  const fields = result.error.issues.map((i) => ({ field: i.path.join('.'), message: i.message }));
  throw new AppError(400, 'VALIDATION_ERROR', 'Data yang dikirim tidak valid.', fields);
}

export const parseBody = <S extends z.ZodType>(schema: S, req: Request) => parse(schema, req.body);
export const parseQuery = <S extends z.ZodType>(schema: S, req: Request) => parse(schema, req.query);
