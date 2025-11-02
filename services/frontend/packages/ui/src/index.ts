export const Button = ({ children }: { children: string }) => {
  return (
    // minimal button placeholder
    // eslint-disable-next-line react/button-has-type
    <button style={{ padding: '8px 12px', borderRadius: 6 }}>{children}</button>
  )
}
