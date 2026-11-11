import { useCallback, useEffect, useState } from 'react'
import './App.css'

type Cve = {
  id: string
  description: string
  severity: string
  cvssScore: number | null
  published: string | null
  version: number | null
}
type CvePage = { items: Cve[]; offset: number; limit: number }
type LogEntry = { t: string; method: string; url: string; status: number; cache: string | null; ms: number }

const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const EMPTY: Cve = { id: '', description: '', severity: 'HIGH', cvssScore: null, published: null, version: null }

function App() {
  const [items, setItems] = useState<Cve[]>([])
  const [offset, setOffset] = useState(0)
  const [limit, setLimit] = useState(10)
  const [search, setSearch] = useState('')
  const [form, setForm] = useState<Cve>(EMPTY)
  const [editing, setEditing] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)
  const [log, setLog] = useState<LogEntry[]>([])
  const [loading, setLoading] = useState(false)

  // Toate cererile trec prin /api -> Vite -> smart proxy (:8080) -> DW -> Postgres
  const call = useCallback(async (method: string, url: string, body?: unknown) => {
    const t0 = performance.now()
    try {
      const res = await fetch('/api' + url, {
        method,
        headers: body ? { 'Content-Type': 'application/json', Accept: 'application/json' } : { Accept: 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
      })
      const text = await res.text()
      setLog((l) => [
        { t: new Date().toLocaleTimeString(), method, url, status: res.status, cache: res.headers.get('X-Cache'), ms: Math.round(performance.now() - t0) },
        ...l.slice(0, 29),
      ])
      if (!res.ok) throw new Error(`${res.status}: ${text || res.statusText}`)
      return text ? JSON.parse(text) : null
    } catch (e) {
      if (e instanceof TypeError) {
        setLog((l) => [{ t: new Date().toLocaleTimeString(), method, url, status: 0, cache: null, ms: 0 }, ...l.slice(0, 29)])
        throw new Error('Smart proxy nu răspunde (pornit pe :8080?)')
      }
      throw e
    }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const page: CvePage = await call('GET', `/cves/?offset=${offset}&limit=${limit}`)
      setItems(page.items)
    } catch (e) {
      setItems([])
      setMsg({ kind: 'err', text: (e as Error).message })
    } finally {
      setLoading(false)
    }
  }, [call, offset, limit])

  useEffect(() => { void load() }, [load])

  async function find() {
    const id = search.trim().toUpperCase()
    if (!id) return load()
    try {
      const cve: Cve = await call('GET', `/cve/?id=${encodeURIComponent(id)}`)
      setItems([cve])
      setMsg(null)
    } catch (e) {
      setItems([])
      setMsg({ kind: 'err', text: (e as Error).message })
    }
  }

  function edit(c: Cve) {
    setForm(c)
    setEditing(true)
    setMsg(null)
  }

  function reset() {
    setForm(EMPTY)
    setEditing(false)
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    const payload = { ...form, cvssScore: form.cvssScore, published: form.published || null }
    try {
      if (editing) {
        await call('POST', `/cve/?id=${encodeURIComponent(form.id)}`, payload)
        setMsg({ kind: 'ok', text: `${form.id} actualizat` })
      } else {
        await call('PUT', '/cve/', { ...payload, version: null })
        setMsg({ kind: 'ok', text: `${form.id} adăugat` })
      }
      reset()
      setSearch('')
      await load()
    } catch (e) {
      setMsg({ kind: 'err', text: (e as Error).message })
    }
  }

  const set = <K extends keyof Cve>(k: K, v: Cve[K]) => setForm((f) => ({ ...f, [k]: v }))

  return (
    <main className="app">
      <div className="top">
        <h1>CVE database</h1>
      </div>

      {msg && <div className={`toast ${msg.kind}`}>{msg.text}</div>}

      <div className="grid">
        <section className="card">
          <h2>Vulnerabilități</h2>
          <div className="toolbar">
            <input placeholder="CVE-2021-44228" value={search} onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void find()} />
            <button onClick={() => void find()}>Caută după id</button>
            <button onClick={() => { setSearch(''); void load() }}>Reîncarcă</button>
            <select value={limit} onChange={(e) => { setOffset(0); setLimit(Number(e.target.value)) }}>
              {[5, 10, 25, 50, 100].map((n) => <option key={n} value={n}>{n} / pagină</option>)}
            </select>
          </div>

          <div className="list">
          <table>
            <thead><tr><th>ID</th><th>Descriere</th><th>Severitate</th><th>CVSS</th><th>Publicat</th><th /></tr></thead>
            <tbody>
              {items.map((c) => (
                <tr key={c.id} className={editing && form.id === c.id ? 'sel' : ''}>
                  <td className="id">{c.id}</td>
                  <td className="desc">{c.description}</td>
                  <td><span className={`badge sev-${c.severity}`}>{c.severity}</span></td>
                  <td>{c.cvssScore ?? '—'}</td>
                  <td>{c.published ?? '—'}</td>
                  <td><button className="link" onClick={() => edit(c)}>Editează</button></td>
                </tr>
              ))}
              {!items.length && <tr><td colSpan={6} className="empty">{loading ? 'Se încarcă…' : 'Nimic de afișat'}</td></tr>}
            </tbody>
          </table>
          </div>

          <div className="pager">
            <button disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - limit))}>← Înapoi</button>
            <button disabled={items.length < limit} onClick={() => setOffset(offset + limit)}>Înainte →</button>
            <span>offset {offset}</span>
          </div>
        </section>

        <section className="card">
          <h2>{editing ? `Editează ${form.id}` : 'Adaugă CVE'}</h2>
          <form onSubmit={(e) => void submit(e)}>
            <label>ID
              <input required pattern="CVE-\d{4}-\d{4,}" placeholder="CVE-2024-1234" disabled={editing}
                value={form.id} onChange={(e) => set('id', e.target.value.toUpperCase())} />
            </label>
            <label>Descriere
              <textarea required rows={6} value={form.description} onChange={(e) => set('description', e.target.value)} />
            </label>
            <div className="row">
              <label>Severitate
                <select value={form.severity} onChange={(e) => set('severity', e.target.value)}>
                  {SEVERITIES.map((s) => <option key={s}>{s}</option>)}
                </select>
              </label>
              <label>CVSS (0–10)
                <input type="number" min={0} max={10} step={0.1} value={form.cvssScore ?? ''}
                  onChange={(e) => set('cvssScore', e.target.value === '' ? null : Number(e.target.value))} />
              </label>
            </div>
            <label>Data publicării
              <input type="date" value={form.published ?? ''} onChange={(e) => set('published', e.target.value || null)} />
            </label>
            <div className="actions">
              <button className="primary" type="submit">{editing ? 'Salvează' : 'Adaugă'}</button>
              {editing && <button type="button" onClick={reset}>Anulează</button>}
            </div>
          </form>
        </section>
      </div>

      <section className="card">
        <h2>Jurnal cereri (vezi HIT/MISS din cache-ul Redis)</h2>
        <div className="log">
          {log.map((l, i) => (
            <div key={i}>
              <span>{l.t}</span>
              <b>{l.method}</b>
              <span>{l.url}</span>
              <span className={l.status >= 200 && l.status < 300 ? 'ok' : 'bad'}>{l.status || 'ERR'}</span>
              {l.cache && <span className={`badge cache-${l.cache}`}>{l.cache}</span>}
              <span>{l.ms} ms</span>
            </div>
          ))}
          {!log.length && <span>Încă nicio cerere</span>}
        </div>
      </section>
    </main>
  )
}

export default App
