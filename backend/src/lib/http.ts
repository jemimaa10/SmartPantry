export class UpstreamError extends Error {
  constructor(
    public readonly service: string,
    public readonly status: number | null,
    message: string,
    public readonly body?: string,
  ) {
    super(message);
    this.name = 'UpstreamError';
  }
}

export interface FetchOptions extends Omit<RequestInit, 'signal'> {
  service: string;
  timeoutMs?: number;
  retries?: number;
  baseDelayMs?: number;
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const isRetryable = (status: number) => status === 429 || status >= 500;

/**
 * fetch with a per-attempt timeout and exponential backoff on 429/5xx and
 * network errors. Callers pass URLs that may hold API keys, so errors never
 * include the URL.
 */
export async function fetchWithRetry(url: string, options: FetchOptions): Promise<Response> {
  const { service, timeoutMs = 10_000, retries = 2, baseDelayMs = 300, ...init } = options;
  let lastError: UpstreamError | undefined;

  for (let attempt = 0; attempt <= retries; attempt++) {
    if (attempt > 0) await sleep(baseDelayMs * 2 ** (attempt - 1));
    let response: Response;
    try {
      response = await fetch(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
    } catch (err) {
      const reason = err instanceof Error && err.name === 'TimeoutError' ? 'timed out' : 'network error';
      lastError = new UpstreamError(service, null, `${service} ${reason}`);
      continue;
    }
    if (response.ok) return response;
    const body = (await response.text().catch(() => '')).slice(0, 500);
    lastError = new UpstreamError(service, response.status, `${service} responded ${response.status}`, body);
    if (!isRetryable(response.status)) throw lastError;
  }
  throw lastError ?? new UpstreamError(service, null, `${service} failed`);
}
