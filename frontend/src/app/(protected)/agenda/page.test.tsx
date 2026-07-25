import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgendaCapabilities } from '@/types/agenda';

const redirectMock = vi.hoisted(() => vi.fn((path: string) => {
  throw new Error(`redirect:${path}`);
}));

const getAgendaFmnaMock = vi.hoisted(() => vi.fn());
const getAgendaProfissionaisSSRMock = vi.hoisted(() => vi.fn());
const getAgendaCapabilitiesMock = vi.hoisted(() => vi.fn());
const isBackendAuthorizationErrorMock = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({
  redirect: redirectMock,
}));

vi.mock('@/services/backend', () => ({
  getAgendaFmna: getAgendaFmnaMock,
  getAgendaProfissionaisSSR: getAgendaProfissionaisSSRMock,
  getAgendaCapabilities: getAgendaCapabilitiesMock,
  isBackendAuthorizationError: isBackendAuthorizationErrorMock,
}));

import AgendaPage from './page';

const CATALOG_CAPABILITIES: AgendaCapabilities = {
  provider: 'DARWIN',
  supportsCatalog: true,
  supportsWriteOperations: true,
  supportsFitIn: true,
  supportsClinicWideListing: false,
  supportsPatientLookup: true,
  supportsBackfill: true,
  coverage: 'KNOWN_CRM_PATIENTS_ONLY',
};

const NO_CATALOG_CAPABILITIES: AgendaCapabilities = {
  provider: 'MEDWARE',
  supportsCatalog: false,
  supportsWriteOperations: false,
  supportsFitIn: false,
  supportsClinicWideListing: true,
  supportsPatientLookup: false,
  supportsBackfill: false,
  coverage: 'FULL',
};

describe('AgendaPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_redirect_to_login_when_backend_rejects_session', async () => {
    const authError = new Error('sessao invalida');
    getAgendaFmnaMock.mockRejectedValue(authError);
    getAgendaProfissionaisSSRMock.mockResolvedValue([]);
    getAgendaCapabilitiesMock.mockResolvedValue(CATALOG_CAPABILITIES);
    isBackendAuthorizationErrorMock.mockReturnValue(true);

    await expect(AgendaPage()).rejects.toThrow('redirect:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('should_not_call_professional_catalog_when_capabilities_report_no_catalog_support', async () => {
    getAgendaFmnaMock.mockResolvedValue([]);
    getAgendaCapabilitiesMock.mockResolvedValue(NO_CATALOG_CAPABILITIES);
    isBackendAuthorizationErrorMock.mockReturnValue(false);

    const element = await AgendaPage();

    expect(element.props.initialAppointments).toEqual([]);
    expect(element.props.initialProfissionais).toEqual([]);
    expect(element.props.initialCapabilities).toEqual(NO_CATALOG_CAPABILITIES);
    expect(element.props.initialError).toBeNull();
    expect(getAgendaProfissionaisSSRMock).not.toHaveBeenCalled();
  });

  it('should_call_professional_catalog_when_capabilities_report_catalog_support', async () => {
    getAgendaFmnaMock.mockResolvedValue([]);
    getAgendaCapabilitiesMock.mockResolvedValue(CATALOG_CAPABILITIES);
    getAgendaProfissionaisSSRMock.mockResolvedValue([{ id: '1', nome: 'Dra. Renata', origem: 'CRM' }]);
    isBackendAuthorizationErrorMock.mockReturnValue(false);

    const element = await AgendaPage();

    expect(element.props.initialProfissionais).toEqual([{ id: '1', nome: 'Dra. Renata', origem: 'CRM' }]);
    expect(element.props.initialCapabilities).toEqual(CATALOG_CAPABILITIES);
  });

  it('should_keep_conservative_fallback_capabilities_when_capabilities_endpoint_fails', async () => {
    getAgendaFmnaMock.mockResolvedValue([]);
    getAgendaCapabilitiesMock.mockRejectedValue(new Error('indisponivel'));
    isBackendAuthorizationErrorMock.mockReturnValue(false);

    const element = await AgendaPage();

    expect(element.props.initialCapabilities).toEqual({
      provider: 'DESCONHECIDO',
      supportsCatalog: false,
      supportsWriteOperations: false,
      supportsFitIn: false,
      supportsClinicWideListing: false,
      supportsPatientLookup: false,
      supportsBackfill: false,
      coverage: 'DESCONHECIDA',
    });
    expect(getAgendaProfissionaisSSRMock).not.toHaveBeenCalled();
  });

  it('should_not_change_capabilities_when_professional_catalog_fetch_fails_transiently', async () => {
    getAgendaFmnaMock.mockResolvedValue([]);
    getAgendaCapabilitiesMock.mockResolvedValue(CATALOG_CAPABILITIES);
    getAgendaProfissionaisSSRMock.mockRejectedValue(new Error('501 nao suportado'));
    isBackendAuthorizationErrorMock.mockReturnValue(false);

    const element = await AgendaPage();

    expect(element.props.initialProfissionais).toEqual([]);
    expect(element.props.initialCapabilities).toEqual(CATALOG_CAPABILITIES);
    expect(element.props.initialError).toBeNull();
  });
});
