import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../api/client.js'
import { EmptyState, ErrorState, Loading } from '../components/Feedback.jsx'
import StepList from '../components/StepList.jsx'
import { useFetch } from '../hooks/useFetch.js'
import { formatDateTime } from '../utils/format.js'

function VersionHistory({ id, currentVersionId }) {
  const { data, loading, error, reload } = useFetch(`/workflows/${id}/versions`)
  if (loading) return <Loading />
  if (error) return <ErrorState message={error} onRetry={reload} />
  return (
    <div className="history">
      {data.map((v) => (
        <div key={v.id} className="history-item">
          <div>
            <strong>Version {v.versionNumber}</strong>
            {v.id === currentVersionId && <span className="badge">current</span>}{' '}
            <span className="muted small">{formatDateTime(v.createdAt)}</span>
          </div>
          <StepList steps={v.steps} />
        </div>
      ))}
    </div>
  )
}

export default function WorkflowsPage() {
  const location = useLocation()
  const { data, loading, error, reload } = useFetch('/workflows')
  const [open, setOpen] = useState({})
  const [busyId, setBusyId] = useState(null)
  const [flash, setFlash] = useState(location.state?.flash ?? null)

  async function toggleActive(w) {
    const next = !w.active
    const msg = next
      ? `Activate "${w.name}"? New requests of this type can be submitted again.`
      : `Deactivate "${w.name}"? New requests of this type cannot be submitted. Requests already in progress continue on their current version.`
    if (!window.confirm(msg)) return

    setBusyId(w.id)
    setFlash(null)
    try {
      await api(`/workflows/${w.id}/activation`, { method: 'PATCH', body: { active: next } })
      setFlash({ type: 'success', message: `"${w.name}" ${next ? 'activated' : 'deactivated'}` })
      reload()
    } catch (err) {
      setFlash({ type: 'error', message: err.message })
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="stack">
      <div className="page-header" style={{ marginBottom: 0 }}>
        <h2>Workflow Definitions</h2>
        <Link className="btn" to="/admin/workflows/new">
          + New workflow
        </Link>
      </div>

      {flash && (
        <div className={`alert ${flash.type}`} role="status">
          {flash.message}
        </div>
      )}

      {loading && !data ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : data.length === 0 ? (
        <div className="card">
          <EmptyState title="No workflows yet" hint="Create a workflow to start routing requests." />
        </div>
      ) : (
        data.map((w) => (
          <div key={w.id} className="card">
            <div className="wf-head">
              <div>
                <h3 style={{ margin: 0 }}>{w.name}</h3>
                <div className="muted small" style={{ marginTop: 4 }}>
                  Request type: {w.requestType}
                  {w.currentVersion && ` · Current version: ${w.currentVersion.versionNumber}`}
                </div>
              </div>
              <span className={`status ${w.active ? 'status-ACTIVE' : 'status-INACTIVE'}`}>
                {w.active ? 'Active' : 'Inactive'}
              </span>
            </div>

            {w.currentVersion ? (
              <StepList steps={w.currentVersion.steps} />
            ) : (
              <p className="muted">No version configured.</p>
            )}

            <div className="actions">
              <button className="btn secondary" onClick={() => setOpen({ ...open, [w.id]: !open[w.id] })}>
                {open[w.id] ? 'Hide versions' : 'Version history'}
              </button>
              <Link className="btn secondary" to="/admin/workflows/new" state={{ workflow: w }}>
                New version
              </Link>
              <button className="btn secondary" disabled={busyId === w.id} onClick={() => toggleActive(w)}>
                {w.active ? 'Deactivate' : 'Activate'}
              </button>
            </div>

            {open[w.id] && <VersionHistory id={w.id} currentVersionId={w.currentVersion?.id} />}
          </div>
        ))
      )}

      <p className="muted small">
        Editing a workflow creates a new immutable version. Requests already in progress keep the version
        they were submitted with. Activation only affects new submissions.
      </p>
    </div>
  )
}