import { forwardBackendRequest } from '@/services/backend';

const DATE_PATTERN = /^(\d{2})\/(\d{2})\/(\d{4})$/;
const INVALID_PERIOD_MESSAGE = 'Informe um período válido no formato dd/MM/yyyy.';

export async function POST(request: Request) {
  const period = await validateRequest(request);
  if (period instanceof Response) return period;

  const query = new URLSearchParams(period);

  try {
    return await forwardBackendRequest(
      `/api/integracoes/sincronizar?${query.toString()}`,
      { method: 'POST' },
    );
  } catch {
    console.error('[integracoes/sincronizar] Falha ao encaminhar solicitação ao backend.');
    return Response.json(
      { message: 'Serviço de sincronização indisponível.' },
      { status: 503 },
    );
  }
}

async function validateRequest(request: Request) {
  const params = new URL(request.url).searchParams;
  if (hasUnexpectedParameters(params)) {
    return Response.json(
      { message: 'Apenas dataInicio e dataFim são permitidos.' },
      { status: 400 },
    );
  }
  let bodyBytes: ArrayBuffer;
  try {
    bodyBytes = await request.arrayBuffer();
  } catch {
    return Response.json(
      { message: 'Não foi possível validar a requisição.' },
      { status: 400 },
    );
  }
  if (bodyBytes.byteLength > 0) {
    return Response.json(
      { message: 'Esta operação não aceita corpo de requisição.' },
      { status: 400 },
    );
  }

  const startValues = params.getAll('dataInicio');
  const endValues = params.getAll('dataFim');
  if (startValues.length !== 1 || endValues.length !== 1) return invalidPeriod();

  const dataInicio = startValues[0].trim();
  const dataFim = endValues[0].trim();
  const inicio = parseDate(dataInicio);
  const fim = parseDate(dataFim);
  return inicio === null || fim === null || fim < inicio
    ? invalidPeriod()
    : { dataInicio, dataFim };
}

function invalidPeriod() {
  return Response.json({ message: INVALID_PERIOD_MESSAGE }, { status: 400 });
}

function hasUnexpectedParameters(params: URLSearchParams) {
  return Array.from(params.keys()).some(
    (key) => key !== 'dataInicio' && key !== 'dataFim',
  );
}

function parseDate(value: string) {
  const match = DATE_PATTERN.exec(value);
  if (!match) return null;

  const [, dayText, monthText, yearText] = match;
  const day = Number(dayText);
  const month = Number(monthText);
  const year = Number(yearText);
  const timestamp = Date.UTC(year, month - 1, day);
  const date = new Date(timestamp);

  if (
    date.getUTCFullYear() !== year
    || date.getUTCMonth() !== month - 1
    || date.getUTCDate() !== day
  ) {
    return null;
  }

  return timestamp;
}
