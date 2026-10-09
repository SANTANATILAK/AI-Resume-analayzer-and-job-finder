import { Routes, Route, Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LayoutDashboard, Briefcase, FileUp, LogOut, Bookmark, ClipboardList, User, ShieldCheck, Building2 } from 'lucide-react'
import { AuthPage, Upload, Dashboard, Opportunities } from './pages.jsx'
import { Profile, Tracking, Admin, Detail, Companies, CompanyDetail } from './pages2.jsx'

const Guard = ({ children }) => localStorage.getItem('token') ? children : <Navigate to="/login" replace />
function Shell() {
  const nav = useNavigate(), admin = localStorage.getItem('role') === 'ADMIN'
  const out = () => { localStorage.clear(); nav('/login') }
  const L = ({ to, icon: I, children }) => <NavLink to={to} className={({ isActive }) => 'nl' + (isActive ? ' on' : '')}><I size={18}/><span>{children}</span></NavLink>
  return <div className="shell">
    <aside><div className="brand">InternMatch</div>
      <nav><L to="/dashboard" icon={LayoutDashboard}>Dashboard</L><L to="/opportunities" icon={Briefcase}>Jobs</L><L to="/companies" icon={Building2}>Companies</L><L to="/saved" icon={Bookmark}>Saved</L><L to="/tracking" icon={ClipboardList}>Tracking</L>
        <L to="/upload" icon={FileUp}>Resume</L><L to="/profile" icon={User}>Profile</L>{admin && <L to="/admin" icon={ShieldCheck}>Admin</L>}</nav>
      <button className="nl out" onClick={out}><LogOut size={18}/><span>Log out</span></button></aside>
    <main><Outlet/></main></div>
}
export default function App() {
  return <Routes>
    <Route path="/login" element={<AuthPage mode="login"/>} /><Route path="/register" element={<AuthPage mode="register"/>} />
    <Route element={<Guard><Shell/></Guard>}><Route path="/upload" element={<Upload/>}/><Route path="/dashboard" element={<Dashboard/>}/><Route path="/opportunities" element={<Opportunities/>}/>
      <Route path="/opportunities/:id" element={<Detail/>}/><Route path="/companies" element={<Companies/>}/><Route path="/companies/:id" element={<CompanyDetail/>}/><Route path="/saved" element={<Opportunities savedOnly/>}/><Route path="/tracking" element={<Tracking/>}/><Route path="/profile" element={<Profile/>}/><Route path="/admin" element={<Admin/>}/></Route>
    <Route path="*" element={<Navigate to="/dashboard" replace />} /></Routes>
}
