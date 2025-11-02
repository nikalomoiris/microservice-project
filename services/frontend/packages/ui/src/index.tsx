import React from 'react'

export const Button: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <button type="button" style={{ padding: '8px 12px', borderRadius: 6 }}>
      {children}
    </button>
  )
}

export default Button
