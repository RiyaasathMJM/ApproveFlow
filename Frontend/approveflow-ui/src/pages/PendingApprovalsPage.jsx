import { useState } from 'react'
import { Link } from 'react-router-dom'
import DecisionDialog from '../components/DecisionDialog.jsx'
import { EmptyState, ErrorState, Loading } from '../components/Feedback.jsx'
import { useFetch } from '../hooks/useFetch.js'
import { describeDecision, formatDateTime, isOverdue, summarize } from '../utils/format.js'

const PAGE_SIZE = 10

export default function PendingApprovalsPage() {
  const [page, setPage] = useState(0)
  const [dialog, setDialog] = useState(null) // { request, action }
  const [flash, setFlash] = useState(null)

  const { data, loading, error, reload } = useFetch(`/approvals/pending?page=${page}&size=${PAGE_SIZE}`)

  function handleDone(result) {
    setDialog(null)
    setFlash({ type: 'success', message: describeDecision(result) })
    // page-oda last item decide aana, mun page-ku poga
    if (data && data.content.length === 1 && page > 0) setPage(page - 1)
    else reload()
  }

  return (
    <div>
      <div className="page-header">
        <h2>My Pending Approvals</h2>
        <button className="btn secondary" onClick={reload} disabled={loading}>
          Refresh
        </button>
      </div>

      {flash && (
        <div className={`alert ${flash.type}`} role="status">
          {flash.message}
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        {loading && !data ? (
          <Loading />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : data.content.length === 0 ? (
          <EmptyState
            title="Nothing waiting for you"
            hint="Requests assigned to your role will show up here."
          />
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Title</th>
                  <th>Requested by</th>
                  <th>Type</th>
                  <th>Details</th>
                  <th>Step</th>
                  <th>Due</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>
                      <Link to={`/requests/${r.id}`}>{r.title}</Link>
                    </td>
                    <td>{r.ownerName}</td>
                    <td>{r.type}</td>
                    <td>{summarize(r)}</td>
                    <td>{r.currentStepRole}</td>
                    <td>
                      {formatDateTime(r.currentStepDueAt)}
                      {isOverdue(r) && <span className="status status-OVERDUE">Overdue</span>}
                    </td>
                    <td>
                      <div className="row-actions">
                        <button className="btn small" onClick={() => setDialog({ request: r, action: 'APPROVE' })}>
                          Approve
                        </button>
                        <button className="btn small danger" onClick={() => setDialog({ request: r, action: 'REJECT' })}>
                          Reject
                        </button>
                        <button
                          className="btn small secondary"
                          onClick={() => setDialog({ request: r, action: 'RETURN_FOR_CHANGES' })}
                        >
                          Return
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button className="btn secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <button
            className="btn secondary"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      )}

      {dialog && (
        <DecisionDialog
          request={dialog.request}
          defaultAction={dialog.action}
          onClose={() => setDialog(null)}
          onDone={handleDone}
          onStale={reload}
        />
      )}
    </div>
  )
}