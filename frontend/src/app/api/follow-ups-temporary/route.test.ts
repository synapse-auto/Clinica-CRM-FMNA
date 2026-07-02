import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const forwardBackendRequestMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/backend', () => ({
  forwardBackendRequest: forwardBackendRequestMock,
}));

import { GET } from './route';

describe('follow-ups-temporary BFF route', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should_normalize_empty_backend_page_to_empty_array', async () => {
    forwardBackendRequestMock.mockResolvedValue(new Response(JSON.stringify({
      content: [],
      totalElements: 0,
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    const response = await GET();
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body).toEqual([]);
    expect(forwardBackendRequestMock).toHaveBeenCalledWith('/api/follow-ups-temporary');
  });
});
