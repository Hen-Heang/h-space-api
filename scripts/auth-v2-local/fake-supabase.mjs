#!/usr/bin/env node
// DEV-ONLY synthetic Supabase for running the Auth V2 pilot locally
// (docs/auth-v2.md "Run it locally"). Not part of any build or image.
//
// Binds to 127.0.0.1 only. Generates a fresh ES256 key pair in memory on every
// start (nothing is written to disk) and serves:
//   GET  /auth/v1/.well-known/jwks.json      the public key (what hub-api fetches)
//   GET  /rest/v1/kori_app_access?...        the caller's admission row, like the Data API
//   POST /__mint?sub=<uuid>&aal=aal1|aal2     a synthetic access token (loopback only)
//   POST /__admission?user=<uuid>&status=approved|suspended|none
//   POST /__outage?on=true|false              make the admission read answer 503
//
// hub-api verifies these tokens exactly as it would Supabase's (signature,
// issuer, audience, exp); this stub replaces the issuer, not the checks. It
// accepts only the synthetic publishable key below - never use a real one.
//
//   node scripts/auth-v2-local/fake-supabase.mjs            # port 54329
//   PORT=54330 node scripts/auth-v2-local/fake-supabase.mjs
import { createServer } from "node:http"
import { createPrivateKey, generateKeyPairSync, randomUUID, sign } from "node:crypto"

const PORT = Number(process.env.PORT ?? 54329)
const ISSUER = `http://127.0.0.1:${PORT}/auth/v1`
export const SYNTHETIC_PUBLISHABLE_KEY = "sb_publishable_local_synthetic_only"
const KID = `local-${randomUUID().slice(0, 8)}`

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" })
const publicJwk = { ...publicKey.export({ format: "jwk" }), kid: KID, alg: "ES256", use: "sig" }
const signer = createPrivateKey(privateKey.export({ format: "pem", type: "pkcs8" }))

const admission = new Map() // user id -> "approved" | "suspended"
let outage = false

const b64url = (value) => Buffer.from(value).toString("base64url")
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

function mint(sub, aal) {
  const now = Math.floor(Date.now() / 1000)
  const header = b64url(JSON.stringify({ alg: "ES256", typ: "JWT", kid: KID }))
  const payload = b64url(
    JSON.stringify({
      iss: ISSUER,
      aud: "authenticated",
      sub,
      role: "authenticated",
      aal,
      iat: now,
      exp: now + 15 * 60,
      session_id: randomUUID(),
    }),
  )
  const signature = sign("sha256", Buffer.from(`${header}.${payload}`), {
    key: signer,
    dsaEncoding: "ieee-p1363",
  })
  return `${header}.${payload}.${b64url(signature)}`
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" })
  res.end(JSON.stringify(body))
}

// Browsers call the Data API directly (like real Supabase): answer CORS for
// local dev origins only.
const DEV_ORIGINS = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/

createServer((req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${PORT}`)
  const origin = req.headers.origin
  if (origin && DEV_ORIGINS.test(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin)
    res.setHeader("Vary", "Origin")
    res.setHeader("Access-Control-Allow-Headers", "apikey, authorization, content-type, x-client-info, accept-profile, prefer")
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
  }
  if (req.method === "OPTIONS") {
    res.writeHead(204)
    return res.end()
  }
  const q = url.searchParams

  if (req.method === "GET" && url.pathname === "/auth/v1/.well-known/jwks.json") {
    return send(res, 200, { keys: [publicJwk] })
  }

  if (req.method === "GET" && url.pathname === "/rest/v1/kori_app_access") {
    if (req.headers.apikey !== SYNTHETIC_PUBLISHABLE_KEY) return send(res, 401, { message: "bad apikey" })
    if (!String(req.headers.authorization ?? "").startsWith("Bearer ")) {
      return send(res, 401, { message: "no bearer" })
    }
    if (outage) return send(res, 503, { message: "synthetic outage" })
    const user = (q.get("user_id") ?? "").replace(/^eq\./, "")
    const status = admission.get(user)
    return send(res, 200, status ? [{ status }] : [])
  }

  if (req.method === "POST" && url.pathname === "/__mint") {
    const sub = q.get("sub") ?? ""
    const aal = q.get("aal") === "aal2" ? "aal2" : "aal1"
    if (!UUID.test(sub)) return send(res, 400, { message: "sub must be a uuid" })
    return send(res, 200, { access_token: mint(sub, aal) })
  }

  if (req.method === "POST" && url.pathname === "/__admission") {
    const user = q.get("user") ?? ""
    const status = q.get("status")
    if (!UUID.test(user)) return send(res, 400, { message: "user must be a uuid" })
    if (status === "none") admission.delete(user)
    else if (status === "approved" || status === "suspended") admission.set(user, status)
    else return send(res, 400, { message: "status: approved | suspended | none" })
    return send(res, 200, { user, status })
  }

  if (req.method === "POST" && url.pathname === "/__outage") {
    outage = q.get("on") === "true"
    return send(res, 200, { outage })
  }

  send(res, 404, { message: "not found" })
}).listen(PORT, "127.0.0.1", () => {
  console.log(`fake-supabase (synthetic, dev only) on ${ISSUER.replace("/auth/v1", "")}`)
  console.log(`  HUB_SUPABASE_URL=http://127.0.0.1:${PORT}`)
  console.log(`  HUB_SUPABASE_PUBLISHABLE_KEY=${SYNTHETIC_PUBLISHABLE_KEY}`)
})
