"use client";

import { useActionState } from "react";

import { signIn, type SignInState } from "@/app/actions/auth";

const initialState: SignInState = {};

const fieldClass =
  "w-full rounded-md border border-black/15 bg-white px-3 py-2 text-sm outline-none " +
  "placeholder:text-zinc-400 focus:border-zinc-900 dark:border-white/20 dark:bg-zinc-900 " +
  "dark:focus:border-zinc-100";

export default function LoginForm() {
  const [state, formAction, pending] = useActionState(signIn, initialState);

  return (
    <form action={formAction} className="flex w-full flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <label htmlFor="tenantSlug" className="text-sm font-medium">
          Workspace
        </label>
        <input
          id="tenantSlug"
          name="tenantSlug"
          required
          autoComplete="organization"
          placeholder="acme"
          defaultValue={state.tenantSlug}
          className={fieldClass}
        />
        <p className="text-xs text-zinc-500">
          Your tenant&apos;s slug. Email is unique per workspace, not globally.
        </p>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="email" className="text-sm font-medium">
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          required
          autoComplete="username"
          placeholder="admin@acme.example"
          defaultValue={state.email}
          className={fieldClass}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="password" className="text-sm font-medium">
          Password
        </label>
        <input
          id="password"
          name="password"
          type="password"
          required
          autoComplete="current-password"
          className={fieldClass}
        />
      </div>

      {state.error && (
        // aria-live so the failure is announced: submitting swaps the message in without a
        // navigation, which a screen reader would otherwise not report at all.
        <p
          role="alert"
          aria-live="polite"
          className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/50 dark:text-red-300"
        >
          {state.error}
        </p>
      )}

      <button
        type="submit"
        disabled={pending}
        className="mt-2 h-10 rounded-md bg-zinc-900 text-sm font-medium text-white transition-colors hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
      >
        {pending ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
