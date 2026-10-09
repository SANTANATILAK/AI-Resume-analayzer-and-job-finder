import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ExternalLink, Search } from 'lucide-react'
import api, { errMsg } from './api.js'
import { BRANCHES, YEARS, careersLink, fit } from './pages.jsx'

export function Profile() {
  const [p, setP] = useState(null), [msg, setMsg] = useState('')
  useEffect(() => { api.get('/api/me').then(x => setP(x.data)) }, [])
  if (!p) return <div className="skeleton tall"/>
  async function save(e) { e.preventDefault(); try { const { data } = await api.put('/api/me', { gradYear: +p.gradYear, branch: p.branch }); setP(data); setMsg('Profile saved. Matching now uses this graduation year.') } catch (x) { setMsg(errMsg(x)) } }
  return <section><h2>Profile</h2><form onSubmit={save} className="card narrow"><p><b>{p.fullName}</b><br/><span className="mut">{p.email}</span></p>
    <label>Graduation year<select value={p.gradYear} onChange={e => setP({ ...p, gradYear: e.target.value })}>{YEARS.map(y => <option key={y}>{y}</option>)}</select></label>
    <label>Branch<select value={p.branch} onChange={e => setP({ ...p, branch: e.target.value })}>{BRANCHES.map(b => <option key={b}>{b}</option>)}</select></label>
    <button className="btn">Save changes</button>{msg && <p role="status" className="ok-t">{msg}</p>}</form></section>
}

const ST = ['INTERESTED', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED']
export function Tracking() {
  const [l, setL] = useState(null)
  const load = () => api.get('/api/applications').then(x => setL(x.data))
  useEffect(() => { load() }, [])
  const upd = (t, patch) => api.post('/api/applications', { oppId: t.oppId, ...patch }).then(load)
  if (!l) return <div className="skeleton tall"/>
  return <section><h2>Application tracking</h2><p className="mut">You set these statuses yourself. Nothing here confirms that an employer received an application.</p>
    {l.length === 0 && <div className="card empty"><h3>Nothing tracked yet</h3><p className="mut">Use the list icon on an opportunity to start tracking it.</p></div>}
    {l.map(t => <div key={t.id} className="card"><div className="oh"><div><h3>{t.title}</h3><p className="mut">{t.company}</p></div>
      <select value={t.status} aria-label="Status" onChange={e => api.post('/api/applications', { oppId: t.oppId ?? 0, status: e.target.value }).then(load)}>{ST.map(s => <option key={s}>{s}</option>)}</select></div>
      <textarea defaultValue={t.notes} placeholder="Notes" onBlur={e => e.target.value !== t.notes && api.post('/api/applications', { oppId: t.oppId ?? 0, notes: e.target.value }).then(load)}/>
      <button className="btn ghost sm" onClick={() => api.delete(`/api/applications/${t.id}`).then(load)}>Remove</button></div>)}</section>
}

export function Detail() {
  const { id } = useParams(), [o, setO] = useState(null), [err, setErr] = useState('')
  useEffect(() => { api.get(`/api/opportunities/${id}`).then(x => setO(x.data)).catch(e => setErr(errMsg(e))) }, [id])
  if (err) return <p className="err">{err}</p>
  if (!o) return <div className="skeleton tall"/>
  const L = a => a.length ? a.join(', ') : 'Not specified'
  return <section><Link to="/opportunities">Back to opportunities</Link>
    <div className="card"><div className="oh"><div>{o.logoUrl && <img src={o.logoUrl} alt="" height="36"/>}<h2>{o.title}</h2><p className="mut">{o.company}{o.location ? ` · ${o.location}` : ''}{o.workMode ? ` · ${o.workMode}` : ''}</p></div><div className="pct"><b>{o.matchPercent}%</b><span>match</span></div></div>
      <div className="chips">{o.matchedSkills.map(s => <span key={s} className="chip ok">{s}</span>)}{o.missingSkills.map(s => <span key={s} className="chip">{s}</span>)}</div>
      <p style={{ whiteSpace: 'pre-wrap' }}>{o.description || 'No description provided by the source.'}</p>
      <table><tbody><tr><th>Type</th><td>{o.category === 'INTERNSHIP' ? 'Internship' : 'Full-time'}{o.ppo ? ' (PPO available)' : ''}</td></tr><tr><th>Experience</th><td>{o.experience || 'Fresher'}</td></tr><tr><th>Stipend / salary</th><td>{o.stipend || 'Not stated'}</td></tr>
        <tr><th>Graduation years</th><td>{L(o.gradYears)} {o.eligible ? '' : '(not open to your year or branch)'}</td></tr><tr><th>Branches</th><td>{L(o.branches)}</td></tr><tr><th>Closing date</th><td>{o.closingDate || 'Not stated'}</td></tr>
        <tr><th>Source</th><td>{o.source || 'unknown'} ({o.verified ? 'verified' : 'unverified'}), status {o.status}, updated {new Date(o.lastUpdated).toLocaleString()}</td></tr></tbody></table>
      <p className="mut">Match shows skill fit only. It does not predict selection.</p>
      {o.canApply ? <a className="btn" href={o.applyUrl} target="_blank" rel="noopener noreferrer">Apply on employer site <ExternalLink size={14}/></a> : <a className="btn" href={careersLink(o.company, o.title)} target="_blank" rel="noopener noreferrer">Find on careers page <ExternalLink size={14}/></a>}</div></section>
}

const BLANK = { company: '', title: '', category: 'INTERNSHIP', requiredSkills: '', gradYears: '', branches: '', location: '', workMode: '', stipend: '', applyUrl: '', closingDate: '', description: '', ppo: false, verified: false }
const FIELDS = [['company', 'Company *'], ['title', 'Title *'], ['requiredSkills', 'Required skills (comma separated)'], ['gradYears', 'Graduation years (e.g. 2027,2028)'], ['branches', 'Branches (e.g. cse,ai/ml)'], ['location', 'Location'], ['workMode', 'Work mode (Remote, Hybrid, Onsite)'], ['stipend', 'Stipend / salary'], ['applyUrl', 'Official apply URL']]
export function Admin() {
  const [s, setS] = useState(null), [l, setL] = useState(null), [cs, setCs] = useState([]), [f, setF] = useState(BLANK), [c, setC] = useState({ name: '', logoUrl: '', website: '', careersUrl: '' }), [busy, setBusy] = useState(false), [err, setErr] = useState(''), [msg, setMsg] = useState('')
  const load = () => { api.get('/api/sync/status').then(x => setS(x.data)); api.get('/api/admin/opportunities').then(x => setL(x.data)).catch(e => setErr(errMsg(e))); api.get('/api/companies', { params: { size: 30 } }).then(x => setCs(x.data.items)) }
  useEffect(() => { load() }, [])
  async function run() { setBusy(true); try { await api.post('/api/admin/sync'); load() } catch (e) { setErr(errMsg(e)) } finally { setBusy(false) } }
  const verify = o => api.put(`/api/admin/opportunities/${o.id}/verify`, null, { params: { verified: !o.verified } }).then(load)
  async function addOpp(e) { e.preventDefault(); setErr(''); try { await api.post('/api/admin/opportunities', { ...f, sourceId: 'manual:' + Date.now(), sourceName: 'Manual (admin)', status: 'ACTIVE', closingDate: f.closingDate || null }); setF(BLANK); setMsg('Opportunity added.'); load() } catch (x) { setErr(errMsg(x)) } }
  async function importCsv(e) { const file = e.target.files[0]; if (!file) return; const fd = new FormData(); fd.append('file', file); setMsg('Importing…'); try { const { data } = await api.post('/api/admin/companies/import', fd); setMsg(`Imported ${data.imported}, skipped ${data.skipped} (duplicates, no website, or consultancy/staffing names).`); load() } catch (x) { setErr(errMsg(x)) } }
  async function addCompany(e) { e.preventDefault(); try { await api.post('/api/admin/companies', c); setC({ name: '', logoUrl: '', website: '', careersUrl: '' }); load() } catch (x) { setErr(errMsg(x)) } }
  if (localStorage.getItem('role') !== 'ADMIN') return <p className="err">Administrators only.</p>
  return <section><h2>Admin</h2>{err && <p className="err">{err}</p>}{msg && <p className="ok-t" role="status">{msg}</p>}
    <div className="card"><div className="oh"><h3>Job synchronisation</h3><button className="btn sm" onClick={run} disabled={busy}>{busy ? 'Syncing…' : 'Run sync now'}</button></div>
      <p className="mut">Runs automatically every hour. Last success: {s?.lastSuccess ? new Date(s.lastSuccess).toLocaleString() : 'none yet'}.</p>
      <div className="tw"><table><thead><tr><th>Source</th><th>Status</th><th>New</th><th>Updated</th><th>Finished</th><th>Error</th></tr></thead>
        <tbody>{s?.recent.map((r, i) => <tr key={i}><td>{r.source}</td><td>{r.status}</td><td>{r.newCount}</td><td>{r.updatedCount}</td><td>{r.finishedAt ? new Date(r.finishedAt).toLocaleString() : ''}</td><td>{r.error}</td></tr>)}</tbody></table></div></div>
    <form className="card narrow wide" onSubmit={addOpp}><h3>Add an opportunity</h3>{FIELDS.map(([k, n]) => <label key={k}>{n}<input value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} required={k === 'company' || k === 'title'}/></label>)}
      <label>Type<select value={f.category} onChange={e => setF({ ...f, category: e.target.value })}><option value="INTERNSHIP">Internship</option><option value="FULL_TIME">Full-time</option></select></label>
      <label>Closing date<input type="date" value={f.closingDate} onChange={e => setF({ ...f, closingDate: e.target.value })}/></label>
      <label>Description<textarea value={f.description} onChange={e => setF({ ...f, description: e.target.value })}/></label>
      <label className="inl"><input type="checkbox" checked={f.ppo} onChange={e => setF({ ...f, ppo: e.target.checked })}/> PPO confirmed by the employer</label>
      <label className="inl"><input type="checkbox" checked={f.verified} onChange={e => setF({ ...f, verified: e.target.checked })}/> I confirmed the apply URL is the employer's official page</label><button className="btn">Add opportunity</button></form>
    <div className="card"><h3>Companies ({cs.length})</h3><form className="row4" onSubmit={addCompany}>{[['name', 'Name *'], ['logoUrl', 'Logo URL'], ['website', 'Website'], ['careersUrl', 'Careers URL']].map(([k, n]) => <input key={k} placeholder={n} value={c[k]} onChange={e => setC({ ...c, [k]: e.target.value })} required={k === 'name'}/>)}<button className="btn sm">Save company</button></form>
      <label className="inl">Bulk import CSV (name,website,careersUrl,country,industry) <input type="file" accept=".csv" onChange={importCsv}/></label>
      <div className="chips">{cs.map(x => <span key={x.id} className="chip">{x.name} <button className="x" aria-label={`Remove ${x.name}`} onClick={() => api.delete(`/api/admin/companies/${x.id}`).then(load)}>×</button></span>)}</div></div>
    <div className="card"><h3>Opportunities ({l?.length ?? '…'})</h3><p className="mut">Verify a role only after confirming the link is the employer's own application page.</p>
      <div className="tw"><table><thead><tr><th>Role</th><th>Source</th><th>Status</th><th>Link</th><th></th></tr></thead>
        <tbody>{l?.map(o => <tr key={o.id}><td>{o.title}<br/><span className="mut">{o.company}</span></td><td>{o.source}</td><td>{o.status}</td><td>{o.applyUrl && <a href={o.applyUrl} target="_blank" rel="noopener noreferrer">Open</a>}</td>
          <td><button className="btn ghost sm" onClick={() => verify(o)}>{o.verified ? 'Unverify' : 'Verify'}</button></td></tr>)}</tbody></table></div></div></section>
}

export function Companies() {
  const [q, setQ] = useState(''), [min, setMin] = useState(0), [pg, setPg] = useState(0), [d, setD] = useState(null), [err, setErr] = useState('')
  useEffect(() => { setD(null); const t = setTimeout(() => api.get('/api/companies/matches', { params: { q, minMatch: min, page: pg, size: 20 } }).then(x => setD(x.data)).catch(e => setErr(errMsg(e))), 250); return () => clearTimeout(t) }, [q, min, pg])
  if (err) return <p className="err">{err}</p>
  return <section><h2>Companies that fit your resume</h2>
    <p className="mut">{d ? `${d.total.toLocaleString()} companies` : '…'}{d ? `, ${d.strong} strong fit${d.strong === 1 ? '' : 's'} (70%+)` : ''}. Each company is ranked by the best role you are eligible for, using the skills found in your resume. Roles are sample roles built from typical requirements for the industry, so check the company's careers page for real openings. A high match is not a guarantee of selection.</p>
    {d && !d.hasResume && <div className="card empty"><h3>Upload your resume first</h3><p className="mut">Matching needs the skills found in your resume.</p><Link className="btn sm" to="/upload">Upload resume</Link></div>}
    <div className="bar2"><label className="srch"><Search size={16}/><input placeholder="Search companies" value={q} onChange={e => { setPg(0); setQ(e.target.value) }}/></label>
      <select value={min} onChange={e => { setPg(0); setMin(+e.target.value) }} aria-label="Minimum match"><option value={0}>Any match</option><option value={25}>25%+</option><option value={50}>50%+</option><option value={70}>70%+ (strong fit)</option></select></div>
    {!d && <><div className="skeleton tall"/><div className="skeleton tall"/></>}
    {d && d.items.length === 0 && <div className="card empty"><h3>No companies found</h3><p className="mut">Try a lower match or a different search.</p></div>}
    {d && d.items.map(c => { const [lab, tone] = fit(c.bestMatch); return <article key={c.name} className="card job"><div className="jt"><div><h3>{c.id ? <Link to={`/companies/${c.id}`}>{c.name}</Link> : c.name}</h3>
      <p className="mut">{[c.industry, c.country].filter(Boolean).join(' · ')} · {c.eligibleRoles} of {c.roles} role{c.roles === 1 ? '' : 's'} open to your year and branch</p></div>
      {c.eligibleRoles > 0 ? <div className={'pct ' + tone}><b>{c.bestMatch}%</b><span>{lab}</span></div> : <span className="chip">Not eligible</span>}</div>
      {c.bestRoleId && <><p>Best role: <Link to={`/opportunities/${c.bestRoleId}`}>{c.bestRole}</Link></p>
        <div className="tags">{c.matchedSkills.map(s => <span key={s} className="t ok">{s}</span>)}{c.missingSkills.map(s => <span key={s} className="t">{s}</span>)}</div></>}
      <div className="jf"><span className="mut sm">Green = skills you have. Grey = skills to build.</span><span className="acts">{c.website && <a className="btn ghost sm" href={c.website} target="_blank" rel="noopener noreferrer">Website</a>}
        <a className="btn sm" href={c.careersUrl || careersLink(c.name)} target="_blank" rel="noopener noreferrer">Careers <ExternalLink size={14}/></a></span></div></article> })}
    {d && d.pages > 1 && <div className="pager"><button className="btn sm" disabled={pg === 0} onClick={() => setPg(pg - 1)}>Previous</button><span>Page {pg + 1} of {d.pages}</span><button className="btn sm" disabled={pg + 1 >= d.pages} onClick={() => setPg(pg + 1)}>Next</button></div>}</section>
}

function Directory() {
  const [q, setQ] = useState(''), [pg, setPg] = useState(0), [d, setD] = useState(null)
  useEffect(() => { const t = setTimeout(() => api.get('/api/companies', { params: { q, page: pg, size: 20 } }).then(x => setD(x.data)).catch(() => setD({ items: [], total: 0, pages: 0 })), 250); return () => clearTimeout(t) }, [q, pg])
  return <div className="dir"><h3>Company directory</h3><p className="mut">{d ? d.total.toLocaleString() : '…'} companies in the database. Being listed does not mean a role is open; open roles appear above and under Opportunities.</p>
    <label className="srch"><input placeholder="Search companies" value={q} onChange={e => { setPg(0); setQ(e.target.value) }}/></label>
    {d && d.items.map(c => <div key={c.id} className="card row-c"><div><b><Link to={`/companies/${c.id}`}>{c.name}</Link></b><p className="mut">{[c.industry, c.country].filter(Boolean).join(' · ') || 'No details'}</p></div>
      <span className="acts"><Link className="btn sm" to={`/companies/${c.id}`}>Skills and roles</Link>{c.website && <a className="btn ghost sm" href={c.website} target="_blank" rel="noopener noreferrer">Website</a>}{c.careersUrl && <a className="btn sm" href={c.careersUrl} target="_blank" rel="noopener noreferrer">Careers page</a>}</span></div>)}
    {d && d.items.length === 0 && <div className="card empty"><p className="mut">No companies found.</p></div>}
    {d && d.pages > 1 && <div className="pager"><button className="btn sm" disabled={pg === 0} onClick={() => setPg(pg - 1)}>Previous</button><span>Page {pg + 1} of {d.pages.toLocaleString()}</span><button className="btn sm" disabled={pg + 1 >= d.pages} onClick={() => setPg(pg + 1)}>Next</button></div>}</div>
}

export function CompanyDetail() {
  const { id } = useParams(), [c, setC] = useState(null), [err, setErr] = useState('')
  useEffect(() => { setC(null); api.get(`/api/companies/${id}`).then(x => setC(x.data)).catch(e => setErr(errMsg(e))) }, [id])
  if (err) return <p className="err">{err}</p>
  if (!c) return <div className="skeleton tall"/>
  const chips = (a, b) => <div className="chips">{a.map(s => <span key={s} className="chip ok">{s}</span>)}{b.map(s => <span key={s} className="chip">{s}</span>)}</div>
  return <section><Link to="/companies">Back to companies</Link>
    <div className="card"><h2>{c.name}</h2><p className="mut">{[c.industry, c.country].filter(Boolean).join(' · ') || 'No details'}</p>
      <span className="acts">{c.website && <a className="btn ghost sm" href={c.website} target="_blank" rel="noopener noreferrer">Website</a>}{c.careersUrl && <a className="btn sm" href={c.careersUrl} target="_blank" rel="noopener noreferrer">Official careers page</a>}</span></div>
    <h3>Open roles and the skills they require</h3>
    {c.roles.length === 0 ? <div className="card empty"><p className="mut">No open roles for this company are in the database yet, so its exact skill requirements are not known. Check its careers page, or ask an admin to add its roles.</p></div>
      : c.roles.map(r => <article key={r.id} className="card opp"><div className="oh"><div><h3><Link to={`/opportunities/${r.id}`}>{r.title}</Link></h3><p className="mut">{r.category === 'INTERNSHIP' ? 'Internship' : 'Full-time'}{r.location ? ` · ${r.location}` : ''}{r.stipend ? ` · ${r.stipend}` : ''}</p></div><div className="pct"><b>{r.matchPercent}%</b><span>match</span></div></div>{chips(r.matchedSkills, r.missingSkills)}</article>)}
    <div className="card"><h3>Typical skills in this field</h3><p className="mut">General guidance for this industry, not this employer's published requirements. Green are skills found in your resume.</p>{chips(c.typicalHave, c.typicalMissing)}</div></section>
}
