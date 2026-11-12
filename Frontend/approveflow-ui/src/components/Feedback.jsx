export function Loading() {
    return <div className="spinner" role="status" aria-label="Loading" />
  }
  
  export function ErrorState({ message, onRetry }) {
    return (
      <div className="empty">
        <div className="alert error" role="alert" style={{ display: 'inline-block' }}>
          {message}
        </div>
        {onRetry && (
          <div>
            <button className="btn secondary" onClick={onRetry}>Retry</button>
          </div>
        )}
      </div>
    )
  }
  
  export function EmptyState({ title, hint, children }) {
    return (
      <div className="empty">
        <h3>{title}</h3>
        {hint && <p className="muted">{hint}</p>}
        {children}
      </div>
    )
  }