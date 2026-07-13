import { useEffect, useState } from 'react';
import { PortalTestRequest, PortalTestScenario, SCENARIOS, brokerApi } from '../api/brokerApi';

interface Props {
  onViewReport: (sessionId: string) => void;
}

interface StartForm {
  itbSessionId: string;
  itbBaseUrl: string;
}

const emptyStartForm: StartForm = { itbSessionId: '', itbBaseUrl: 'http://itb-srv:8080' };

export default function PortalRequests({ onViewReport }: Props) {
  const [requests, setRequests] = useState<PortalTestRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [forms, setForms] = useState<Record<string, StartForm>>({});
  const [busyId, setBusyId] = useState<string | null>(null);

  const refresh = () =>
    brokerApi.listPortalRequests().then(setRequests).catch((e) => setError(e.message));

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 5000);
    return () => clearInterval(interval);
  }, []);

  const formFor = (id: string) => forms[id] ?? emptyStartForm;
  const setFormFor = (id: string, patch: Partial<StartForm>) =>
    setForms((f) => ({ ...f, [id]: { ...formFor(id), ...patch } }));

  const start = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await brokerApi.startPortalRequest(id, formFor(id));
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const sendStatus = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await brokerApi.sendPortalStatus(id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-6">
      <section className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-1">Certification Portal Test Requests</h2>
        <p className="text-sm text-slate-500 mb-4">
          Requests submitted by the Certification Portal (POST /api/portal/test-requests). A
          pending request needs an ITB session id — created manually in gitb-ui — before it can
          run.
        </p>
        {error && (
          <div className="mb-4 rounded bg-red-50 border border-red-200 text-red-700 px-4 py-2 text-sm">
            {error}
          </div>
        )}

        {requests.length === 0 && (
          <p className="text-sm text-slate-400 py-6 text-center">No portal test requests yet.</p>
        )}

        <div className="space-y-4">
          {requests.map((request) => (
            <RequestCard
              key={request.id}
              request={request}
              form={formFor(request.id)}
              busy={busyId === request.id}
              onFormChange={(patch) => setFormFor(request.id, patch)}
              onStart={() => start(request.id)}
              onSendStatus={() => sendStatus(request.id)}
              onViewReport={onViewReport}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function RequestCard({
  request,
  form,
  busy,
  onFormChange,
  onStart,
  onSendStatus,
  onViewReport
}: {
  request: PortalTestRequest;
  form: StartForm;
  busy: boolean;
  onFormChange: (patch: Partial<StartForm>) => void;
  onStart: () => void;
  onSendStatus: () => void;
  onViewReport: (sessionId: string) => void;
}) {
  const overall = overallStatus(request);

  return (
    <div className="border rounded-lg p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="font-semibold">
            {request.systemConfig?.systemName ?? 'Unknown system'}
            {request.systemConfig?.systemVersion && (
              <span className="text-slate-400 font-normal"> · v{request.systemConfig.systemVersion}</span>
            )}
          </p>
          <p className="text-sm text-slate-500">
            {request.systemConfig?.organizationName ?? '—'}
            {request.requestId && <span className="ml-2 font-mono text-xs">req: {request.requestId}</span>}
          </p>
        </div>
        <span className={`px-2 py-1 rounded text-xs font-semibold ${overall.className}`}>
          {overall.label}
        </span>
      </div>

      <div className="mt-3 space-y-2">
        {request.scenarios.map((scenario) => (
          <ScenarioRow key={scenario.id} scenario={scenario} onViewReport={onViewReport} />
        ))}
      </div>

      <div className="mt-4 flex items-center justify-between border-t pt-3">
        {request.status === 'PENDING' ? (
          <div className="flex flex-wrap items-end gap-2">
            <label className="block">
              <span className="block text-xs font-medium text-slate-600 mb-1">ITB Session ID</span>
              <input
                className="input text-sm"
                value={form.itbSessionId}
                onChange={(e) => onFormChange({ itbSessionId: e.target.value })}
                placeholder="pasted from gitb-ui"
              />
            </label>
            <label className="block">
              <span className="block text-xs font-medium text-slate-600 mb-1">ITB Base URL</span>
              <input
                className="input text-sm"
                value={form.itbBaseUrl}
                onChange={(e) => onFormChange({ itbBaseUrl: e.target.value })}
              />
            </label>
            <button
              className="px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700 text-sm disabled:opacity-50"
              disabled={busy || !form.itbSessionId}
              onClick={onStart}
            >
              {busy ? 'Starting…' : 'Start Test'}
            </button>
          </div>
        ) : (
          <p className="text-xs text-slate-400">
            Started {request.scenarios.length} scenario{request.scenarios.length === 1 ? '' : 's'}.
          </p>
        )}
        <button
          className="px-4 py-2 bg-slate-800 text-white rounded hover:bg-slate-700 text-sm disabled:opacity-50"
          disabled={busy || request.status === 'PENDING'}
          onClick={onSendStatus}
        >
          {busy ? 'Sending…' : 'Send Status to Portal'}
        </button>
      </div>
    </div>
  );
}

function ScenarioRow({
  scenario,
  onViewReport
}: {
  scenario: PortalTestScenario;
  onViewReport: (sessionId: string) => void;
}) {
  const session = scenario.testSession;
  const results = session?.results ?? [];
  const readResults = results.filter((r) => r.testType === 'READ');
  const writeResults = results.filter((r) => r.testType === 'WRITE');
  const readPassed = readResults.filter(
    (r) => r.fetchStatus === '200' && r.itbPostStatus === '200'
  ).length;
  const writePassed = writeResults.filter((r) => r.writeVerifyPassed === true).length;

  return (
    <div className="flex items-center justify-between bg-slate-50 rounded px-3 py-2 text-sm">
      <div>
        <span className="font-medium">{SCENARIOS[scenario.scenarioKey as keyof typeof SCENARIOS]?.label ?? scenario.scenarioKey}</span>
        <span className="text-slate-400 ml-2">{scenario.testCases.length} testcase{scenario.testCases.length === 1 ? '' : 's'}</span>
        {session && (
          <span className="text-slate-500 ml-3">
            READ {readPassed}/{readResults.length}
            {writeResults.length > 0 && ` · WRITE ${writePassed}/${writeResults.length}`}
          </span>
        )}
      </div>
      <div className="flex items-center gap-3">
        <StatusBadge status={session?.status ?? null} />
        {session ? (
          <button className="text-blue-600 hover:underline" onClick={() => onViewReport(session.id)}>
            View Report
          </button>
        ) : (
          <span className="text-slate-400">Not started</span>
        )}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string | null }) {
  if (!status) {
    return <span className="px-2 py-1 rounded text-xs font-semibold bg-slate-100 text-slate-400">—</span>;
  }
  const styles: Record<string, string> = {
    CONFIGURED: 'bg-slate-100 text-slate-700',
    RUNNING: 'bg-blue-100 text-blue-700',
    COMPLETED: 'bg-emerald-100 text-emerald-700',
    FAILED: 'bg-red-100 text-red-700'
  };
  return (
    <span className={`px-2 py-1 rounded text-xs font-semibold ${styles[status] ?? 'bg-slate-100 text-slate-700'}`}>
      {status}
    </span>
  );
}

function overallStatus(request: PortalTestRequest): { label: string; className: string } {
  if (request.status === 'PENDING') {
    return { label: 'PENDING', className: 'bg-slate-100 text-slate-700' };
  }
  const statuses = request.scenarios.map((s) => s.testSession?.status ?? 'CONFIGURED');
  if (statuses.some((s) => s === 'FAILED')) {
    return { label: 'FAILED', className: 'bg-red-100 text-red-700' };
  }
  if (statuses.some((s) => s === 'RUNNING')) {
    return { label: 'RUNNING', className: 'bg-blue-100 text-blue-700' };
  }
  if (statuses.length > 0 && statuses.every((s) => s === 'COMPLETED')) {
    return { label: 'COMPLETED', className: 'bg-emerald-100 text-emerald-700' };
  }
  return { label: 'STARTED', className: 'bg-amber-100 text-amber-700' };
}
