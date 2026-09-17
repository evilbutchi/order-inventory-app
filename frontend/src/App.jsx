import { useEffect, useState } from 'react'
import './index.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const LOW_STOCK_THRESHOLD = 5

function App() {
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])
  const [cart, setCart] = useState([])
  const [addProductId, setAddProductId] = useState('')
  const [lastResult, setLastResult] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)
  const [loadError, setLoadError] = useState(null)

  const loadInventory = () =>
    fetch(`${API_BASE}/api/inventory`)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`Failed to load inventory (${res.status})`)
        }

        return res.json()
      })
      .then((data) => {
        setProducts(data)
        setLoadError(null)

        if (data.length > 0 && !addProductId) {
          setAddProductId(data[0].productId)
        }
      })
      .catch((err) => setLoadError(err.message))

  const loadOrders = () =>
    fetch(`${API_BASE}/api/orders`)
      .then((res) => (res.ok ? res.json() : []))
      .then(setOrders)
      .catch(() => {})

  const loadNotifications = () =>
    fetch(`${API_BASE}/api/notifications`)
      .then((res) => (res.ok ? res.json() : []))
      .then(setNotifications)
      .catch(() => {})

  const refreshAll = () => {
    loadInventory()
    loadOrders()
    loadNotifications()
  }

  useEffect(() => {
    refreshAll()

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stockFor = (productId) =>
    products.find((p) => p.productId === productId)?.stock ?? 0

  const addLineToCart = () => {
    if (!addProductId) return

    setCart((prev) => {
      const existing = prev.find(
        (line) => line.productId === addProductId
      )

      if (existing) {
        return prev.map((line) =>
          line.productId === addProductId
            ? {
                ...line,
                quantity: line.quantity + 1,
              }
            : line
        )
      }

      return [
        ...prev,
        {
          productId: addProductId,
          quantity: 1,
        },
      ]
    })
  }

  const updateLineQuantity = (productId, quantity) => {
    const qty = Math.max(1, Number(quantity) || 1)

    setCart((prev) =>
      prev.map((line) =>
        line.productId === productId
          ? {
              ...line,
              quantity: qty,
            }
          : line
      )
    )
  }

  const removeLine = (productId) => {
    setCart((prev) =>
      prev.filter((line) => line.productId !== productId)
    )
  }

  const submitOrder = async () => {
    if (cart.length === 0) return

    setSubmitting(true)
    setLastResult(null)

    try {
      const res = await fetch(`${API_BASE}/api/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          items: cart.map(({ productId, quantity }) => ({
            productId,
            quantity,
          })),
        }),
      })

      const data = await res.json()

      setLastResult(data)

      if (data.status === 'CONFIRMED') {
        setCart([])
      }

      refreshAll()
    } catch (err) {
      setLastResult({
        status: 'ERROR',
        reason: err.message,
      })
    } finally {
      setSubmitting(false)
    }
  }

  const cancelOrder = async (orderId) => {
    setCancellingId(orderId)

    try {
      const res = await fetch(
        `${API_BASE}/api/orders/${orderId}/cancel`,
        {
          method: 'POST',
        }
      )

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))

        throw new Error(
          err.reason || `Cancel failed (${res.status})`
        )
      }

      refreshAll()
    } catch (err) {
      setLastResult({
        status: 'ERROR',
        reason: err.message,
      })
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.layout}>
        <h1 style={styles.pageTitle}>
          Order &amp; Inventory
        </h1>

        {loadError && (
          <p style={styles.loadError}>
            Couldn't load inventory: {loadError}. Is the backend
            running on {API_BASE}?
          </p>
        )}

        <div style={styles.grid}>
          {/* ── Cart / place order ── */}
          <section style={styles.card}>
            <h2 style={styles.cardTitle}>
              Build an order
            </h2>

            <div style={styles.addLineRow}>
              <select
                value={addProductId}
                onChange={(e) =>
                  setAddProductId(e.target.value)
                }
                style={styles.input}
              >
                {products.length === 0 && (
                  <option value="">
                    No products loaded
                  </option>
                )}

                {products.map((p) => (
                  <option
                    key={p.productId}
                    value={p.productId}
                  >
                    {p.productId} — {p.name} ({p.stock} in
                    stock)
                  </option>
                ))}
              </select>

              <button
                type="button"
                onClick={addLineToCart}
                style={styles.secondaryButton}
                disabled={!addProductId}
              >
                Add to cart
              </button>
            </div>

            {cart.length === 0 ? (
              <p style={styles.emptyState}>
                Cart is empty — add a product above.
              </p>
            ) : (
              <ul style={styles.cartList}>
                {cart.map((line) => {
                  const product = products.find(
                    (p) => p.productId === line.productId
                  )

                  return (
                    <li
                      key={line.productId}
                      style={styles.cartLine}
                    >
                      <span style={styles.cartLineName}>
                        {line.productId} —{' '}
                        {product?.name || 'Unknown product'}
                      </span>

                      <input
                        type="number"
                        min="1"
                        value={line.quantity}
                        onChange={(e) =>
                          updateLineQuantity(
                            line.productId,
                            e.target.value
                          )
                        }
                        style={styles.qtyInput}
                      />

                      <button
                        type="button"
                        onClick={() =>
                          removeLine(line.productId)
                        }
                        style={styles.removeButton}
                        aria-label={`Remove ${line.productId}`}
                      >
                        ✕
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}

            <button
              type="button"
              onClick={submitOrder}
              disabled={
                submitting || cart.length === 0
              }
              style={styles.button}
            >
              {submitting
                ? 'Submitting…'
                : 'Submit order'}
            </button>

            {lastResult && (
              <div
                style={{
                  ...styles.result,
                  borderColor:
                    lastResult.status === 'CONFIRMED'
                      ? '#2e7d32'
                      : '#c62828',
                  background:
                    lastResult.status === 'CONFIRMED'
                      ? '#eaf6ea'
                      : '#fdecea',
                }}
              >
                <p style={styles.resultStatus}>
                  {lastResult.status}
                </p>

                {lastResult.reason && (
                  <p style={styles.resultReason}>
                    {lastResult.reason}
                  </p>
                )}

                {lastResult.items &&
                  lastResult.items.length > 0 && (
                    <ul style={styles.resultItems}>
                      {lastResult.items.map((it) => (
                        <li key={it.productId}>
                          {it.productId}: {it.outcome}
                        </li>
                      ))}
                    </ul>
                  )}
              </div>
            )}
          </section>

          {/* ── Live inventory ── */}
          <section style={styles.card}>
            <h2 style={styles.cardTitle}>
              Inventory
            </h2>

            <div style={styles.tableWrapper}>
              <table style={styles.table}>
                <thead>
                  <tr>
                    <th style={styles.th}>
                      Product
                    </th>
                    <th style={styles.th}>
                      Name
                    </th>
                    <th style={styles.th}>
                      Stock
                    </th>
                  </tr>
                </thead>

                <tbody>
                  {products.map((p) => {
                    const low =
                      p.stock < LOW_STOCK_THRESHOLD

                    return (
                      <tr
                        key={p.productId}
                        style={
                          low
                            ? styles.lowStockRow
                            : undefined
                        }
                      >
                        <td style={styles.td}>
                          {p.productId}
                        </td>

                        <td style={styles.td}>
                          {p.name}
                        </td>

                        <td style={styles.td}>
                          {p.stock}

                          {low && (
                            <span
                              style={
                                styles.lowStockTag
                              }
                            >
                              low
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </section>

          {/* ── Order history ── */}
          <section style={styles.card}>
            <h2 style={styles.cardTitle}>
              Order history
            </h2>

            {orders.length === 0 ? (
              <p style={styles.emptyState}>
                No orders yet.
              </p>
            ) : (
              <ul style={styles.orderList}>
                {orders.map((o) => (
                  <li
                    key={o.orderId}
                    style={styles.orderRow}
                  >
                    <div style={styles.orderContent}>
                      <span style={styles.orderId}>
                        #{o.orderId}
                      </span>{' '}

                      <span
                        style={{
                          ...styles.statusTag,
                          color: statusColor(
                            o.status
                          ),
                        }}
                      >
                        {o.status}
                      </span>

                      <ul
                        style={
                          styles.orderItemsList
                        }
                      >
                        {o.items.map((it, idx) => (
                          <li key={idx}>
                            {it.productId} ×{' '}
                            {it.quantity}
                          </li>
                        ))}
                      </ul>

                      {o.reason && (
                        <p
                          style={
                            styles.orderReason
                          }
                        >
                          {o.reason}
                        </p>
                      )}
                    </div>

                    {o.status === 'CONFIRMED' && (
                      <button
                        type="button"
                        onClick={() =>
                          cancelOrder(o.orderId)
                        }
                        disabled={
                          cancellingId ===
                          o.orderId
                        }
                        style={
                          styles.cancelButton
                        }
                      >
                        {cancellingId === o.orderId
                          ? 'Cancelling…'
                          : 'Cancel'}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* ── Notification feed ── */}
          <section style={styles.card}>
            <h2 style={styles.cardTitle}>
              Activity feed
            </h2>

            {notifications.length === 0 ? (
              <p style={styles.emptyState}>
                No notifications yet.
              </p>
            ) : (
              <ul style={styles.feedList}>
                {notifications.map((n) => (
                  <li
                    key={n.notificationId}
                    style={styles.feedItem}
                  >
                    {n.message}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </div>
  )
}

function statusColor(status) {
  if (status === 'CONFIRMED') return '#2e7d32'
  if (status === 'CANCELLED') return '#8a6d00'
  return '#c62828'
}

const styles = {
  page: {
    minHeight: '100vh',
    background: '#f4f5f7',
    fontFamily: 'system-ui, sans-serif',
    padding: '32px 16px',
    boxSizing: 'border-box',
    overflowX: 'hidden',
  },

  layout: {
    maxWidth: 1080,
    margin: '0 auto',
    width: '100%',
    boxSizing: 'border-box',
  },

  pageTitle: {
    margin: '0 0 20px',
    fontSize: 26,
    color: 'var(--maroon)',
  },

  loadError: {
    color: '#c62828',
    fontSize: 13,
    marginBottom: 16,
  },

  grid: {
    display: 'grid',
    gridTemplateColumns:
      'repeat(auto-fit, minmax(min(320px, 100%), 1fr))',
    gap: 20,
    width: '100%',
    boxSizing: 'border-box',
  },

  card: {
    background: '#fff',
    borderRadius: 12,
    boxShadow:
      '0 2px 10px rgba(0,0,0,0.08)',
    padding: '24px 26px',
    boxSizing: 'border-box',
    minWidth: 0,
    overflow: 'hidden',
  },

  cardTitle: {
    margin: '0 0 16px',
    fontSize: 18,
  },

  addLineRow: {
    display: 'flex',
    gap: 8,
    marginBottom: 16,
    width: '100%',
    minWidth: 0,
    boxSizing: 'border-box',
    alignItems: 'stretch',
  },

  input: {
    flex: '1 1 auto',
    minWidth: 0,
    width: 0,
    boxSizing: 'border-box',
    padding: '8px 10px',
    borderRadius: 8,
    border: '1px solid #ccc',
    fontSize: 14,
    background: '#fff',
  },

  secondaryButton: {
    flex: '0 0 auto',
    padding: '8px 14px',
    borderRadius: 8,
    border: '1px solid var(--maroon)',
    background: '#fff',
    color: 'var(--maroon)',
    fontSize: 14,
    fontWeight: 600,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },

  emptyState: {
    color: '#888',
    fontSize: 13,
  },

  cartList: {
    listStyle: 'none',
    padding: 0,
    margin: '0 0 16px',
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
    width: '100%',
  },

  cartLine: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontSize: 14,
    width: '100%',
    minWidth: 0,
    boxSizing: 'border-box',
  },

  cartLineName: {
    flex: '1 1 auto',
    minWidth: 0,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },

  qtyInput: {
    flex: '0 0 60px',
    width: 60,
    boxSizing: 'border-box',
    padding: '6px 8px',
    borderRadius: 6,
    border: '1px solid #ccc',
  },

  removeButton: {
    flex: '0 0 auto',
    width: 28,
    height: 28,
    border: 'none',
    background: 'transparent',
    color: '#c62828',
    fontSize: 14,
    cursor: 'pointer',
    padding: 0,
  },

  button: {
    width: '100%',
    padding: '10px 16px',
    borderRadius: 8,
    border: 'none',
    background: 'var(--maroon)',
    color: '#fff',
    fontSize: 15,
    fontWeight: 600,
    cursor: 'pointer',
    boxSizing: 'border-box',
  },

  result: {
    marginTop: 16,
    padding: '14px 16px',
    borderRadius: 8,
    border: '1px solid',
    boxSizing: 'border-box',
  },

  resultStatus: {
    margin: 0,
    fontWeight: 700,
    fontSize: 16,
  },

  resultReason: {
    margin: '6px 0 0',
    fontSize: 13,
  },

  resultItems: {
    margin: '8px 0 0',
    paddingLeft: 18,
    fontSize: 13,
  },

  tableWrapper: {
    width: '100%',
    overflowX: 'auto',
    boxSizing: 'border-box',
  },

  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: 14,
  },

  th: {
    textAlign: 'left',
    borderBottom: '2px solid #eee',
    padding: '6px 4px',
  },

  td: {
    borderBottom: '1px solid #f0f0f0',
    padding: '6px 4px',
  },

  lowStockRow: {
    background: '#fdecea',
  },

  lowStockTag: {
    marginLeft: 8,
    fontSize: 11,
    fontWeight: 700,
    color: '#c62828',
    border: '1px solid #c62828',
    borderRadius: 4,
    padding: '1px 5px',
  },

  orderList: {
    listStyle: 'none',
    padding: 0,
    margin: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: 14,
  },

  orderRow: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    gap: 12,
    borderBottom: '1px solid #f0f0f0',
    paddingBottom: 12,
    fontSize: 14,
    minWidth: 0,
  },

  orderContent: {
    minWidth: 0,
    flex: '1 1 auto',
  },

  orderId: {
    fontWeight: 700,
  },

  statusTag: {
    fontWeight: 700,
    fontSize: 12,
  },

  orderItemsList: {
    margin: '6px 0 0',
    paddingLeft: 18,
    fontSize: 13,
    color: '#555',
  },

  orderReason: {
    margin: '6px 0 0',
    fontSize: 12,
    color: '#888',
  },

  cancelButton: {
    flex: '0 0 auto',
    padding: '6px 12px',
    borderRadius: 6,
    border: '1px solid #c62828',
    background: '#fff',
    color: '#c62828',
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },

  feedList: {
    listStyle: 'none',
    padding: 0,
    margin: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
    maxHeight: 320,
    overflowY: 'auto',
  },

  feedItem: {
    fontSize: 13,
    padding: '8px 10px',
    background: '#fffaf0',
    borderRadius: 6,
    border: '1px solid #f0e6d2',
  },
}

export default App