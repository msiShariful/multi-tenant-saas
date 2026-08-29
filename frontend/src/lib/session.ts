import "server-only";

import { cookies } from "next/headers";
import { EncryptJWT, jwtDecrypt } from "jose";

/**
 * The browser's half of the session. It holds the auth-service token pair, so it is encrypted
 * (JWE) rather than signed: a signed JWT's payload is only base64, and anyone who could read the
 * cookie could lift the 30-day refresh token straight out of it. Encrypting makes the value
 * opaque, which is what frontend/README.md promised.
 */
export type Session = {
  accessToken: string;
  refreshToken: string;
  /** Epoch millis the access token stops being usable. Not yet acted on -- see the README's Phase 1. */
  accessTokenExpiresAt: number;
  tenantSlug: string;
  email: string;
};

const COOKIE_NAME = "tb_session";

// The refresh token is valid for 30 days, so the cookie outliving it would only produce a session
// that looks alive and cannot be rotated.
const SESSION_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

/**
 * Read at call time rather than at module scope: a missing secret should fail the request that
 * needs it, not the whole build, and Next evaluates modules during `next build`.
 */
function key(): Uint8Array {
  const secret = process.env.SESSION_SECRET;
  if (!secret) {
    throw new Error(
      "SESSION_SECRET is not set. Generate one with `openssl rand -base64 32` and put it in frontend/.env.local",
    );
  }
  // A256GCM needs exactly 32 bytes; base64 of 32 random bytes is the documented way to produce it.
  const bytes = Buffer.from(secret, "base64");
  if (bytes.length !== 32) {
    throw new Error(
      `SESSION_SECRET must be 32 bytes base64-encoded (got ${bytes.length}). Generate one with \`openssl rand -base64 32\`.`,
    );
  }
  return new Uint8Array(bytes);
}

export async function createSession(session: Session): Promise<void> {
  const jwe = await new EncryptJWT({ ...session })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setIssuedAt()
    .setExpirationTime(`${SESSION_MAX_AGE_SECONDS}s`)
    .encrypt(key());

  (await cookies()).set(COOKIE_NAME, jwe, {
    httpOnly: true, // the whole point: JavaScript cannot read this, so XSS cannot exfiltrate the pair
    secure: process.env.NODE_ENV === "production", // localhost is http, so this cannot be unconditional
    sameSite: "lax", // partial CSRF cover only -- see the note in frontend/README.md
    path: "/",
    maxAge: SESSION_MAX_AGE_SECONDS,
  });
}

export async function getSession(): Promise<Session | null> {
  const cookie = (await cookies()).get(COOKIE_NAME)?.value;
  if (!cookie) return null;

  try {
    const { payload } = await jwtDecrypt(cookie, key());
    return payload as unknown as Session;
  } catch {
    // Tampered, expired, or encrypted under a secret that has since been rotated. All three mean
    // the same thing to a caller -- no session -- and none of them is worth a 500.
    return null;
  }
}

export async function deleteSession(): Promise<void> {
  (await cookies()).delete(COOKIE_NAME);
}
