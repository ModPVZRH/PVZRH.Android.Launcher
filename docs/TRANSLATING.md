# Translation Guide

Thank you for your interest in translating PVZRH Launcher! This guide will help you get started.

## Getting Started with GitLocalize

We use [GitLocalize](https://gitlocalize.com/) to manage translations.

1. Go to [gitlocalize.com](https://gitlocalize.com/)
2. Sign in with your GitHub account
3. Find our project: `ModPVZRH/modpvzrh.github.io`
4. Select your language and start translating

GitLocalize parses files into translatable segments and highlights missing translations. You can see diffs when source files change.

## How to Add a New Language

1. Fork the [documentation repository](https://github.com/ModPVZRH/modpvzrh.github.io)
2. Create a new directory under the root for your locale (e.g., `ja/` for Japanese)
3. Copy all `.md` files from the root directory into your new locale directory
4. Translate the content of each file
5. Add your locale configuration in `.vitepress/config.js`:

```javascript
ja: {
  label: '日本語',
  lang: 'ja-JP',
  link: '/ja/',
  themeConfig: {
    nav: [
      { text: 'ホーム', link: '/ja/' },
    ],
    sidebar: [
      // ... translated sidebar
    ]
  }
}
```

6. Submit a Pull Request

## Translation Guidelines

### File Format
- Keep the Markdown structure intact
- Do not translate code blocks, URLs, or technical identifiers
- Keep YAML frontmatter keys (like `title`) translated

### Tone
- Use a friendly, helpful tone
- Keep translations natural and idiomatic

### Technical Terms
Some terms are kept in English even in translations:
BepInEx, .NET, libunity, IL2CPP, Git, GitHub, DLL, APK, BepInEx Launcher

### Locale Codes
Use standard ISO 639-1 codes:
- `zh` - 简体中文
- `zh-TW` - 繁體中文
- `ja` - 日本語
- `ko` - 한국어
- `es` - Español
- `pt-BR` - Português (Brasil)

## Testing Locally

```bash
cd modpvzrh.github.io
npm install
npm run dev
```

Then open `http://localhost:5173/<locale>/` to preview your translations.

## Questions?

If you have questions, feel free to open a [Discussion](https://github.com/ModPVZRH/PVZRH.Android.Launcher/discussions) on GitHub.
