import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

const MENU = [
  { to: '/requests', label: 'My Requests', roles: ['REQUESTER'], end: true },
  { to: '/requests/new', label: 'Create Request', roles: ['REQUESTER'] },
  { to: '/approvals', label: 'My Pending Approvals', roles: ['MANAGER', 'FINANCE'] },
  { to: '/admin/dashboard', label: 'Dashboard', roles: ['ADMIN'] },
  { to: '/admin/requests', label: 'All Requests', roles: ['ADMIN'] },
  { to: '/admin/workflows', label: 'Workflow Definitions', roles: ['ADMIN'] },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const items = MENU.filter((m) => m.roles.some((r) => user.roles.includes(r)))

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <span className="brand">ApproveFlow</span>
          <nav className="nav">
            {items.map((m) => (
              <NavLink key={m.to} to={m.to} end={m.end}>
                {m.label}
              </NavLink>
            ))}
          </nav>
          <div className="userbox">
            <span>
              {user.name}
              {user.roles.map((r) => (
                <span key={r} className="badge">{r}</span>
              ))}
            </span>
            <button className="btn secondary" onClick={logout}>Logout</button>
          </div>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </>
  )
}