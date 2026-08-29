import type { Metadata } from "next";
import { redirect } from "next/navigation";

import LoginForm from "./login-form";
import { getSession } from "@/lib/session";

export const metadata: Metadata = { title: "Sign in" };

export default async function LoginPage() {
  // Reading the session makes this route dynamic, which is correct -- a cached sign-in page could
  // be served to someone who is already signed in.
  if (await getSession()) {
    redirect("/dashboard");
  }

  return (
    <main className="flex flex-1 items-center justify-center px-6 py-16">
      <div className="flex w-full max-w-sm flex-col gap-8">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold tracking-tight">Sign in</h1>
          <p className="text-sm text-zinc-500">Sign in to your TenantBase workspace.</p>
        </div>
        <LoginForm />
      </div>
    </main>
  );
}
