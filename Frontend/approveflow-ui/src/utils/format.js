export const STATUS_LABELS = {
    DRAFT: 'Draft',
    PENDING_APPROVAL: 'Pending approval',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    RETURNED: 'Returned',
  }
  
  export function formatDateTime(iso) {
    if (!iso) return '—'
    return new Date(iso).toLocaleString()
  }
  
  export function formatLKR(n) {
    return `LKR ${Number(n).toLocaleString('en-US')}`
  }
  
  export function humanize(key) {
    return key.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())
  }
  
  export function isOverdue(r) {
    return (
      r.status === 'PENDING_APPROVAL' &&
      !!r.currentStepDueAt &&
      new Date(r.currentStepDueAt) < new Date()
    )
  }
  
  export function summarize(r) {
    const p = r.payload || {}
    if (r.type === 'EXPENSE') return formatLKR(p.amount)
    if (r.type === 'LEAVE') return `${p.leaveType}: ${p.startDate} to ${p.endDate}`
    return ''
  }
  export function describeDecision(r) {
    switch (r.status) {
      case 'APPROVED':
        return `Request #${r.id} approved (final)`
      case 'PENDING_APPROVAL':
        return `Request #${r.id} approved and sent to ${r.currentStepRole}`
      case 'REJECTED':
        return `Request #${r.id} rejected`
      case 'RETURNED':
        return `Request #${r.id} returned to the requester`
      default:
        return `Request #${r.id} updated`
    }
  }