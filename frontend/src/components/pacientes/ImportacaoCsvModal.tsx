'use client';

import { AlertCircle, CheckCircle2, Download, FileSpreadsheet, LoaderCircle, Upload, X } from 'lucide-react';
import { useRef, useState } from 'react';
import {
  confirmarImportacaoCsv,
  previewImportacaoCsv,
} from '@/services/pacientes';
import type {
  ImportacaoCsvContatoMapping,
  ImportacaoCsvContatoPreview,
  ImportacaoCsvContatoResultado,
} from '@/types/paciente';

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const EMPTY_MAPPING: ImportacaoCsvContatoMapping = { nameColumn: null, phoneColumn: null };

type Props = {
  onClose: () => void;
  onComplete: () => void;
};

export function ImportacaoCsvModal({ onClose, onComplete }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportacaoCsvContatoPreview | null>(null);
  const [mapping, setMapping] = useState<ImportacaoCsvContatoMapping>(EMPTY_MAPPING);
  const [result, setResult] = useState<ImportacaoCsvContatoResultado | null>(null);
  const [stage, setStage] = useState<'arquivo' | 'colunas' | 'validacao' | 'resultado'>('arquivo');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function selectFile(candidate: File | null) {
    setError(null);
    setPreview(null);
    setResult(null);
    setMapping(EMPTY_MAPPING);
    setStage('arquivo');
    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith('.csv')) {
      setError('Selecione um arquivo CSV.');
      return;
    }
    if (candidate.size > MAX_FILE_SIZE) {
      setError('O arquivo excede o limite de 5 MB.');
      return;
    }
    setFile(candidate);
  }

  async function analyze() {
    if (!file) {
      setError('Selecione um arquivo CSV.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const next = await previewImportacaoCsv(file);
      setPreview(next);
      setMapping(next.suggestedMapping);
      setStage('colunas');
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  async function validate() {
    if (!file || !mapping.nameColumn || !mapping.phoneColumn) {
      setError('Selecione as colunas de nome e telefone.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const next = await previewImportacaoCsv(file, mapping);
      setPreview(next);
      setStage('validacao');
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!file || !preview || !mapping.nameColumn || !mapping.phoneColumn || busy) return;
    setBusy(true);
    setError(null);
    try {
      const next = await confirmarImportacaoCsv(file, preview.fileHash, mapping);
      setResult(next);
      setStage('resultado');
      onComplete();
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/35 p-3" role="dialog" aria-modal="true" aria-label="Importar contatos por CSV">
      <div className="flex max-h-[92vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl border border-clinic-border bg-clinic-surface shadow-xl">
        <header className="flex items-start justify-between gap-4 border-b border-clinic-border px-4 py-3">
          <div>
            <h2 className="text-[14px] font-extrabold text-clinic-text">Importar CSV</h2>
            <p className="mt-0.5 text-[10px] text-clinic-muted">Nome e telefone, sem criar atendimentos ou enviar mensagens.</p>
          </div>
          <button type="button" aria-label="Fechar importação CSV" disabled={busy} onClick={onClose} className="rounded-lg p-1.5 text-clinic-muted hover:bg-clinic-hover hover:text-clinic-text disabled:opacity-50">
            <X className="h-4 w-4" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto p-4 custom-scrollbar">
          <Steps stage={stage} />
          {error ? <p role="alert" className="mt-3 rounded-lg border border-clinic-danger/30 bg-clinic-danger/10 px-3 py-2 text-[10px] font-semibold text-clinic-danger">{error}</p> : null}
          {stage === 'arquivo' ? (
            <FileStep file={file} busy={busy} inputRef={inputRef} onSelect={selectFile} onAnalyze={() => void analyze()} />
          ) : null}
          {stage === 'colunas' && preview ? (
            <ColumnsStep preview={preview} mapping={mapping} busy={busy} onMapping={setMapping} onValidate={() => void validate()} />
          ) : null}
          {stage === 'validacao' && preview ? (
            <ValidationStep preview={preview} busy={busy} onBack={() => setStage('colunas')} onConfirm={() => void confirm()} />
          ) : null}
          {stage === 'resultado' && result ? (
            <ResultStep result={result} onClose={onClose} onView={() => { onComplete(); onClose(); }} />
          ) : null}
        </div>
      </div>
    </div>
  );
}

function Steps({ stage }: { stage: string }) {
  const labels = ['Arquivo', 'Colunas', 'Validação', 'Resultado'];
  const current = ['arquivo', 'colunas', 'validacao', 'resultado'].indexOf(stage);
  return <ol className="grid grid-cols-4 gap-1 text-center text-[9px] font-bold text-clinic-muted">
    {labels.map((label, index) => <li key={label} className={index <= current ? 'text-clinic-primary' : ''}>{index + 1}. {label}</li>)}
  </ol>;
}

function FileStep({ file, busy, inputRef, onSelect, onAnalyze }: {
  file: File | null; busy: boolean; inputRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (file: File | null) => void; onAnalyze: () => void;
}) {
  return <section className="mt-5 space-y-4">
    <input ref={inputRef} className="hidden" type="file" accept=".csv,text/csv" onChange={(event) => onSelect(event.target.files?.[0] ?? null)} />
    <button type="button" onClick={() => inputRef.current?.click()} onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); onSelect(event.dataTransfer.files?.[0] ?? null); }} className="flex min-h-36 w-full flex-col items-center justify-center rounded-xl border-2 border-dashed border-clinic-border bg-clinic-soft px-4 text-center hover:border-clinic-primary/50">
      <Upload className="mb-2 h-6 w-6 text-clinic-primary" />
      <span className="text-[11px] font-extrabold text-clinic-text">Selecionar arquivo CSV</span>
      <span className="mt-1 text-[9px] text-clinic-muted">Máximo de 5 MB. Também é possível arrastar o arquivo aqui.</span>
    </button>
    {file ? <div className="flex items-center gap-2 rounded-lg border border-clinic-border px-3 py-2 text-[10px]"><FileSpreadsheet className="h-4 w-4 text-clinic-primary" /><span className="min-w-0 flex-1 truncate font-bold text-clinic-text">{file.name}</span><span className="text-clinic-muted">{formatSize(file.size)}</span></div> : null}
    <div className="flex flex-wrap justify-between gap-2">
      <button type="button" onClick={downloadModel} className="inline-flex items-center gap-1 text-[10px] font-bold text-clinic-primary hover:underline"><Download className="h-3.5 w-3.5" />Baixar modelo CSV</button>
      <button type="button" disabled={!file || busy} onClick={onAnalyze} className="inline-flex h-9 items-center gap-2 rounded-lg bg-clinic-primary px-4 text-[10px] font-extrabold text-white disabled:opacity-50">
        {busy ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : null}Analisar arquivo
      </button>
    </div>
  </section>;
}

function ColumnsStep({ preview, mapping, busy, onMapping, onValidate }: {
  preview: ImportacaoCsvContatoPreview; mapping: ImportacaoCsvContatoMapping; busy: boolean;
  onMapping: (mapping: ImportacaoCsvContatoMapping) => void; onValidate: () => void;
}) {
  return <section className="mt-5 space-y-4">
    <p className="text-[10px] text-clinic-muted">Separador: <strong>{preview.delimiter === ';' ? 'ponto e vírgula' : 'vírgula'}</strong> · Codificação: <strong>{preview.encoding}</strong> · {preview.totalRows} linhas</p>
    {preview.warnings.map((warning) => <p key={warning} className="rounded-lg bg-clinic-warning/10 px-3 py-2 text-[10px] font-semibold text-clinic-warning">{warning}</p>)}
    <div className="grid gap-3 sm:grid-cols-2">
      <ColumnSelect label="Nome" value={mapping.nameColumn} other={mapping.phoneColumn} headers={preview.headers} onChange={(nameColumn) => onMapping({ ...mapping, nameColumn })} />
      <ColumnSelect label="Telefone" value={mapping.phoneColumn} other={mapping.nameColumn} headers={preview.headers} onChange={(phoneColumn) => onMapping({ ...mapping, phoneColumn })} />
    </div>
    <SampleRows preview={preview} />
    <div className="flex justify-end"><button type="button" disabled={busy || !mapping.nameColumn || !mapping.phoneColumn} onClick={onValidate} className="inline-flex h-9 items-center gap-2 rounded-lg bg-clinic-primary px-4 text-[10px] font-extrabold text-white disabled:opacity-50">{busy ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : null}Validar linhas</button></div>
  </section>;
}

function ColumnSelect({ label, value, other, headers, onChange }: { label: string; value: string | null; other: string | null; headers: string[]; onChange: (value: string | null) => void }) {
  return <label className="text-[10px] font-bold text-clinic-muted">{label}<select aria-label={`Coluna de ${label.toLowerCase()}`} value={value ?? ''} onChange={(event) => onChange(event.target.value || null)} className="mt-1 h-9 w-full rounded-lg border border-clinic-border bg-clinic-input px-2 text-[11px] text-clinic-text"><option value="">Selecionar coluna</option>{headers.map((header) => <option key={header} value={header} disabled={header === other}>{header}</option>)}</select></label>;
}

function SampleRows({ preview }: { preview: ImportacaoCsvContatoPreview }) {
  return <div className="overflow-x-auto rounded-lg border border-clinic-border"><table className="min-w-full text-left text-[9px]"><thead className="bg-clinic-soft text-clinic-muted"><tr><th className="px-2 py-2">Linha</th>{preview.headers.map((header) => <th key={header} className="px-2 py-2">{header}</th>)}</tr></thead><tbody>{preview.sampleRows.map((row) => <tr key={row.rowNumber} className="border-t border-clinic-border"><td className="px-2 py-2">{row.rowNumber}</td>{preview.headers.map((header, index) => <td key={header} className="max-w-40 truncate px-2 py-2 text-clinic-text">{row.values[index] ?? ''}</td>)}</tr>)}</tbody></table></div>;
}

function ValidationStep({ preview, busy, onBack, onConfirm }: { preview: ImportacaoCsvContatoPreview; busy: boolean; onBack: () => void; onConfirm: () => void }) {
  const summary = preview.validation;
  return <section className="mt-5 space-y-4"><div className="grid grid-cols-2 gap-2 sm:grid-cols-3"><Metric label="Registros" value={summary.totalRows} /><Metric label="Válidos" value={summary.valid} /><Metric label="Já existentes" value={summary.existing} /><Metric label="Duplicados" value={summary.duplicateInFile} /><Metric label="Inválidos" value={summary.invalid} /><Metric label="Serão criados" value={summary.toCreate} /></div>{summary.valid === 0 ? <p className="rounded-lg border border-clinic-warning/30 bg-clinic-warning/10 px-3 py-2 text-[10px] font-semibold text-clinic-warning">Nenhum contato válido foi encontrado.</p> : null}{summary.errors.length > 0 ? <Errors errors={summary.errors} truncated={summary.errorsTruncated} /> : null}<div className="flex justify-between gap-2"><button type="button" disabled={busy} onClick={onBack} className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text">Voltar</button><button type="button" disabled={busy || summary.toCreate === 0} onClick={onConfirm} className="inline-flex h-9 items-center gap-2 rounded-lg bg-clinic-primary px-4 text-[10px] font-extrabold text-white disabled:opacity-50">{busy ? <LoaderCircle className="h-3.5 w-3.5 animate-spin" /> : null}Importar {summary.toCreate} contatos</button></div></section>;
}

function ResultStep({ result, onClose, onView }: { result: ImportacaoCsvContatoResultado; onClose: () => void; onView: () => void }) {
  return <section className="mt-5 space-y-4"><div className="rounded-xl border border-clinic-success/30 bg-clinic-success/10 p-4 text-center"><CheckCircle2 className="mx-auto mb-2 h-8 w-8 text-clinic-success" /><p className="text-[13px] font-extrabold text-clinic-text">{result.created} contatos importados com sucesso.</p></div><div className="grid grid-cols-2 gap-2 sm:grid-cols-4"><Metric label="Criados" value={result.created} /><Metric label="Existentes" value={result.skippedExisting} /><Metric label="Duplicados" value={result.skippedDuplicateInFile} /><Metric label="Inválidos" value={result.invalid} /></div>{result.errors.length > 0 ? <><Errors errors={result.errors} truncated={result.errorsTruncated} /><button type="button" onClick={() => downloadErrors(result.errors)} className="inline-flex items-center gap-1 text-[10px] font-bold text-clinic-primary hover:underline"><Download className="h-3.5 w-3.5" />Baixar relatório de erros</button></> : null}<div className="flex justify-end gap-2"><button type="button" onClick={onClose} className="h-9 rounded-lg border border-clinic-border px-4 text-[10px] font-bold text-clinic-text">Fechar</button><button type="button" onClick={onView} className="h-9 rounded-lg bg-clinic-primary px-4 text-[10px] font-extrabold text-white">Ver contatos importados</button></div></section>;
}

function Metric({ label, value }: { label: string; value: number }) { return <div className="rounded-lg border border-clinic-border bg-clinic-soft px-3 py-2"><p className="text-[14px] font-extrabold text-clinic-text">{value}</p><p className="text-[9px] font-semibold text-clinic-muted">{label}</p></div>; }
function Errors({ errors, truncated }: { errors: ImportacaoCsvContatoResultado['errors']; truncated: boolean }) { return <div className="rounded-lg border border-clinic-warning/30 bg-clinic-warning/10 p-3"><p className="mb-2 flex items-center gap-1 text-[10px] font-extrabold text-clinic-warning"><AlertCircle className="h-3.5 w-3.5" />Linhas com erro{truncated ? ' (lista limitada)' : ''}</p><ul className="max-h-28 space-y-1 overflow-auto text-[9px] text-clinic-text custom-scrollbar">{errors.map((error) => <li key={`${error.rowNumber}-${error.field}-${error.code}`}>Linha {error.rowNumber}: {error.message}</li>)}</ul></div>; }
function downloadModel() { downloadText('modelo-contatos.csv', '\uFEFFnome;telefone\r\nMaria da Silva;5583999999999\r\nJoão Souza;83988887777\r\n'); }
function downloadErrors(errors: ImportacaoCsvContatoResultado['errors']) { const lines = ['linha;campo;codigo;erro', ...errors.map((error) => [error.rowNumber, error.field, error.code, error.message].map(csvCell).join(';'))]; downloadText('relatorio-importacao-contatos.csv', `\uFEFF${lines.join('\r\n')}\r\n`); }
function csvCell(value: string | number) { const safe = String(value).replace(/^([=+\-@])/, "'$1").replace(/"/g, '""'); return /[;"\r\n]/.test(safe) ? `"${safe}"` : safe; }
function downloadText(filename: string, content: string) { const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url); }
function formatSize(size: number) { return `${(size / 1024).toFixed(size < 1024 * 1024 ? 0 : 1)} ${size < 1024 * 1024 ? 'KB' : 'MB'}`; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : 'Não foi possível concluir a importação.'; }
