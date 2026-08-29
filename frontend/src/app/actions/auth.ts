"use server";

import { redirect } from "next/navigation";

import { login } from "@/lib/auth-service";
import { createSession, deleteSession } from "@/lib/session";

export type SignInState = { error?: string; tenantSlug?: string; email?: string };

export async function signIn(
  _previous: SignInState,
  formData: FormData,
): Promise<SignInState> {
  const tenantSlug = String(formData.get("tenantSlug") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");

  // The browser enforces `required` too, but that is a convenience, not a check -- this action is a
  // POST endpoint and anything can call it.
  if (!tenantSlug || !email || !password) {
    return { error: "Workspace, email and password are all required.", tenantSlug, email };
  }

  const result = await login(tenantSlug, email, password);
  if (!result.ok) {
    // Echo the identifiers back so a failed attempt does not clear the whole form; never the password.
    return { error: result.message, tenantSlug, email };
  }

  await createSession({
    accessToken: result.tokens.accessToken,
    refreshToken: result.tokens.refreshToken,
    accessTokenExpiresAt: Date.now() + result.tokens.expiresIn * 1000,
    tenantSlug,
    email,
  });

  // Outside any try/catch by design: redirect() signals by throwing NEXT_REDIRECT, so a catch
  // around it would swallow the navigation and look like a login that silently did nothing.
  redirect("/dashboard");
}

export async function signOut(): Promise<void> {
  // Only drops this browser's session. auth-service's /auth/logout would also revoke the refresh
  // token server-side; wiring that up belongs with the apiFetch helper in the README's Phase 1.
  await deleteSession();
  redirect("/login");
}
