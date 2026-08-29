import type { Metadata } from "next";
import { redirect } from "next/navigation";

import { signOut } from "@/app/actions/auth";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Dashboard" };

export default async function DashboardPage() {
  const session = await getSession();
  // Guarded here rather than in middleware: the check runs where the data is read, so a route that
  // forgets it cannot leak. Middleware is the optimisation, not the security boundary.
  if (!session) {
    redirect("/login");
  }

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-6 py-16">
      <div className="flex flex-col items-center gap-2 text-center">
        <h1 className="text-2xl font-semibold tracking-tight">Signed in</h1>
        <p className="text-sm text-zinc-500">
          {session.email} · workspace <span className="font-mono">{session.tenantSlug}</span>
        </p>
      </div>

      <p className="max-w-sm text-center text-sm text-zinc-500">
        Deliberately blank. This is the placeholder the sign-in flow lands on.
      </p>

      <form action={signOut}>
        <button
          type="submit"
          className="h-10 rounded-md border border-black/15 px-4 text-sm font-medium transition-colors hover:bg-black/[.04] dark:border-white/20 dark:hover:bg-white/[.06]"
        >
          Sign out
        </button>
      </form>
    </main>
  );
}
