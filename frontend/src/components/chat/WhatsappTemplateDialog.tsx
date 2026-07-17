'use client';

import { Dialog } from '@base-ui/react/dialog';
import {
  AlertCircle,
  CheckCircle2,
  LoaderCircle,
  MessageSquareText,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AtendimentoApiError, getWhatsappTemplates } from '@/services/atendimentos';
import type {
  EnviarTemplateWhatsappRequest,
  WhatsappTemplate,
  WhatsappTemplateButton,
  WhatsappTemplateVariable,
} from '@/types/atendimento';

type Props = {
  open: boolean;
  atendimentoId: number | null;
  onOpenChange: (open: boolean) => void;
  onSend: (request: EnviarTemplateWhatsappRequest) => Promise<void>;
};

type ParameterValues = Record<string, string>;

const STATUS_LABELS: Record<string, string> = {
  APPROVED: 'Aprovado',
  PENDING: 'Pendente',
  PAUSED: 'Pausado',
  REJECTED: 'Rejeitado',
};

export function WhatsappTemplateDialog({ open, atendimentoId, onOpenChange, onSend }: Props) {
  const [templates, setTemplates] = useState<WhatsappTemplate[]>([]);
  const [selected, setSelected] = useState<WhatsappTemplate | null>(null);
  const [values, setValues] = useState<ParameterValues>({});
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [validationVisible, setValidationVisible] = useState(false);
  const requestSequence = useRef(0);
  const searchInput = useRef<HTMLInputElement>(null);

  const loadTemplates = useCallback(async () => {
    if (!atendimentoId) return;
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError(null);
    setSendError(null);
    try {
      const result = await getWhatsappTemplates(atendimentoId);
      if (sequence !== requestSequence.current) return;
      setTemplates(result);
      setSelected(result.find(canSendTemplate) ?? null);
      setValues({});
    } catch (cause) {
      if (sequence !== requestSequence.current) return;
      setLoadError(errorMessage(cause));
      setTemplates([]);
      setSelected(null);
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [atendimentoId]);

  useEffect(() => {
    if (!open) {
      requestSequence.current += 1;
      return;
    }
    setSearch('');
    setValues({});
    setSendError(null);
    setValidationVisible(false);
    void loadTemplates();
    return () => {
      requestSequence.current += 1;
    };
  }, [loadTemplates, open]);

  const filteredTemplates = useMemo(() => {
    const term = normalizeTemplateSearch(search);
    if (!term) return templates;
    return templates.filter((template) => normalizeTemplateSearch([
      template.nome,
      template.idioma,
      template.categoria,
      template.corpo ?? '',
    ].join(' ')).includes(term));
  }, [search, templates]);

  const missingKeys = useMemo(() => new Set(
    selected?.variaveis
      .filter((variable) => !(values[templateParameterKey(variable)] ?? '').trim())
      .map(templateParameterKey) ?? [],
  ), [selected, values]);

  const sendAllowed = Boolean(selected && canSendTemplate(selected));

  function selectTemplate(template: WhatsappTemplate) {
    setSelected(template);
    setValues({});
    setSendError(null);
    setValidationVisible(false);
  }

  async function submit() {
    if (!selected || sending) return;
    setValidationVisible(true);
    if (!canSendTemplate(selected) || missingKeys.size > 0) return;
    const request: EnviarTemplateWhatsappRequest = {
      nome: selected.nome,
      idioma: selected.idioma,
      parametros: selected.variaveis.map((variable) => ({
        componente: variable.componente,
        posicao: variable.posicao,
        indiceBotao: variable.indiceBotao,
        nomeParametro: variable.nomeParametro ?? null,
        valor: values[templateParameterKey(variable)],
      })),
    };
    setSending(true);
    setSendError(null);
    try {
      await onSend(request);
      setValues({});
      setSearch('');
      setSelected(null);
      setLoadError(null);
      setSendError(null);
      onOpenChange(false);
    } catch (cause) {
      setSendError(sendErrorMessage(cause));
    } finally {
      setSending(false);
    }
  }

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && sending) return;
        onOpenChange(nextOpen);
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[80] bg-black/45 backdrop-blur-[1px]" />
        <Dialog.Viewport className="fixed inset-0 z-[81] flex items-center justify-center p-3 sm:p-6">
          <Dialog.Popup
            initialFocus={searchInput}
            className="flex max-h-[92vh] w-full max-w-[1180px] flex-col overflow-hidden rounded-lg border border-clinic-border bg-clinic-surface text-clinic-text shadow-2xl outline-none"
          >
            <header className="flex shrink-0 items-start justify-between gap-4 border-b border-clinic-border px-5 py-4">
              <div>
                <Dialog.Title className="text-base font-extrabold">Enviar template do WhatsApp</Dialog.Title>
                <Dialog.Description className="mt-1 text-xs text-clinic-muted">
                  Selecione uma mensagem aprovada e preencha as variáveis necessárias.
                </Dialog.Description>
              </div>
              <button
                type="button"
                aria-label="Fechar templates"
                disabled={sending}
                onClick={() => onOpenChange(false)}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text disabled:opacity-40"
              >
                <X className="h-4 w-4" />
              </button>
            </header>

            <div className="min-h-0 flex-1 overflow-y-auto custom-scrollbar">
              <div className="grid min-h-full lg:grid-cols-[minmax(220px,0.8fr)_minmax(270px,1fr)_minmax(250px,0.9fr)]">
                <section className="min-h-[280px] border-b border-clinic-border p-4 lg:border-b-0 lg:border-r">
                  <h3 className="text-xs font-extrabold uppercase text-clinic-muted">Templates</h3>
                  <label className="relative mt-3 block">
                    <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-clinic-muted" />
                    <input
                      ref={searchInput}
                      type="search"
                      value={search}
                      onChange={(event) => setSearch(event.target.value)}
                      placeholder="Pesquisar por nome..."
                      aria-label="Pesquisar templates"
                      className="h-10 w-full rounded-md border border-clinic-border bg-clinic-input pl-9 pr-3 text-xs outline-none focus:border-clinic-primary focus:ring-2 focus:ring-clinic-primary/15"
                    />
                  </label>

                  <div aria-live="polite" className="mt-3 max-h-[46vh] space-y-2 overflow-y-auto pr-1 custom-scrollbar lg:max-h-[58vh]">
                    {loading ? <LoadingState /> : null}
                    {!loading && loadError ? (
                      <div className="rounded-md border border-clinic-danger/30 bg-clinic-danger/10 p-3 text-xs text-clinic-danger">
                        <p>{loadError}</p>
                        <button type="button" onClick={() => void loadTemplates()} className="mt-3 inline-flex items-center gap-2 font-bold underline">
                          <RefreshCw className="h-3.5 w-3.5" /> Tentar novamente
                        </button>
                      </div>
                    ) : null}
                    {!loading && !loadError && filteredTemplates.length === 0 ? (
                      <p className="py-8 text-center text-xs text-clinic-muted">
                        {templates.length ? 'Nenhum template encontrado.' : 'Nenhum template cadastrado na Meta.'}
                      </p>
                    ) : null}
                    {!loading && !loadError ? filteredTemplates.map((template) => {
                      const active = selected?.nome === template.nome && selected.idioma === template.idioma;
                      return (
                        <button
                          key={`${template.nome}:${template.idioma}`}
                          type="button"
                          aria-pressed={active}
                          onClick={() => selectTemplate(template)}
                          className={`w-full rounded-md border p-3 text-left transition ${active
                            ? 'border-clinic-primary bg-clinic-primary/10'
                            : 'border-clinic-border bg-clinic-surface-muted hover:bg-clinic-hover'}`}
                        >
                          <span className="block truncate text-xs font-extrabold">{template.nome}</span>
                          <span className="mt-1 flex flex-wrap items-center gap-1.5 text-[10px] text-clinic-muted">
                            <span>{template.idioma}</span><span>·</span><span>{template.categoria}</span>
                          </span>
                          <StatusBadge status={template.status} />
                        </button>
                      );
                    }) : null}
                  </div>
                </section>

                <section className="border-b border-clinic-border p-4 lg:border-b-0 lg:border-r">
                  <h3 className="text-xs font-extrabold uppercase text-clinic-muted">Configuração de envio</h3>
                  {!selected ? (
                    <p className="py-12 text-center text-xs text-clinic-muted">Selecione um template para continuar.</p>
                  ) : (
                    <div className="mt-4 space-y-4">
                      {!canSendTemplate(selected) ? (
                        <div role="alert" className="flex gap-2 rounded-md border border-clinic-warning/40 bg-clinic-warning/10 p-3 text-xs text-clinic-text">
                          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-clinic-warning" />
                          <span>{templateBlockReason(selected)}</span>
                        </div>
                      ) : null}
                      {selected.variaveis.length === 0 ? (
                        <div className="flex items-center gap-2 rounded-md bg-clinic-success/10 p-3 text-xs text-clinic-text">
                          <CheckCircle2 className="h-4 w-4 text-clinic-success" />
                          Este template não precisa de personalização.
                        </div>
                      ) : selected.variaveis.map((variable) => {
                        const key = templateParameterKey(variable);
                        const invalid = validationVisible && missingKeys.has(key);
                        return (
                          <label key={key} className="block text-xs font-bold text-clinic-text">
                            {variableLabel(variable)}
                            <input
                              value={values[key] ?? ''}
                              maxLength={1024}
                              aria-invalid={invalid}
                              aria-describedby={invalid ? `${key}-error` : undefined}
                              onChange={(event) => setValues((current) => ({ ...current, [key]: event.target.value }))}
                              className="mt-1.5 h-10 w-full rounded-md border border-clinic-border bg-clinic-input px-3 text-xs font-normal outline-none focus:border-clinic-primary aria-invalid:border-clinic-danger"
                            />
                            {invalid ? <span id={`${key}-error`} className="mt-1 block text-[10px] font-semibold text-clinic-danger">Preencha esta variável.</span> : null}
                          </label>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section role="region" aria-label="Prévia do template" className="min-h-[300px] bg-clinic-surface-muted p-4">
                  <h3 className="text-xs font-extrabold uppercase text-clinic-muted">Prévia</h3>
                  <div className="mt-4 rounded-md bg-clinic-canvas p-4">
                    {selected ? <TemplatePreview template={selected} values={values} /> : (
                      <p className="py-14 text-center text-xs text-clinic-muted">A prévia aparecerá aqui.</p>
                    )}
                  </div>
                </section>
              </div>
            </div>

            <footer className="flex shrink-0 flex-col-reverse gap-2 border-t border-clinic-border bg-clinic-surface px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <p role="status" aria-live="polite" className="min-h-5 text-xs text-clinic-danger">{sendError}</p>
              <div className="flex justify-end gap-2">
                <button type="button" disabled={sending} onClick={() => onOpenChange(false)} className="h-10 rounded-md border border-clinic-border px-4 text-xs font-bold hover:bg-clinic-hover disabled:opacity-40">
                  Cancelar
                </button>
                <button type="button" disabled={!sendAllowed || sending} onClick={() => void submit()} className="inline-flex h-10 min-w-24 items-center justify-center gap-2 rounded-md bg-clinic-primary px-4 text-xs font-bold text-white hover:bg-clinic-primary-strong disabled:opacity-40">
                  {sending ? <LoaderCircle className="h-4 w-4 animate-spin" /> : <MessageSquareText className="h-4 w-4" />}
                  {sending ? 'Enviando...' : 'Enviar'}
                </button>
              </div>
            </footer>
          </Dialog.Popup>
        </Dialog.Viewport>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function LoadingState() {
  return <div className="flex items-center justify-center gap-2 py-10 text-xs text-clinic-muted"><LoaderCircle className="h-4 w-4 animate-spin" /> Carregando templates...</div>;
}

function StatusBadge({ status }: { status: string }) {
  const style = status === 'APPROVED'
    ? 'bg-clinic-success/15 text-clinic-success'
    : status === 'REJECTED'
      ? 'bg-clinic-danger/15 text-clinic-danger'
      : 'bg-clinic-warning/15 text-clinic-warning';
  return <span className={`mt-2 inline-flex rounded-full px-2 py-0.5 text-[9px] font-extrabold ${style}`}>{statusLabel(status)}</span>;
}

function TemplatePreview({ template, values }: { template: WhatsappTemplate; values: ParameterValues }) {
  return (
    <div className="max-w-[360px] rounded-md rounded-tl-none border border-clinic-border bg-clinic-surface p-3 text-xs text-clinic-text shadow-sm">
      {template.cabecalho ? <p className="whitespace-pre-wrap font-extrabold">{renderComponentText(template.cabecalho, 'HEADER', null, values)}</p> : null}
      {template.corpo ? <p className="mt-1 whitespace-pre-wrap leading-5">{renderComponentText(template.corpo, 'BODY', null, values)}</p> : null}
      {template.rodape ? <p className="mt-2 whitespace-pre-wrap text-[10px] text-clinic-muted">{template.rodape}</p> : null}
      {template.botoes.length ? <div className="mt-3 border-t border-clinic-border pt-2">{template.botoes.map((button, index) => <PreviewButton key={`${button.tipo}:${index}`} button={button} index={index} values={values} />)}</div> : null}
    </div>
  );
}

function PreviewButton({ button, index, values }: { button: WhatsappTemplateButton; index: number; values: ParameterValues }) {
  const url = button.url ? renderComponentText(button.url, 'BUTTON', index, values) : null;
  return (
    <div className="py-1.5 text-center text-[11px] font-bold text-clinic-primary">
      <span>{button.texto}</span>
      {url ? <span className="block truncate text-[9px] font-normal text-clinic-muted">{url}</span> : null}
    </div>
  );
}

export function renderComponentText(
  text: string,
  component: WhatsappTemplateVariable['componente'],
  buttonIndex: number | null,
  values: ParameterValues,
) {
  return text.replace(/\{\{(\d+|[A-Za-z_][A-Za-z0-9_]{0,63})}}/g, (_, token: string) => {
    const numeric = /^\d+$/.test(token);
    const variable: WhatsappTemplateVariable = {
      componente: component,
      posicao: numeric ? Number(token) : 1,
      indiceBotao: buttonIndex,
      nomeParametro: numeric ? null : token,
    };
    return values[templateParameterKey(variable)] || `[variável ${token}]`;
  });
}

export function templateParameterKey(variable: WhatsappTemplateVariable) {
  return `${variable.componente}:${variable.nomeParametro ?? variable.posicao}:${variable.indiceBotao ?? '-'}`;
}

export function normalizeTemplateSearch(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLocaleLowerCase('pt-BR');
}

function canSendTemplate(template: WhatsappTemplate) {
  return template.status === 'APPROVED' && template.suportado;
}

function templateBlockReason(template: WhatsappTemplate) {
  if (template.status !== 'APPROVED') return `Este template está ${statusLabel(template.status).toLocaleLowerCase('pt-BR')} e não pode ser enviado.`;
  return template.motivoNaoSuportado ?? 'Este formato ainda não é suportado.';
}

function statusLabel(status: string) {
  return STATUS_LABELS[status]
    ?? (status.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 30) || 'Desconhecido');
}

function variableLabel(variable: WhatsappTemplateVariable) {
  const identifier = variable.nomeParametro ?? `variável ${variable.posicao}`;
  if (variable.componente === 'HEADER') return `Cabeçalho — ${identifier}`;
  if (variable.componente === 'BUTTON') return `Botão ${(variable.indiceBotao ?? 0) + 1} — complemento da URL`;
  return `Mensagem — ${identifier}`;
}

function errorMessage(cause: unknown) {
  return cause instanceof Error ? cause.message : 'Não foi possível concluir a operação.';
}

function sendErrorMessage(cause: unknown) {
  if (cause instanceof AtendimentoApiError && cause.code === 'WHATSAPP_TEMPLATE_SEND_FAILED') {
    return 'A Meta não aceitou o envio deste template. Confira os campos obrigatórios e tente novamente.';
  }
  return errorMessage(cause);
}
