export class AppError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export const notFound = (what: string) => new AppError(404, 'NOT_FOUND', `${what} tidak ditemukan.`);
export const unauthorized = (message = 'Sesi tidak valid. Silakan masuk kembali.') =>
  new AppError(401, 'UNAUTHORIZED', message);
export const notConfigured = (feature: string) =>
  new AppError(503, 'NOT_CONFIGURED', `${feature} belum dikonfigurasi di server.`);
