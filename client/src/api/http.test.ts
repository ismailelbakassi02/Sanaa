import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from './errors';
import { createHttpClient } from './http';
import type { ProblemDetail } from './types';
import { useAuthStore } from '../store/auth';

function ok(config: InternalAxiosRequestConfig, data: unknown = { ok: true }): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config };
}

function unauthorized(config: InternalAxiosRequestConfig): AxiosError<ProblemDetail> {
  return new AxiosError<ProblemDetail>(
    'Unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    {
      data: { status: 401, title: 'Unauthorized', detail: 'A valid access token is required.' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    },
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    status: 'anonymous',
    sessionStartedAt: null,
  });
});

describe('HTTP refresh interceptor', () => {
  it('shares one refresh across three parallel 401s and retries each request once', async () => {
    let refreshCalls = 0;
    let protectedCalls = 0;

    const refresh = () =>
      new Promise<string>((resolve) => {
        setTimeout(() => {
          refreshCalls += 1;
          resolve('fresh-token');
        }, 10);
      });

    const adapter: AxiosAdapter = async (config) => {
      if (config.url !== '/api/protected') {
        return ok(config);
      }
      protectedCalls += 1;
      const authorization = config.headers.get('Authorization');
      if (authorization === 'Bearer fresh-token') {
        return ok(config);
      }
      throw unauthorized(config);
    };

    const client = createHttpClient({ adapter, refresh });
    const responses = await Promise.all([
      client.get('/api/protected'),
      client.get('/api/protected'),
      client.get('/api/protected'),
    ]);

    expect(refreshCalls).toBe(1);
    // Three initial calls plus three retries.
    expect(protectedCalls).toBe(6);
    expect(responses.map((response) => response.status)).toEqual([200, 200, 200]);
  });

  it('does not loop when the refresh itself fails', async () => {
    let refreshCalls = 0;
    let expiredCalls = 0;
    let protectedCalls = 0;

    const refresh = () =>
      new Promise<string>((_resolve, reject) => {
        setTimeout(() => {
          refreshCalls += 1;
          reject(new Error('refresh refused'));
        }, 10);
      });

    const adapter: AxiosAdapter = async (config) => {
      protectedCalls += 1;
      throw unauthorized(config);
    };

    const client = createHttpClient({
      adapter,
      refresh,
      onSessionExpired: () => {
        expiredCalls += 1;
      },
    });

    const settled = await Promise.allSettled([
      client.get('/api/protected'),
      client.get('/api/protected'),
      client.get('/api/protected'),
    ]);

    expect(refreshCalls).toBe(1);
    expect(protectedCalls).toBe(3);
    expect(settled.every((result) => result.status === 'rejected')).toBe(true);
    expect(expiredCalls).toBeGreaterThanOrEqual(1);
    const first = settled[0];
    if (first.status === 'rejected') {
      expect(first.reason).toBeInstanceOf(ApiError);
      expect((first.reason as ApiError).status).toBe(401);
    } else {
      throw new Error('Expected a rejected request');
    }
  });

  it('never refreshes on a failed login', async () => {
    let refreshCalls = 0;
    const adapter: AxiosAdapter = async (config) => {
      if (config.url === '/api/auth/login') {
        throw unauthorized(config);
      }
      return ok(config);
    };
    const client = createHttpClient({
      adapter,
      refresh: async () => {
        refreshCalls += 1;
        return 'unused';
      },
      onSessionExpired: () => {},
    });

    await expect(client.post('/api/auth/login', {})).rejects.toBeInstanceOf(ApiError);
    expect(refreshCalls).toBe(0);
  });

  it('never refreshes a failing refresh request', async () => {
    let refreshCalls = 0;
    const adapter: AxiosAdapter = async (config) => {
      throw unauthorized(config);
    };
    const client = createHttpClient({
      adapter,
      refresh: async () => {
        refreshCalls += 1;
        return 'unused';
      },
      onSessionExpired: () => {},
    });

    await expect(client.post('/api/auth/refresh')).rejects.toBeInstanceOf(ApiError);
    expect(refreshCalls).toBe(0);
  });

  it('attaches the in-memory bearer token', async () => {
    useAuthStore.setState({ accessToken: 'stored-token' });
    let seen: string | null = null;
    const adapter: AxiosAdapter = async (config) => {
      const authorization = config.headers.get('Authorization');
      seen = typeof authorization === 'string' ? authorization : null;
      return ok(config);
    };
    const client = createHttpClient({ adapter });

    await client.get('/api/auth/me');
    expect(seen).toBe('Bearer stored-token');
  });
});
