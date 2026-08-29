import "server-only";

/** The subset of auth-service's RFC 9457 ApiError this app reads. */
type ApiError = {
  code?: string;
  detail?: string;
  lockedUntil?: string;
};

/** auth-service's TokenResponse. Field names follow RFC 6749. */
type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  issuedAt: string;
};

export type LoginResult =
  | { ok: true; tokens: TokenResponse }
  | { ok: false; message: string };

function baseUrl(): string {
  return process.env.AUTH_SERVICE_URL ?? "http://localhost:8081";
}

/**
 * Copy for each failure is chosen by the stable `code`, never by `detail`. Re-wording a message in
 * auth-service must not change what this UI does, and the codes are the documented contract.
 */
function messageFor(status: number, error: ApiError): string {
  switch (error.code) {
    case "INVALID_CREDENTIALS":
      // Deliberately as vague as the backend's: it answers unknown tenant, unknown email and wrong
      // password identically so the form cannot be used to enumerate either.
      return "Invalid workspace, email or password.";
    case "ACCOUNT_LOCKED": {
      const until = error.lockedUntil ? new Date(error.lockedUntil) : null;
      return until && !Number.isNaN(until.getTime())
        ? `Too many failed attempts. Try again after ${until.toLocaleTimeString()}.`
        : "Too many failed attempts. This account is temporarily locked.";
    }
    case "ACCOUNT_DISABLED":
      return "This account has been disabled. Contact your administrator.";
    case "TENANT_SUSPENDED":
      return "This workspace is suspended. Contact your administrator.";
    default:
      return `Sign-in failed (${status}). Please try again.`;
  }
}

export async function login(
  tenantSlug: string,
  email: string,
  password: string,
): Promise<LoginResult> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl()}/api/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tenantSlug, email, password }),
      cache: "no-store",
    });
  } catch {
    // auth-service down or unreachable. Distinguished from a rejected credential on purpose: the
    // user can do something about one of these and nothing about the other.
    return {
      ok: false,
      message: "Cannot reach the authentication service. Is auth-service running on :8081?",
    };
  }

  if (response.ok) {
    return { ok: true, tokens: (await response.json()) as TokenResponse };
  }

  // A non-JSON error body is possible (a proxy, a crash), so parsing must not throw over the failure.
  const error: ApiError = await response.json().catch(() => ({}));
  return { ok: false, message: messageFor(response.status, error) };
}
