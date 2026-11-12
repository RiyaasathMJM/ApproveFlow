import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client.js'
import { homePathFor, useAuth } from '../auth/AuthContext.jsx'
import DecisionDialog from '../components/DecisionDialog.jsx'
import { ErrorState, Loading } from '../components/Feedback.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Timeline from '../components/Timeline.jsx'
import { useFetch } from '../hooks/useFetch.js'
import {
  describeDecision,
  formatDateTime,
  formatLKR,
  humanize,
  isOverdue,
} from '../utils/format.js'

export default function RequestDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const { data, loading, error, reload } = useFetch(`/requests/${id}`)

  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')
  const [flash, setFlash] = useState(location.state?.flash ?? null)
  const [showDecision, setShowDecision] = useState(false)

  async function submit() {
    setBusy(true)
    setActionError('')
    setFlash(null)
    try {
      await api(`/requests/${id}/submit`, { method: 'POST' })
      setFlash({ type: 'success', message: 'Request submitted for approval' })
      reload()
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!window.confirm('Delete this draft? This cannot be undone.')) return
    setBusy(true)
    setActionError('')
    try {
      await api(`/requests/${id}`, { method: 'DELETE' })
      navigate('/requests', { replace: true })
    } catch (err) {
      setActionError(err.message)
      setBusy(false)
    }
  }

  function handleDecisionDone(result) {
    setShowDecision(false)
    setActionError('')
    setFlash({ type: 'success', message: describeDecision(result) })
    reload()
  }

  if (loading && !data) return <Loading />
  if (error) return <ErrorState message={error} onRetry={reload} />

  const { request: r, timeline } = data
  const isOwner = r.ownerId === user.id
  const editable = isOwner && (r.status === 'DRAFT' || r.status === 'RETURNED')
  // UI hint mattum. Real check server-la (role, self-approval, step)
  const canDecide =
    r.status === 'PENDING_APPROVAL' &&
    !isOwner &&
    !!r.currentStepRole &&
    user.roles.includes(r.currentStepRole)
  const returnEvent =
    r.status === 'RETURNED' ? [...timeline].reverse().find((e) => e.action === 'RETURN') : null

  return (
    <div className="stack">
      <div className="page-header" style={{ marginBottom: 0 }}>
        <h2>
          #{r.id} {r.title}
        </h2>
        <Link to={homePathFor(user)}>← Back</Link>
      </div>

      {flash && (
        <div className={`alert ${flash.type}`} role="status">
          {flash.message}
        </div>
      )}
      {actionError && (
        <div className="alert error" role="alert">
          {actionError}
        </div>
      )}
      {returnEvent && (
        <div className="alert warning">
          <strong>Returned for changes{returnEvent.actorName ? ` by ${returnEvent.actorName}` : ''}:</strong>{' '}
          {returnEvent.comment}
        </div>
      )}
      {isOverdue(r) && (
        <div className="alert error">This step is overdue. It can still be actioned by the approver.</div>
      )}

      <div className="card">
        <div>
          <StatusBadge status={r.status} />
          {isOverdue(r) && <span className="status status-OVERDUE">Overdue</span>}
        </div>

        <dl className="details">
          <div><dt>Type</dt><dd>{r.type}</dd></div>
          <div><dt>Requested by</dt><dd>{r.ownerName}</dd></div>
          {Object.entries(r.payload).map(([k, v]) => (
            <div key={k}>
              <dt>{humanize(k)}</dt>
              <dd>{k === 'amount' ? formatLKR(v) : String(v)}</dd>
            </div>
          ))}
          <div><dt>Approval cycle</dt><dd>{r.cycle === 0 ? '—' : r.cycle}</dd></div>
          <div><dt>Waiting for</dt><dd>{r.currentStepRole ?? '—'}</dd></div>
          <div><dt>Step due</dt><dd>{formatDateTime(r.currentStepDueAt)}</dd></div>
          <div><dt>Submitted</dt><dd>{formatDateTime(r.submittedAt)}</dd></div>
          <div><dt>Completed</dt><dd>{formatDateTime(r.completedAt)}</dd></div>
        </dl>

        {r.description && (
          <>
            <h4 style={{ marginBottom: 4 }}>Description</h4>
            <p style={{ marginTop: 0 }}>{r.description}</p>
          </>
        )}

        {(editable || canDecide) && (
          <div className="actions">
            {editable && (
              <>
                <Link className="btn secondary" to={`/requests/${r.id}/edit`}>
                  Edit
                </Link>
                <button className="btn" disabled={busy} onClick={submit}>
                  {busy ? 'Please wait...' : r.status === 'RETURNED' ? 'Resubmit' : 'Submit for approval'}
                </button>
                {r.status === 'DRAFT' && (
                  <button className="btn danger" disabled={busy} onClick={remove}>
                    Delete
                  </button>
                )}
              </>
            )}
            {canDecide && (
              <button className="btn" onClick={() => setShowDecision(true)}>
                Take action
              </button>
            )}
          </div>
        )}
      </div>

      <div className="card">
        <h3 style={{ marginTop: 0 }}>Timeline</h3>
        <Timeline events={timeline} />
      </div>

      {showDecision && (
        <DecisionDialog
          request={r}
          onClose={() => setShowDecision(false)}
          onDone={handleDecisionDone}
          onStale={reload}
        />
      )}
    </div>
  )
}