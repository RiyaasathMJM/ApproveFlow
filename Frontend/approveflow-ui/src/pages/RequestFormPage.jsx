import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client.js'
import { ErrorState, Loading } from '../components/Feedback.jsx'

const LEAVE_TYPES = ['ANNUAL', 'SICK', 'CASUAL', 'OTHER']

function Field({ id, label, error, children }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  )
}

export default function RequestFormPage() {
  const { id } = useParams()
  const isEdit = !!id
  const navigate = useNavigate()

  const [loading, setLoading] = useState(isEdit)
  const [loadError, setLoadError] = useState('')
  const [existingStatus, setExistingStatus] = useState(null)

  const [type, setType] = useState('EXPENSE')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState('')
  const [leaveType, setLeaveType] = useState('ANNUAL')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [reason, setReason] = useState('')

  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!isEdit) return
    api(`/requests/${id}`)
      .then(({ request: r }) => {
        if (r.status !== 'DRAFT' && r.status !== 'RETURNED') {
          setLoadError('This request cannot be edited in its current status')
          return
        }
        const p = r.payload || {}
        setExistingStatus(r.status)
        setType(r.type)
        setTitle(r.title)
        setDescription(r.description ?? '')
        setAmount(p.amount ?? '')
        setCategory(p.category ?? '')
        setLeaveType(p.leaveType ?? 'ANNUAL')
        setStartDate(p.startDate ?? '')
        setEndDate(p.endDate ?? '')
        setReason(p.reason ?? '')
      })
      .catch((err) => setLoadError(err.message))
      .finally(() => setLoading(false))
  }, [id, isEdit])

  function validate() {
    const e = {}
    if (!title.trim()) e.title = 'Title is required'
    if (type === 'EXPENSE') {
      if (amount === '') e.amount = 'Amount is required'
      else if (!(Number(amount) > 0)) e.amount = 'Amount must be greater than 0'
    } else {
      if (!startDate) e.startDate = 'Start date is required'
      if (!endDate) e.endDate = 'End date is required'
      if (startDate && endDate && endDate < startDate) {
        e.endDate = 'End date must not be before start date'
      }
      if (!reason.trim()) e.reason = 'Reason is required'
    }
    return e
  }

  function buildPayload() {
    if (type === 'EXPENSE') {
      const p = { amount: Number(amount) }
      if (category.trim()) p.category = category.trim()
      return p
    }
    return { leaveType, startDate, endDate, reason: reason.trim() }
  }

  async function save(submitAfter) {
    setFormError('')
    const e = validate()
    setErrors(e)
    if (Object.keys(e).length > 0) return

    setSaving(true)
    let savedId = id
    try {
      const body = {
        title: title.trim(),
        description: description.trim() || null,
        payload: buildPayload(),
      }
      if (isEdit) {
        await api(`/requests/${id}`, { method: 'PATCH', body })
      } else {
        const created = await api('/requests', { method: 'POST', body: { type, ...body } })
        savedId = created.id
      }
    } catch (err) {
      setFormError(err.message)
      setSaving(false)
      return
    }

    if (!submitAfter) {
      navigate(`/requests/${savedId}`, {
        state: { flash: { type: 'success', message: isEdit ? 'Changes saved' : 'Draft saved' } },
      })
      return
    }
    try {
      await api(`/requests/${savedId}/submit`, { method: 'POST' })
      navigate(`/requests/${savedId}`, {
        state: { flash: { type: 'success', message: 'Request submitted for approval' } },
      })
    } catch (err) {
      navigate(`/requests/${savedId}`, {
        state: { flash: { type: 'error', message: `Saved, but submit failed: ${err.message}` } },
      })
    }
  }

  if (loading) return <Loading />
  if (loadError) return <ErrorState message={loadError} />

  const resubmit = existingStatus === 'RETURNED'

  return (
    <div>
      <div className="page-header">
        <h2>{isEdit ? `Edit Request #${id}` : 'Create Request'}</h2>
        <Link to={isEdit ? `/requests/${id}` : '/requests'}>Cancel</Link>
      </div>

      <form
        className="card"
        onSubmit={(e) => {
          e.preventDefault()
          save(false)
        }}
        noValidate
      >
        {formError && <div className="alert error" role="alert">{formError}</div>}

        <div className="form-grid">
          <Field id="type" label="Request type">
            <select id="type" value={type} onChange={(e) => setType(e.target.value)} disabled={isEdit}>
              <option value="EXPENSE">Expense claim</option>
              <option value="LEAVE">Leave request</option>
            </select>
          </Field>

          <Field id="title" label="Title" error={errors.title}>
            <input id="title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
          </Field>

          <div className="full">
            <Field id="description" label="Description (optional)">
              <textarea
                id="description"
                rows={3}
                maxLength={2000}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </Field>
          </div>

          {type === 'EXPENSE' ? (
            <>
              <Field id="amount" label="Amount (LKR)" error={errors.amount}>
                <input
                  id="amount"
                  type="number"
                  min="0"
                  step="0.01"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                />
              </Field>
              <Field id="category" label="Category (optional)">
                <input id="category" value={category} onChange={(e) => setCategory(e.target.value)} />
              </Field>
            </>
          ) : (
            <>
              <Field id="leaveType" label="Leave type">
                <select id="leaveType" value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
                  {LEAVE_TYPES.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </Field>
              <div />
              <Field id="startDate" label="Start date" error={errors.startDate}>
                <input id="startDate" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
              </Field>
              <Field id="endDate" label="End date" error={errors.endDate}>
                <input id="endDate" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </Field>
              <div className="full">
                <Field id="reason" label="Reason" error={errors.reason}>
                  <textarea id="reason" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
                </Field>
              </div>
            </>
          )}
        </div>

        <div className="actions">
          <button type="submit" className="btn secondary" disabled={saving}>
            {isEdit ? 'Save changes' : 'Save draft'}
          </button>
          <button type="button" className="btn" disabled={saving} onClick={() => save(true)}>
            {saving ? 'Saving...' : resubmit ? 'Save & resubmit' : 'Save & submit'}
          </button>
        </div>
      </form>
    </div>
  )
}