/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Midnight Mirage: primary dark / base (app shell, headings on dark)
        midnight: { DEFAULT: '#04243C', 700: '#0A3552', 600: '#10466A' },
        // Teal Blue: accent. #2B9CAE is the brand colour (focus rings, icons, active markers).
        // Solid button fills use 600 (#237F8F), which gives 4.66:1 against white (WCAG AA); the raw
        // accent only reaches about 3.2:1.
        brand: { DEFAULT: '#2B9CAE', 50: '#EAF6F8', 100: '#D2EBEF', 600: '#237F8F', 700: '#1C6573' },
        // Charcoal: neutral dark / headings
        charcoal: '#202124',
        ink: '#2C3E50',
        // Subtle cool-gray surfaces and refined slate borders
        surface: { DEFAULT: '#F8FAFC', 2: '#F1F5F9' },
        line: '#E2E8F0',
        // Severity badges. DEFAULT is the brief's exact hex (dots, borders, filled buttons).
        // Badge text uses the darker `ink` shade on the `tint` background so it stays AA-readable.
        sev: {
          critical: { DEFAULT: '#DC2626', tint: '#FEF2F2', ink: '#991B1B' }, // Critical / Blocker
          high: { DEFAULT: '#EA580C', tint: '#FFF7ED', ink: '#9A3412' },
          medium: { DEFAULT: '#2563EB', tint: '#EFF6FF', ink: '#1E40AF' },
          low: { DEFAULT: '#475569', tint: '#F1F5F9', ink: '#334155' },
        },
        // Workflow status pills
        status: {
          open: { DEFAULT: '#2563EB', tint: '#EFF6FF', ink: '#1E40AF' },
          progress: { DEFAULT: '#2B9CAE', tint: '#EAF6F8', ink: '#1C6573' },
          resolved: { DEFAULT: '#16A34A', tint: '#F0FDF4', ink: '#166534' },
          closed: { DEFAULT: '#64748B', tint: '#F1F5F9', ink: '#334155' },
          reopened: { DEFAULT: '#D97706', tint: '#FFFBEB', ink: '#92400E' },
        },
      },
      fontFamily: {
        sans: [
          'Inter Variable',
          'Inter',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '6px',
        md: '6px',
        lg: '8px',
      },
      boxShadow: {
        card: '0 1px 2px 0 rgb(4 36 60 / 0.06)',
        pop: '0 10px 30px -8px rgb(4 36 60 / 0.25)',
      },
      maxWidth: {
        page: '88rem',
      },
      keyframes: {
        fadeIn: { from: { opacity: '0' }, to: { opacity: '1' } },
      },
      animation: {
        'fade-in': 'fadeIn 120ms ease-out',
      },
    },
  },
  plugins: [],
};
