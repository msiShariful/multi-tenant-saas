import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  // A template so each page supplies only its own name; the default covers the
  // root, which has no title of its own. Note the template applies to child
  // segments only, so a title set in this segment's page.tsx is used verbatim.
  title: {
    default: "TenantBase",
    template: "%s · TenantBase",
  },
  description: "Multi-tenant SaaS platform — tenant sign-up, accounts, and access.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      {/* Browser extensions (ColorZilla, Grammarly, …) mutate <body> before React
          hydrates, which React reports as a mismatch. This suppresses the warning
          for this element's own attributes only — one level deep, so a genuine
          mismatch inside the tree is still reported. */}
      <body className="min-h-full flex flex-col" suppressHydrationWarning>
        {children}
      </body>
    </html>
  );
}
