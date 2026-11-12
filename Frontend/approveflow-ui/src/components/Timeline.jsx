import { STATUS_LABELS, formatDateTime } from '../utils/format.js'

const ACTION_LABELS = {
  SUBMIT: 'Submitted',
  APPROVE: 'Approved',
  REJECT: 'Rejected',
  RETURN: 'Returned for changes',
  RESUBMIT: 'Resubmitted',
  ESCALATE: 'Escalated (overdue)',
}

export default function Timeline({ events }) {
  if (!events || events.length === 0) return <p className="muted">No activity yet.</p>

  return (
    <ol className="timeline">
      {events.map((e) => {
        const changed = e.fromStatus && e.toStatus && e.fromStatus !== e.toStatus
        return (
          <li key={e.id}>
            <div>
              <strong>{ACTION_LABELS[e.action] ?? e.action}</strong>{' '}
              <span className="muted">by {e.actorName}</span>
            </div>
            <div className="muted small">
              {formatDateTime(e.createdAt)}
              {changed && ` · ${STATUS_LABELS[e.fromStatus]} → ${STATUS_LABELS[e.toStatus]}`}
            </div>
            {e.comment && <div className="tl-comment">{e.comment}</div>}
          </li>
        )
      })}
    </ol>
  )
}