import { reactive } from 'vue'

export const session = reactive({ user: null, expiresAt: null, loaded: false })

export async function request(path, options = {}) {
  let response
  try {
    response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000), ...options })
  } catch {
    throw Object.assign(new Error('连接暂时不可用，请稍后重试。'), { status: 0 })
  }
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) throw Object.assign(new Error(body?.message || '操作未完成，请稍后重试。'), { status: response.status, code: body?.code })
  return body
}

function setSession(value) {
  session.user = value?.user || null
  session.expiresAt = value?.expiresAt || null
  session.loaded = true
}

export async function ensureSession() {
  if (session.loaded) return session
  try { setSession(await request('/api/user/auth/me')) }
  catch (error) { if (error.status === 401) setSession(null); else throw error }
  return session
}

export async function mutation(path, body, method = 'POST') {
  const csrf = await request('/api/user/auth/csrf')
  return request(path, { method, headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: body ? JSON.stringify(body) : undefined })
}

export async function login(credentials) {
  const result = await mutation('/api/user/auth/login', credentials)
  setSession(result)
  return result
}

export async function logout() {
  try { await mutation('/api/user/auth/logout') }
  catch (error) { if (error.status !== 401) throw error }
  setSession(null)
}

export function clearSession() { setSession(null) }
