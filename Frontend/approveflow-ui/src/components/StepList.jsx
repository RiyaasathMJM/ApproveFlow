function conditionText(c) {
    if (!c) return 'Always'
    return `When ${c.field} ${c.op} ${Number(c.value).toLocaleString('en-US')}`
  }
  
  export default function StepList({ steps }) {
    return (
      <ol className="steps">
        {steps.map((s) => (
          <li key={s.id}>
            <strong>Step {s.sequence}</strong> · {s.approverRole}{' '}
            <span className="muted">
              · {conditionText(s.condition)} · due in {s.dueHours}h
            </span>
          </li>
        ))}
      </ol>
    )
  }