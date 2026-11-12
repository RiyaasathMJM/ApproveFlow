import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorState, Loading } from '../components/Feedback.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { useFetch } from '../hooks/useFetch.js'
import { STATUS_LABELS, formatDateTime, isOverdue, summarize } from '../utils/format.js'

const PAGE_SIZE = 10

export default function RequestListPage({ title, canCreate = false, showOwner = false }) {
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)

  const query = `/requests?page=${page}&size=${PAGE_SIZE}${status ? `&status=${status}` : ''}`
  const { data, loading, error, reload } = useFetch(query)

  return (
    <div>
      <div className="page-header">
        <h2>{title}</h2>
        {canCreate && (
          <Link className="btn" to="/requests/new">
            + Create Request
          </Link>
        )}
      </div>

      <div className="toolbar">
        <label htmlFor="status-filter">Status</label>
        <select
          id="status-filter"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(0)
          }}
        >
          <option value="">All</option>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorState message={error} onRetry={reload} />
        ) : data.content.length === 0 ? (
          status ? (
            <EmptyState title="No matching requests" hint="Try a different status filter." />
          ) : (
            <EmptyState title="No requests yet" hint="Requests you create will show up here.">
              {canCreate && (
                <Link className="btn" to="/requests/new">
                  Create your first request
                </Link>
              )}
            </EmptyState>
          )
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Title</th>
                  {showOwner && <th>Owner</th>}
                  <th>Type</th>
                  <th>Details</th>
                  <th>Status</th>
                  <th>Waiting for</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>
                      <Link to={`/requests/${r.id}`}>{r.title}</Link>
                    </td>
                    {showOwner && <td>{r.ownerName}</td>}
                    <td>{r.type}</td>
                    <td>{summarize(r)}</td>
                    <td>
                      <StatusBadge status={r.status} />
                      {isOverdue(r) && <span className="status status-OVERDUE">Overdue</span>}
                    </td>
                    <td>{r.currentStepRole ?? '—'}</td>
                    <td>{formatDateTime(r.createdAt)}</td>
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
    </div>
  )
}