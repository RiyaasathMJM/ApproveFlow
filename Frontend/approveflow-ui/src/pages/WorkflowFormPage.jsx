import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client.js'

const ROLES = ['MANAGER', 'FINANCE', 'ADMIN']
const OPS = ['>', '>=', '<', '<=', '==']

let keySeq = 0
const newStep = (sequence, init = {}) => ({
  key: ++keySeq,
  sequence,
  approverRole: 'MANAGER',
  hasCondition: false,
  op: '>',
  value: '',
  dueHours: 48,
  ...init,
})

export default function WorkflowFormPage() {
  const navigate = useNavigate()
  const base = useLocation().state?.workflow
  const isNewVersion = !!base

  const [name, setName] = useState(base?.name ?? '')
  const [type, setType] = useState(base?.requestType ?? 'EXPENSE')
  const [steps, setSteps] = useState(() =>
    base?.currentVersion
      ? base.currentVersion.steps.map((s) =>
          newStep(s.sequence, {
            approverRole: s.approverRole,
            hasCondition: !!s.condition,
            op: s.condition?.op ?? '>',
            value: s.condition?.value ?? '',
            dueHours: s.dueHours,
          })
        )
      : [newStep(1)]
  )
  const [errors, setErrors] = useState({ steps: {} })
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  function updateStep(key, patch) {
    setSteps(steps.map((s) => (s.key === key ? { ...s, ...patch } : s)))
  }

  function addStep() {
    const next = steps.reduce((m, s) => Math.max(m, Number(s.sequence) || 0), 0) + 1
    setSteps([...steps, newStep(next)])
  }

  function changeType(value) {
    setType(value)
    // conditions amount-ku mattum. LEAVE-ku amount illa
    if (value === 'LEAVE') setSteps(steps.map((s) => ({ ...s, hasCondition: false })))
  }

  function validate() {
    const e = { steps: {} }
    if (!name.trim()) e.name = 'Name is required'
    if (steps.length === 0) e.general = 'Add at least one step'

    const seen = new Set()
    steps.forEach((s) => {
      const seq = Number(s.sequence)
      if (!Number.isInteger(seq) || seq < 1) {
        e.steps[s.key] = 'Sequence must be a whole number, 1 or more'
      } else if (seen.has(seq)) {
        e.steps[s.key] = `Duplicate sequence ${seq}`
      } else {
        seen.add(seq)
      }
      if (!e.steps[s.key]) {
        const due = Number(s.dueHours)
        if (!Number.isInteger(due) || due < 1) {
          e.steps[s.key] = 'Due hours must be a whole number, 1 or more'
        } else if (s.hasCondition && (s.value === '' || Number.isNaN(Number(s.value)))) {
          e.steps[s.key] = 'Enter a number for the condition'
        }
      }
    })

    const first = [...steps].sort((a, b) => Number(a.sequence) - Number(b.sequence))[0]
    if (first && first.hasCondition && !e.steps[first.key]) {
      e.steps[first.key] = 'The first step must be unconditional so every request has a route'
    }
    return e
  }

  async function handleSubmit(ev) {
    ev.preventDefault()
    setFormError('')
    const e = validate()
    setErrors(e)
    if (e.name || e.general || Object.keys(e.steps).length > 0) return

    setSaving(true)
    try {
      const saved = await api('/workflows', {
        method: 'POST',
        body: {
          name: name.trim(),
          requestType: type,
          steps: steps.map((s) => ({
            sequence: Number(s.sequence),
            approverRole: s.approverRole,
            condition: s.hasCondition ? { field: 'amount', op: s.op, value: Number(s.value) } : null,
            dueHours: Number(s.dueHours),
          })),
        },
      })
      navigate('/admin/workflows', {
        state: {
          flash: {
            type: 'success',
            message: `"${saved.name}" saved as version ${saved.currentVersion.versionNumber}`,
          },
        },
      })
    } catch (err) {
      setFormError(err.message)
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h2>{isNewVersion ? `New version of "${base.name}"` : 'New workflow'}</h2>
        <Link to="/admin/workflows">Cancel</Link>
      </div>

      <form className="card" onSubmit={handleSubmit} noValidate>
        {formError && (
          <div className="alert error" role="alert">
            {formError}
          </div>
        )}
        {errors.general && <div className="alert error">{errors.general}</div>}

        <div className="form-grid">
          <div className="field">
            <label htmlFor="wf-name">Workflow name</label>
            <input
              id="wf-name"
              value={name}
              maxLength={100}
              disabled={isNewVersion}
              onChange={(e) => setName(e.target.value)}
            />
            {errors.name && <span className="field-error">{errors.name}</span>}
          </div>
          <div className="field">
            <label htmlFor="wf-type">Request type</label>
            <select id="wf-type" value={type} disabled={isNewVersion} onChange={(e) => changeType(e.target.value)}>
              <option value="EXPENSE">EXPENSE</option>
              <option value="LEAVE">LEAVE</option>
            </select>
          </div>
        </div>

        <h3>Approval steps</h3>
        {steps.map((s) => (
          <div key={s.key} className="step-card">
            <div className="step-grid">
              <div className="field">
                <label htmlFor={`seq-${s.key}`}>Sequence</label>
                <input
                  id={`seq-${s.key}`}
                  type="number"
                  min="1"
                  value={s.sequence}
                  onChange={(e) => updateStep(s.key, { sequence: e.target.value })}
                />
              </div>
              <div className="field">
                <label htmlFor={`role-${s.key}`}>Approver role</label>
                <select
                  id={`role-${s.key}`}
                  value={s.approverRole}
                  onChange={(e) => updateStep(s.key, { approverRole: e.target.value })}
                >
                  {ROLES.map((r) => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor={`due-${s.key}`}>Due (hours)</label>
                <input
                  id={`due-${s.key}`}
                  type="number"
                  min="1"
                  value={s.dueHours}
                  onChange={(e) => updateStep(s.key, { dueHours: e.target.value })}
                />
              </div>
            </div>

            <div className="condition-row">
              <label className="check">
                <input
                  type="checkbox"
                  checked={s.hasCondition}
                  disabled={type === 'LEAVE'}
                  onChange={(e) => updateStep(s.key, { hasCondition: e.target.checked })}
                />
                Only when amount
              </label>
              <select
                aria-label="Operator"
                value={s.op}
                disabled={!s.hasCondition}
                onChange={(e) => updateStep(s.key, { op: e.target.value })}
              >
                {OPS.map((o) => (
                  <option key={o} value={o}>
                    {o}
                  </option>
                ))}
              </select>
              <input
                aria-label="Condition value"
                type="number"
                placeholder="10000"
                value={s.value}
                disabled={!s.hasCondition}
                onChange={(e) => updateStep(s.key, { value: e.target.value })}
              />
              <button
                type="button"
                className="btn small secondary"
                disabled={steps.length === 1}
                onClick={() => setSteps(steps.filter((x) => x.key !== s.key))}
              >
                Remove step
              </button>
            </div>
            {errors.steps[s.key] && <span className="field-error">{errors.steps[s.key]}</span>}
          </div>
        ))}

        <button type="button" className="btn secondary" onClick={addStep}>
          + Add step
        </button>
        {type === 'LEAVE' && (
          <p className="muted small">Conditions are only supported for expense amounts.</p>
        )}

        <div className="actions">
          <button className="btn" disabled={saving}>
            {saving ? 'Saving...' : isNewVersion ? 'Save as new version' : 'Create workflow'}
          </button>
        </div>
      </form>
    </div>
  )
}