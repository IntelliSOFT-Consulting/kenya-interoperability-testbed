import { FormEvent, useEffect, useState } from 'react';
import {
  PortalTestRequest,
  SCENARIOS,
  ScenarioKey,
  SystemConfig,
  brokerApi
} from '../api/brokerApi';

interface Props {
  onStarted: (sessionId: string) => void;
  preselectedSystemId?: string | null;
}

export default function StartSession({ onStarted, preselectedSystemId }: Props) {
  const [systems, setSystems] = useState<SystemConfig[]>([]);
  const [systemConfigId, setSystemConfigId] = useState('');
  const [itbSessionId, setItbSessionId] = useState('');
  const [itbBaseUrl, setItbBaseUrl] = useState('http://itb-srv:8080');
  const [testScenario, setTestScenario] = useState<ScenarioKey>('PATIENT_SUMMARY');
  const [patientId, setPatientId] = useState('');
  const [writeTestEnabled, setWriteTestEnabled] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingRequests, setPendingRequests] = useState<PortalTestRequest[]>([]);

  useEffect(() => {
    brokerApi
      .listSystems()
      .then((list) => {
        setSystems(list);
        if (preselectedSystemId && list.some((s) => s.id === preselectedSystemId)) {
          setSystemConfigId(preselectedSystemId);
        } else if (list.length > 0) {
          setSystemConfigId(list[0].id);
        }
      })
      .catch((e) => setError(e.message));
  }, [preselectedSystemId]);

  useEffect(() => {
    if (!preselectedSystemId) {
      setPendingRequests([]);
      return;
    }
    brokerApi
      .listPortalRequests()
      .then((requests) =>
        setPendingRequests(
          requests.filter(
            (r) => r.status === 'PENDING' && r.systemConfig?.id === preselectedSystemId
          )
        )
      )
      .catch((e) => setError(e.message));
  }, [preselectedSystemId]);

  const scenario = SCENARIOS[testScenario];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const session = await brokerApi.startSession({
        systemConfigId,
        itbSessionId: itbSessionId.trim(),
        itbBaseUrl,
        testScenario,
        patientId: patientId.trim() || null,
        writeTestEnabled
      });
      onStarted(session.id);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="bg-white rounded-lg shadow p-6 max-w-3xl">
      <h2 className="text-xl font-semibold mb-4">Start Test Session</h2>
      {error && (
        <div className="mb-4 rounded bg-red-50 border border-red-200 text-red-700 px-4 py-2 text-sm">
          {error}
        </div>
      )}

      {pendingRequests.length > 0 && (
        <div className="mb-6 rounded border border-amber-200 bg-amber-50 p-4">
          <p className="font-medium text-amber-800 mb-2">
            Pending Certification Portal request{pendingRequests.length === 1 ? '' : 's'} for this system
          </p>
          <div className="space-y-2 text-sm">
            {pendingRequests.map((req) => (
              <div key={req.id}>
                {req.requestId && <span className="font-mono text-xs text-amber-700 mr-2">{req.requestId}</span>}
                {req.scenarios.map((sc) => (
                  <span
                    key={sc.id}
                    className="inline-block mr-2 mb-1 px-2 py-1 rounded bg-white border border-amber-200 text-amber-800"
                  >
                    {SCENARIOS[sc.scenarioKey as ScenarioKey]?.label ?? sc.scenarioKey} —{' '}
                    {sc.testCases.length} testcase{sc.testCases.length === 1 ? '' : 's'}
                  </span>
                ))}
              </div>
            ))}
          </div>
          <p className="text-xs text-amber-700 mt-2">
            This is what the portal asked to be tested — the scenario picked below is entered
            manually and isn't auto-filled from these requests yet.
          </p>
        </div>
      )}

      <form onSubmit={submit} className="space-y-4">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">Select System</span>
          <select
            required
            className="input"
            value={systemConfigId}
            onChange={(e) => setSystemConfigId(e.target.value)}
          >
            {systems.map((system) => (
              <option key={system.id} value={system.id}>
                {system.systemName}
              </option>
            ))}
          </select>
          {systems.length === 0 && (
            <p className="text-sm text-amber-600 mt-1">
              No systems registered. Add one on the System Configuration screen first.
            </p>
          )}
        </label>

        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">
            ITB Session ID (from gitb-ui)
          </span>
          <input
            required
            className="input font-mono"
            value={itbSessionId}
            onChange={(e) => setItbSessionId(e.target.value)}
            placeholder="78595BBDX5F0FX452DX8A34XE2FF49184EB3"
          />
        </label>

        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">ITB Base URL</span>
          <input
            required
            className="input"
            value={itbBaseUrl}
            onChange={(e) => setItbBaseUrl(e.target.value)}
          />
        </label>

        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">Test Scenario</span>
          <select
            className="input"
            value={testScenario}
            onChange={(e) => setTestScenario(e.target.value as ScenarioKey)}
          >
            {(Object.keys(SCENARIOS) as ScenarioKey[]).map((key) => (
              <option key={key} value={key}>
                {SCENARIOS[key].label}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="block text-sm font-medium text-slate-700 mb-1">
            Patient ID (for scoped queries, optional)
          </span>
          <input
            className="input"
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
            placeholder="12345"
          />
        </label>

        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={writeTestEnabled}
            onChange={(e) => setWriteTestEnabled(e.target.checked)}
          />
          <span className="text-sm text-slate-700">Enable write and verify test</span>
        </label>

        <div className="rounded border border-slate-200 bg-slate-50 p-4 text-sm">
          <p className="font-medium text-slate-700 mb-2">Resources to test:</p>
          <p>
            <span className="font-semibold text-slate-600">READ:</span>{' '}
            {scenario.readResources.join(', ')}
          </p>
          <p>
            <span className="font-semibold text-slate-600">WRITE:</span>{' '}
            {writeTestEnabled ? scenario.writeResources.join(', ') : 'disabled'}
          </p>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="submit"
            disabled={submitting || systems.length === 0}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {submitting ? 'Starting…' : 'Start Test'}
          </button>
        </div>
      </form>
    </section>
  );
}
