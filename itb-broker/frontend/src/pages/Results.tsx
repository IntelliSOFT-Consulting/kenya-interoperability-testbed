import { useEffect, useState } from 'react';
import {
  ResourceResult,
  SCENARIOS,
  TestSession,
  brokerApi
} from '../api/brokerApi';

interface Props {
  sessionId: string | null;
  onSelectSession: (id: string) => void;
}

export default function Results({ sessionId, onSelectSession }: Props) {
  const [sessions, setSessions] = useState<TestSession[]>([]);
  const [session, setSession] = useState<TestSession | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [retrying, setRetrying] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    brokerApi.listSessions().then(setSessions).catch((e) => setError(e.message));
  }, [sessionId]);

  const poll = () => {
    if (!sessionId) return Promise.resolve();
    return brokerApi
      .getSession(sessionId)
      .then(setSession)
      .catch((e) => setError(e.message));
  };

  useEffect(() => {
    if (!sessionId) {
      setSession(null);
      return;
    }
    let cancelled = false;
    const run = () =>
      brokerApi
        .getSession(sessionId)
        .then((s) => {
          if (!cancelled) setSession(s);
        })
        .catch((e) => {
          if (!cancelled) setError(e.message);
        });
    run();
    const interval = setInterval(run, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [sessionId]);

  const retry = async (resultId: number) => {
    if (!sessionId) return;
    setRetrying(resultId);
    setError(null);
    try {
      await brokerApi.retryResult(sessionId, resultId);
      await poll();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setRetrying(null);
    }
  };

  const results = session?.results ?? [];
  // testType, not status presence — pending (not-yet-run) testcases must show
  // up immediately, before fetchStatus/writeTestStatus exist.
  const readResults = results.filter((r) => r.testType === 'READ');
  const writeResults = results.filter((r) => r.testType === 'WRITE');
  const readPassed = readResults.filter(
    (r) => r.fetchStatus === '200' && r.itbPostStatus === '200'
  ).length;
  const writePassed = writeResults.filter((r) => r.writeVerifyPassed === true).length;

  return (
    <div className="space-y-6">
      <section className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-4">Test Sessions</h2>
        {error && (
          <div className="mb-4 rounded bg-red-50 border border-red-200 text-red-700 px-4 py-2 text-sm">
            {error}
          </div>
        )}
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left border-b text-slate-500">
              <th className="py-2">System</th>
              <th className="py-2">Scenario</th>
              <th className="py-2">ITB Session</th>
              <th className="py-2">Status</th>
              <th className="py-2"></th>
            </tr>
          </thead>
          <tbody>
            {sessions.map((s) => (
              <tr key={s.id} className={`border-b last:border-0 ${s.id === sessionId ? 'bg-blue-50' : ''}`}>
                <td className="py-2">{s.systemConfig?.systemName ?? '—'}</td>
                <td className="py-2">{SCENARIOS[s.testScenario]?.label ?? s.testScenario}</td>
                <td className="py-2 font-mono text-xs">{s.itbSessionId.slice(0, 12)}…</td>
                <td className="py-2"><StatusBadge status={s.status} /></td>
                <td className="py-2 text-right">
                  <button
                    className="text-blue-600 hover:underline"
                    onClick={() => onSelectSession(s.id)}
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
            {sessions.length === 0 && (
              <tr>
                <td colSpan={5} className="py-4 text-center text-slate-400">
                  No test sessions yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      {session && (
        <section className="bg-white rounded-lg shadow p-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h2 className="text-xl font-semibold">
                Results: {session.systemConfig?.systemName ?? 'Unknown system'} —{' '}
                {SCENARIOS[session.testScenario]?.label ?? session.testScenario}
              </h2>
              <p className="text-sm text-slate-500 font-mono mt-1">
                Session: {session.itbSessionId} · ITB: {session.itbBaseUrl}
              </p>
            </div>
            <StatusBadge status={session.status} />
          </div>

          {session.status === 'RUNNING' && (
            <p className="mb-4 text-sm text-blue-600">Test running — refreshing every 5 seconds…</p>
          )}

          <h3 className="font-semibold text-slate-700 mt-6 mb-2">
            READ TESTS — GET from SUT, then POST to ITB for validation
          </h3>
          <div className="overflow-x-auto mb-6">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left border-b text-slate-500">
                  <th className="py-2 pr-3">Resource</th>
                  <th className="py-2 pr-3">SUT Endpoint</th>
                  <th className="py-2 pr-3">ITB Endpoint</th>
                  <th className="py-2 pr-3">GET</th>
                  <th className="py-2 pr-3">ITB Status</th>
                  <th className="py-2 pr-3">Details</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody>
                {readResults.map((r) => (
                  <ReadRow
                    key={r.id}
                    result={r}
                    expanded={expanded === r.id}
                    onToggle={() => setExpanded(expanded === r.id ? null : r.id)}
                    onRetry={() => retry(r.id)}
                    retrying={retrying === r.id}
                  />
                ))}
                {readResults.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-4 text-center text-slate-400">
                      No read testcases in this scenario.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {session.writeTestEnabled && (
            <>
              <h3 className="font-semibold text-slate-700 mb-2">
                WRITE TESTS — POST synthetic via ITB to SUT, fetch back, compare
              </h3>
              <div className="overflow-x-auto mb-6">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left border-b text-slate-500">
                      <th className="py-2 pr-3">Resource</th>
                      <th className="py-2 pr-3">ITB Endpoint</th>
                      <th className="py-2 pr-3">Stored</th>
                      <th className="py-2 pr-3">Fields Match</th>
                      <th className="py-2 pr-3">Diff</th>
                      <th className="py-2"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {writeResults.map((r) => (
                      <tr key={r.id} className="border-b last:border-0 align-top">
                        <td className="py-2 pr-3 font-medium">{r.resourceType}</td>
                        <td className="py-2 pr-3"><EndpointCell url={r.itbEndpoint} /></td>
                        <td className="py-2 pr-3">
                          {r.writeTestStatus === null ? (
                            <span className="text-slate-400">Pending…</span>
                          ) : r.writeTestStatus.startsWith('ERROR') || r.writeTestStatus.startsWith('SKIPPED') ? (
                            <span className="text-red-600">{r.writeTestStatus}</span>
                          ) : (
                            <span className="text-emerald-600">Yes</span>
                          )}
                        </td>
                        <td className="py-2 pr-3">
                          {r.writeTestStatus === null ? (
                            <span className="text-slate-400">—</span>
                          ) : r.writeVerifyPassed === true ? (
                            <span className="text-emerald-600">All matched</span>
                          ) : (
                            <span className="text-amber-600">Mismatch</span>
                          )}
                        </td>
                        <td className="py-2 pr-3 text-slate-600 whitespace-pre-wrap">
                          {r.writeVerifyDiff ?? '—'}
                        </td>
                        <td className="py-2 text-right">
                          <button
                            className="text-xs text-blue-600 hover:underline disabled:opacity-50"
                            disabled={retrying === r.id}
                            onClick={() => retry(r.id)}
                          >
                            {retrying === r.id ? 'Retrying…' : 'Retry'}
                          </button>
                        </td>
                      </tr>
                    ))}
                    {writeResults.length === 0 && (
                      <tr>
                        <td colSpan={6} className="py-4 text-center text-slate-400">
                          No write testcases in this scenario.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <div className="flex items-center justify-between border-t pt-4">
            <p className="text-sm text-slate-600">
              READ: {readPassed}/{readResults.length} passed
              {session.writeTestEnabled &&
                ` · WRITE: ${writePassed}/${writeResults.length} passed`}
            </p>
            <div className="flex gap-3">
              {session.certificatePath ? (
                <a
                  href={brokerApi.certificateUrl(session.id)}
                  className="px-4 py-2 bg-slate-800 text-white rounded hover:bg-slate-700 text-sm"
                >
                  Download Certificate
                </a>
              ) : (
                session.status === 'COMPLETED' && (
                  <span className="text-sm text-amber-600 self-center">
                    Certificate not available — download from ITB failed.
                  </span>
                )
              )}
            </div>
          </div>
        </section>
      )}

      {!session && sessionId === null && (
        <p className="text-sm text-slate-500">
          Select a session above, or start a new test from the Start Test Session screen.
        </p>
      )}
    </div>
  );
}

function EndpointCell({ url }: { url: string | null }) {
  if (!url) {
    return <span className="text-slate-400">—</span>;
  }
  return (
    <span
      title={url}
      className="font-mono text-xs text-slate-500 block max-w-[220px] truncate"
    >
      {url}
    </span>
  );
}

function ReadRow({
  result,
  expanded,
  onToggle,
  onRetry,
  retrying
}: {
  result: ResourceResult;
  expanded: boolean;
  onToggle: () => void;
  onRetry: () => void;
  retrying: boolean;
}) {
  const pending = result.fetchStatus === null;
  const fetchOk = result.fetchStatus === '200';
  const skipped = result.fetchStatus === '404';
  return (
    <>
      <tr className="border-b last:border-0">
        <td className="py-2 pr-3 font-medium">{result.resourceType}</td>
        <td className="py-2 pr-3"><EndpointCell url={result.sutEndpoint} /></td>
        <td className="py-2 pr-3"><EndpointCell url={result.itbEndpoint} /></td>
        <td className="py-2 pr-3">
          {pending ? (
            <span className="text-slate-400">Pending…</span>
          ) : (
            <span className={fetchOk ? 'text-emerald-600' : 'text-red-600'}>
              {result.fetchStatus}
            </span>
          )}
        </td>
        <td className="py-2 pr-3">
          {pending ? (
            <span className="text-slate-400">—</span>
          ) : skipped ? (
            <span className="text-slate-400">SKIPPED</span>
          ) : result.itbPostStatus === '200' ? (
            <span className="text-emerald-600">VALID</span>
          ) : result.itbPostStatus ? (
            <span className="text-red-600">{result.itbPostStatus}</span>
          ) : (
            <span className="text-slate-400">—</span>
          )}
        </td>
        <td className="py-2 pr-3">
          {pending ? (
            '—'
          ) : skipped ? (
            <span className="text-slate-500">Not found on SUT</span>
          ) : result.itbResponse ? (
            <button className="text-blue-600 hover:underline" onClick={onToggle}>
              {expanded ? 'Hide Response' : 'View Response'}
            </button>
          ) : (
            '—'
          )}
        </td>
        <td className="py-2 text-right">
          <button
            className="text-xs text-blue-600 hover:underline disabled:opacity-50"
            disabled={retrying}
            onClick={onRetry}
          >
            {retrying ? 'Retrying…' : 'Retry'}
          </button>
        </td>
      </tr>
      {expanded && result.itbResponse && (
        <tr>
          <td colSpan={7} className="bg-slate-50 p-3">
            <pre className="text-xs overflow-x-auto whitespace-pre-wrap">
              {result.itbResponse}
            </pre>
          </td>
        </tr>
      )}
    </>
  );
}

function StatusBadge({ status }: { status: TestSession['status'] }) {
  const styles: Record<string, string> = {
    CONFIGURED: 'bg-slate-100 text-slate-700',
    RUNNING: 'bg-blue-100 text-blue-700',
    COMPLETED: 'bg-emerald-100 text-emerald-700',
    FAILED: 'bg-red-100 text-red-700'
  };
  return (
    <span className={`px-2 py-1 rounded text-xs font-semibold ${styles[status]}`}>
      {status}
    </span>
  );
}
