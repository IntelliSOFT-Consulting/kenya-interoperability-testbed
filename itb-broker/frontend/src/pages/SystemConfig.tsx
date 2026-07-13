import { FormEvent, ReactNode, useEffect, useState } from 'react';
import { AuthType, SystemConfig, SystemConfigRequest, brokerApi } from '../api/brokerApi';

const emptyForm: SystemConfigRequest = {
  systemName: '',
  sutBaseUrl: '',
  organizationName: '',
  systemVersion: '',
  authType: 'BEARER',
  authToken: '',
  certificationPortalSystemId: ''
};

export default function SystemConfigPage() {
  const [systems, setSystems] = useState<SystemConfig[]>([]);
  const [form, setForm] = useState<SystemConfigRequest>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showToken, setShowToken] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = () =>
    brokerApi.listSystems().then(setSystems).catch((e) => setError(e.message));

  useEffect(() => {
    refresh();
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await brokerApi.updateSystem(editingId, form);
      } else {
        await brokerApi.createSystem(form);
      }
      setForm(emptyForm);
      setEditingId(null);
      refresh();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const startEdit = (system: SystemConfig) => {
    setEditingId(system.id);
    setForm({
      systemName: system.systemName,
      sutBaseUrl: system.sutBaseUrl ?? '',
      organizationName: system.organizationName ?? '',
      systemVersion: system.systemVersion ?? '',
      authType: system.authType,
      authToken: system.authToken ?? '',
      certificationPortalSystemId: system.certificationPortalSystemId ?? ''
    });
  };

  const remove = async (id: string) => {
    await brokerApi.deleteSystem(id);
    if (editingId === id) {
      setEditingId(null);
      setForm(emptyForm);
    }
    refresh();
  };

  return (
    <div className="space-y-8">
      <section className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-4">
          {editingId ? 'Edit System' : 'System Configuration'}
        </h2>
        {error && (
          <div className="mb-4 rounded bg-red-50 border border-red-200 text-red-700 px-4 py-2 text-sm">
            {error}
          </div>
        )}
        <form onSubmit={submit} className="space-y-4">
          <Field label="System Name">
            <input
              required
              className="input"
              value={form.systemName}
              onChange={(e) => setForm({ ...form, systemName: e.target.value })}
              placeholder="Aga Khan EMR v2.1"
            />
          </Field>
          <Field label="SUT Base URL">
            <input
              required
              className="input"
              value={form.sutBaseUrl}
              onChange={(e) => setForm({ ...form, sutBaseUrl: e.target.value })}
              placeholder="https://emr.agakhan.co.ke/fhir"
            />
          </Field>
          <Field label="Organization Name">
            <input
              className="input"
              value={form.organizationName}
              onChange={(e) => setForm({ ...form, organizationName: e.target.value })}
              placeholder="Aga Khan University Hospital"
            />
          </Field>
          <Field label="System Version">
            <input
              className="input"
              value={form.systemVersion}
              onChange={(e) => setForm({ ...form, systemVersion: e.target.value })}
              placeholder="2.1.0"
            />
          </Field>
          <Field label="Auth Type">
            <select
              className="input"
              value={form.authType}
              onChange={(e) => setForm({ ...form, authType: e.target.value as AuthType })}
            >
              <option value="BEARER">Bearer Token</option>
              <option value="BASIC">Basic</option>
              <option value="NONE">None</option>
            </select>
          </Field>
          <Field label="Token">
            <div className="flex gap-2">
              <input
                className="input flex-1"
                type={showToken ? 'text' : 'password'}
                value={form.authToken}
                onChange={(e) => setForm({ ...form, authToken: e.target.value })}
                placeholder="eyJhbGci..."
              />
              <button
                type="button"
                className="px-3 py-2 text-sm border rounded text-slate-600 hover:bg-slate-50"
                onClick={() => setShowToken(!showToken)}
              >
                {showToken ? 'Hide' : 'Show'}
              </button>
            </div>
          </Field>
          <Field label="Portal Sys ID">
            <input
              className="input"
              value={form.certificationPortalSystemId}
              onChange={(e) =>
                setForm({ ...form, certificationPortalSystemId: e.target.value })
              }
              placeholder="sys-00123"
            />
          </Field>
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              className="px-4 py-2 border rounded text-slate-600 hover:bg-slate-50"
              onClick={() => {
                setForm(emptyForm);
                setEditingId(null);
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700"
            >
              {editingId ? 'Update System' : 'Save System'}
            </button>
          </div>
        </form>
      </section>

      <section className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-4">Saved Systems</h2>
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left border-b text-slate-500">
              <th className="py-2">Name</th>
              <th className="py-2">Organization</th>
              <th className="py-2">Version</th>
              <th className="py-2">URL</th>
              <th className="py-2">Auth Type</th>
              <th className="py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {systems.map((system) => (
              <tr key={system.id} className="border-b last:border-0">
                <td className="py-2 font-medium">{system.systemName}</td>
                <td className="py-2 text-slate-600">{system.organizationName || '—'}</td>
                <td className="py-2 text-slate-600">{system.systemVersion || '—'}</td>
                <td className="py-2 text-slate-600">{system.sutBaseUrl || '—'}</td>
                <td className="py-2">{system.authType}</td>
                <td className="py-2">
                  <button
                    className="text-blue-600 hover:underline mr-3"
                    onClick={() => startEdit(system)}
                  >
                    Edit
                  </button>
                  <button
                    className="text-red-600 hover:underline"
                    onClick={() => remove(system.id)}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {systems.length === 0 && (
              <tr>
                <td colSpan={6} className="py-4 text-center text-slate-400">
                  No systems saved yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-slate-700 mb-1">{label}</span>
      {children}
    </label>
  );
}
