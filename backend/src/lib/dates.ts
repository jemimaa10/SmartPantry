/** Parses YYYY-MM-DD as UTC midnight, the form Prisma stores in DATE columns. */
export const parseDate = (value: string) => new Date(`${value}T00:00:00.000Z`);
export const formatDate = (value: Date) => value.toISOString().slice(0, 10);

/** Today's calendar date in Asia/Jakarta, at UTC midnight. */
export function today(now = new Date()): Date {
  return parseDate(now.toLocaleDateString('en-CA', { timeZone: 'Asia/Jakarta' }));
}

export const addDays = (date: Date, days: number) => new Date(date.getTime() + days * 86_400_000);

/** Monday of the week containing date. */
export function weekStartOf(date: Date): Date {
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  return addDays(date, -daysSinceMonday);
}
