# Contributing to PVZRH Android Launcher

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch (`git checkout -b feature/my-feature`)
4. Make your changes
5. Commit and push
6. Open a Pull Request

## Development Environment

- Android Studio Ladybug or later
- JDK 17
- Android SDK with NDK 27.0.12077973 and CMake 3.22.1

## Build

```powershell
.\gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Code Style

- Kotlin, Jetpack Compose
- Follow existing patterns in the codebase
- Keep comments minimal — only describe *what* and *why*, not *how*
- No "fix: xxx" style comments in code
- Code comments max 2 lines

## Commit Convention

Use clear, descriptive commit messages:

- `feat: add new feature`
- `fix: resolve issue`
- `refactor: improve code structure`
- `docs: update documentation`
- `ci: change CI configuration`

## Pull Requests

- Keep PRs focused on a single change
- Describe what you changed and why
- Test on a real device before submitting
- Ensure the build passes

## Reporting Issues

- Use GitHub Issues
- Include device model, Android version, and launcher version
- Attach crash logs if available
- Describe steps to reproduce

## License

By contributing, you agree that your contributions will be licensed under the GPL-3.0 License.
