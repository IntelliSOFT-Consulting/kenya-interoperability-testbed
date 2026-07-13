import { useState } from 'react';
import SystemConfigPage from './pages/SystemConfig';
import SystemsToTest from './pages/SystemsToTest';
import StartSession from './pages/StartSession';
import Results from './pages/Results';
import PortalRequests from './pages/PortalRequests';

type Screen = 'systems' | 'systemsToTest' | 'start' | 'results' | 'portal';

export default function App() {
  const [screen, setScreen] = useState<Screen>('systems');
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [preselectedSystemId, setPreselectedSystemId] = useState<string | null>(null);

  const tabs: { key: Screen; label: string }[] = [
    { key: 'systems', label: 'System Configuration' },
    { key: 'systemsToTest', label: 'Systems' },
    { key: 'start', label: 'Start Test Session' },
    { key: 'results', label: 'Results' },
    { key: 'portal', label: 'Portal Requests' }
  ];

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="bg-slate-900 text-white">
        <div className="mx-auto max-w-5xl px-6 py-4 flex items-center justify-between">
          <h1 className="text-lg font-semibold">ITB Testing Broker</h1>
          <nav className="flex gap-1">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setScreen(tab.key)}
                className={`px-4 py-2 rounded text-sm ${
                  screen === tab.key
                    ? 'bg-white text-slate-900 font-medium'
                    : 'text-slate-300 hover:bg-slate-700'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">
        {screen === 'systems' && <SystemConfigPage />}
        {screen === 'systemsToTest' && (
          <SystemsToTest
            onSelectSystem={(systemId) => {
              setPreselectedSystemId(systemId);
              setScreen('start');
            }}
          />
        )}
        {screen === 'start' && (
          <StartSession
            preselectedSystemId={preselectedSystemId}
            onStarted={(sessionId) => {
              setActiveSessionId(sessionId);
              setPreselectedSystemId(null);
              setScreen('results');
            }}
          />
        )}
        {screen === 'results' && (
          <Results sessionId={activeSessionId} onSelectSession={setActiveSessionId} />
        )}
        {screen === 'portal' && (
          <PortalRequests
            onViewReport={(sessionId) => {
              setActiveSessionId(sessionId);
              setScreen('results');
            }}
          />
        )}
      </main>
    </div>
  );
}
