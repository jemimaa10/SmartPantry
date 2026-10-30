import multer from 'multer';
import { AppError } from '../lib/errors';

export const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

export const imageUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_IMAGE_BYTES, files: 1 },
}).single('image');

/** Identifies the image type from magic bytes; client-sent MIME types are not trusted. */
export function sniffImageMime(buffer: Buffer): 'image/jpeg' | 'image/png' | 'image/webp' {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) return 'image/jpeg';
  const pngSignature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(pngSignature)) return 'image/png';
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('latin1') === 'RIFF' &&
    buffer.subarray(8, 12).toString('latin1') === 'WEBP'
  ) {
    return 'image/webp';
  }
  throw new AppError(415, 'UNSUPPORTED_IMAGE', 'Format gambar harus JPEG, PNG, atau WEBP.');
}
