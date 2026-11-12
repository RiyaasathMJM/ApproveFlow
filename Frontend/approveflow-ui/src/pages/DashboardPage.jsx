import { useState } from 'react'
import { api } from '../api/client.js'
import { ErrorState, Loading } from '../components/Feedback.jsx'
import { useFetch } from '../hooks/useFetch.js'

function Stat({ label, value, tone }) {
  return (
    <div className={`stat ${tone ?? ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
    </div>
  )
}

export default function DashboardPage() {
  const { data, loading, error, reload } = useFetch('/dashboard/summary')
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState(null)

  async function runEscalation() {
    setRunning(true)
    setResult(null)
    try {
      const r = await api('/admin/escalations/run', { method: 'POST' })
      setResult({
        type: 'success',
        message: `Overdue found: ${r.overdueFound}. Newly escalated: ${r.newlyEscalated}. Already escalated: ${r.alreadyEscalated}.`,
      })
      reload()
    } catch (err) {
      setResult({ type: 'error', message: err.message })
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="stack">
      <div className="page-header" style={{ marginBottom: 0 }}>
        <h2>Summary Dashboard</h2>
        <div className="actions" style={{ marginTop: 0 }}>
          <button className="btn secondary" onClick={reload} disabled={loading}>
            Refresh
          </button>
          <button className="btn" onClick={runEscalation} disabled={running}>
            {running ? 'Running...' : 'Run overdue escalation'}
          </button>
        </div>
      </div>

      {result && (
        <div className={`alert ${result.type}`} role="status">
          {result.message}
        </div>
      )}

      {loading && !data ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <div className="stat-grid">
            <Stat label="Pending approvals" value={data.pendingApprovals} />
            <Stat
              label="Overdue approvals"
              value={data.overdueApprovals}
              tone={data.overdueApprovals > 0 ? 'danger' : ''}
            />
            <Stat label="Approved" value={data.approved} tone="success" />
            <Stat label="Rejected" value={data.rejected} />
            <Stat label="Returned" value={data.returned} />
            <Stat label="Escalations recorded" value={data.escalations} />
            <Stat
              label="Avg completion time"
              value={data.avgCompletionHours == null ? '—' : `${data.avgCompletionHours} h`}
            />
          </div>
          <p className="muted small">
            Average completion time is measured from first submission to final approval, over approved
            requests only. Running escalation twice does not create duplicate records.
          </p>
        </>
      )}
    </div>
  )
}