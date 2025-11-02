import './globals.css'

export const metadata = {
  title: 'Microservices Frontend',
  description: 'Frontend scaffold for microservices-project'
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main>{children}</main>
      </body>
    </html>
  )
}
