import { useEffect, useState } from 'react'
import './index.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function App() {
  const [products, setProducts] = useState([])
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(null)

  const loadInventory = () => {
    setLoadError(null)
    fetch(`${API_BASE}/api/inventory`)
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load inventory (${res.status})`)
        return res.json()
      })
      .then((data) => {
        setProducts(data)
        if (data.length > 0 && !productId) {
          setProductId(data[0].productId)
        }
      })
      .catch((err) => setLoadError(err.message))
  }

  useEffect(() => {
    loadInventory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setResult(null)
    try {
      const res = await fetch(`${API_BASE}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })
      const data = await res.json()
      setResult(data)
      // Refresh stock numbers in the dropdown after the order attempt.
      loadInventory()
    } catch (err) {
      setResult({ status: 'ERROR', reason: err.message, inventory: null })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>Place an Order</h1>

        {loadError && (
          <p style={styles.loadError}>
            Couldn't load inventory: {loadError}. Is the backend running on {API_BASE}?
          </p>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <label style={styles.label}>
            Product
            <select
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
              style={styles.input}
              required
            >
              {products.length === 0 && <option value="">No products loaded</option>}
              {products.map((p) => (
                <option key={p.productId} value={p.productId}>
                  {p.productId} — {p.name} ({p.stock} in stock)
                </option>
              ))}
            </select>
          </label>

          <label style={styles.label}>
            Quantity
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              style={styles.input}
              required
            />
          </label>

          <button type="submit" disabled={loading || !productId} style={styles.button}>
            {loading ? 'Submitting…' : 'Submit Order'}
          </button>
        </form>

        {result && (
          <div
            style={{
              ...styles.result,
              borderColor: result.status === 'CONFIRMED' ? '#2e7d32' : '#c62828',
              background: result.status === 'CONFIRMED' ? '#eaf6ea' : '#fdecea',
            }}
          >
            <p style={styles.resultStatus}>{result.status}</p>
            {result.reason && <p style={styles.resultReason}>{result.reason}</p>}
            {result.inventory && (
              <p style={styles.resultInventory}>
                {result.inventory.name} ({result.inventory.productId}) — {result.inventory.stock} remaining
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

const styles = {
  page: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: '#f4f5f7',
    fontFamily: 'system-ui, sans-serif',
  },
  card: {
    background: '#fff',
    borderRadius: 12,
    boxShadow: '0 2px 10px rgba(0,0,0,0.08)',
    padding: '32px 36px',
    width: 380,
  },
  title: { margin: '0 0 20px', fontSize: 22 },
  loadError: { color: '#c62828', fontSize: 13, marginBottom: 12 },
  form: { display: 'flex', flexDirection: 'column', gap: 16 },
  label: { display: 'flex', flexDirection: 'column', gap: 6, fontSize: 14, fontWeight: 600 },
  input: {
    padding: '8px 10px',
    borderRadius: 8,
    border: '1px solid #ccc',
    fontSize: 14,
  },
  button: {
    marginTop: 8,
    padding: '10px 16px',
    borderRadius: 8,
    border: 'none',
    background: '#3f51b5',
    color: '#fff',
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
  },
  result: {
    marginTop: 20,
    padding: '14px 16px',
    borderRadius: 8,
    border: '1px solid',
  },
  resultStatus: { margin: 0, fontWeight: 700, fontSize: 16 },
  resultReason: { margin: '6px 0 0', fontSize: 13 },
  resultInventory: { margin: '6px 0 0', fontSize: 13, opacity: 0.8 },
}

export default App
