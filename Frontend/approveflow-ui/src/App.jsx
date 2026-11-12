import { Navigate, Route, Routes } from 'react-router-dom'
import { homePathFor, useAuth } from './auth/AuthContext.jsx'
import ProtectedRoute from './auth/ProtectedRoute.jsx'
import Layout from './components/Layout.jsx'
import Placeholder from './components/Placeholder.jsx'
import LoginPage from './pages/LoginPage.jsx'

function HomeRedirect() {
  const { user } = useAuth()
  return <Navigate to={user ? homePathFor(user) : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/requests/:id" element={<Placeholder title="Request Details" />} />

          <Route element={<ProtectedRoute roles={['REQUESTER']} />}>
            <Route path="/requests" element={<Placeholder title="My Requests" />} />
            <Route path="/requests/new" element={<Placeholder title="Create Request" />} />
          </Route>

          <Route element={<ProtectedRoute roles={['MANAGER', 'FINANCE']} />}>
            <Route path="/approvals" element={<Placeholder title="My Pending Approvals" />} />
          </Route>

          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="/admin/dashboard" element={<Placeholder title="Summary Dashboard" />} />
            <Route path="/admin/requests" element={<Placeholder title="All Requests" />} />
            <Route path="/admin/workflows" element={<Placeholder title="Workflow Definitions" />} />
          </Route>

          <Route path="*" element={<HomeRedirect />} />
        </Route>
      </Route>
    </Routes>
  )
}