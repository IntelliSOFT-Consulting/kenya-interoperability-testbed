import { useEffect, useMemo, useState } from 'react';
import { PortalTestRequest, brokerApi } from '../api/brokerApi';

interface Props {
  onSelectSystem: (systemId: string) => void;
}

interface SystemRow {
  systemId: string;
  systemName: string;
  organizationName: string | null;
  systemVersion: string | null;
  certificationPortalSystemId: string | null;
  pendingRequests: number;
  totalRequests: number;
  latestSubmittedAt: string | null;
}

const PAGE_SIZE = 8;

export default function SystemsToTest({ onSelectSystem }: Props) {
  const [requests, setRequests] = useState<PortalTestRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    brokerApi.listPortalRequests().then(setRequests).catch((e) => setError(e.message));
  }, []);

  const systems = useMemo<SystemRow[]>(() => {
    const bySystem = new Map<string, SystemRow>();
    for (const req of requests) {
      const sc = req.systemConfig;
      if (!sc) continue;
      const isPending = req.status === 'PENDING';
      const existing = bySystem.get(sc.id);
      if (existing) {
        existing.totalRequests += 1;
        if (isPending) existing.pendingRequests += 1;
        if (!existing.latestSubmittedAt || (req.submittedAt ?? '') > existing.latestSubmittedAt) {
          existing.latestSubmittedAt = req.submittedAt;
        }
      } else {
        bySystem.set(sc.id, {
          systemId: sc.id,
          systemName: sc.systemName,
          organizationName: sc.organizationName,
          systemVersion: sc.systemVersion,
          certificationPortalSystemId: sc.certificationPortalSystemId,
          pendingRequests: isPending ? 1 : 0,
          totalRequests: 1,
          latestSubmittedAt: req.submittedAt
        });
      }
    }
    return Array.from(bySystem.values()).sort((a, b) =>
      (b.latestSubmittedAt ?? '').localeCompare(a.latestSubmittedAt ?? '')
    );
  }, [requests]);

  const pageCount = Math.max(1, Math.ceil(systems.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount - 1);
  const pageRows = systems.slice(currentPage * PAGE_SIZE, currentPage * PAGE_SIZE + PAGE_SIZE);

  return (
    <section className="bg-white rounded-lg shadow p-6">
      <h2 className="text-xl font-semibold mb-1">Systems</h2>
      <p className="text-sm text-slate-500 mb-4">
        Systems submitted by the Certification Portal. Click a system to review its pending
        requests and start testing.
      </p>
      {error && (
        <div className="mb-4 rounded bg-red-50 border border-red-200 text-red-700 px-4 py-2 text-sm">
          {error}
        </div>
      )}
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left border-b text-slate-500">
            <th className="py-2">System</th>
            <th className="py-2">Organization</th>
            <th className="py-2">Version</th>
            <th className="py-2">Portal System ID</th>
            <th className="py-2">Pending</th>
            <th className="py-2">Last Submitted</th>
            <th className="py-2"></th>
          </tr>
        </thead>
        <tbody>
          {pageRows.map((s) => (
            <tr
              key={s.systemId}
              className="border-b last:border-0 hover:bg-slate-50 cursor-pointer"
              onClick={() => onSelectSystem(s.systemId)}
            >
              <td className="py-2 font-medium">{s.systemName}</td>
              <td className="py-2 text-slate-600">{s.organizationName ?? '—'}</td>
              <td className="py-2 text-slate-600">{s.systemVersion ?? '—'}</td>
              <td className="py-2 text-slate-600">{s.certificationPortalSystemId ?? '—'}</td>
              <td className="py-2">
                {s.pendingRequests > 0 ? (
                  <span className="px-2 py-1 rounded text-xs font-semibold bg-amber-100 text-amber-700">
                    {s.pendingRequests} pending
                  </span>
                ) : (
                  <span className="px-2 py-1 rounded text-xs font-semibold bg-slate-100 text-slate-500">
                    none
                  </span>
                )}
              </td>
              <td className="py-2 text-slate-500">
                {s.latestSubmittedAt ? new Date(s.latestSubmittedAt).toLocaleString() : '—'}
              </td>
              <td className="py-2 text-right text-blue-600">Review →</td>
            </tr>
          ))}
          {pageRows.length === 0 && (
            <tr>
              <td colSpan={7} className="py-6 text-center text-slate-400">
                No systems submitted by the Certification Portal yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {systems.length > PAGE_SIZE && (
        <div className="flex items-center justify-between pt-4 text-sm">
          <span className="text-slate-500">
            Page {currentPage + 1} of {pageCount} ({systems.length} systems)
          </span>
          <div className="flex gap-2">
            <button
              className="px-3 py-1 border rounded disabled:opacity-40"
              disabled={currentPage === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <button
              className="px-3 py-1 border rounded disabled:opacity-40"
              disabled={currentPage >= pageCount - 1}
              onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
