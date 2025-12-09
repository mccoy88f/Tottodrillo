# Contributing to Tottodrillo

Thank you for your interest in contributing to Tottodrillo! 🎉

## How to Contribute

### Reporting Bugs

If you find a bug, please open an [issue](https://github.com/mccoy88f/Tottodrillo/issues) including:
- Description of the problem
- Steps to reproduce it
- Expected vs actual behavior
- Screenshots (if applicable)
- Android version and device model

### Proposing Features

To propose new features:
1. Check that it hasn't already been proposed in [issues](https://github.com/mccoy88f/Tottodrillo/issues)
2. Open a new issue with the "enhancement" label
3. Describe the feature and its added value

### Pull Requests

1. Fork the project
2. Create a branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Development Guidelines

### Code Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Max line length: 120 characters
- Organize imports alphabetically

### Architecture

- Maintain separation between layers (data/domain/presentation)
- Use dependency injection with Hilt
- ViewModel for business logic
- Repository pattern for data access

### Testing

- Write unit tests for business logic
- Add UI tests for critical flows
- Ensure all tests pass before submitting a PR

### Commit Messages

Use the [Conventional Commits](https://www.conventionalcommits.org/) format:

```
feat: add download queue feature
fix: resolve crash on search
docs: update README
style: format code
refactor: simplify download manager
test: add unit tests for repository
chore: update dependencies
```

## Development Environment Setup

1. Install Android Studio Hedgehog or higher
2. Android SDK API 34
3. JDK 17
4. Clone the repository and sync Gradle

## Questions?

Open a [discussion](https://github.com/mccoy88f/Tottodrillo/discussions) for general questions.

Thank you for contributing! 🙏
