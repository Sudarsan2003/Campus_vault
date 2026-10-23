/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: { 950: '#0A1426', 900: '#0E1B33', 800: '#16284A', 700: '#223864', 600: '#3B4F78', 400: '#8A97B1', 300: '#B6C0D4' },
        paper: { DEFAULT: '#F5F7FA', 200: '#E9EDF3', 300: '#D9DFE9' },
        chain: { DEFAULT: '#2B4BF2', dark: '#1E37C4', soft: '#E8ECFE' },
        verified: { DEFAULT: '#14906A', soft: '#E3F5EE' },
        pending: { DEFAULT: '#B7791F', soft: '#FBF1DC' },
        alert: { DEFAULT: '#C0392B', soft: '#FBE7E4' },
      },
      fontFamily: {
        sans: ['"Schibsted Grotesk"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      keyframes: {
        'block-in': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'link-grow': {
          '0%': { transform: 'scaleY(0)' },
          '100%': { transform: 'scaleY(1)' },
        },
        'confirm-flip': {
          '0%, 55%': { opacity: '1' },
          '60%, 100%': { opacity: '0' },
        },
        'confirm-flip-in': {
          '0%, 55%': { opacity: '0' },
          '60%, 100%': { opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};