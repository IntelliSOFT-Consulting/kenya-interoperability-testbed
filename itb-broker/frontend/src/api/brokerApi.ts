export type AuthType = 'BEARER' | 'BASIC' | 'NONE';
export type SessionStatus = 'CONFIGURED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type ScenarioKey = 'PATIENT_SUMMARY' | 'ECLAIMS' | 'LAB' | 'IMMUNIZATION';
export type PortalRequestStatus = 'PENDING' | 'STARTED';
export type TestCaseType = 'READ' | 'WRITE';

export interface SystemConfig {
  id: string;
  systemName: string;
  sutBaseUrl: string | null;
  organizationName: string | null;
  systemVersion: string | null;
  authType: AuthType;
  authToken: string | null;
  certificationPortalSystemId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface SystemConfigRequest {
  systemName: string;
  sutBaseUrl: string;
  organizationName: string;
  systemVersion: string;
  authType: AuthType;
  authToken: string;
  certificationPortalSystemId: string;
}

export interface ResourceResult {
  id: number;
  resourceType: string;
  testType: TestCaseType | null;
  sutEndpoint: string | null;
  itbEndpoint: string | null;
  fetchStatus: string | null;
  fetchedPayload: string | null;
  itbPostStatus: string | null;
  itbResponse: string | null;
  writeTestStatus: string | null;
  writeTestResponse: string | null;
  writeVerifyPassed: boolean | null;
  writeVerifyDiff: string | null;
  testedAt: string | null;
}

export interface TestSession {
  id: string;
  systemConfig: SystemConfig | null;
  itbSessionId: string;
  itbBaseUrl: string;
  testScenario: ScenarioKey;
  patientId: string | null;
  writeTestEnabled: boolean;
  status: SessionStatus;
  certificatePath: string | null;
  startedAt: string | null;
  completedAt: string | null;
  results: ResourceResult[] | null;
}

export interface StartSessionRequest {
  systemConfigId: string;
  itbSessionId: string;
  itbBaseUrl: string;
  testScenario: ScenarioKey;
  patientId: string | null;
  writeTestEnabled: boolean;
}

export interface PortalTestCase {
  id: number;
  resourceType: string;
  endpoint: string;
  testType: TestCaseType;
}

export interface PortalTestScenario {
  id: string;
  scenarioKey: string;
  testSession: TestSession | null;
  testCases: PortalTestCase[];
}

export interface PortalTestRequest {
  id: string;
  systemConfig: SystemConfig | null;
  requestId: string | null;
  submittedAt: string | null;
  patientId: string | null;
  status: PortalRequestStatus;
  createdAt: string | null;
  scenarios: PortalTestScenario[];
}

export interface StartPortalRequestBody {
  itbSessionId: string;
  itbBaseUrl: string;
}

export interface ScenarioInfo {
  label: string;
  readResources: string[];
  writeResources: string[];
}

// Mirrors the backend YAML scenario registry (spec Section 4).
export const SCENARIOS: Record<ScenarioKey, ScenarioInfo> = {
  PATIENT_SUMMARY: {
    label: 'Patient Summary (IPS)',
    readResources: [
      'Patient', 'Condition', 'AllergyIntolerance', 'MedicationStatement',
      'Observation', 'Immunization', 'DiagnosticReport'
    ],
    writeResources: ['Patient', 'Observation']
  },
  ECLAIMS: {
    label: 'eClaims',
    readResources: ['Patient', 'Claim', 'ClaimResponse', 'Coverage', 'Organization', 'Practitioner'],
    writeResources: ['Claim']
  },
  LAB: {
    label: 'Laboratory',
    readResources: ['Patient', 'ServiceRequest', 'DiagnosticReport', 'Observation', 'Specimen'],
    writeResources: ['ServiceRequest', 'DiagnosticReport']
  },
  IMMUNIZATION: {
    label: 'Immunization',
    readResources: ['Patient', 'Immunization', 'ImmunizationRecommendation'],
    writeResources: ['Immunization']
  }
};

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!response.ok) {
    throw new Error(`${options?.method ?? 'GET'} ${path} failed: ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const brokerApi = {
  listSystems: () => request<SystemConfig[]>('/api/systems'),
  getSystem: (id: string) => request<SystemConfig>(`/api/systems/${id}`),
  createSystem: (body: SystemConfigRequest) =>
    request<SystemConfig>('/api/systems', { method: 'POST', body: JSON.stringify(body) }),
  updateSystem: (id: string, body: SystemConfigRequest) =>
    request<SystemConfig>(`/api/systems/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteSystem: (id: string) =>
    request<void>(`/api/systems/${id}`, { method: 'DELETE' }),

  listSessions: () => request<TestSession[]>('/api/sessions'),
  getSession: (id: string) => request<TestSession>(`/api/sessions/${id}`),
  startSession: (body: StartSessionRequest) =>
    request<TestSession>('/api/sessions', { method: 'POST', body: JSON.stringify(body) }),
  getResults: (id: string) => request<ResourceResult[]>(`/api/sessions/${id}/results`),
  certificateUrl: (id: string) => `/api/sessions/${id}/certificate`,
  retryResult: (sessionId: string, resultId: number) =>
    request<void>(`/api/sessions/${sessionId}/results/${resultId}/retry`, { method: 'POST' }),

  listPortalRequests: () => request<PortalTestRequest[]>('/api/portal/test-requests'),
  getPortalRequest: (id: string) => request<PortalTestRequest>(`/api/portal/test-requests/${id}`),
  startPortalRequest: (id: string, body: StartPortalRequestBody) =>
    request<PortalTestRequest>(`/api/portal/test-requests/${id}/start`, {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  sendPortalStatus: (id: string) =>
    request<void>(`/api/portal/test-requests/${id}/send-status`, { method: 'POST' })
};
