import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client.js'
import { summarize } from '../utils/format.js'

const ACTIONS = [
  { value: 'APPROVE', label: 'Approve', hint: 'Reason is optional.' },
  { value: 'REJECT', label: 'Reject', hint: 'Reason required. The workflow stops.' },
  {
    value: 'RETURN_FOR_CHANGES',
    label: 'Return for changes',
    hint: 'Reason required. The requester can edit and resubmit.',
  },
]

export default function DecisionDialog({ request, defaultAction = 'APPROVE', onClose, onDone, onStale }) {
  const [action, setAction] = useState(defaultAction)
  const [comment, setComment] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const textRef = useRef(null)

  useEffect(() => {
    textRef.current?.focus()
  }, [])

  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape' && !saving) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose, saving])

  const reasonRequired = action !== 'APPROVE'
  const selected = ACTIONS.find((a) => a.value === action)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    const text = comment.trim()

    if (reasonRequired && !text) {
      setFieldError('A reason is required to reject or return a request')
      return
    }
    setFieldError('')
    setSaving(true)
    try {
      const result = await api(`/requests/${request.id}/decisions`, {
        method: 'POST',
        body: { action, comment: text || null },
      })
      onDone(result)
    } catch (err) {
      setError(err.message)
      // vera yaaro munnaadi decide pannitaanga na, background-la data refresh
      if (err.status === 403 || err.status === 404 || err.status === 409) onStale?.()
      setSaving(false)
    }
  }

  return (
    <div
      className="overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget && !saving) onClose()
      }}
    >
      <form
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="decision-title"
        onSubmit={handleSubmit}
        noValidate
      >
        <h3 id="decision-title" style={{ marginTop: 0 }}>
          Decide on #{request.id}
        </h3>
        <p className="muted" style={{ marginTop: -6 }}>
          {request.title} · {summarize(request)} · by {request.ownerName}
        </p>

        {error && (
          <div className="alert error" role="alert">
            {error}
          </div>
        )}

        <fieldset className="radio-group">
          <legend className="sr-only">Decision</legend>
          {ACTIONS.map((a) => (
            <label key={a.value} className={`radio ${action === a.value ? 'selected' : ''}`}>
              <input
                type="radio"
                name="action"
                value={a.value}
                checked={action === a.value}
                onChange={() => {
                  setAction(a.value)
                  setFieldError('')
                }}
              />
              {a.label}
            </label>
          ))}
        </fieldset>

        <div className="field" style={{ marginTop: 14 }}>
          <label htmlFor="comment">
            Reason {reasonRequired ? '(required)' : '(optional)'}
          </label>
          <textarea
            id="comment"
            ref={textRef}
            rows={3}
            maxLength={2000}
            value={comment}
            onChange={(e) => {
              setComment(e.target.value)
              if (fieldError) setFieldError('')
            }}
            aria-invalid={!!fieldError}
          />
          <span className="muted small">{selected.hint}</span>
          {fieldError && <span className="field-error">{fieldError}</span>}
        </div>

        <div className="actions" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="btn secondary" disabled={saving} onClick={onClose}>
            Cancel
          </button>
          <button className={`btn ${action === 'REJECT' ? 'danger' : ''}`} disabled={saving}>
            {saving ? 'Saving...' : `Confirm ${selected.label.toLowerCase()}`}
          </button>
        </div>
      </form>
    </div>
  )
}