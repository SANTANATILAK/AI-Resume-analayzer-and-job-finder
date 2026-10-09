import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UploadCloud, ExternalLink, Search, CheckCircle2, Bookmark, BookmarkCheck, ListPlus, Briefcase, IndianRupee, MapPin, FileText, EyeOff } from 'lucide-react'
import api, { errMsg } from './api.js'

export const BRANCHES = ['CSE','AI/ML','AI & Data Science','Data Science','IT','Cyber Security','ECE','EEE','EIE','Mechanical','Automobile','Aerospace','Civil','Chemical','Biotechnology','Biomedical','Industrial / Production','Mining','Textile','Agriculture','Food Technology','Architecture','Physics','Chemistry','Mathematics','Statistics','Pharmacy','B.Com / Commerce','BBA / MBA','Design (UI/UX)','Arts / Humanities','Law','Other']
export const YEARS = [2020, 2021, 2022, 2023, 2024, 2025, 2026, 2027, 2028, 2029, 2030]

export function AuthPage({ mode }) {
  const reg = mode === 'register', nav = useNavigate()
  const [f, setF] = useState({ fullName:'', email:'', password:'', confirm:'', gradYear:2027, branch:'CSE' })
  const [err, setErr] = useState(''), [busy, setBusy] = useState(false)
  const set = k => e => setF({ ...f, [k]: e.target.value })
  async function submit(e) {
    e.preventDefault(); setErr('')
    if (reg) {
      if (f.fullName.trim().length < 2) return setErr('Enter your full name.')
      if (f.password.length < 8) return setErr('Password must be at least 8 characters.')
      if (f.password !== f.confirm) return setErr('Passwords do not match.') }
    setBusy(true)
    try {
      const { data } = await api.post(reg ? '/api/auth/register' : '/api/auth/login', reg ? { fullName:f.fullName, email:f.email, password:f.password, gradYear:+f.gradYear, branch:f.branch } : { email:f.email, password:f.password })
      localStorage.setItem('token', data.token); localStorage.setItem('name', data.name); localStorage.setItem('role', data.role)
      nav(data.hasResume ? '/dashboard' : '/upload')
    } catch (x) { setErr(errMsg(x)) } finally { setBusy(false) } }
  return <div className="auth"><div className="auth-art"><h1>Know where your resume stands, then apply where it fits.</h1>
    <p>Upload a PDF or DOCX. We score it, list the skills we actually found, and rank openings by how many required skills you already have.</p></div>
    <form onSubmit={submit} className="card auth-form" noValidate><h2>{reg ? 'Create your account' : 'Log in'}</h2>
      {reg && <label>Full name<input value={f.fullName} onChange={set('fullName')} autoComplete="name" required/></label>}
      <label>Email<input type="email" value={f.email} onChange={set('email')} autoComplete="email" required/></label>
      <label>Password<input type="password" value={f.password} onChange={set('password')} autoComplete={reg ? 'new-password' : 'current-password'} required/></label>
      {reg && <><label>Confirm password<input type="password" value={f.confirm} onChange={set('confirm')} autoComplete="new-password" required/></label>
        <div className="row"><label>Graduation year<select value={f.gradYear} onChange={set('gradYear')}>{YEARS.map(y => <option key={y}>{y}</option>)}</select></label>
        <label>Branch<select value={f.branch} onChange={set('branch')}>{BRANCHES.map(b => <option key={b}>{b}</option>)}</select></label></div></>}
      {err && <p className="err" role="alert">{err}</p>}
      <button className="btn" disabled={busy}>{busy ? 'Please wait…' : reg ? 'Create account' : 'Log in'}</button>
      <p className="alt">{reg ? <>Already registered? <Link to="/login">Log in</Link></> : <>New here? <Link to="/register">Create an account</Link></>}</p></form></div>
}

export function Upload() {
  const nav = useNavigate(), inp = useRef(), [drag, setDrag] = useState(false), [err, setErr] = useState(''), [busy, setBusy] = useState(false)
  async function download() { try { const { data, headers } = await api.get('/api/resume/file', { responseType: 'blob' }); const m = /filename="(.+)"/.exec(headers['content-disposition'] || ''); const a = document.createElement('a'); a.href = URL.createObjectURL(data); a.download = m ? m[1] : 'resume'; a.click() } catch (e) { setErr(errMsg(e)) } }
  async function send(file) {
    setErr(''); if (!file) return
    if (!/\.(pdf|docx)$/i.test(file.name)) return setErr('Choose a PDF or DOCX file.')
    if (file.size > 5 * 1024 * 1024) return setErr('File is larger than 5 MB.')
    const fd = new FormData(); fd.append('file', file); setBusy(true)
    try { await api.post('/api/resume', fd); nav('/companies') } catch (x) { setErr(errMsg(x)) } finally { setBusy(false) } }
  return <section><h2>Upload your resume</h2><p className="mut">PDF or DOCX, up to 5 MB. After upload you go straight to the companies that match. Uploading again replaces your current resume and re-runs the analysis.</p>
    <div className={'drop' + (drag ? ' over' : '')} onClick={() => inp.current.click()} onDragOver={e => { e.preventDefault(); setDrag(true) }} onDragLeave={() => setDrag(false)}
      onDrop={e => { e.preventDefault(); setDrag(false); send(e.dataTransfer.files[0]) }} role="button" tabIndex={0} onKeyDown={e => e.key === 'Enter' && inp.current.click()}>
      <UploadCloud size={36}/><strong>{busy ? 'Analysing your resume…' : 'Drag a file here or browse'}</strong>
      <input ref={inp} type="file" accept=".pdf,.docx" hidden onChange={e => send(e.target.files[0])}/></div>
    {err && <p className="err" role="alert">{err}</p>}<p><button className="btn ghost sm" onClick={download}>Download current resume</button></p></section>
}

function Ring({ value }) {
  const [v, setV] = useState(0); useEffect(() => { const t = setTimeout(() => setV(value), 80); return () => clearTimeout(t) }, [value])
  const r = 70, c = 2 * Math.PI * r
  return <svg viewBox="0 0 180 180" className="ring" role="img" aria-label={`ATS score ${value} out of 100`}><circle cx="90" cy="90" r={r} className="trk"/>
    <circle cx="90" cy="90" r={r} className="bar" strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} transform="rotate(-90 90 90)"/>
    <text x="90" y="92" textAnchor="middle" className="num">{value}</text><text x="90" y="116" textAnchor="middle" className="sub">out of 100</text></svg>
}

export function Dashboard() {
  const nav = useNavigate(), [r, setR] = useState(null), [o, setO] = useState(null), [sy, setSy] = useState(null), [x2, setX2] = useState({})
  useEffect(() => {
    api.get('/api/resume/me').then(x => setR(x.data)).catch(e => e.response?.status === 404 ? nav('/upload') : setR({ error: errMsg(e) }))
    Promise.all([api.get('/api/opportunities', { params: { paid: true, size: 1 } }), api.get('/api/opportunities', { params: { ppo: true, size: 1 } })]).then(([a, b]) => setX2({ paid: a.data.total, ppo: b.data.total })).catch(() => {})
    api.get('/api/sync/status').then(x => setSy(x.data)).catch(() => {})
    api.get('/api/opportunities?size=3').then(x => setO(x.data)).catch(() => setO({ items: [], total: 0, eligibleTotal: 0 })) }, [])
  if (!r) return <div className="skeleton tall"/>
  if (r.error) return <p className="err">{r.error}</p>
  const b = r.breakdown
  return <section><h2>Hi {localStorage.getItem('name')}</h2><p className="mut">Last successful job sync: {sy?.lastSuccess ? new Date(sy.lastSuccess).toLocaleString() : 'none yet'}</p>
    <div className="grid"><div className="card center"><Ring value={r.score}/><p className="mut">ATS score for {r.fileName}</p></div>
      <div className="card"><h3>How the score is built</h3>
        {[['Skills found', b.skills, 40], ['Resume sections', b.sections, 50], ['Length', b.length, 10]].map(([n, v, m]) => <div key={n} className="meter"><span>{n}</span><div><i style={{ width: v / m * 100 + '%' }}/></div><b>{v}/{m}</b></div>)}
        <p className="mut">Skills: 4 points each up to 40. Sections: 10 each for education, projects, experience, skills, certifications. Length: 10 for 200–1200 words.</p></div>
      <div className="card"><h3>Eligible opportunities</h3>{o ? <p className="big">{o.eligibleTotal}</p> : <div className="skeleton"/>}<p className="mut">out of {o?.total ?? '…'} in the database, based on your graduation year.</p><p className="mut">Paid: {x2.paid ?? '…'} · PPO-track: {x2.ppo ?? '…'}</p><Link to="/opportunities" className="btn sm">Browse opportunities</Link></div></div>
    <div className="grid two"><div className="card"><h3>Skills we found</h3><div className="chips">{r.skills.length ? r.skills.map(s => <span key={s} className="chip ok">{s}</span>) : <p className="mut">No known skills detected.</p>}</div></div>
      <div className="card"><h3>What to improve</h3>{r.suggestions.length ? <ul>{r.suggestions.map(s => <li key={s}>{s}</li>)}</ul> : <p className="mut"><CheckCircle2 size={16}/> Nothing flagged.</p>}</div></div></section>
}

export const careersLink = (company, title) => `https://www.google.com/search?q=${encodeURIComponent(`${company} careers ${title || ''}`.trim())}`
export const fit = p => p >= 70 ? ['Strong fit', 'good'] : p >= 40 ? ['Good fit', 'mid'] : p > 0 ? ['Partial fit', 'low'] : ['Low fit', 'none']
export const daysAgo = iso => { const d = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 864e5)); return d === 0 ? 'Today' : d === 1 ? '1 Day Ago' : `${d} Days Ago` }
const hiddenIds = () => { try { return new Set(JSON.parse(localStorage.getItem('hidden') || '[]')) } catch { return new Set() } }

export function Opportunities({ savedOnly = false }) {
  const [q, setQ] = useState(''), [loc, setLoc] = useState(''), [cat, setCat] = useState(''), [remote, setRemote] = useState(false), [sort, setSort] = useState('match')
  const [minMatch, setMin] = useState(0), [page, setPage] = useState(0), [d, setD] = useState(null), [err, setErr] = useState(''), [note, setNote] = useState(''), [hidden, setHidden] = useState(hiddenIds)
  useEffect(() => { setD(null); const t = setTimeout(() => api.get('/api/opportunities', { params: { q, location: loc, category: cat, workMode: remote ? 'Remote' : '', minMatch, savedOnly, page, size: 15, sort } }).then(x => setD(x.data)).catch(e => setErr(errMsg(e))), 250); return () => clearTimeout(t) }, [q, loc, cat, remote, minMatch, page, savedOnly, sort])
  const reset = fn => v => { setPage(0); fn(v) }
  function hide(o) { const h = new Set(hidden); h.add(o.id); setHidden(h); try { localStorage.setItem('hidden', JSON.stringify([...h])) } catch {} }
  async function toggleSave(o) { await (o.saved ? api.delete(`/api/saved/${o.id}`) : api.post(`/api/saved/${o.id}`)); setD({ ...d, items: savedOnly ? d.items.filter(i => i.id !== o.id) : d.items.map(i => i.id === o.id ? { ...i, saved: !i.saved } : i) }) }
  async function track(o) { await api.post('/api/applications', { oppId: o.id, status: 'INTERESTED' }); setNote(`${o.title} added to Tracking.`) }
  const items = d ? d.items.filter(o => !hidden.has(o.id)) : []
  return <section><h2>{savedOnly ? 'Saved jobs' : 'Jobs for you'}</h2><p className="mut">{d ? `${d.total.toLocaleString()} roles` : '…'}, ranked by how many of the required skills are in your resume. Match shows fit, not your chance of selection.</p>
    <div className="bar2"><label className="srch"><Search size={16}/><input placeholder="Search company, title or skill" value={q} onChange={e => reset(setQ)(e.target.value)}/></label>
      <label className="srch"><MapPin size={16}/><input placeholder="Location (e.g. Hyderabad)" value={loc} onChange={e => reset(setLoc)(e.target.value)}/></label></div>
    <div className="bar2 tg"><select value={cat} onChange={e => reset(setCat)(e.target.value)} aria-label="Type"><option value="">Jobs and internships</option><option value="FULL_TIME">Jobs</option><option value="INTERNSHIP">Internships</option></select>
      <select value={minMatch} onChange={e => reset(setMin)(+e.target.value)} aria-label="Minimum match"><option value={0}>Any match</option><option value={25}>25%+</option><option value={50}>50%+</option><option value={75}>75%+</option></select>
      <select value={sort} onChange={e => reset(setSort)(e.target.value)} aria-label="Sort"><option value="match">Best match</option><option value="recent">Most recent</option></select>
      <label><input type="checkbox" checked={remote} onChange={e => reset(setRemote)(e.target.checked)}/> Remote</label></div>
    {note && <p className="ok-t" role="status">{note}</p>}{err && <p className="err">{err}</p>}{!d && !err && <><div className="skeleton tall"/><div className="skeleton tall"/></>}
    {d && items.length === 0 && <div className="card empty"><h3>{savedOnly ? 'Nothing saved yet' : 'No jobs found'}</h3><p className="mut">{savedOnly ? 'Save roles from the Jobs page.' : 'Try clearing a filter.'}</p></div>}
    {items.map(o => { const [lab, tone] = fit(o.matchPercent); return <article key={o.id} className="card job">
      <div className="jt"><div><h3><Link to={`/opportunities/${o.id}`}>{o.title}</Link></h3><p className="co">{o.company}</p></div>
        <div className={'pct ' + tone}><b>{o.matchPercent}%</b><span>{lab}</span></div></div>
      <div className="meta"><span><Briefcase size={16}/> {o.experience || 'Fresher'}</span><span><IndianRupee size={16}/> {o.stipend || 'Not disclosed'}</span><span><MapPin size={16}/> {o.location || 'Not specified'}{o.workMode ? ` · ${o.workMode}` : ''}</span></div>
      <p className="snip"><FileText size={15}/> {o.snippet}</p>
      <div className="tags">{o.matchedSkills.map(s => <span key={s} className="t ok">{s}</span>)}{o.missingSkills.map(s => <span key={s} className="t">{s}</span>)}</div>
      <div className="jf"><span className="mut sm">{daysAgo(o.lastUpdated)}{o.source === 'Sample data' ? ' · Sample role' : ''}{!o.eligible ? ' · Not open to your graduation year or branch' : ''}</span>
        <span className="acts"><button className="lnk" onClick={() => hide(o)}><EyeOff size={15}/> Hide</button><button className="lnk" onClick={() => toggleSave(o)}>{o.saved ? <BookmarkCheck size={15}/> : <Bookmark size={15}/>} {o.saved ? 'Saved' : 'Save'}</button>
        <button className="lnk" onClick={() => track(o)}><ListPlus size={15}/> Track</button>
        <a className="btn sm" href={o.canApply ? o.applyUrl : careersLink(o.company, o.title)} target="_blank" rel="noopener noreferrer">{o.canApply ? 'Apply' : 'Find on careers page'} <ExternalLink size={14}/></a></span></div></article> })}
    {d && d.pages > 1 && <div className="pager"><button className="btn sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1} of {d.pages}</span><button className="btn sm" disabled={page + 1 >= d.pages} onClick={() => setPage(page + 1)}>Next</button></div>}</section>
}
